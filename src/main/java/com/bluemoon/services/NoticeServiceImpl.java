package com.bluemoon.services;

import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.MonthlyCharge;
import com.bluemoon.models.NoticeSlip;
import com.bluemoon.models.Permission;
import com.bluemoon.services.BillingResult.Status;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Builds notices from BillingService reads (stored snapshot) plus the household head name. */
public final class NoticeServiceImpl implements NoticeService {
    /** Head of household name, or empty. Production: ResidentRepository.findHead. */
    @FunctionalInterface public interface HeadNames { Optional<String> headName(String maHo) throws SQLException; }

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String MSG_NO_PERIOD = "Chưa có dữ liệu hóa đơn cho kỳ thu này. Vui lòng lập kỳ thu trước!";
    static final String MSG_KY_FORMAT = "Kỳ thu phải theo định dạng YYYY-MM!";
    static final String MSG_APARTMENT = "Vui lòng chọn căn hộ!";
    static final String MSG_NO_INVOICE = "Không có hóa đơn của căn hộ này trong kỳ thu.";
    static final String MSG_SYSTEM = "Lỗi hệ thống, vui lòng thử lại sau.";

    private final BillingService billing;
    private final HeadNames heads;
    private final AuthorizationService authorization;
    private final Clock clock;

    public NoticeServiceImpl(BillingService billing, HeadNames heads, AuthorizationService authorization, Clock clock) {
        this.billing = Objects.requireNonNull(billing);
        this.heads = Objects.requireNonNull(heads);
        this.authorization = Objects.requireNonNull(authorization);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override public BillingResult<NoticeSlip> getNotice(String kyThu, String soCanHo) {
        authorization.requirePermission(Permission.DEBT_REPORT_VIEW);
        var invalid = validate(kyThu);
        if (invalid != null) { return invalid.cast(); }
        if (soCanHo == null || soCanHo.isBlank()) {
            return BillingResult.failure(Status.VALIDATION_ERROR, MSG_APARTMENT, Map.of("so_can_ho", MSG_APARTMENT));
        }
        var period = billing.getPeriod(kyThu);
        if (!period.isSuccess()) { return translate(period); }
        var header = period.data().orElseThrow().charges().stream()
                .filter(c -> c.soCanHo().equals(soCanHo.trim())).findFirst();
        if (header.isEmpty()) { return BillingResult.failure(Status.NOT_FOUND, MSG_NO_INVOICE, Map.of()); }
        return slip(header.get());
    }

    @Override public BillingResult<List<NoticeSlip>> getNotices(String kyThu) {
        authorization.requirePermission(Permission.DEBT_REPORT_VIEW);
        var invalid = validate(kyThu);
        if (invalid != null) { return invalid.cast(); }
        var period = billing.getPeriod(kyThu);
        if (!period.isSuccess()) { return translate(period); }
        var slips = new ArrayList<NoticeSlip>();
        for (MonthlyCharge header : period.data().orElseThrow().charges()) {
            var one = slip(header);
            if (!one.isSuccess()) { return BillingResult.failure(one.status(), one.message(), one.fieldErrors()); }
            slips.add(one.data().orElseThrow());
        }
        return BillingResult.success("OK", slips);
    }

    private BillingResult<NoticeSlip> slip(MonthlyCharge header) {
        var invoice = billing.getInvoice(header.maHoaDon());
        if (invoice.status() == Status.NOT_FOUND) {
            return BillingResult.failure(Status.NOT_FOUND, MSG_NO_INVOICE, Map.of());
        }
        if (!invoice.isSuccess()) { return translate(invoice); }
        MonthlyCharge stored = invoice.data().orElseThrow();
        String head;
        try {
            head = heads.headName(stored.maHo()).orElse(null);
        } catch (SQLException | RuntimeException failure) {
            return BillingResult.failure(Status.SYSTEM_ERROR, MSG_SYSTEM, Map.of());
        }
        var month = BillingPeriod.parseKyThu(stored.kyThu());
        String mmYyyy = String.format("%02d/%d", month.getMonthValue(), month.getYear());
        return BillingResult.success("OK", new NoticeSlip("TB-" + stored.kyThu().replace("-", "") + "-" + stored.soCanHo(),
                stored.kyThu(), stored.soCanHo(), head, stored.dienTich(), stored.soNhanKhau(), stored.lines(),
                stored.tongTien(), stored.hanNop(), clock.instant().atZone(BUSINESS_ZONE).toLocalDate(),
                stored.soCanHo() + " Dong phi thang " + mmYyyy));
    }

    private static <T, U> BillingResult<U> translate(BillingResult<T> failed) {
        if (failed.status() == Status.NOT_FOUND) {
            return BillingResult.failure(Status.NOT_FOUND, MSG_NO_PERIOD, Map.of("ky_thu", MSG_NO_PERIOD));
        }
        return BillingResult.failure(failed.status(), failed.message(), failed.fieldErrors());
    }

    private record Failure(Status status, String message, Map<String, String> fieldErrors) {
        <T> BillingResult<T> cast() { return BillingResult.failure(status, message, fieldErrors); }
    }

    private static Failure validate(String kyThu) {
        try {
            BillingPeriod.parseKyThu(kyThu);
            return null;
        } catch (IllegalArgumentException bad) {
            return new Failure(Status.VALIDATION_ERROR, MSG_KY_FORMAT, Map.of("ky_thu", MSG_KY_FORMAT));
        }
    }
}
