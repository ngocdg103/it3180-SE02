package com.bluemoon.services;

import com.bluemoon.dao.FeeRateRepository;
import com.bluemoon.dao.FeeTypeRepository;
import com.bluemoon.models.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import static com.bluemoon.services.FeeRateResult.Status.*;

/** DAO insert owns the atomic parent eligibility check. UI/prechecks cannot replace its FOR UPDATE guard. */
public final class FeeRateServiceImpl implements FeeRateService {
    private static final String CODE_ERROR = "Vui lòng chọn khoản thu cần đặt giá!";
    private static final String PRICE_ERROR = "Đơn giá phải là số dương lớn hơn 0!";
    private static final String DATE_ERROR = "Ngày áp dụng không hợp lệ!";
    private final FeeRateRepository rates;
    private final FeeTypeRepository types;
    private final AuthorizationService authorization;

    public FeeRateServiceImpl(FeeRateRepository rates, FeeTypeRepository types, AuthorizationService authorization) {
        this.rates = Objects.requireNonNull(rates);
        this.types = Objects.requireNonNull(types);
        this.authorization = Objects.requireNonNull(authorization);
    }
    @Override public FeeRateResult<List<FeeType>> listFeeTypes() {
        authorization.requirePermission(Permission.FEE_RATE_VIEW);
        try {
            return FeeRateResult.success("OK", types.findAll(null).stream()
                    .filter(type -> type.loai() == FeeKind.BAT_BUOC).toList());
        } catch (SQLException failure) { return readFailure(failure); }
    }
    @Override public FeeRateResult<List<FeeRate>> listHistory(String code) {
        authorization.requirePermission(Permission.FEE_RATE_VIEW);
        if (!validCode(code)) { return invalid(Map.of("ma_khoan_thu", CODE_ERROR)); }
        try { return FeeRateResult.success("OK", List.copyOf(rates.findHistory(code))); }
        catch (SQLException failure) { return readFailure(failure); }
    }
    @Override public FeeRateResult<Optional<FeeRate>> getEffectiveRate(String code, LocalDate date) {
        authorization.requirePermission(Permission.FEE_RATE_VIEW);
        var errors = new LinkedHashMap<String, String>();
        if (!validCode(code)) { errors.put("ma_khoan_thu", CODE_ERROR); }
        if (!validDate(date)) { errors.put("ngay_ap_dung", DATE_ERROR); }
        if (!errors.isEmpty()) { return invalid(errors); }
        try { return FeeRateResult.success("OK", rates.findEffectiveRate(code, date)); }
        catch (SQLException failure) { return readFailure(failure); }
    }
    @Override public FeeRateResult<FeeRate> createRate(FeeRateForm form) {
        authorization.requirePermission(Permission.FEE_RATE_CREATE);
        var input = form == null ? new FeeRateForm(null, null, null, null) : form;
        var errors = new LinkedHashMap<String, String>();
        if (!validCode(input.maKhoanThu())) { errors.put("ma_khoan_thu", CODE_ERROR); }
        BigDecimal price = null;
        String text = input.donGia() == null ? "" : input.donGia().trim();
        // Plain decimal only: UI uses '.' as decimal separator. No grouping, exponent, NaN or double conversion.
        if (!text.matches("[0-9]{1,17}(\\.[0-9]{1,2})?")) { errors.put("don_gia", PRICE_ERROR); }
        else {
            price = new BigDecimal(text);
            if (price.signum() <= 0) { errors.put("don_gia", PRICE_ERROR); }
        }
        if (!validDate(input.ngayApDung())) { errors.put("ngay_ap_dung", DATE_ERROR); }
        if (input.ghiChu() != null && input.ghiChu().codePointCount(0, input.ghiChu().length()) > 255) {
            errors.put("ghi_chu", "Ghi chú tối đa 255 ký tự!");
        }
        if (!errors.isEmpty()) { return invalid(errors); }
        var rate = new FeeRate(null, input.maKhoanThu(), price, input.ngayApDung(), input.ghiChu());
        try { return FeeRateResult.success("Lưu đơn giá thành công.", rates.insert(rate)); }
        catch (SQLException failure) { return writeFailure(failure); }
    }
    private static boolean validCode(String code) { return code != null && code.matches("[A-Z0-9_]{1,20}"); }
    private static boolean validDate(LocalDate date) {
        return date != null && !date.isBefore(LocalDate.of(1000, 1, 1)) && !date.isAfter(LocalDate.of(9999, 12, 31));
    }
    private static <T> FeeRateResult<T> writeFailure(SQLException failure) {
        if ("02000".equals(failure.getSQLState()) || failure.getErrorCode() == 1452) {
            return FeeRateResult.failure(NOT_FOUND, "Không tìm thấy khoản thu.", Map.of("ma_khoan_thu", CODE_ERROR));
        }
        if ("45000".equals(failure.getSQLState())) {
            return invalid(Map.of("ma_khoan_thu", "Chỉ đặt giá cho khoản thu bắt buộc đang áp dụng."));
        }
        if (failure.getErrorCode() == 1062) {
            String message = "Khoản thu đã có đơn giá tại ngày áp dụng này!";
            return FeeRateResult.failure(DUPLICATE, message, Map.of("ngay_ap_dung", message));
        }
        if (failure.getErrorCode() == 1213 || failure.getErrorCode() == 1205) {
            return FeeRateResult.failure(BUSY, "Dữ liệu đang được xử lý. Vui lòng thử lại.", Map.of());
        }
        return systemError(); // includes uncertain commit outcome: no automatic retry
    }
    private static <T> FeeRateResult<T> invalid(Map<String, String> errors) {
        return FeeRateResult.failure(VALIDATION_ERROR, "Dữ liệu không hợp lệ.", errors);
    }
    private static <T> FeeRateResult<T> readFailure(SQLException failure) {
        if (failure.getErrorCode() == 1213 || failure.getErrorCode() == 1205) {
            return FeeRateResult.failure(BUSY, "Dữ liệu đang được xử lý. Vui lòng thử lại.", Map.of());
        }
        return systemError();
    }
    private static <T> FeeRateResult<T> systemError() {
        return FeeRateResult.failure(SYSTEM_ERROR, "Lỗi hệ thống, vui lòng thử lại sau.", Map.of());
    }
}
