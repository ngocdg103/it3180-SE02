package com.bluemoon.models;

import java.util.Objects;

/** Non-blocking note attached to a preview, for example a vacant apartment that still has residents (Q15). */
public record BillingWarning(String soCanHo, Code code, String message) {
    public enum Code { VACANT_WITH_RESIDENTS }

    public BillingWarning {
        BillingMoney.requireText(soCanHo, "soCanHo");
        Objects.requireNonNull(code, "code");
        BillingMoney.requireText(message, "message");
    }
}
