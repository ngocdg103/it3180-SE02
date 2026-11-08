package com.bluemoon.services;

import com.bluemoon.dao.BillingInputs;
import com.bluemoon.dao.BillingPeriodExistsException;
import com.bluemoon.dao.BillingRepository;
import com.bluemoon.models.Account;
import com.bluemoon.models.AppliedRate;
import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.BillingPeriodDetail;
import com.bluemoon.models.BillingPreview;
import com.bluemoon.models.MonthlyCharge;
import com.bluemoon.models.Permission;
import com.bluemoon.services.BillingResult.Status;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * US-007 service. Authorization first, then validation, then storage. The current month is always taken from
 * the injected Clock in Asia/Ho_Chi_Minh (Q11). generatePeriod is one all-or-nothing transaction whose unique
 * ky_thu key prevents duplicates and serializes concurrent requests.
 */
public final class BillingServiceImpl implements BillingService {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String MSG_KY_FORMAT = "Kỳ thu phải theo định dạng YYYY-MM!";
    static final String MSG_KY_NOT_CURRENT = "Chỉ được lập kỳ thu cho tháng hiện tại (%s)!";
    static final String MSG_DUPLICATE = "Kỳ thu %s đã tồn tại trên hệ thống!";
    static final String MSG_MISSING_RATE = "Chưa có đơn giá hiệu lực cho khoản thu: %s. "
            + "Vui lòng cập nhật đơn giá trước khi lập kỳ thu.";
    static final String MSG_MISSING_RATE_FIELD = "Chưa có đơn giá hiệu lực tại ngày đầu kỳ.";
    static final String MSG_NOTHING = "Không có khoản thu bắt buộc đang áp dụng hoặc không có hộ nào để lập kỳ thu.";
    static final String MSG_NOT_FOUND = "Không tìm thấy dữ liệu yêu cầu.";
    static final String MSG_MA_HOA_DON = "Vui lòng nhập mã hóa đơn!";
    static final String MSG_SO_CAN_HO = "Vui lòng nhập số căn hộ!";
    static final String MSG_BUSY = "Hệ thống đang bận, vui lòng thử lại sau.";
    static final String MSG_SYSTEM = "Lỗi hệ thống, vui lòng thử lại sau.";
    private static final int LOCK_WAIT_TIMEOUT = 1205;
    private static final int DEADLOCK = 1213;

    private final BillingRepository repository;
    private final AuthorizationService authorization;
    private final Clock clock;
    private final Supplier<Optional<Account>> currentUser;

    /** Production: new BillingServiceImpl(new BillingDAO(db), Clock.systemUTC()). The session is SessionContext. */
    public BillingServiceImpl(BillingRepository repository, Clock clock) {
        this(repository, new RoleBasedAuthorizationService(null), clock, SessionContext::getCurrentUser);
    }

    public BillingServiceImpl(BillingRepository repository, AuthorizationService authorization, Clock clock) {
        this(repository, authorization, clock, SessionContext::getCurrentUser);
    }

    public BillingServiceImpl(BillingRepository repository, AuthorizationService authorization, Clock clock,
                              Supplier<Optional<Account>> currentUser) {
        this.repository = Objects.requireNonNull(repository);
        this.authorization = Objects.requireNonNull(authorization);
        this.clock = Objects.requireNonNull(clock);
        this.currentUser = Objects.requireNonNull(currentUser);
    }

    @Override
    public BillingResult<BillingPreview> previewPeriod(String kyThu) {
        authorization.requirePermission(Permission.BILLING_PERIOD_CREATE);
        var invalid = validateCurrentMonth(kyThu);
        if (invalid != null) { return invalid.cast(); }
        var period = YearMonth.parse(kyThu);
        try {
            var existing = repository.findPeriod(kyThu);
            if (existing.isPresent()) { return BillingResult.success("OK", storedPreview(existing.get())); }
            var inputs = repository.inReadOnlyTransaction(tx -> tx.loadInputs(period));
            var hanNop = BillingCalculator.hanNop(period);
            var early = precheck(inputs);
            if (early != null) { return early.cast(); }
            var calc = BillingCalculator.compute(period, hanNop, inputs);
            if (!calc.missingRateCodes().isEmpty()) { return missingRate(calc.missingRateCodes()).cast(); }
            if (calc.charges().isEmpty()) { return nothingToBill().cast(); }
            return BillingResult.success("OK", new BillingPreview(kyThu, hanNop, false, calc.charges(),
                    calc.skipped(), calc.warnings(), calc.rates(), calc.total()));
        } catch (SQLException | RuntimeException failure) {
            return systemOrBusy(failure);
        }
    }

