package com.bluemoon.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Shared money rules (Q6): BigDecimal only, whole VND (HALF_UP) stored as scale 2 (future DECIMAL(19,2)). */
final class BillingMoney {
    private BillingMoney() { }

    /** Rounds HALF_UP to 1 VND and returns it with scale 2, e.g. 18001.5 -> 18002.00. */
    static BigDecimal toStored(BigDecimal amount) {
        return Objects.requireNonNull(amount, "amount").setScale(0, RoundingMode.HALF_UP).setScale(2);
    }

    /** Non-null, non-negative and already a whole number of VND. Returns it with scale 2. */
    static BigDecimal requireWholeVnd(BigDecimal amount, String name) {
        Objects.requireNonNull(amount, name);
        if (amount.signum() < 0) { throw new IllegalArgumentException(name + " must not be negative"); }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(name + " must be a whole number of VND (round HALF_UP before building)");
        }
        return amount.setScale(2);
    }

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
        return value;
    }
}
