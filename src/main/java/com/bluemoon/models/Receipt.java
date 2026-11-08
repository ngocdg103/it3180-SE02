package com.bluemoon.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Receipt(String maPhieuThu, String requestId, String maHoaDon, String nguoiNop, BigDecimal soTienNop,
                      LocalDate ngayNop, PaymentMethod hinhThuc, String ghiChu, long createdBy, Instant createdAt) {
    public Receipt {
        if (maPhieuThu == null || maPhieuThu.isBlank() || maPhieuThu.length() > 64) { throw new IllegalArgumentException("receipt code"); }
        if (!UUID.fromString(Objects.requireNonNull(requestId)).toString().equals(requestId)) { throw new IllegalArgumentException("requestId"); }
        if (maHoaDon == null || maHoaDon.isBlank() || maHoaDon.length() > 30) { throw new IllegalArgumentException("invoice"); }
        int length = nguoiNop == null ? 0 : nguoiNop.codePointCount(0, nguoiNop.length());
        if (length < 2 || length > 50) { throw new IllegalArgumentException("payer"); }
        soTienNop = wholeMoney(soTienNop);
        if (soTienNop.signum() <= 0) { throw new IllegalArgumentException("amount"); }
        Objects.requireNonNull(ngayNop); Objects.requireNonNull(hinhThuc); Objects.requireNonNull(createdAt);
        if (createdBy <= 0) { throw new IllegalArgumentException("actor"); }
        if (ghiChu != null && ghiChu.codePointCount(0, ghiChu.length()) > 255) { throw new IllegalArgumentException("note"); }
    }
    public static BigDecimal wholeMoney(BigDecimal value) {
        Objects.requireNonNull(value);
        if (value.signum() < 0 || value.compareTo(new BigDecimal("99999999999999999.99")) > 0
                || value.stripTrailingZeros().scale() > 0) { throw new IllegalArgumentException("Whole VND in DECIMAL range required"); }
        return value.setScale(2);
    }
}