    @Override
    public BillingResult<BillingPeriod> generatePeriod(String kyThu) {
        authorization.requirePermission(Permission.BILLING_PERIOD_CREATE);
        var invalid = validateCurrentMonth(kyThu);
        if (invalid != null) { return invalid.cast(); }
        var period = YearMonth.parse(kyThu);
        long createdBy = currentUser.get().map(Account::userId).orElseThrow(() ->
                new AccessDeniedException("Bạn không có quyền thực hiện chức năng này."));
        for (int attempt = 0; ; attempt++) {
            try {
                return repository.inTransaction(tx -> generate(tx, period, createdBy));
            } catch (BillingPeriodExistsException exists) {
                return duplicate(kyThu);
            } catch (SQLException failure) {
                if (failure.getErrorCode() == DEADLOCK) {
                    // A deadlock is not proof of a duplicate: look at the committed state first.
                    try {
                        if (repository.findPeriod(kyThu).isPresent()) { return duplicate(kyThu); }
                    } catch (SQLException | RuntimeException readFailure) {
                        return systemError();
                    }
                    if (attempt == 0) { continue; }
                    return busy();
                }
                return systemOrBusy(failure);
            } catch (RuntimeException failure) {
                return systemError();
            }
        }
    }

    private BillingResult<BillingPeriod> generate(com.bluemoon.dao.BillingTx tx, YearMonth period, long createdBy)
            throws SQLException, BillingPeriodExistsException {
        String kyThu = period.toString();
        Instant now = clock.instant();
        var hanNop = BillingCalculator.hanNop(period);
        // 1. Take the unique ky_thu key first: serializes concurrent requests for the same period.
        tx.insertPeriod(new BillingPeriod(kyThu, hanNop, 0, 0, java.math.BigDecimal.ZERO, now), createdBy);
        // 2. Read everything on this same connection (one snapshot), then compute purely.
        var inputs = tx.loadInputs(period);
        var early = precheck(inputs);
        if (early != null) { tx.rollbackOnly(); return early.cast(); }
        var calc = BillingCalculator.compute(period, hanNop, inputs);
        if (!calc.missingRateCodes().isEmpty()) { tx.rollbackOnly(); return missingRate(calc.missingRateCodes()).cast(); }
        if (calc.charges().isEmpty()) { tx.rollbackOnly(); return nothingToBill().cast(); }
        // 3. Persist the snapshot and the totals; any exception rolls everything back.
        tx.insertInvoices(calc.charges(), now);
        tx.finalizePeriod(kyThu, calc.charges().size(), calc.skipped().size(), calc.total());
        return BillingResult.success("Lập kỳ thu " + kyThu + " thành công",
                new BillingPeriod(kyThu, hanNop, calc.charges().size(), calc.skipped().size(), calc.total(), now));
    }

    @Override
    public BillingResult<List<BillingPeriod>> listPeriods() {
        authorization.requirePermission(Permission.DEBT_REPORT_VIEW);
        try {
            return BillingResult.success("OK", repository.findAllPeriods());
        } catch (SQLException | RuntimeException failure) {
            return systemOrBusy(failure);
        }
    }

    @Override
    public BillingResult<BillingPeriodDetail> getPeriod(String kyThu) {
        authorization.requirePermission(Permission.DEBT_REPORT_VIEW);
        try {
            BillingPeriod.parseKyThu(kyThu);
        } catch (IllegalArgumentException bad) {
            return fieldFailure(Status.VALIDATION_ERROR, MSG_KY_FORMAT, "ky_thu", MSG_KY_FORMAT);
        }
        try {
            var period = repository.findPeriod(kyThu);
            if (period.isEmpty()) { return notFound(); }
            return BillingResult.success("OK",
                    new BillingPeriodDetail(period.get(), repository.findCharges(kyThu, false)));
        } catch (SQLException | RuntimeException failure) {
            return systemOrBusy(failure);
        }
    }

