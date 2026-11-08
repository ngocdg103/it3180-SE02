package com.bluemoon.services;

import static com.bluemoon.services.BillingResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.dao.BillingInputs;
import com.bluemoon.dao.BillingInputs.FeeInput;
import com.bluemoon.dao.BillingInputs.HouseholdInput;
import com.bluemoon.dao.BillingPeriodExistsException;
import com.bluemoon.dao.BillingRepository;
import com.bluemoon.dao.BillingTx;
import com.bluemoon.models.Account;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.AppliedRate;
import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.MonthlyCharge;
import com.bluemoon.models.Role;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Service logic against an in-memory fake repository. Transactions, locking and MySQL are covered by the IT. */
class BillingServiceTest {
    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 1, 1);
    private final Fake repo = new Fake();

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static Clock at(String instant) { return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC); }

    private BillingServiceImpl service(Role role, Clock clock) {
        var auth = new RoleBasedAuthorizationService(() -> role == null ? Optional.empty()
                : Optional.of(new Account(7, "staff", "Staff", role)), null);
        return new BillingServiceImpl(repo, auth, clock, () -> role == null ? Optional.empty()
                : Optional.of(new Account(7, "staff", "Staff", role)));
    }

    private BillingServiceImpl service() { return service(Role.ACCOUNTANT, at("2026-11-15T03:00:00Z")); }

    private static FeeInput fee(String code, ChargeMethod method, String price) {
        return new FeeInput(code, "Khoan " + code, method, Optional.of(new AppliedRate(code, d(price), EFFECTIVE)));
    }

    private void standardData() {
        repo.inputs = new BillingInputs(List.of(fee("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "10000"),
                fee("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "7000"), fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "6000")),
                List.of(new HouseholdInput("HK-1204", "P1204", d("80"), ApartmentStatus.DANG_O, 4)));
    }

    // ---- RBAC ----
    private interface Call { void run(BillingServiceImpl service); }

    private static final Map<String, Call> CALLS = new LinkedHashMap<>();
    static {
        CALLS.put("preview", s -> s.previewPeriod("2026-11"));
        CALLS.put("generate", s -> s.generatePeriod("2026-11"));
        CALLS.put("list", BillingServiceImpl::listPeriods);
        CALLS.put("getPeriod", s -> s.getPeriod("2026-11"));
        CALLS.put("getInvoice", s -> s.getInvoice("HD-202611-P1204"));
        CALLS.put("history", s -> s.getApartmentHistory("P1204"));
    }

    @Test void adminAndAccountantAreAllowedEverywhere() {
        standardData();
        for (Role role : List.of(Role.ADMIN, Role.ACCOUNTANT)) {
            for (var call : CALLS.entrySet()) {
                repo.reset();
                standardData();
                assertDoesNotThrow(() -> call.getValue().run(service(role, at("2026-11-15T03:00:00Z"))),
                        role + " " + call.getKey());
            }
        }
    }

    @Test void residentAndNoSessionAreDeniedBeforeAnyStorageAccess() {
        standardData();
        for (Role role : new Role[] {Role.RESIDENT, null}) {
            for (var call : CALLS.entrySet()) {
                repo.reset();
                var s = service(role, at("2026-11-15T03:00:00Z"));
                assertThrows(AccessDeniedException.class, () -> call.getValue().run(s), role + " " + call.getKey());
                assertTrue(repo.calls.isEmpty(), "no repository access for " + role + " " + call.getKey());
            }
        }
    }

    @Test void permissionIsCheckedBeforeValidation() {
        var s = service(Role.RESIDENT, at("2026-11-15T03:00:00Z"));
        assertThrows(AccessDeniedException.class, () -> s.generatePeriod("garbage"));
        assertThrows(AccessDeniedException.class, () -> s.previewPeriod(null));
    }

    // ---- Validation and Clock (Q11) ----
    @ParameterizedTest @ValueSource(strings = {"", " ", "2026-1", "2026-13", "2026-00", "2026/11", "202611", " 2026-11", "2026-11 ", "2026-11-01"})
    void malformedPeriodIsRejected(String value) {
        for (Call call : List.<Call>of(s -> assertEquals(VALIDATION_ERROR, s.previewPeriod(value).status()),
                s -> assertEquals(VALIDATION_ERROR, s.generatePeriod(value).status()))) {
            call.run(service());
        }
        assertTrue(repo.calls.isEmpty());
    }

    @Test void nullPeriodIsRejected() {
        var result = service().generatePeriod(null);
        assertEquals(VALIDATION_ERROR, result.status());
        assertNotNull(result.fieldErrors().get("ky_thu"));
        assertTrue(repo.calls.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"2026-10", "2025-11", "2026-12", "2027-11", "2026-01"})
    void pastAndFuturePeriodsAreRejectedForBothOperations(String value) {
        var s = service(Role.ADMIN, at("2026-11-15T03:00:00Z"));
        var generate = s.generatePeriod(value);
        var preview = s.previewPeriod(value);
        assertEquals(VALIDATION_ERROR, generate.status());
        assertEquals(VALIDATION_ERROR, preview.status());
        assertTrue(generate.fieldErrors().get("ky_thu").contains("2026-11"));
        assertTrue(repo.calls.isEmpty(), "nothing is read or written");
    }

    @Test void currentMonthPassesValidation() {
        standardData();
        assertEquals(SUCCESS, service().generatePeriod("2026-11").status());
    }

    @Test void monthBoundaryUsesVietnamTimeNotUtc() {
        standardData();
        // 2026-11-30T17:30Z is already 2026-12-01 00:30 in Ho Chi Minh City.
        var clock = at("2026-11-30T17:30:00Z");
        assertEquals(VALIDATION_ERROR, service(Role.ADMIN, clock).generatePeriod("2026-11").status());
        assertEquals(SUCCESS, service(Role.ADMIN, clock).generatePeriod("2026-12").status());
    }

    @Test void justBeforeMidnightStillCurrentMonth() {
        standardData();
        var clock = at("2026-11-30T16:59:59Z"); // 23:59:59 on 30 Nov in Vietnam
        assertEquals(VALIDATION_ERROR, service(Role.ADMIN, clock).generatePeriod("2026-12").status());
        assertEquals(SUCCESS, service(Role.ADMIN, clock).generatePeriod("2026-11").status());
    }

    @Test void yearBoundary() {
        standardData();
        var clock = at("2026-12-31T17:00:00Z"); // 2027-01-01 00:00 in Vietnam
        assertEquals(VALIDATION_ERROR, service(Role.ADMIN, clock).generatePeriod("2026-12").status());
        var ok = service(Role.ADMIN, clock).generatePeriod("2027-01");
        assertEquals(SUCCESS, ok.status());
        assertEquals(LocalDate.of(2027, 2, 15), ok.data().orElseThrow().hanNop());
    }

    @Test void clockZoneOfTheInjectedClockDoesNotMatter() {
        standardData();
        var clock = Clock.fixed(Instant.parse("2026-11-30T17:30:00Z"), ZoneId.of("America/Los_Angeles"));
        assertEquals(SUCCESS, service(Role.ADMIN, clock).generatePeriod("2026-12").status());
    }

    // ---- generate ----
    @Test void generateAc01StoresSnapshotAndCreator() {
        standardData();
        var result = service().generatePeriod("2026-11");
        assertEquals(SUCCESS, result.status());
        assertEquals("Lập kỳ thu 2026-11 thành công", result.message());
        var period = result.data().orElseThrow();
        assertEquals(new BigDecimal("1384000.00"), period.tongTien());
        assertEquals(1, period.soHoaDon());
        assertEquals(0, period.soHoBoQua());
        assertEquals(LocalDate.of(2026, 12, 15), period.hanNop());
        assertEquals(Instant.parse("2026-11-15T03:00:00Z"), period.createdAt());
        assertEquals(1, repo.committed);
        assertEquals(7L, repo.createdBy);
        assertEquals(1, repo.invoices.size());
        assertEquals(3, repo.invoices.get(0).lines().size());
        assertEquals(1, repo.finalized);
    }

    @Test void secondGenerateIsDuplicateAndWritesNothingMore() {
        standardData();
        var s = service();
        assertEquals(SUCCESS, s.generatePeriod("2026-11").status());
        int invoicesBefore = repo.invoices.size();
        var again = s.generatePeriod("2026-11");
        assertEquals(DUPLICATE, again.status());
        assertEquals("Kỳ thu 2026-11 đã tồn tại trên hệ thống!", again.message());
        assertEquals("Kỳ thu 2026-11 đã tồn tại trên hệ thống!", again.fieldErrors().get("ky_thu"));
        assertEquals(invoicesBefore, repo.invoices.size());
        assertEquals(1, repo.committed);
    }

    @Test void missingRateRollsBackWholePeriod() {
        standardData();
        repo.inputs = new BillingInputs(List.of(fee("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "10000"),
                new FeeInput("PHI_QL", "Phi QL", ChargeMethod.THEO_DIEN_TICH, Optional.empty())),
                repo.inputs.households());
        var result = service().generatePeriod("2026-11");
        assertEquals(MISSING_RATE, result.status());
        assertTrue(result.message().contains("PHI_QL"));
        assertTrue(result.fieldErrors().containsKey("PHI_QL"));
        assertEquals(0, repo.committed);
        assertEquals(1, repo.rolledBack);
        assertTrue(repo.invoices.isEmpty());
        assertTrue(repo.periods.isEmpty());
    }

    @Test void nothingToBillWhenNoActiveFeeOrNoHouseholdOrAllSkipped() {
        standardData();
        var households = repo.inputs.households();
        var fees = repo.inputs.fees();
        repo.inputs = new BillingInputs(List.of(), households);
        assertEquals(NOTHING_TO_BILL, service().generatePeriod("2026-11").status());
        repo.inputs = new BillingInputs(fees, List.of());
        assertEquals(NOTHING_TO_BILL, service().generatePeriod("2026-11").status());
        repo.inputs = new BillingInputs(fees,
                List.of(new HouseholdInput("HK-1", "P1", null, ApartmentStatus.DANG_O, 2)));
        assertEquals(NOTHING_TO_BILL, service().generatePeriod("2026-11").status());
        assertEquals(0, repo.committed);
        assertTrue(repo.periods.isEmpty());
    }

    @Test void zeroTotalHouseholdsAreCountedAsSkipped() {
        repo.inputs = new BillingInputs(List.of(fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "6000")),
                List.of(new HouseholdInput("HK-1", "P1", d("50"), ApartmentStatus.CAN_HO_TRONG, 0),
                        new HouseholdInput("HK-2", "P2", d("50"), ApartmentStatus.DANG_O, 2)));
        var period = service().generatePeriod("2026-11").data().orElseThrow();
        assertEquals(1, period.soHoaDon());
        assertEquals(1, period.soHoBoQua());
        assertEquals(new BigDecimal("12000.00"), period.tongTien());
        assertEquals(List.of("P2"), repo.invoices.stream().map(MonthlyCharge::soCanHo).toList());
    }

    @Test void generateDoesNotNeedAnyFeeRateLookup() {
        // The service takes only a repository: rates arrive through BillingTx.loadInputs on the same connection.
        standardData();
        service().generatePeriod("2026-11");
        assertEquals(List.of("tx", "insertPeriod", "loadInputs", "insertInvoices", "finalizePeriod", "commit"),
                repo.calls);
    }

    // ---- error mapping ----
    @Test void duplicateKeyFromDatabaseMapsToDuplicate() {
        standardData();
        repo.insertFailures.add(new BillingPeriodExistsException(new SQLException("dup", "23000", 1062)));
        var result = service().generatePeriod("2026-11");
        assertEquals(DUPLICATE, result.status());
        assertEquals(0, repo.committed);
    }

    @Test void deadlockWhenPeriodNowExistsMapsToDuplicate() {
        standardData();
        repo.insertFailures.add(new SQLException("deadlock", "40001", 1213));
        repo.periods.put("2026-11", new BillingPeriod("2026-11", LocalDate.of(2026, 12, 15), 1, 0,
                d("1384000"), Instant.EPOCH));
        assertEquals(DUPLICATE, service().generatePeriod("2026-11").status());
    }

    @Test void deadlockWithoutPeriodRetriesOnceAndSucceeds() {
        standardData();
        repo.insertFailures.add(new SQLException("deadlock", "40001", 1213));
        var result = service().generatePeriod("2026-11");
        assertEquals(SUCCESS, result.status());
        assertEquals(1, repo.invoices.size());
    }

    @Test void repeatedDeadlockWithoutPeriodIsBusyNotDuplicate() {
        standardData();
        repo.insertFailures.add(new SQLException("deadlock", "40001", 1213));
        repo.insertFailures.add(new SQLException("deadlock", "40001", 1213));
        var result = service().generatePeriod("2026-11");
        assertEquals(BUSY, result.status());
        assertNotEquals(DUPLICATE, result.status());
        assertTrue(repo.invoices.isEmpty());
    }

    @Test void lockWaitTimeoutIsBusy() {
        standardData();
        repo.insertFailures.add(new SQLException("Lock wait timeout exceeded", "HY000", 1205));
        assertEquals(BUSY, service().generatePeriod("2026-11").status());
    }

    @Test void otherSqlFailuresAreGenericSystemError() {
        standardData();
        repo.insertFailures.add(new SQLException("secret table detail users.password", "23000", 1452));
        var result = service().generatePeriod("2026-11");
        assertEquals(SYSTEM_ERROR, result.status());
        assertFalse(result.message().contains("secret"));
        assertEquals(BillingServiceImpl.MSG_SYSTEM, result.message());
    }

    @Test void failureWhileWritingInvoicesRollsBackEverything() {
        standardData();
        repo.invoiceFailure = new SQLException("disk full", "HY000", 1114);
        var result = service().generatePeriod("2026-11");
        assertEquals(SYSTEM_ERROR, result.status());
        assertEquals(0, repo.committed);
        assertTrue(repo.periods.isEmpty());
        assertTrue(repo.invoices.isEmpty());
    }

    @Test void unexpectedRuntimeFailureIsSystemError() {
        standardData();
        repo.runtimeFailure = new IllegalStateException("boom");
        assertEquals(SYSTEM_ERROR, service().generatePeriod("2026-11").status());
        assertEquals(SYSTEM_ERROR, service().previewPeriod("2026-11").status());
    }

    // ---- preview ----
    @Test void previewDoesNotWrite() {
        standardData();
        var result = service().previewPeriod("2026-11");
        assertEquals(SUCCESS, result.status());
        var preview = result.data().orElseThrow();
        assertFalse(preview.alreadyGenerated());
        assertEquals(new BigDecimal("1384000.00"), preview.tongTien());
        assertEquals(3, preview.rates().size());
        assertEquals(0, repo.committed);
        assertTrue(repo.invoices.isEmpty());
        assertTrue(repo.periods.isEmpty());
        assertFalse(repo.calls.contains("insertPeriod"));
        assertFalse(repo.calls.contains("insertInvoices"));
    }

    @Test void previewOfExistingPeriodReturnsStoredSnapshotNotRecomputation() {
        standardData();
        var s = service();
        s.generatePeriod("2026-11");
        // Data changes after the period was closed: the stored snapshot must still be returned.
        repo.inputs = new BillingInputs(List.of(fee("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "99999")),
                List.of(new HouseholdInput("HK-1204", "P1204", d("500"), ApartmentStatus.DANG_O, 9)));
        repo.calls.clear();
        var preview = s.previewPeriod("2026-11").data().orElseThrow();
        assertTrue(preview.alreadyGenerated());
        assertEquals(new BigDecimal("1384000.00"), preview.tongTien());
        assertEquals(4, preview.charges().get(0).soNhanKhau());
        assertFalse(repo.calls.contains("loadInputs"), "must not reload current data");
    }

    @Test void previewReportsMissingRateAndNothingToBill() {
        standardData();
        repo.inputs = new BillingInputs(List.of(new FeeInput("PHI_QL", "Phi QL", ChargeMethod.THEO_DIEN_TICH,
                Optional.empty())), repo.inputs.households());
        assertEquals(MISSING_RATE, service().previewPeriod("2026-11").status());
        repo.inputs = new BillingInputs(List.of(), List.of());
        assertEquals(NOTHING_TO_BILL, service().previewPeriod("2026-11").status());
    }

    @Test void previewCarriesWarningsAndSkipped() {
        repo.inputs = new BillingInputs(List.of(fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "6000")),
                List.of(new HouseholdInput("HK-1", "P1", d("50"), ApartmentStatus.CAN_HO_TRONG, 2),
                        new HouseholdInput("HK-2", "P2", d("50"), ApartmentStatus.DANG_O, 0)));
        var preview = service().previewPeriod("2026-11").data().orElseThrow();
        assertEquals(1, preview.warnings().size());
        assertEquals(1, preview.skipped().size());
        assertEquals(1, preview.charges().size());
    }

    // ---- read operations ----
    @Test void listPeriodsReturnsEmptyListAsSuccess() {
        var result = service().listPeriods();
        assertEquals(SUCCESS, result.status());
        assertTrue(result.data().orElseThrow().isEmpty());
    }

    @Test void getPeriodAndInvoiceLifecycle() {
        standardData();
        var s = service();
        s.generatePeriod("2026-11");
        var detail = s.getPeriod("2026-11").data().orElseThrow();
        assertEquals("2026-11", detail.period().kyThu());
        assertEquals(1, detail.charges().size());
        var invoice = s.getInvoice(" HD-202611-P1204 ").data().orElseThrow();
        assertEquals(3, invoice.lines().size());
        assertEquals(NOT_FOUND, s.getPeriod("2026-10").status());
        assertEquals(NOT_FOUND, s.getInvoice("HD-202610-P1204").status());
        assertEquals(VALIDATION_ERROR, s.getPeriod("2026-13").status());
        assertEquals(VALIDATION_ERROR, s.getInvoice("  ").status());
        assertEquals(VALIDATION_ERROR, s.getInvoice(null).status());
    }

    @Test void apartmentHistoryReturnsStoredHeadersOnly() {
        standardData();
        var s = service();
        assertTrue(s.getApartmentHistory("P1204").data().orElseThrow().isEmpty());
        s.generatePeriod("2026-11");
        var history = s.getApartmentHistory(" P1204 ").data().orElseThrow();
        assertEquals(1, history.size());
        assertEquals("2026-11", history.get(0).kyThu());
        assertEquals(com.bluemoon.models.ChargeStatus.CHUA_DONG, history.get(0).trangThai());
        assertTrue(history.get(0).lines().isEmpty());
        assertTrue(s.getApartmentHistory("P9999").data().orElseThrow().isEmpty());
        assertEquals(VALIDATION_ERROR, s.getApartmentHistory(" ").status());
        assertEquals(VALIDATION_ERROR, s.getApartmentHistory(null).status());
    }

    @Test void readsOfOlderPeriodsAreAllowedEvenThoughGenerateIsCurrentMonthOnly() {
        repo.periods.put("2026-10", new BillingPeriod("2026-10", LocalDate.of(2026, 11, 15), 1, 0,
                d("100"), Instant.EPOCH));
        assertEquals(SUCCESS, service().getPeriod("2026-10").status());
    }

    @Test void readFailuresAreGenericSystemError() {
        repo.readFailure = new SQLException("detail", "HY000", 2013);
        assertEquals(SYSTEM_ERROR, service().listPeriods().status());
        assertEquals(SYSTEM_ERROR, service().getPeriod("2026-11").status());
        assertEquals(SYSTEM_ERROR, service().getInvoice("HD-202611-P1204").status());
        assertEquals(SYSTEM_ERROR, service().getApartmentHistory("P1204").status());
    }

    // ---- fake ----
    private static final class Fake implements BillingRepository {
        BillingInputs inputs = new BillingInputs(List.of(), List.of());
        final Map<String, BillingPeriod> periods = new LinkedHashMap<>();
        final List<MonthlyCharge> invoices = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        final Deque<Exception> insertFailures = new ArrayDeque<>();
        SQLException invoiceFailure, readFailure;
        RuntimeException runtimeFailure;
        int committed, rolledBack, finalized;
        long createdBy;

        void reset() { calls.clear(); committed = rolledBack = finalized = 0; periods.clear(); invoices.clear(); }

        private void failIfRequested() throws SQLException {
            if (readFailure != null) { throw readFailure; }
            if (runtimeFailure != null) { throw runtimeFailure; }
        }

        @Override public <T> T inTransaction(TxWork<T> work) throws SQLException, BillingPeriodExistsException {
            calls.add("tx");
            var tx = new FakeTx(false);
            try {
                T result = work.run(tx);
                if (tx.rollbackOnly) { rolledBack++; calls.add("rollback"); }
                else {
                    committed++; calls.add("commit");
                    if (tx.stagedPeriod != null) { periods.put(tx.stagedPeriod.kyThu(), tx.stagedPeriod); }
                    invoices.addAll(tx.stagedInvoices);
                }
                return result;
            } catch (SQLException | BillingPeriodExistsException | RuntimeException failure) {
                rolledBack++; calls.add("rollback");
                throw failure;
            }
        }

        @Override public <T> T inReadOnlyTransaction(TxWork<T> work) throws SQLException {
            calls.add("readTx");
            try { return work.run(new FakeTx(true)); }
            catch (BillingPeriodExistsException impossible) { throw new IllegalStateException(impossible); }
        }

        @Override public Optional<BillingPeriod> findPeriod(String kyThu) throws SQLException {
            calls.add("findPeriod"); failIfRequested();
            return Optional.ofNullable(periods.get(kyThu));
        }

        @Override public List<BillingPeriod> findAllPeriods() throws SQLException {
            calls.add("findAll"); failIfRequested();
            return List.copyOf(periods.values());
        }

        @Override public List<MonthlyCharge> findCharges(String kyThu, boolean withLines) throws SQLException {
            calls.add("findCharges"); failIfRequested();
            return invoices.stream().filter(c -> c.kyThu().equals(kyThu)).map(c -> withLines ? c :
                    new MonthlyCharge(c.maHoaDon(), c.kyThu(), c.maHo(), c.soCanHo(), c.dienTich(), c.soNhanKhau(),
                            c.tongTien(), c.daThanhToan(), c.conThieu(), c.trangThai(), c.hanNop(), List.of())).toList();
        }

        @Override public Optional<MonthlyCharge> findInvoice(String maHoaDon) throws SQLException {
            calls.add("findInvoice"); failIfRequested();
            return invoices.stream().filter(c -> c.maHoaDon().equals(maHoaDon)).findFirst();
        }

        @Override public List<MonthlyCharge> findChargesByApartment(String soCanHo) throws SQLException {
            calls.add("findByApartment"); failIfRequested();
            return invoices.stream().filter(c -> c.soCanHo().equals(soCanHo))
                    .sorted(java.util.Comparator.comparing(MonthlyCharge::kyThu).reversed())
                    .map(c -> new MonthlyCharge(c.maHoaDon(), c.kyThu(), c.maHo(), c.soCanHo(), c.dienTich(),
                            c.soNhanKhau(), c.tongTien(), c.daThanhToan(), c.conThieu(), c.trangThai(), c.hanNop(),
                            List.of())).toList();
        }

        @Override public boolean hasCharges(String soCanHo) throws SQLException {
            calls.add("hasCharges"); failIfRequested();
            return invoices.stream().anyMatch(c -> c.soCanHo().equals(soCanHo));
        }

        private final class FakeTx implements BillingTx {
            final boolean readOnly;
            boolean rollbackOnly;
            BillingPeriod stagedPeriod;
            final List<MonthlyCharge> stagedInvoices = new ArrayList<>();

            FakeTx(boolean readOnly) { this.readOnly = readOnly; }

            @Override public void insertPeriod(BillingPeriod period, long by) throws SQLException, BillingPeriodExistsException {
                calls.add("insertPeriod");
                if (readOnly) { throw new SQLException("read only", "25006", 1792); }
                var queued = insertFailures.poll();
                if (queued instanceof SQLException sql) { throw sql; }
                if (queued instanceof BillingPeriodExistsException exists) { throw exists; }
                if (periods.containsKey(period.kyThu())) {
                    throw new BillingPeriodExistsException(new SQLException("dup", "23000", 1062));
                }
                stagedPeriod = period; createdBy = by;
            }

            @Override public BillingInputs loadInputs(YearMonth period) throws SQLException {
                calls.add("loadInputs"); failIfRequested();
                return inputs;
            }

            @Override public void insertInvoices(List<MonthlyCharge> charges, Instant createdAt) throws SQLException {
                calls.add("insertInvoices");
                if (readOnly) { throw new SQLException("read only", "25006", 1792); }
                if (invoiceFailure != null) { throw invoiceFailure; }
                stagedInvoices.addAll(charges);
            }

            @Override public void finalizePeriod(String kyThu, int soHoaDon, int soHoBoQua, BigDecimal tongTien) {
                calls.add("finalizePeriod"); finalized++;
                stagedPeriod = new BillingPeriod(kyThu, stagedPeriod.hanNop(), soHoaDon, soHoBoQua, tongTien,
                        stagedPeriod.createdAt());
            }

            @Override public void rollbackOnly() { rollbackOnly = true; }
        }
    }
}
