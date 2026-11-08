package com.bluemoon.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Result of previewPeriod. alreadyGenerated == true means charges is the stored snapshot, not a recomputation.
 * rates lists the unit prices used, so the user can see them before confirming.
 */
public record BillingPreview(String kyThu, LocalDate hanNop, boolean alreadyGenerated, List<MonthlyCharge> charges,
                             List<SkippedHousehold> skipped, List<BillingWarning> warnings,
                             List<AppliedRate> rates, BigDecimal tongTien) {
    public BillingPreview {
        BillingPeriod.parseKyThu(kyThu);
        Objects.requireNonNull(hanNop, "hanNop");
        charges = List.copyOf(Objects.requireNonNull(charges, "charges"));
        skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        rates = List.copyOf(Objects.requireNonNull(rates, "rates"));
        tongTien = BillingMoney.requireWholeVnd(tongTien, "tongTien");
        BigDecimal sum = charges.stream().map(MonthlyCharge::tongTien).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(tongTien) != 0) { throw new IllegalArgumentException("tongTien must equal the sum of charges"); }
    }
}
