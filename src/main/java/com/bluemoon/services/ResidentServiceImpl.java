package com.bluemoon.services;

import com.bluemoon.dao.HouseholdRepository;
import com.bluemoon.dao.ResidentDuplicateException;
import com.bluemoon.dao.ResidentRepository;
import com.bluemoon.models.HouseholdMembers;
import com.bluemoon.models.Permission;
import com.bluemoon.models.RelationToHead;
import com.bluemoon.models.Resident;
import com.bluemoon.models.ResidentForm;
import com.bluemoon.services.ResidentResult.ConfirmationReason;
import com.bluemoon.services.ResidentResult.Status;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * US-002 rules from docs/srs/EP01_QuanLyCuDan.md section 2 plus the PO decisions recorded in
 * docs/design/us-002-contract-review.md. Messages are the SRS text where one is given.
 * Authorization is checked first, through the existing AuthorizationService, before any data access.
 */
public final class ResidentServiceImpl implements ResidentService {
    static final String MSG_MA_HO = "Vui lòng chọn hộ gia đình/căn hộ!";
    static final String MSG_HO_TEN = "Họ tên không hợp lệ (từ 2-50 ký tự, không chứa ký tự đặc biệt)!";
    static final String MSG_NGAY_SINH = "Ngày sinh không hợp lệ!";
    static final String MSG_GIOI_TINH = "Vui lòng chọn giới tính!";
    static final String MSG_CCCD = "CCCD phải đúng 12 chữ số và không được trùng lặp!";
    static final String MSG_CCCD_REQUIRED = "Công dân từ 14 tuổi trở lên bắt buộc phải nhập số CCCD!";
    static final String MSG_QUAN_HE = "Vui lòng chọn quan hệ với chủ hộ!";
    static final String MSG_NGAY_CHUYEN_DEN = "Ngày chuyển đến không hợp lệ!";
    // Not in the SRS: wording proposed by Dev-3, pending PO/Lead review.
    static final String MSG_VACANT = "Căn hộ đang ở trạng thái Căn hộ trống. "
            + "Hãy cập nhật trạng thái căn hộ trước khi thêm nhân khẩu.";
    static final String MSG_FIRST_HEAD = "Người đầu tiên của hộ phải có quan hệ là Chủ hộ!";
    static final String MSG_PERSON_DUPLICATE =
            "Nhân khẩu này (cùng họ tên, ngày sinh, giới tính) đã có trong hộ!";
    static final String MSG_STALE = "Thông tin chủ hộ của hộ này đã thay đổi. Vui lòng kiểm tra lại và lưu lại.";
    static final String MSG_HOUSEHOLD_NOT_FOUND = "Không tìm thấy hộ gia đình!";
    static final String MSG_INVALID = "Dữ liệu không hợp lệ, vui lòng kiểm tra lại các trường được đánh dấu.";
    static final String MSG_SYSTEM = "Lỗi hệ thống, vui lòng thử lại sau.";

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{M}]+( [\\p{L}\\p{M}]+)*");
    private static final Pattern CCCD = Pattern.compile("\\d{9}|\\d{12}");
    private static final Pattern BLANKS = Pattern.compile("[\\p{Z}\\s]+");
    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 50;
    private static final int MA_HO_MAX = 20;
    private static final int CCCD_REQUIRED_AGE = 14;

    private final ResidentRepository residents;
    private final HouseholdRepository households;
    private final AuthorizationService authorization;
    private final Clock clock;

    public ResidentServiceImpl(ResidentRepository residents, HouseholdRepository households,
                               AuthorizationService authorization) {
        this(residents, households, authorization, Clock.systemDefaultZone());
    }

    public ResidentServiceImpl(ResidentRepository residents, HouseholdRepository households,
                               AuthorizationService authorization, Clock clock) {
        this.residents = Objects.requireNonNull(residents);
        this.households = Objects.requireNonNull(households);
        this.authorization = Objects.requireNonNull(authorization);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ResidentResult<Resident> createForHousehold(ResidentForm form, Long confirmReplaceHeadId) {
        authorization.requirePermission(Permission.RESIDENT_PROFILE_CREATE);
        Objects.requireNonNull(form);
        var today = LocalDate.now(clock);
        var errors = new LinkedHashMap<String, String>();

        var maHo = trim(form.maHo());
        if (maHo.isEmpty() || maHo.length() > MA_HO_MAX) { errors.put("ma_ho", MSG_MA_HO); }

        var hoTen = normalizeName(form.hoTen());
        if (hoTen.length() < NAME_MIN || hoTen.length() > NAME_MAX || !NAME.matcher(hoTen).matches()) {
            errors.put("ho_ten", MSG_HO_TEN);
        }

        var ngaySinh = parseDate(form.ngaySinh());
        if (ngaySinh == null || !ngaySinh.isBefore(today)) { errors.put("ngay_sinh", MSG_NGAY_SINH); }

        if (form.gioiTinh() == null) { errors.put("gioi_tinh", MSG_GIOI_TINH); }
        if (form.quanHeChuHo() == null) { errors.put("quan_he_chu_ho", MSG_QUAN_HE); }

        var ngayChuyenDen = parseDate(form.ngayChuyenDen());
        if (ngayChuyenDen == null || ngayChuyenDen.isAfter(today)
                || (ngaySinh != null && ngayChuyenDen.isBefore(ngaySinh))) {
            errors.put("ngay_chuyen_den", MSG_NGAY_CHUYEN_DEN);
        }

        var cccd = trim(form.soCccd());
        if (!errors.containsKey("ngay_sinh")) {
            boolean adult = Period.between(ngaySinh, today).getYears() >= CCCD_REQUIRED_AGE;
            if (cccd.isEmpty()) {
                if (adult) { errors.put("so_cccd", MSG_CCCD_REQUIRED); }
            } else if (!CCCD.matcher(cccd).matches()) {
                errors.put("so_cccd", MSG_CCCD);
            }
        } else if (!cccd.isEmpty() && !CCCD.matcher(cccd).matches()) {
            errors.put("so_cccd", MSG_CCCD);
        }
        if (!errors.isEmpty()) { return invalid(errors); }

        var draft = new Resident(0, maHo, hoTen, ngaySinh, form.gioiTinh(), cccd.isEmpty() ? null : cccd,
                form.quanHeChuHo(), ngayChuyenDen);
        // A confirmation only means something for a Chu ho; for anyone else it is ignored and never demotes anyone.
        var policy = draft.isHead() && confirmReplaceHeadId != null
                ? ResidentRepository.HeadPolicy.REPLACE_HEAD_IF_UNCHANGED
                : ResidentRepository.HeadPolicy.REJECT_IF_HEAD_EXISTS;
        var expected = draft.isHead() ? confirmReplaceHeadId : null;
        try {
            return toResult(residents.insert(draft, policy, expected), maHo);
        } catch (ResidentDuplicateException duplicate) {
            return duplicateResult(duplicate, maHo);
        } catch (SQLException | RuntimeException failure) {
            return systemError();
        }
    }

    @Override
    public ResidentResult<HouseholdMembers> getHousehold(String maHo) {
        authorization.requirePermission(Permission.RESIDENT_PROFILE_VIEW);
        var key = trim(maHo);
        if (key.isEmpty()) {
            return ResidentResult.failure(Status.VALIDATION_ERROR, MSG_MA_HO, Map.of("ma_ho", MSG_MA_HO));
        }
        try {
            if (households.findByMaHo(key).isEmpty()) {
                return ResidentResult.failure(Status.NOT_FOUND, MSG_HOUSEHOLD_NOT_FOUND,
                        Map.of("ma_ho", MSG_HOUSEHOLD_NOT_FOUND));
            }
            var members = new HouseholdMembers(key, residents.findByMaHo(key));
            return ResidentResult.success("Đã tải " + members.memberCount() + " nhân khẩu của hộ " + key, members);
        } catch (SQLException | RuntimeException failure) {
            return systemError();
        }
    }

    private static ResidentResult<Resident> toResult(ResidentRepository.InsertOutcome outcome, String maHo) {
        if (outcome instanceof ResidentRepository.Added added) {
            return ResidentResult.success(
                    "Thêm nhân khẩu " + added.resident().hoTen() + " vào hộ " + maHo + " thành công", added.resident());
        }
        if (outcome instanceof ResidentRepository.HeadExists exists) {
            return ResidentResult.confirmation(
                    "Hộ này đã có chủ hộ. Bạn có muốn đổi " + exists.currentHead().hoTen()
                            + " sang quan hệ khác không?",
                    ConfirmationReason.HEAD_EXISTS, exists.currentHead());
        }
        if (outcome instanceof ResidentRepository.HeadChanged changed) {
            return ResidentResult.confirmation(
                    MSG_STALE, ConfirmationReason.CONFIRMATION_STALE, changed.currentHead().orElse(null));
        }
        if (outcome instanceof ResidentRepository.ApartmentVacant) {
            return ResidentResult.failure(Status.VALIDATION_ERROR, MSG_VACANT, Map.of("ma_ho", MSG_VACANT));
        }
        if (outcome instanceof ResidentRepository.FirstMemberMustBeHead) {
            return ResidentResult.failure(Status.VALIDATION_ERROR, MSG_FIRST_HEAD,
                    Map.of("quan_he_chu_ho", MSG_FIRST_HEAD));
        }
        return ResidentResult.failure(Status.NOT_FOUND, MSG_HOUSEHOLD_NOT_FOUND,
                Map.of("ma_ho", MSG_HOUSEHOLD_NOT_FOUND));
    }

    private ResidentResult<Resident> duplicateResult(ResidentDuplicateException duplicate, String maHo) {
        switch (duplicate.field()) {
            case SO_CCCD:
                return ResidentResult.failure(Status.DUPLICATE, MSG_CCCD, Map.of("so_cccd", MSG_CCCD));
            case PERSON:
                return ResidentResult.failure(Status.DUPLICATE, MSG_PERSON_DUPLICATE,
                        Map.of("ho_ten", MSG_PERSON_DUPLICATE));
            default:
                // A second head slipped past the locked check (a write that bypassed it): re-read, never overwrite.
                try {
                    var head = residents.findHead(maHo);
                    if (head.isPresent()) {
                        return ResidentResult.confirmation(MSG_STALE, ConfirmationReason.CONFIRMATION_STALE, head.get());
                    }
                } catch (SQLException | RuntimeException failure) {
                    return systemError();
                }
                return systemError();
        }
    }

    private static <T> ResidentResult<T> invalid(Map<String, String> errors) {
        return ResidentResult.failure(Status.VALIDATION_ERROR, MSG_INVALID, errors);
    }

    private static <T> ResidentResult<T> systemError() {
        return ResidentResult.failure(Status.SYSTEM_ERROR, MSG_SYSTEM, Map.of());
    }

    private static String trim(String text) { return text == null ? "" : text.trim(); }

    /** NFC, trimmed, any run of blanks collapsed to one space. */
    static String normalizeName(String text) {
        return BLANKS.matcher(Normalizer.normalize(trim(text), Normalizer.Form.NFC)).replaceAll(" ").trim();
    }

    private static LocalDate parseDate(String text) {
        try { return LocalDate.parse(trim(text)); } catch (DateTimeParseException invalid) { return null; }
    }
}
