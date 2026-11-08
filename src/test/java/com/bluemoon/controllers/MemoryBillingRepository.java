package com.bluemoon.controllers;

import com.bluemoon.dao.BillingInputs;
import com.bluemoon.dao.BillingPeriodExistsException;
import com.bluemoon.dao.BillingRepository;
import com.bluemoon.dao.BillingTx;
import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.MonthlyCharge;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only repository for UI tests: real BillingServiceImpl runs on top, nothing is mocked above the DAO. */
final class MemoryBillingRepository implements BillingRepository {
    volatile BillingInputs inputs = new BillingInputs(List.of(), List.of());
    final Map<String, BillingPeriod> periods = new LinkedHashMap<>();
    final List<MonthlyCharge> invoices = new ArrayList<>();
    final AtomicInteger writeTransactions = new AtomicInteger();
    final AtomicInteger commits = new AtomicInteger();
    volatile CountDownLatch gate = new CountDownLatch(0);
    volatile SQLException generateFailure;

    synchronized void addStoredPeriod(BillingPeriod period, List<MonthlyCharge> charges) {
        periods.put(period.kyThu(), period);
        invoices.addAll(charges);
    }

    @Override public <T> T inTransaction(TxWork<T> work) throws SQLException, BillingPeriodExistsException {
        writeTransactions.incrementAndGet();
        try { gate.await(10, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (generateFailure != null) { throw generateFailure; }
        var tx = new Tx();
        T result = work.run(tx);
        if (!tx.rollbackOnly) {
            synchronized (this) {
                if (tx.period != null) { periods.put(tx.period.kyThu(), tx.period); }
                invoices.addAll(tx.invoices);
            }
            commits.incrementAndGet();
        }
        return result;
    }

    @Override public <T> T inReadOnlyTransaction(TxWork<T> work) throws SQLException {
        try { return work.run(new Tx()); }
        catch (BillingPeriodExistsException impossible) { throw new IllegalStateException(impossible); }
    }

    @Override public synchronized Optional<BillingPeriod> findPeriod(String kyThu) {
        return Optional.ofNullable(periods.get(kyThu));
    }

    @Override public synchronized List<BillingPeriod> findAllPeriods() {
        var all = new ArrayList<>(periods.values());
        all.sort((a, b) -> b.kyThu().compareTo(a.kyThu()));
        return all;
    }

    @Override public synchronized List<MonthlyCharge> findCharges(String kyThu, boolean withLines) {
        return invoices.stream().filter(c -> c.kyThu().equals(kyThu)).map(c -> withLines ? c : new MonthlyCharge(
                c.maHoaDon(), c.kyThu(), c.maHo(), c.soCanHo(), c.dienTich(), c.soNhanKhau(), c.tongTien(),
                c.daThanhToan(), c.conThieu(), c.trangThai(), c.hanNop(), List.of())).toList();
    }

    @Override public synchronized Optional<MonthlyCharge> findInvoice(String maHoaDon) {
        return invoices.stream().filter(c -> c.maHoaDon().equals(maHoaDon)).findFirst();
    }

    @Override public synchronized boolean hasCharges(String soCanHo) {
        return invoices.stream().anyMatch(c -> c.soCanHo().equals(soCanHo));
    }

    private final class Tx implements BillingTx {
        BillingPeriod period;
        final List<MonthlyCharge> invoices = new ArrayList<>();
        boolean rollbackOnly;

        @Override public void insertPeriod(BillingPeriod p, long createdBy) throws BillingPeriodExistsException {
            synchronized (MemoryBillingRepository.this) {
                if (periods.containsKey(p.kyThu())) {
                    throw new BillingPeriodExistsException(new SQLException("dup", "23000", 1062));
                }
            }
            period = p;
        }

        @Override public BillingInputs loadInputs(YearMonth month) { return inputs; }

        @Override public void insertInvoices(List<MonthlyCharge> charges, Instant createdAt) { invoices.addAll(charges); }

        @Override public void finalizePeriod(String kyThu, int soHoaDon, int soHoBoQua, BigDecimal tongTien) {
            period = new BillingPeriod(kyThu, period.hanNop(), soHoaDon, soHoBoQua, tongTien, period.createdAt());
        }

        @Override public void rollbackOnly() { rollbackOnly = true; }
    }
}
