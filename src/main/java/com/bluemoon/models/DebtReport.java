package com.bluemoon.models;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** KPI covers the entire selected period; filteredDebt is the footer for the currently displayed rows. */
public record DebtReport(String kyThu, List<DebtRow> rows, BigDecimal tongPhaiThu, BigDecimal tongDaThu,
                         BigDecimal tongNo, BigDecimal tyLeThu, int hoHoanThanh, int tongSoHo, BigDecimal filteredDebt) {
    public DebtReport { Objects.requireNonNull(kyThu); rows=List.copyOf(rows); }
}