    @Override
    public BillingResult<MonthlyCharge> getInvoice(String maHoaDon) {
        authorization.requirePermission(Permission.DEBT_REPORT_VIEW);
        if (maHoaDon == null || maHoaDon.isBlank()) {
            return fieldFailure(Status.VALIDATION_ERROR, MSG_MA_HOA_DON, "ma_hoa_don", MSG_MA_HOA_DON);
        }
        try {
            return repository.findInvoice(maHoaDon.trim()).map(c -> BillingResult.success("OK", c))
                    .orElseGet(BillingServiceImpl::notFound);
        } catch (SQLException | RuntimeException failure) {
            return systemOrBusy(failure);
        }
    }

    @Override
    public BillingResult<List<MonthlyCharge>> getApartmentHistory(String soCanHo) {
        authorization.requirePermission(Permission.DEBT_REPORT_VIEW);
        if (soCanHo == null || soCanHo.isBlank()) {
            return fieldFailure(Status.VALIDATION_ERROR, MSG_SO_CAN_HO, "so_can_ho", MSG_SO_CAN_HO);
        }
        try {
            return BillingResult.success("OK", repository.findChargesByApartment(soCanHo.trim()));
        } catch (SQLException | RuntimeException failure) {
            return systemOrBusy(failure);
        }
    }

    // ---- helpers ----

    /** Failure carrier that can be re-typed; avoids unchecked casts at every call site. */
    private record Failure(Status status, String message, Map<String, String> fieldErrors) {
        <T> BillingResult<T> cast() { return BillingResult.failure(status, message, fieldErrors); }
    }

    private Failure validateCurrentMonth(String kyThu) {
        try {
            BillingPeriod.parseKyThu(kyThu);
        } catch (IllegalArgumentException bad) {
            return new Failure(Status.VALIDATION_ERROR, MSG_KY_FORMAT, Map.of("ky_thu", MSG_KY_FORMAT));
        }
        var current = YearMonth.from(clock.instant().atZone(BUSINESS_ZONE));
        if (!YearMonth.parse(kyThu).equals(current)) {
            String message = String.format(MSG_KY_NOT_CURRENT, current);
            return new Failure(Status.VALIDATION_ERROR, message, Map.of("ky_thu", message));
        }
        return null;
    }

    private static Failure precheck(BillingInputs inputs) {
        return inputs.fees().isEmpty() || inputs.households().isEmpty() ? nothingToBill() : null;
    }

    private static Failure nothingToBill() { return new Failure(Status.NOTHING_TO_BILL, MSG_NOTHING, Map.of()); }

    private static Failure missingRate(List<String> codes) {
        var fields = new LinkedHashMap<String, String>();
        codes.forEach(code -> fields.put(code, MSG_MISSING_RATE_FIELD));
        return new Failure(Status.MISSING_RATE, String.format(MSG_MISSING_RATE, String.join(", ", codes)), fields);
    }

    private static <T> BillingResult<T> duplicate(String kyThu) {
        String message = String.format(MSG_DUPLICATE, kyThu);
        return fieldFailure(Status.DUPLICATE, message, "ky_thu", message);
    }

    private static <T> BillingResult<T> notFound() {
        return BillingResult.failure(Status.NOT_FOUND, MSG_NOT_FOUND, Map.of());
    }

    private static <T> BillingResult<T> busy() { return BillingResult.failure(Status.BUSY, MSG_BUSY, Map.of()); }

    private static <T> BillingResult<T> systemError() {
        return BillingResult.failure(Status.SYSTEM_ERROR, MSG_SYSTEM, Map.of());
    }

    private static <T> BillingResult<T> systemOrBusy(Exception failure) {
        if (failure instanceof SQLException sql && (sql.getErrorCode() == LOCK_WAIT_TIMEOUT
                || sql.getErrorCode() == DEADLOCK)) {
            return busy();
        }
        return systemError();
    }

    private static <T> BillingResult<T> fieldFailure(Status status, String message, String field, String fieldMessage) {
        return BillingResult.failure(status, message, Map.of(field, fieldMessage));
    }

    /** Stored snapshot only: never recomputed from current data. */
    private BillingPreview storedPreview(BillingPeriod period) throws SQLException {
        var charges = repository.findCharges(period.kyThu(), true);
        var rates = new LinkedHashMap<String, AppliedRate>();
        for (var charge : charges) {
            for (var line : charge.lines()) {
                rates.putIfAbsent(line.maKhoanThu(),
                        new AppliedRate(line.maKhoanThu(), line.donGia(), line.ngayApDungGia()));
            }
        }
        return new BillingPreview(period.kyThu(), period.hanNop(), true, charges, List.of(), List.of(),
                List.copyOf(rates.values()), period.tongTien());
    }
}
