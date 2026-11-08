package com.bluemoon.models;

import java.util.List;
import java.util.Objects;

/** A stored period with its monthly charges (header level; lines may be empty, use getInvoice for lines). */
public record BillingPeriodDetail(BillingPeriod period, List<MonthlyCharge> charges) {
    public BillingPeriodDetail {
        Objects.requireNonNull(period, "period");
        charges = List.copyOf(Objects.requireNonNull(charges, "charges"));
    }
}
