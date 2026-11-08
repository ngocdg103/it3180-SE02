package com.bluemoon.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One fee line of a monthly charge (BillingDetail), an immutable snapshot of what was billed.
 * soLuong is square metres (THEO_DIEN_TICH), people (THEO_NHAN_KHAU) or exactly 1 (THEO_CAN_HO).
 * thanhTien must equal round-HALF_UP-to-1-VND(soLuong x donGia) and is exposed with scale 2.
 */
public record ChargeLine(String maKhoanThu, String tenKhoanThu, ChargeMethod hinhThuc, BigDecimal soLuong,
                         BigDecimal donGia, LocalDate ngayApDungGia, BigDecimal thanhTien) {
    public ChargeLine {
        BillingMoney.requireText(maKhoanThu, "maKhoanThu");
        BillingMoney.requireText(tenKhoanThu, "tenKhoanThu");
        Objects.requireNonNull(hinhThuc, "hinhThuc");
        if (hinhThuc == ChargeMethod.TU_NGUYEN) {
            throw new IllegalArgumentException("Voluntary fees are not part of a monthly charge (Q1)");
        }
        Objects.requireNonNull(soLuong, "soLuong");
        if (soLuong.signum() < 0) { throw new IllegalArgumentException("soLuong must not be negative"); }
        if (hinhThuc == ChargeMethod.THEO_CAN_HO && soLuong.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("THEO_CAN_HO requires soLuong = 1");
        }
        Objects.requireNonNull(donGia, "donGia");
        if (donGia.signum() <= 0) { throw new IllegalArgumentException("donGia must be greater than 0"); }
        Objects.requireNonNull(ngayApDungGia, "ngayApDungGia");
        thanhTien = BillingMoney.requireWholeVnd(thanhTien, "thanhTien");
        if (thanhTien.compareTo(BillingMoney.toStored(soLuong.multiply(donGia))) != 0) {
            throw new IllegalArgumentException("thanhTien must equal soLuong x donGia rounded HALF_UP to 1 VND");
        }
    }
}
