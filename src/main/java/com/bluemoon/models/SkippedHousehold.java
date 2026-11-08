package com.bluemoon.models;

import java.util.Objects;

/** A household that did not get a monthly charge. Not persisted in Sprint 1 beyond the period's skipped count. */
public record SkippedHousehold(String soCanHo, Reason reason, String message) {
    public enum Reason {
        /** BR-11 defensive case: apartment area missing or not positive. */
        MISSING_AREA,
        /** Computed total is 0 (Q16). */
        ZERO_TOTAL
    }

    public SkippedHousehold {
        BillingMoney.requireText(soCanHo, "soCanHo");
        Objects.requireNonNull(reason, "reason");
        BillingMoney.requireText(message, "message");
    }
}
