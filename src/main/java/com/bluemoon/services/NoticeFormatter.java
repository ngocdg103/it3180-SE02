package com.bluemoon.services;

import com.bluemoon.models.ChargeLine;
import com.bluemoon.models.NoticeSlip;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Renders the EP-04 notice template as text (screen) and HTML (print / export). Presentation only, no arithmetic. */
public final class NoticeFormatter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final String PLACEHOLDER_BANK = "[Chưa nhập thông tin tài khoản nhận]";

    private NoticeFormatter() { }

    public static String text(NoticeSlip slip, String bankInfo) {
        var month = slip.kyThu().substring(5) + "/" + slip.kyThu().substring(0, 4);
        var out = new StringBuilder();
        out.append("BAN QUẢN TRỊ TÒA NHÀ CHUNG CƯ BLUEMOON\n");
        out.append("GIẤY THÔNG BÁO THU PHÍ THÁNG ").append(month).append("\n\n");
        out.append("Mã thông báo: ").append(slip.maThongBao()).append("  |  Ngày phát hành: ")
                .append(date(slip.ngayPhatHanh())).append("\n");
        out.append("Căn hộ số: ").append(slip.soCanHo()).append("  |  Chủ hộ: ")
                .append(slip.tenChuHo() == null ? "(chưa có chủ hộ)" : slip.tenChuHo()).append("\n");
        out.append("Diện tích tính phí: ").append(plain(slip.dienTich())).append(" m²  |  Số nhân khẩu: ")
                .append(slip.soNhanKhau()).append(" người\n\n");
        out.append("BẢNG KÊ CHI TIẾT CÁC KHOẢN PHÍ\n");
        for (ChargeLine line : slip.lines()) { out.append("- ").append(describe(line)).append("\n"); }
        out.append("- Các khoản đóng góp tự nguyện (nếu có): 0 VNĐ\n");
        out.append("TỔNG TIỀN PHẢI NỘP: ").append(money(slip.tongTien())).append(" VNĐ\n\n");
        out.append("HƯỚNG DẪN THANH TOÁN\n");
        out.append("- Nộp trực tiếp tại Văn phòng Ban quản trị (Tầng 1).\n");
        out.append("- Hoặc chuyển khoản: ").append(bank(bankInfo)).append("\n");
        out.append("  Nội dung chuyển khoản: ").append(slip.noiDungChuyenKhoan()).append("\n");
        out.append("- Hạn cuối thanh toán: Ngày ").append(date(slip.hanNop())).append("\n\n");
        out.append("Ban quản trị (ký tên): ______________________\n");
        return out.toString();
    }

    /** One printable page per notice. All dynamic text is HTML-escaped. */
    public static String html(List<NoticeSlip> slips, String bankInfo) {
        var out = new StringBuilder("<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\">"
                + "<title>Thông báo thu phí</title><style>body{font-family:sans-serif}"
                + ".slip{page-break-after:always;margin:24px;white-space:pre-wrap}</style></head><body>");
        for (NoticeSlip slip : slips) {
            out.append("<div class=\"slip\">").append(escape(text(slip, bankInfo))).append("</div>");
        }
        return out.append("</body></html>").toString();
    }

    private static String describe(ChargeLine line) {
        String unit = switch (line.hinhThuc()) {
            case THEO_DIEN_TICH -> plain(line.soLuong()) + " m² x ";
            case THEO_NHAN_KHAU -> plain(line.soLuong()) + " người x ";
            case THEO_CAN_HO -> "1 căn hộ x ";
            case TU_NGUYEN -> "";
        };
        return line.tenKhoanThu() + ": " + unit + money(line.donGia()) + " = " + money(line.thanhTien()) + " VNĐ";
    }

    private static String bank(String bankInfo) {
        return bankInfo == null || bankInfo.isBlank() ? PLACEHOLDER_BANK : bankInfo.strip();
    }

    private static String date(LocalDate value) { return DATE.format(value); }
    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    static String money(BigDecimal value) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(value);
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
