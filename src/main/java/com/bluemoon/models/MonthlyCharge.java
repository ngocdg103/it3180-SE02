package com.bluemoon.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Amount due for one household in one billing period (hoa_don_ky). dienTich and soNhanKhau are the values
 * used at billing time, so the record never depends on current apartment/resident data.
 * lines may be empty for header-only listings; when present, tongTien must equal the sum of thanhTien.
 */
public record MonthlyCharge(String maHoaDon, String kyThu, String maHo, String soCanHo, BigDecimal dienTich,
                            int soNhanKhau, BigDecimal tongTien, BigDecimal daThanhToan, BigDecimal conThieu,
                            ChargeStatus trangThai, LocalDate hanNop, List<ChargeLine> lines) {
    public MonthlyCharge {
        BillingMoney.requireText(maHo, "maHo");
        BillingMoney.requireText(soCanHo, "soCanHo");
        BillingPeriod.parseKyThu(kyThu);
        if (!("HD-" + kyThu.replace("-", "") + "-" + soCanHo).equals(maHoaDon)) {
            throw new IllegalArgumentException("maHoaDon must be HD-YYYYMM-soCanHo");
        }
        Objects.requireNonNull(dienTich, "dienTich");
        if (dienTich.signum() <= 0) { throw new IllegalArgumentException("dienTich must be greater than 0"); }
        if (soNhanKhau < 0) { throw new IllegalArgumentException("soNhanKhau must not be negative"); }
        tongTien = BillingMoney.requireWholeVnd(tongTien, "tongTien");
        daThanhToan = BillingMoney.requireWholeVnd(daThanhToan, "daThanhToan");
        Objects.requireNonNull(conThieu, "conThieu");
        if (conThieu.compareTo(tongTien.subtract(daThanhToan)) != 0) {
            throw new IllegalArgumentException("conThieu must equal tongTien - daThanhToan");
        }
        conThieu = conThieu.setScale(2);
        Objects.requireNonNull(trangThai, "trangThai");
        requireStatusConsistent(trangThai, daThanhToan, conThieu);
        Objects.requireNonNull(hanNop, "hanNop");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (!lines.isEmpty()) {
            BigDecimal sum = lines.stream().map(ChargeLine::thanhTien).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(tongTien) != 0) {
                throw new IllegalArgumentException("tongTien must equal the sum of line amounts");
            }
        }
    }

    /**
     * EP-03 BR-13 and the ChargeStatus definitions: CHUA_DONG = nothing paid; DONG_THIEU = something paid and
     * still owing; DA_DONG_DU = nothing owing; DONG_THUA = overpaid (conThieu below 0).
     */
    private static void requireStatusConsistent(ChargeStatus status, BigDecimal daThanhToan, BigDecimal conThieu) {
        boolean consistent = switch (status) {
            case CHUA_DONG -> daThanhToan.signum() == 0;
            case DONG_THIEU -> daThanhToan.signum() > 0 && conThieu.signum() > 0;
            case DA_DONG_DU -> conThieu.signum() == 0;
            case DONG_THUA -> conThieu.signum() < 0;
        };
        if (!consistent) {
            throw new IllegalArgumentException("trangThai " + status + " is inconsistent with daThanhToan/conThieu");
        }
    }
}
