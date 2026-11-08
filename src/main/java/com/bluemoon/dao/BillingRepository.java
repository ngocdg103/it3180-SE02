package com.bluemoon.dao;

import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.MonthlyCharge;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for US-007. No validation or authorization here: that is the Service's job.
 * There is deliberately no update or delete for periods and invoice lines (snapshot immutability, Q9).
 */
public interface BillingRepository {
    @FunctionalInterface
    interface TxWork<T> {
        T run(BillingTx tx) throws SQLException, BillingPeriodExistsException;
    }

    /** Fresh connection, REPEATABLE READ. Commits when work returns, unless rollbackOnly() was called. */
    <T> T inTransaction(TxWork<T> work) throws SQLException, BillingPeriodExistsException;

    /** Same, but READ ONLY and never commits any change (used by previewPeriod). */
    <T> T inReadOnlyTransaction(TxWork<T> work) throws SQLException;

    Optional<BillingPeriod> findPeriod(String kyThu) throws SQLException;

    /** Newest period first. */
    List<BillingPeriod> findAllPeriods() throws SQLException;

    /** Ordered by so_can_ho. Lines are loaded only when withLines is true. */
    List<MonthlyCharge> findCharges(String kyThu, boolean withLines) throws SQLException;

    /** Always with lines. */
    Optional<MonthlyCharge> findInvoice(String maHoaDon) throws SQLException;

    /** Header-level invoices (no lines) of one apartment, newest period first. Empty if never billed. */
    List<MonthlyCharge> findChargesByApartment(String soCanHo) throws SQLException;

    /** BR-02 hook: has this apartment ever been billed? */
    boolean hasCharges(String soCanHo) throws SQLException;
}
