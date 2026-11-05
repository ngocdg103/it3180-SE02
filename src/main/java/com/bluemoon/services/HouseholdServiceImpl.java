package com.bluemoon.services;

import com.bluemoon.dao.ApartmentChargeLookup;
import com.bluemoon.dao.DuplicateRecordException;
import com.bluemoon.dao.HouseholdRepository;
import com.bluemoon.models.Apartment;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.Household;
import com.bluemoon.models.HouseholdForm;
import com.bluemoon.models.HouseholdProfile;
import com.bluemoon.models.HouseholdUpdateForm;
import com.bluemoon.models.Role;
import com.bluemoon.services.HouseholdResult.Status;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** US-001 rules from docs/srs/EP01_QuanLyCuDan.md section 1. Messages are the SRS text where one is given. */
public final class HouseholdServiceImpl implements HouseholdService {
    static final String MSG_SO_CAN_HO = "Số căn hộ không đúng định dạng hoặc đã tồn tại trên hệ thống!";
    static final String MSG_SO_CAN_HO_DUPLICATE = "Số căn hộ đã tồn tại";
    static final String MSG_TANG = "Tầng phải là số nguyên từ 1 đến 30!";
    static final String MSG_DIEN_TICH = "Diện tích phải là số dương lớn hơn 0!";
    static final String MSG_MA_HO = "Mã hộ khẩu không được để trống hoặc bị trùng!";
    static final String MSG_NGAY_FUTURE = "Ngày nhận nhà không được lớn hơn ngày hiện tại!";
    static final String MSG_NGAY_FORMAT = "Ngày nhận nhà không đúng định dạng YYYY-MM-DD!";
    static final String MSG_TRANG_THAI = "Vui lòng chọn trạng thái căn hộ!";
    static final String MSG_INVALID = "Dữ liệu không hợp lệ, vui lòng kiểm tra lại các trường được đánh dấu.";
    static final String MSG_SYSTEM = "Lỗi hệ thống, vui lòng thử lại sau.";
    static final String MSG_AREA_CONFIRM =
            "Căn hộ này đã phát sinh kỳ thu phí. Thay đổi diện tích sẽ ảnh hưởng tính phí, bạn có chắc chắn muốn lưu?";

    private static final Pattern SO_CAN_HO = Pattern.compile("P\\d{4}");
    private static final Pattern INTEGER = Pattern.compile("\\d{1,9}");
    private static final Pattern DECIMAL = Pattern.compile("\\d{1,6}(\\.\\d{1,2})?");
    private static final int MA_HO_MAX = 20;

    private final HouseholdRepository repository;
    private final ApartmentChargeLookup charges;
    private final Supplier<Role> currentRole;
    private final Clock clock;

    /** Production: new HouseholdServiceImpl(new HouseholdDAO(connection), ApartmentChargeLookup.NONE). */
    public HouseholdServiceImpl(HouseholdRepository repository, ApartmentChargeLookup charges) {
        this(repository, charges, SessionContext::getCurrentRole, Clock.systemDefaultZone());
    }

