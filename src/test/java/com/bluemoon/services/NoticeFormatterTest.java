package com.bluemoon.services;

import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.models.ChargeLine;
import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.NoticeSlip;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoticeFormatterTest {
    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static NoticeSlip slip(String head) {
        var lines = List.of(
                new ChargeLine("PHI_DV", "Phí dịch vụ chung cư", ChargeMethod.THEO_DIEN_TICH, d("80"), d("10000"),
                        LocalDate.of(2026, 1, 1), d("800000")),
                new ChargeLine("PHI_QL", "Phí quản lý chung cư", ChargeMethod.THEO_DIEN_TICH, d("80"), d("7000"),
                        LocalDate.of(2026, 1, 1), d("560000")),
                new ChargeLine("PHI_VS", "Phí vệ sinh môi trường", ChargeMethod.THEO_NHAN_KHAU, d("4"), d("6000"),
                        LocalDate.of(2026, 1, 1), d("24000")));
        return new NoticeSlip("TB-202611-P1204", "2026-11", "P1204", head, d("80.00"), 4, lines, d("1384000"),
                LocalDate.of(2026, 12, 15), LocalDate.of(2026, 11, 8), "P1204 Dong phi thang 11/2026");
    }

    @Test void textFollowsTheSrsTemplate() {
        var text = NoticeFormatter.text(slip("Nguyễn Văn A"), "Vietcombank - 0123456789 - BQT BlueMoon");
        for (String expected : List.of("BAN QUẢN TRỊ TÒA NHÀ CHUNG CƯ BLUEMOON", "GIẤY THÔNG BÁO THU PHÍ THÁNG 11/2026",
                "TB-202611-P1204", "08/11/2026", "Căn hộ số: P1204", "Chủ hộ: Nguyễn Văn A",
                "Diện tích tính phí: 80 m²", "Số nhân khẩu: 4 người",
                "Phí dịch vụ chung cư: 80 m² x 10.000 = 800.000 VNĐ",
                "Phí quản lý chung cư: 80 m² x 7.000 = 560.000 VNĐ",
                "Phí vệ sinh môi trường: 4 người x 6.000 = 24.000 VNĐ",
                "Các khoản đóng góp tự nguyện (nếu có): 0 VNĐ", "TỔNG TIỀN PHẢI NỘP: 1.384.000 VNĐ",
                "Nộp trực tiếp tại Văn phòng Ban quản trị (Tầng 1).", "Vietcombank - 0123456789 - BQT BlueMoon",
                "P1204 Dong phi thang 11/2026", "Hạn cuối thanh toán: Ngày 15/12/2026")) {
            assertTrue(text.contains(expected), "missing: " + expected + "\n" + text);
        }
    }

    @Test void missingHeadAndBankUsePlaceholdersInsteadOfInventedData() {
        var text = NoticeFormatter.text(slip(null), "  ");
        assertTrue(text.contains("(chưa có chủ hộ)"));
        assertTrue(text.contains(NoticeFormatter.PLACEHOLDER_BANK));
    }

    @Test void htmlEscapesEveryDynamicValueAndSeparatesNotices() {
        var html = NoticeFormatter.html(List.of(slip("<script>alert(1)</script>"), slip("B & C")), "Bank \"X\" <b>");
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<b>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("B &amp; C"));
        assertEquals(2, html.split("class=\"slip\"", -1).length - 1);
        assertTrue(html.startsWith("<!DOCTYPE html>"));
    }

    @Test void fixedFeeUsesApartmentWording() {
        var fixed = new ChargeLine("PHI_CH", "Phí cố định", ChargeMethod.THEO_CAN_HO, d("1"), d("50000"),
                LocalDate.of(2026, 1, 1), d("50000"));
        var s = new NoticeSlip("TB-202611-P1", "2026-11", "P1", "A", d("50"), 1, List.of(fixed), d("50000"),
                LocalDate.of(2026, 12, 15), LocalDate.of(2026, 11, 8), "P1 Dong phi thang 11/2026");
        assertTrue(NoticeFormatter.text(s, "x").contains("Phí cố định: 1 căn hộ x 50.000 = 50.000 VNĐ"));
    }
}
