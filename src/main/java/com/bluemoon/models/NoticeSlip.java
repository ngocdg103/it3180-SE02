package com.bluemoon.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One fee notice (US-011) for one household and period. Built only from a stored US-007 invoice: every amount,
 * quantity and price is copied from the snapshot and never recomputed. tenChuHo is null if the household has no head.
 */
public record NoticeSlip(String maThongBao, String kyThu, String soCanHo, String tenChuHo, BigDecimal dienTich,
                         int soNhanKhau, List<ChargeLine> lines, BigDecimal tongTien, LocalDate hanNop,
                         LocalDate ngayPhatHanh, String noiDungChuyenKhoan) {
    public NoticeSlip {
        BillingPeriod.parseKyThu(kyThu);
        Objects.requireNonNull(maThongBao);
        Objects.requireNonNull(soCanHo);
        Objects.requireNonNull(dienTich);
        Objects.requireNonNull(tongTien);
        Objects.requireNonNull(hanNop);
        Objects.requireNonNull(ngayPhatHanh);
        Objects.requireNonNull(noiDungChuyenKhoan);
        lines = List.copyOf(Objects.requireNonNull(lines));
    }
}