    public HouseholdServiceImpl(HouseholdRepository repository, ApartmentChargeLookup charges,
                                Supplier<Role> currentRole, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.charges = Objects.requireNonNull(charges);
        this.currentRole = Objects.requireNonNull(currentRole);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public HouseholdResult<HouseholdProfile> createHousehold(HouseholdForm form) {
        requireRole(true);
        Objects.requireNonNull(form);
        var errors = new LinkedHashMap<String, String>();
        var soCanHo = trim(form.soCanHo());
        var maHo = trim(form.maHo());
        if (!SO_CAN_HO.matcher(soCanHo).matches()) { errors.put("so_can_ho", MSG_SO_CAN_HO); }
        if (maHo.isEmpty() || maHo.length() > MA_HO_MAX) { errors.put("ma_ho", MSG_MA_HO); }
        var fields = validateEditable(form.tang(), form.dienTich(), form.ngayNhanNha(), form.trangThai(), errors);
        if (!errors.isEmpty()) { return invalid(errors); }

        var profile = new HouseholdProfile(
                new Apartment(soCanHo, fields.tang, fields.dienTich, fields.trangThai),
                new Household(maHo, soCanHo, fields.ngayNhanNha));
        try {
            repository.insert(profile);
        } catch (DuplicateRecordException duplicate) {
            return duplicate.field() == DuplicateRecordException.Field.MA_HO
                    ? HouseholdResult.failure(Status.DUPLICATE, MSG_MA_HO, Map.of("ma_ho", MSG_MA_HO))
                    : HouseholdResult.failure(Status.DUPLICATE, MSG_SO_CAN_HO_DUPLICATE,
                            Map.of("so_can_ho", MSG_SO_CAN_HO_DUPLICATE));
        } catch (SQLException failure) {
            return systemError();
        }
        return HouseholdResult.success("Tạo hồ sơ căn hộ " + soCanHo + " thành công", profile);
    }

    @Override
    public HouseholdResult<HouseholdProfile> updateHousehold(String maHo, HouseholdUpdateForm form,
                                                             boolean confirmAreaChange) {
        requireRole(true);
        Objects.requireNonNull(form);
        var errors = new LinkedHashMap<String, String>();
        var fields = validateEditable(form.tang(), form.dienTich(), form.ngayNhanNha(), form.trangThai(), errors);
        if (!errors.isEmpty()) { return invalid(errors); }
        try {
            var existing = repository.findByMaHo(trim(maHo));
            if (existing.isEmpty()) { return notFound(); }
            var current = existing.get();
            // compareTo: 85.5 and 85.50 are the same area.
            if (current.dienTich().compareTo(fields.dienTich) != 0 && !confirmAreaChange
                    && charges.hasCharges(current.soCanHo())) {
                return HouseholdResult.failure(Status.CONFIRMATION_REQUIRED, MSG_AREA_CONFIRM,
                        Map.of("dien_tich", MSG_AREA_CONFIRM));
            }
            if (!repository.update(current.maHo(), fields.tang, fields.dienTich, fields.ngayNhanNha,
                    fields.trangThai)) {
                return notFound();
            }
            var updated = repository.findByMaHo(current.maHo());
            return updated.isPresent()
                    ? HouseholdResult.success("Cập nhật hồ sơ căn hộ " + current.soCanHo() + " thành công",
                            updated.get())
                    : notFound();
        } catch (SQLException failure) {
            return systemError();
        }
    }

    @Override
    public HouseholdResult<HouseholdProfile> getHouseholdById(String maHo) {
        requireRole(false);
        try {
            return repository.findByMaHo(trim(maHo)).map(p -> HouseholdResult.success("OK", p))
                    .orElseGet(HouseholdServiceImpl::notFound);
        } catch (SQLException failure) {
            return systemError();
        }
    }

    @Override
    public HouseholdResult<HouseholdProfile> getHouseholdByApartment(String soCanHo) {
        requireRole(false);
        try {
            return repository.findBySoCanHo(trim(soCanHo)).map(p -> HouseholdResult.success("OK", p))
                    .orElseGet(HouseholdServiceImpl::notFound);
        } catch (SQLException failure) {
            return systemError();
        }
    }

    @Override
    public HouseholdResult<List<HouseholdProfile>> listHouseholds(ApartmentStatus filter) {
        requireRole(false);
        try {
            return HouseholdResult.success("OK", List.copyOf(repository.findAll(filter)));
        } catch (SQLException failure) {
            return systemError();
        }
    }

    /** Fields shared by create and update; failures are added to errors. */
    private Editable validateEditable(String tang, String dienTich, String ngay, ApartmentStatus status,
                                      Map<String, String> errors) {
        var result = new Editable();
        var tangText = trim(tang);
        if (INTEGER.matcher(tangText).matches() && Integer.parseInt(tangText) >= 1
                && Integer.parseInt(tangText) <= 30) {
            result.tang = Integer.parseInt(tangText);
        } else {
            errors.put("tang", MSG_TANG);
        }
        var areaText = trim(dienTich);
        if (DECIMAL.matcher(areaText).matches() && new BigDecimal(areaText).signum() > 0) {
            result.dienTich = new BigDecimal(areaText);
        } else {
            errors.put("dien_tich", MSG_DIEN_TICH);
        }
        try {
            var date = LocalDate.parse(trim(ngay));
            if (date.isAfter(LocalDate.now(clock))) { errors.put("ngay_nhan_nha", MSG_NGAY_FUTURE); }
            result.ngayNhanNha = date;
        } catch (DateTimeParseException invalid) {
            errors.put("ngay_nhan_nha", MSG_NGAY_FORMAT);
        }
        if (status == null) { errors.put("trang_thai", MSG_TRANG_THAI); }
        result.trangThai = status;
        return result;
    }

    private void requireRole(boolean write) {
        Role role;
        try {
            role = currentRole.get();
        } catch (IllegalStateException noSession) {
            throw new AccessDeniedException("Chưa đăng nhập");
        }
        boolean allowed = role == Role.ADMIN || (!write && role == Role.ACCOUNTANT);
        if (!allowed) { throw new AccessDeniedException("Bạn không có quyền thực hiện chức năng này"); }
    }

    private static String trim(String text) { return text == null ? "" : text.trim(); }

    private static <T> HouseholdResult<T> invalid(Map<String, String> errors) {
        return HouseholdResult.failure(Status.VALIDATION_ERROR, MSG_INVALID, errors);
    }

    private static <T> HouseholdResult<T> notFound() {
        return HouseholdResult.failure(Status.NOT_FOUND, "Không tìm thấy hồ sơ hộ/căn hộ", Map.of());
    }

    private static <T> HouseholdResult<T> systemError() {
        return HouseholdResult.failure(Status.SYSTEM_ERROR, MSG_SYSTEM, Map.of());
    }

    private static final class Editable {
        int tang;
        BigDecimal dienTich;
        LocalDate ngayNhanNha;
        ApartmentStatus trangThai;
    }
}
