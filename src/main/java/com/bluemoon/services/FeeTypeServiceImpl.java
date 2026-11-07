package com.bluemoon.services;

import com.bluemoon.dao.FeeTypeRepository;
import com.bluemoon.models.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import static com.bluemoon.services.FeeTypeResult.Status.*;

/** US-005 validation and US-018 authorization, independent of the UI. */
public final class FeeTypeServiceImpl implements FeeTypeService {
    private static final String CODE_ERROR = "Mã khoản thu không được để trống, không chứa khoảng trắng và không được trùng!";
    private final FeeTypeRepository repository;
    private final AuthorizationService authorization;

    public FeeTypeServiceImpl(FeeTypeRepository repository) {
        this(repository, new RoleBasedAuthorizationService(null));
    }

    public FeeTypeServiceImpl(FeeTypeRepository repository, AuthorizationService authorization) {
        this.repository = Objects.requireNonNull(repository);
        this.authorization = Objects.requireNonNull(authorization);
    }

    @Override public FeeTypeResult<List<FeeType>> listFeeTypes(FeeStatus filter) {
        authorization.requirePermission(Permission.FEE_CATEGORY_VIEW);
        try { return FeeTypeResult.success("OK", List.copyOf(repository.findAll(filter))); }
        catch (SQLException failure) { return systemError(); }
    }

    @Override public FeeTypeResult<FeeType> getFeeType(String code) {
        authorization.requirePermission(Permission.FEE_CATEGORY_VIEW);
        if (!validCode(code)) { return invalid(Map.of("ma_khoan_thu", CODE_ERROR)); }
        try { return found(repository.findByCode(code)); }
        catch (SQLException failure) { return systemError(); }
    }

    @Override public FeeTypeResult<FeeType> createFeeType(FeeTypeForm form) {
        authorization.requirePermission(Permission.FEE_CATEGORY_CREATE);
        return save(null, form);
    }

    @Override public FeeTypeResult<FeeType> updateFeeType(String code, FeeTypeForm form) {
        authorization.requirePermission(Permission.FEE_CATEGORY_UPDATE);
        if (!validCode(code)) { return invalid(Map.of("ma_khoan_thu", CODE_ERROR)); }
        return save(code, form);
    }

    private FeeTypeResult<FeeType> save(String code, FeeTypeForm input) {
        var form = input == null ? new FeeTypeForm(null, null, null, null, null, null) : input;
        var errors = new LinkedHashMap<String, String>();
        if (!validCode(form.maKhoanThu())) { errors.put("ma_khoan_thu", CODE_ERROR); }
        if (code != null && !code.equals(form.maKhoanThu())) {
            errors.put("ma_khoan_thu", "Không được thay đổi mã khoản thu sau khi tạo!");
        }
        String name = form.tenKhoanThu() == null ? "" : form.tenKhoanThu().trim();
        int length = name.codePointCount(0, name.length());
        if (length < 3 || length > 100) { errors.put("ten_khoan_thu", "Tên khoản thu phải từ 3 đến 100 ký tự!"); }
        if (form.loai() == null) { errors.put("loai_khoan_thu", "Vui lòng chọn loại khoản thu!"); }
        if (form.hinhThuc() == null || !ChargeMethod.forKind(form.loai()).contains(form.hinhThuc())) {
            errors.put("hinh_thuc_thu", "Vui lòng chọn hình thức tính phí!");
        }
        BigDecimal amount = BigDecimal.ZERO;
        String text = form.soTienMacDinh() == null ? "" : form.soTienMacDinh().trim();
        if (!text.isEmpty()) {
            if (!text.matches("[0-9]{1,17}(\\.[0-9]{1,2})?")) {
                errors.put("so_tien_mac_dinh", "Số tiền mặc định phải lớn hơn hoặc bằng 0! Tối đa 17 chữ số nguyên và 2 chữ số thập phân.");
            } else { amount = new BigDecimal(text); }
        }
        if (form.loai() == FeeKind.DONG_GOP && amount.signum() != 0) {
            errors.put("so_tien_mac_dinh", "Khoản đóng góp phải có số tiền mặc định bằng 0!");
        }
        if (form.trangThai() == null) { errors.put("trang_thai", "Vui lòng chọn trạng thái!"); }
        if (!errors.isEmpty()) { return invalid(errors); }
        var fee = new FeeType(form.maKhoanThu(), name, form.loai(), form.hinhThuc(), amount, form.trangThai());
        try {
            if (code != null) { return found(repository.update(fee)); }
            repository.insert(fee);
            return FeeTypeResult.success("Tạo khoản thu " + fee.maKhoanThu() + " thành công", fee);
        } catch (SQLException failure) {
            if (code == null && failure.getErrorCode() == 1062) {
                return FeeTypeResult.failure(DUPLICATE, CODE_ERROR, Map.of("ma_khoan_thu", CODE_ERROR));
            }
            return systemError();
        }
    }

    @Override public FeeTypeResult<FeeType> deactivateFeeType(String code) {
        authorization.requirePermission(Permission.FEE_CATEGORY_UPDATE);
        if (!validCode(code)) { return invalid(Map.of("ma_khoan_thu", CODE_ERROR)); }
        try { return found(repository.deactivate(code)); }
        catch (SQLException failure) { return systemError(); }
    }

    private static boolean validCode(String code) { return code != null && code.matches("[A-Z0-9_]{1,20}"); }
    private static FeeTypeResult<FeeType> found(Optional<FeeType> fee) {
        return fee.map(value -> FeeTypeResult.success("OK", value)).orElseGet(() ->
                FeeTypeResult.failure(NOT_FOUND, "Không tìm thấy khoản thu", Map.of()));
    }
    private static <T> FeeTypeResult<T> invalid(Map<String, String> errors) {
        return FeeTypeResult.failure(VALIDATION_ERROR, "Dữ liệu không hợp lệ", errors);
    }
    private static <T> FeeTypeResult<T> systemError() {
        return FeeTypeResult.failure(SYSTEM_ERROR, "Lỗi hệ thống, vui lòng thử lại sau.", Map.of());
    }
}
