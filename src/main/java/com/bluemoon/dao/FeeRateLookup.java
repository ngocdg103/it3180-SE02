package com.bluemoon.dao;

import com.bluemoon.models.AppliedRate;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Standalone unit-price lookup shared by US-006 (implements it) and US-007 (uses it for display and tests).
 * Each call may open its own connection, so it must NOT be used to compute a billing period: generatePeriod
 * reads fee_rates through the same JDBC transaction (BillingTx), not through this interface.
 * Implementations convert SQL failures into an unchecked exception.
 */
public interface FeeRateLookup {
    /**
     * The rate with the greatest ngayApDung that is not after atDate, or empty if none exists.
     * AppliedRate.ngayApDung() is that rate's effective date, not atDate.
     */
    Optional<AppliedRate> getEffectiveRate(String maKhoanThu, LocalDate atDate);
}
