package com.bluemoon.services;

import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import static com.bluemoon.services.ReceiptResult.Status.*;

public final class ReceiptServiceImpl implements ReceiptService {
    private final ReceiptRepository repository;
    private final AuthorizationService authorization;
    private final Clock clock;
    public ReceiptServiceImpl(ReceiptRepository repository, AuthorizationService authorization, Clock clock) {
        this.repository = Objects.requireNonNull(repository); this.authorization = Objects.requireNonNull(authorization); this.clock = Objects.requireNonNull(clock);
    }
    @Override public ReceiptResult<List<PaymentInvoice>> listOutstanding() {
        authorization.requirePermission(Permission.PAYMENT_HISTORY_VIEW_ALL);
        try { return ReceiptResult.success(List.copyOf(repository.listOutstanding())); } catch (SQLException failure) { return sql(failure); }
    }
    @Override public ReceiptResult<List<Receipt>> listByHousehold(String code) {
        authorization.requirePermission(Permission.PAYMENT_HISTORY_VIEW_ALL);
        if (code == null || code.isBlank() || code.length() > 20) { return invalid(Map.of("ma_ho", "Vui lòng chọn hộ gia đình!")); }
        try { return ReceiptResult.success(List.copyOf(repository.listByHousehold(code))); } catch (SQLException failure) { return sql(failure); }
    }
    @Override public ReceiptResult<Optional<Receipt>> findByRequestId(String id) {
        authorization.requirePermission(Permission.PAYMENT_HISTORY_VIEW_ALL);
        if (!validId(id)) { return invalid(Map.of("request_id", "Mã yêu cầu không hợp lệ!")); }
        try { return ReceiptResult.success(repository.findByRequestId(id)); } catch (SQLException failure) { return sql(failure); }
    }
    @Override public ReceiptResult<ReceiptOutcome> recordPayment(ReceiptForm input) {
        authorization.requirePermission(Permission.PAYMENT_RECORD);
        var form = input == null ? new ReceiptForm(null,null,null,null,null,null,null) : input;
        var errors = new LinkedHashMap<String,String>();
        if (!validId(form.requestId())) { errors.put("request_id", "Mã yêu cầu không hợp lệ!"); }
        if (form.maHoaDon() == null || form.maHoaDon().isBlank() || form.maHoaDon().length() > 30) { errors.put("ma_hoa_don", "Vui lòng chọn hóa đơn cần thanh toán!"); }
        String payer = form.nguoiNop() == null ? "" : form.nguoiNop().trim();
        int length = payer.codePointCount(0, payer.length());
        if (length < 2 || length > 50) { errors.put("nguoi_nop", "Vui lòng nhập tên người nộp tiền!"); }
        BigDecimal amount = null;
        String text = form.soTienNop() == null ? "" : form.soTienNop().trim();
        try {
            if (!text.matches("[0-9]{1,17}(\\.[0-9]{1,2})?")) { throw new IllegalArgumentException(); }
            amount = Receipt.wholeMoney(new BigDecimal(text));
            if (amount.signum() <= 0) { throw new IllegalArgumentException(); }
        } catch (IllegalArgumentException failure) { errors.put("so_tien_nop", "Số tiền nộp phải lớn hơn 0! Nhập số VNĐ nguyên."); }
        var today = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        if (form.ngayNop() == null || form.ngayNop().isAfter(today) || form.ngayNop().getYear() < 1000) { errors.put("ngay_nop", "Ngày nộp tiền không hợp lệ!"); }
        if (form.hinhThuc() == null) { errors.put("hinh_thuc", "Vui lòng chọn hình thức thanh toán!"); }
        if (form.ghiChu() != null && form.ghiChu().codePointCount(0,form.ghiChu().length()) > 255) { errors.put("ghi_chu", "Ghi chú tối đa 255 ký tự!"); }
        if (!errors.isEmpty()) { return invalid(errors); }
        long actor;
        // Session changes use this same monitor: permission and audit identity belong to one session snapshot.
        synchronized (SessionContext.class) {
            authorization.requirePermission(Permission.PAYMENT_RECORD);
            actor = SessionContext.getCurrentUser().orElseThrow(() -> new AccessDeniedException("Không có phiên đăng nhập.")).userId();
        }
        var receipt = new Receipt("PENDING", form.requestId(), form.maHoaDon(), payer, amount, form.ngayNop(), form.hinhThuc(), form.ghiChu(), actor, clock.instant());
        try {
            var outcome = repository.record(receipt);
            return outcome.replay() ? ReceiptResult.failure(DUPLICATE, "Yêu cầu này đã được ghi nhận; không tạo phiếu mới.", Map.of()) : ReceiptResult.success(outcome);
        } catch (ReceiptRuleException refusal) {
            return switch (refusal.reason()) {
                case NOT_FOUND -> ReceiptResult.failure(NOT_FOUND, "Không tìm thấy hóa đơn.", Map.of("ma_hoa_don", "Không tìm thấy hóa đơn."));
                case ALREADY_SETTLED -> ReceiptResult.failure(ALREADY_SETTLED, "Hóa đơn đã thanh toán đủ hoặc thừa.", Map.of("ma_hoa_don", "Chọn hóa đơn còn phải thu."));
                case REQUEST_CONFLICT -> ReceiptResult.failure(CONFLICT, "Mã yêu cầu đã dùng cho nội dung khác. Hãy đối soát trước khi tạo yêu cầu mới.", Map.of("request_id", "Mã yêu cầu đã sử dụng."));
                case AMOUNT_OVERFLOW -> invalid(Map.of("so_tien_nop", "Tổng tiền đã nộp vượt giới hạn lưu trữ."));
            };
        } catch (SQLException failure) { return sql(failure); }
    }
    private static boolean validId(String id) {
        try { return id != null && UUID.fromString(id).toString().equals(id); } catch (IllegalArgumentException failure) { return false; }
    }
    private static <T> ReceiptResult<T> invalid(Map<String,String> errors) { return ReceiptResult.failure(VALIDATION_ERROR, "Dữ liệu không hợp lệ.", errors); }
    private static <T> ReceiptResult<T> sql(SQLException failure) {
        if (failure.getErrorCode() == 1062) { return ReceiptResult.failure(CONFLICT, "Mã phiếu hoặc mã yêu cầu đã tồn tại. Vui lòng đối soát.", Map.of("request_id", "Mã đã tồn tại.")); }
        if (failure.getErrorCode() == 1205 || failure.getErrorCode() == 1213) { return ReceiptResult.failure(BUSY, "Giao dịch đang bận. Không tự động thử lại.", Map.of()); }
        return ReceiptResult.failure(SYSTEM_ERROR, "Lỗi hệ thống. Kết quả giao dịch có thể chưa xác định; tra cứu mã yêu cầu trước khi thao tác tiếp.", Map.of());
    }
}
