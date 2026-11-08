package com.bluemoon.dao;

import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.MonthlyCharge;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * One billing transaction on a single JDBC connection. loadInputs reads fee_types, fee_rates, households,
 * apartments and residents on this same connection, so the data is one consistent snapshot; it never goes
 * through FeeRateLookup, which opens its own connection.
 */
public interface BillingTx {
    /** Takes the unique ky_thu lock first. Throws BillingPeriodExistsException on MySQL 1062. */
    void insertPeriod(BillingPeriod period, long createdBy) throws SQLException, BillingPeriodExistsException;

    BillingInputs loadInputs(YearMonth period) throws SQLException;

    /** Header and lines, as a batch. con_thieu is a generated column and is not written. */
    void insertInvoices(List<MonthlyCharge> charges, Instant createdAt) throws SQLException;

    void finalizePeriod(String kyThu, int soHoaDon, int soHoBoQua, BigDecimal tongTien) throws SQLException;

    /** Roll back instead of commit when the work returns (business abort such as MISSING_RATE). */
    void rollbackOnly();
}
