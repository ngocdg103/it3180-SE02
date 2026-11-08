package com.bluemoon.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A finalized billing period (billing_periods). hanNop is always the 15th of the following month (Q17).
 * Immutable once generated: there is no update or delete operation (Q9).
 */
public record BillingPeriod(String kyThu, LocalDate hanNop, int soHoaDon, int soHoBoQua, BigDecimal tongTien,
                            Instant createdAt) {
    private static final Pattern KY_THU = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])$");

    public BillingPeriod {
        YearMonth period = parseKyThu(kyThu);
        Objects.requireNonNull(hanNop, "hanNop");
        if (!hanNop.equals(defaultHanNop(period))) {
            throw new IllegalArgumentException("hanNop must be the 15th of the following month");
        }
        if (soHoaDon < 0 || soHoBoQua < 0) { throw new IllegalArgumentException("counts must not be negative"); }
        tongTien = BillingMoney.requireWholeVnd(tongTien, "tongTien");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Strict YYYY-MM (no spaces, month 01..12). */
    public static YearMonth parseKyThu(String kyThu) {
        if (kyThu == null || !KY_THU.matcher(kyThu).matches()) {
            throw new IllegalArgumentException("ky_thu must be YYYY-MM");
        }
        return YearMonth.parse(kyThu);
    }

    /** 15th of the month after the period (December rolls into January of the next year). */
    public static LocalDate defaultHanNop(YearMonth period) {
        return Objects.requireNonNull(period, "period").plusMonths(1).atDay(15);
    }
}
