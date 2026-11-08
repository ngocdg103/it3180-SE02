package com.bluemoon.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Unit price in force for a fee type, shared by US-006 (rate lookup) and US-007 (billing snapshot).
 * There must be no second copy of this type in US-006.
 *
 * @param maKhoanThu fee type code (fee_types.ma_loai_thu)
 * @param donGia     VND per unit, strictly positive
 * @param ngayApDung the effective date of this rate (not the date it was looked up)
 */
public record AppliedRate(String maKhoanThu, BigDecimal donGia, LocalDate ngayApDung) {
    public AppliedRate {
        BillingMoney.requireText(maKhoanThu, "maKhoanThu");
        Objects.requireNonNull(donGia, "donGia");
        if (donGia.signum() <= 0) { throw new IllegalArgumentException("donGia must be greater than 0"); }
        Objects.requireNonNull(ngayApDung, "ngayApDung");
    }
}
