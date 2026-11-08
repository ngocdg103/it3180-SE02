package com.bluemoon.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/** Append-only price. Null id denotes a new rate; persisted ids are positive. */
public record FeeRate(Long id, String maLoaiThu, BigDecimal donGia,
                      LocalDate ngayApDung, String ghiChu) {
    public FeeRate {
        if (id != null && id <= 0) { throw new IllegalArgumentException("id must be positive"); }
        requireCode(maLoaiThu);
        Objects.requireNonNull(donGia, "donGia");
        if (donGia.signum() <= 0 || donGia.compareTo(new BigDecimal("99999999999999999.99")) > 0) {
            throw new IllegalArgumentException("donGia outside DECIMAL(19,2) positive range");
        }
        try { donGia = donGia.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("donGia must be exact to cents", invalid); }
        requireDate(ngayApDung);
        if (ghiChu != null && ghiChu.codePointCount(0, ghiChu.length()) > 255) {
            throw new IllegalArgumentException("ghiChu exceeds 255 characters");
        }
    }

    public static void requireCode(String code) {
        Objects.requireNonNull(code, "maLoaiThu");
        if (!code.matches("[A-Z0-9_]{1,20}")) { throw new IllegalArgumentException("Invalid maLoaiThu"); }
    }

    public static void requireDate(LocalDate date) {
        Objects.requireNonNull(date, "date");
        if (date.isBefore(LocalDate.of(1000, 1, 1)) || date.isAfter(LocalDate.of(9999, 12, 31))) {
            throw new IllegalArgumentException("Date outside MySQL DATE range");
        }
    }
}
