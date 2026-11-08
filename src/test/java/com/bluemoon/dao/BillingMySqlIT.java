package com.bluemoon.dao;

import static com.bluemoon.services.BillingResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.config.DatabaseConfig;
import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.Account;
import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.ChargeStatus;
import com.bluemoon.models.MonthlyCharge;
import com.bluemoon.models.Role;
import com.bluemoon.services.BillingResult;
import com.bluemoon.services.BillingServiceImpl;
import com.bluemoon.services.HouseholdTestAuthorization;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * US-007 on a real MySQL. Disposable *_test schema only.
 * fee_rates comes ONLY from the official database/migrations/us006_fee_rates.sql (US-006, merged); there is no
 * fixture mirror. The billing tables are dropped again in AfterAll so other ITs are not blocked by foreign keys.
 */
@EnabledIfEnvironmentVariable(named = "BLUEMOON_MYSQL_TEST", matches = "1")
@Timeout(120)
class BillingMySqlIT {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneOffset.UTC);
    private static final List<String> MY_TABLES = List.of("invoice_lines", "invoices", "billing_periods", "fee_rates");
    private static final AtomicInteger CCCD_SEQUENCE = new AtomicInteger();

    private static DatabaseConnection connections;
    private BillingDAO dao;

    @BeforeAll static void connect() throws Exception {
        connections = new DatabaseConnection(DatabaseConfig.load());
        try (var c = connections.getConnection()) {
            assertTrue(c.getCatalog().endsWith("_test"), "Dedicated disposable *_test schema required");
            assertEquals("MySQL", c.getMetaData().getDatabaseProductName());
            assertEquals(8, c.getMetaData().getDatabaseMajorVersion());
        }
    }

    @AfterAll static void cleanUp() throws Exception {
        for (String table : MY_TABLES) { sql("DROP TABLE IF EXISTS " + table); }
    }

    @BeforeEach void reset() throws Exception {
        for (String table : MY_TABLES) { sql("DROP TABLE IF EXISTS " + table); }
        for (String table : List.of("residents", "households", "apartments", "fee_types")) {
            sql("DROP TABLE IF EXISTS " + table);
        }
        script("database/schema.sql");
        script("database/migrations/us001_household_apartment.sql");
        script("database/migrations/us002_residents.sql");
        script("database/migrations/us005_fee_types.sql");
        script("database/migrations/us006_fee_rates.sql"); // official US-006 migration, no mirror
        script("database/migrations/us007_billing.sql");
        sql("INSERT IGNORE INTO users (id, username, password_hash, ho_ten, role) "
                + "VALUES (7, 'billing-it-7', 'x', 'Billing IT', 'ACCOUNTANT')");
        dao = new BillingDAO(connections);
        fee("PHI_DV", "THEO_DIEN_TICH", "10000");
        fee("PHI_QL", "THEO_DIEN_TICH", "7000");
        fee("PHI_VS", "THEO_NHAN_KHAU", "6000");
        home("P1204", "HK-1204", "80.00", "DANG_O", 4);
    }

    // ---- helpers ----
    private BillingServiceImpl service() { return service(dao); }

    private static BillingServiceImpl service(BillingRepository repository) {
        return new BillingServiceImpl(repository, HouseholdTestAuthorization.admin(), CLOCK,
                () -> Optional.of(new Account(7, "billing-it-7", "Billing IT", Role.ACCOUNTANT)));
    }

    private static void fee(String code, String method, String price) throws Exception {
        sql("INSERT INTO fee_types (ma_loai_thu, ten_loai_thu, loai_thu, phuong_thuc_tinh, so_tien_mac_dinh, trang_thai) "
                + "VALUES ('" + code + "', 'Khoan " + code + "', 'BAT_BUOC', '" + method + "', 0, 'DANG_AP_DUNG')");
        rate(code, price, "2026-01-01");
    }

    private static void rate(String code, String price, String from) throws Exception {
        sql("INSERT INTO fee_rates (ma_loai_thu, don_gia, ngay_ap_dung) VALUES ('" + code + "', " + price
                + ", '" + from + "')");
    }

    private static void home(String apartment, String household, String area, String status, int people) throws Exception {
        sql("INSERT INTO apartments (so_can_ho, tang, dien_tich, trang_thai) VALUES ('" + apartment + "', "
                + Integer.parseInt(apartment.substring(1, 3)) + ", " + area + ", '" + status + "')");
        sql("INSERT INTO households (ma_ho, so_can_ho, ngay_nhan_nha) VALUES ('" + household + "', '" + apartment
                + "', '2024-01-01')");
        for (int i = 0; i < people; i++) { resident(household, i, "2024-01-01"); }
    }

    private static void resident(String household, int index, String movedIn) throws Exception {
        String cccd = String.format("%012d", 100000000000L + CCCD_SEQUENCE.incrementAndGet());
        sql("INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, ngay_chuyen_den) "
                + "VALUES ('" + household + "', 'Nguoi " + index + "', '1990-01-01', 'NAM', '" + cccd + "', '"
                + (index == 0 ? "CHU_HO" : "CON") + "', '" + movedIn + "')");
    }

    private static void sql(String text) throws Exception {
        try (var c = connections.getConnection(); var s = c.createStatement()) { s.execute(text); }
    }

    private static void script(String file) throws Exception {
        String text = Files.readString(Path.of(file)).replaceAll("(?m)^\\s*--.*$", "");
        for (String command : text.split(";")) { if (!command.isBlank()) { sql(command); } }
    }

    private static long count(String table) throws Exception {
        try (var c = connections.getConnection(); var s = c.createStatement();
             var rows = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static void assertNothingStored() throws Exception {
        assertEquals(0, count("billing_periods"));
        assertEquals(0, count("invoices"));
        assertEquals(0, count("invoice_lines"));
    }

    private static BigDecimal money(String value) { return new BigDecimal(value); }

    // ---- schema ----
    @Test void migrationIsIdempotentAndCreatesTheThreeTables() throws Exception {
        script("database/migrations/us007_billing.sql");
        script("database/migrations/us007_billing.sql");
        for (String table : List.of("billing_periods", "invoices", "invoice_lines")) {
            assertEquals(0, count(table));
        }
    }

    @Test void databaseRejectsInvalidRows() throws Exception {
        assertThrows(SQLException.class, () -> sql("INSERT INTO billing_periods (ky_thu, han_nop, created_by, created_at) "
                + "VALUES ('2026-13', '2026-12-15', 7, NOW(6))"));
        assertThrows(SQLException.class, () -> sql("INSERT INTO billing_periods (ky_thu, han_nop, tong_tien, created_by, created_at) "
                + "VALUES ('2026-11', '2026-12-15', 10.5, 7, NOW(6))"), "fractional VND");
        assertThrows(SQLException.class, () -> sql("INSERT INTO billing_periods (ky_thu, han_nop, created_by, created_at) "
                + "VALUES ('2026-11', '2026-12-15', 999, NOW(6))"), "unknown creator");
    }

    @Test void restrictForeignKeysProtectBilledData() throws Exception {
        assertEquals(SUCCESS, service().generatePeriod("2026-11").status());
        assertThrows(SQLException.class, () -> sql("DELETE FROM fee_types WHERE ma_loai_thu = 'PHI_QL'"));
        assertThrows(SQLException.class, () -> sql("DELETE FROM households WHERE ma_ho = 'HK-1204'"));
        assertThrows(SQLException.class, () -> sql("DELETE FROM apartments WHERE so_can_ho = 'P1204'"));
        assertThrows(SQLException.class, () -> sql("DELETE FROM billing_periods WHERE ky_thu = '2026-11'"));
        assertThrows(SQLException.class, () -> sql("DELETE FROM invoices WHERE ma_hoa_don = 'HD-202611-P1204'"));
    }

    @Test void conThieuIsGeneratedFromPayments() throws Exception {
        service().generatePeriod("2026-11");
        sql("UPDATE invoices SET da_thanh_toan = 1000000, trang_thai = 'DONG_THIEU' WHERE ma_hoa_don = 'HD-202611-P1204'");
        var invoice = dao.findInvoice("HD-202611-P1204").orElseThrow();
        assertEquals(money("384000.00"), invoice.conThieu());
    }

    @Test void knownGapSnapshotImmutabilityIsApplicationLevelOnly() throws Exception {
        service().generatePeriod("2026-11");
        // R2 in the contract review: the database does NOT block a direct UPDATE of invoice_lines (no exception here).
        sql("UPDATE invoice_lines SET thanh_tien = 1 WHERE ma_hoa_don = 'HD-202611-P1204' AND ma_loai_thu = 'PHI_DV'");
        // Reads fail closed: the model invariant (total == sum of lines) detects the tampering, the database does not.
        assertThrows(IllegalArgumentException.class, () -> dao.findInvoice("HD-202611-P1204"));
        assertEquals(SYSTEM_ERROR, service().getInvoice("HD-202611-P1204").status());
    }

    // ---- generate ----
    @Test void ac01EndToEndOnRealMySql() throws Exception {
        var result = service().generatePeriod("2026-11");
        assertEquals(SUCCESS, result.status());
        var period = result.data().orElseThrow();
        assertEquals(money("1384000.00"), period.tongTien());
        var stored = dao.findPeriod("2026-11").orElseThrow();
        assertEquals(1, stored.soHoaDon());
        assertEquals(0, stored.soHoBoQua());
        assertEquals(money("1384000.00"), stored.tongTien());
        assertEquals(LocalDate.of(2026, 12, 15), stored.hanNop());
        assertEquals(Instant.parse("2026-11-15T03:00:00Z"), stored.createdAt());
        var invoice = service().getInvoice("HD-202611-P1204").data().orElseThrow();
        assertEquals(money("1384000.00"), invoice.tongTien());
        assertEquals(money("0.00"), invoice.daThanhToan());
        assertEquals(money("1384000.00"), invoice.conThieu());
        assertEquals(ChargeStatus.CHUA_DONG, invoice.trangThai());
        assertEquals(money("80.00"), invoice.dienTich());
        assertEquals(4, invoice.soNhanKhau());
        assertEquals(LocalDate.of(2026, 12, 15), invoice.hanNop());
        var amounts = new java.util.HashMap<String, BigDecimal>();
        invoice.lines().forEach(l -> amounts.put(l.maKhoanThu(), l.thanhTien()));
        assertEquals(money("800000.00"), amounts.get("PHI_DV"));
        assertEquals(money("560000.00"), amounts.get("PHI_QL"));
        assertEquals(money("24000.00"), amounts.get("PHI_VS"));
        try (var c = connections.getConnection(); var s = c.createStatement();
             var rows = s.executeQuery("SELECT created_by FROM billing_periods WHERE ky_thu = '2026-11'")) {
            rows.next();
            assertEquals(7, rows.getLong(1));
        }
    }

    @Test void secondGenerateIsDuplicateAndAddsNothing() throws Exception {
        assertEquals(SUCCESS, service().generatePeriod("2026-11").status());
        long lines = count("invoice_lines");
        var again = service().generatePeriod("2026-11");
        assertEquals(DUPLICATE, again.status());
        assertEquals("Kỳ thu 2026-11 đã tồn tại trên hệ thống!", again.message());
        assertEquals(1, count("billing_periods"));
        assertEquals(1, count("invoices"));
        assertEquals(lines, count("invoice_lines"));
    }

    @Test void rateInForceAtFirstDayOfPeriodIsUsed() throws Exception {
        rate("PHI_QL", "6000", "2025-01-01");      // older, superseded
        rate("PHI_QL", "8000", "2026-12-01");      // starts after the period
        rate("PHI_QL", "9000", "2026-11-02");      // starts the day after the first day: must NOT apply
        var invoice = service().generatePeriod("2026-11").data().orElseThrow();
        var line = dao.findInvoice("HD-202611-P1204").orElseThrow().lines().stream()
                .filter(l -> l.maKhoanThu().equals("PHI_QL")).findFirst().orElseThrow();
        assertEquals(money("7000.00"), line.donGia());
        assertEquals(LocalDate.of(2026, 1, 1), line.ngayApDungGia());
        assertEquals(money("1384000.00"), invoice.tongTien());
    }

    @Test void rateStartingExactlyOnFirstDayApplies() throws Exception {
        rate("PHI_QL", "8000", "2026-11-01");
        service().generatePeriod("2026-11");
        var line = dao.findInvoice("HD-202611-P1204").orElseThrow().lines().stream()
                .filter(l -> l.maKhoanThu().equals("PHI_QL")).findFirst().orElseThrow();
        assertEquals(money("8000.00"), line.donGia());
        assertEquals(LocalDate.of(2026, 11, 1), line.ngayApDungGia());
        assertEquals(money("640000.00"), line.thanhTien());
    }

    @Test void ratesCreatedThroughTheRealUs006DaoAreBilled() throws Exception {
        var rates = new FeeRateDAO(connections);
        rates.insert(new com.bluemoon.models.FeeRate(null, "PHI_QL", money("8000"), LocalDate.of(2026, 11, 1), "US-006"));
        rates.insert(new com.bluemoon.models.FeeRate(null, "PHI_QL", money("9000"), LocalDate.of(2027, 1, 1), "later"));
        var result = service().generatePeriod("2026-11");
        assertEquals(SUCCESS, result.status());
        var line = dao.findInvoice("HD-202611-P1204").orElseThrow().lines().stream()
                .filter(l -> l.maKhoanThu().equals("PHI_QL")).findFirst().orElseThrow();
        assertEquals(money("8000.00"), line.donGia());
        assertEquals(LocalDate.of(2026, 11, 1), line.ngayApDungGia());
        assertEquals(money("1464000.00"), result.data().orElseThrow().tongTien());
    }

    @Test void billingInputsAgreeWithTheUs006EffectiveRateQuery() throws Exception {
        rate("PHI_QL", "6500", "2025-06-01");
        rate("PHI_QL", "8000", "2026-11-01");
        rate("PHI_QL", "9000", "2026-12-01");
        var rates = new FeeRateDAO(connections);
        for (String month : List.of("2026-10", "2026-11", "2026-12", "2027-01")) {
            var ym = java.time.YearMonth.parse(month);
            var loaded = dao.inReadOnlyTransaction(tx -> tx.loadInputs(ym)).fees().stream()
                    .filter(f -> f.maKhoanThu().equals("PHI_QL")).findFirst().orElseThrow().rate().orElseThrow();
            var expected = rates.findEffectiveRate("PHI_QL", ym.atDay(1)).orElseThrow();
            assertEquals(expected.donGia().compareTo(loaded.donGia()), 0, month);
            assertEquals(expected.ngayApDung(), loaded.ngayApDung(), month);
        }
    }

    @Test void missingRateRollsBackEverythingAndReleasesTheKey() throws Exception {
        sql("INSERT INTO fee_types (ma_loai_thu, ten_loai_thu, loai_thu, phuong_thuc_tinh, so_tien_mac_dinh, trang_thai) "
                + "VALUES ('PHI_X', 'Khoan moi', 'BAT_BUOC', 'THEO_CAN_HO', 0, 'DANG_AP_DUNG')");
        var result = service().generatePeriod("2026-11");
        assertEquals(MISSING_RATE, result.status());
        assertTrue(result.fieldErrors().containsKey("PHI_X"));
        assertNothingStored();
        rate("PHI_X", "50000", "2026-01-01");
        assertEquals(SUCCESS, service().generatePeriod("2026-11").status(), "the failed attempt left no lock or row");
        assertEquals(money("1434000.00"), dao.findPeriod("2026-11").orElseThrow().tongTien());
    }

    @Test void inactiveAndVoluntaryFeesAreNotBilled() throws Exception {
        sql("INSERT INTO fee_types (ma_loai_thu, ten_loai_thu, loai_thu, phuong_thuc_tinh, so_tien_mac_dinh, trang_thai) "
                + "VALUES ('PHI_OLD', 'Khoan ngung', 'BAT_BUOC', 'THEO_CAN_HO', 0, 'NGUNG_AP_DUNG')");
        rate("PHI_OLD", "99999", "2026-01-01");
        sql("INSERT INTO fee_types (ma_loai_thu, ten_loai_thu, loai_thu, phuong_thuc_tinh, so_tien_mac_dinh, trang_thai) "
                + "VALUES ('QUY_NGHEO', 'Quy tu nguyen', 'DONG_GOP', 'TU_NGUYEN', 0, 'DANG_AP_DUNG')");
        service().generatePeriod("2026-11");
        var invoice = dao.findInvoice("HD-202611-P1204").orElseThrow();
        assertEquals(3, invoice.lines().size());
        assertEquals(money("1384000.00"), invoice.tongTien());
    }

    @Test void vacantApartmentIsStillChargedManagementAndService() throws Exception {
        home("P1301", "HK-1301", "60.00", "CAN_HO_TRONG", 0);
        service().generatePeriod("2026-11");
        var invoice = dao.findInvoice("HD-202611-P1301").orElseThrow();
        assertEquals(0, invoice.soNhanKhau());
        assertEquals(money("1020000.00"), invoice.tongTien());
        var sanitation = invoice.lines().stream().filter(l -> l.maKhoanThu().equals("PHI_VS")).findFirst().orElseThrow();
        assertEquals(0, sanitation.thanhTien().signum());
    }

    @Test void householdsAndResidentsAfterPeriodEndAreExcluded() throws Exception {
        sql("INSERT INTO apartments (so_can_ho, tang, dien_tich, trang_thai) VALUES ('P1401', 14, 70.00, 'DANG_O')");
        sql("INSERT INTO households (ma_ho, so_can_ho, ngay_nhan_nha) VALUES ('HK-1401', 'P1401', '2026-12-01')");
        resident("HK-1204", 50, "2026-12-01");
        service().generatePeriod("2026-11");
        assertEquals(1, count("invoices"), "household received after the period end is not billed");
        assertEquals(4, dao.findInvoice("HD-202611-P1204").orElseThrow().soNhanKhau(), "later move-in not counted");
    }

    @Test void zeroTotalHouseholdIsSkippedAndCountedOnTheMySqlPeriod() throws Exception {
        sql("DELETE FROM fee_rates WHERE ma_loai_thu IN ('PHI_DV', 'PHI_QL')");
        sql("UPDATE fee_types SET trang_thai = 'NGUNG_AP_DUNG' WHERE ma_loai_thu IN ('PHI_DV', 'PHI_QL')");
        home("P1301", "HK-1301", "60.00", "CAN_HO_TRONG", 0);
        service().generatePeriod("2026-11");
        var period = dao.findPeriod("2026-11").orElseThrow();
        assertEquals(1, period.soHoaDon());
        assertEquals(1, period.soHoBoQua());
        assertEquals(money("24000.00"), period.tongTien());
    }

    @Test void nothingToBillWritesNothing() throws Exception {
        sql("UPDATE fee_types SET trang_thai = 'NGUNG_AP_DUNG'");
        assertEquals(NOTHING_TO_BILL, service().generatePeriod("2026-11").status());
        assertNothingStored();
    }

    @Test void previewWritesNothingAndExistingPreviewIsTheStoredSnapshot() throws Exception {
        var preview = service().previewPeriod("2026-11").data().orElseThrow();
        assertFalse(preview.alreadyGenerated());
        assertEquals(money("1384000.00"), preview.tongTien());
        assertNothingStored();
        service().generatePeriod("2026-11");
        var stored = service().previewPeriod("2026-11").data().orElseThrow();
        assertTrue(stored.alreadyGenerated());
        assertEquals(3, stored.rates().size());
    }

    // ---- historical snapshot ----
    @Test void closedPeriodNeverChangesWhenInputsChangeLater() throws Exception {
        service().generatePeriod("2026-11");
        var before = dao.findInvoice("HD-202611-P1204").orElseThrow();
        sql("UPDATE apartments SET dien_tich = 120.00 WHERE so_can_ho = 'P1204'");
        resident("HK-1204", 70, "2026-11-10");
        resident("HK-1204", 71, "2026-11-10");
        rate("PHI_QL", "9999", "2026-11-05");
        sql("UPDATE fee_types SET trang_thai = 'NGUNG_AP_DUNG', ten_loai_thu = 'Doi ten' WHERE ma_loai_thu = 'PHI_DV'");
        var after = dao.findInvoice("HD-202611-P1204").orElseThrow();
        assertEquals(before, after);
        var preview = service().previewPeriod("2026-11").data().orElseThrow();
        assertTrue(preview.alreadyGenerated());
        assertEquals(before.tongTien(), preview.tongTien());
        assertEquals(1, count("invoices"));
        assertTrue(dao.hasCharges("P1204"));
        assertFalse(dao.hasCharges("P9999"));
        assertEquals("Khoan PHI_DV", after.lines().stream().filter(l -> l.maKhoanThu().equals("PHI_DV"))
                .findFirst().orElseThrow().tenKhoanThu());
    }

    @Test void readEndpointsListAndGet() throws Exception {
        service().generatePeriod("2026-11");
        var all = service().listPeriods().data().orElseThrow();
        assertEquals(List.of("2026-11"), all.stream().map(BillingPeriod::kyThu).toList());
        var detail = service().getPeriod("2026-11").data().orElseThrow();
        assertEquals(1, detail.charges().size());
        assertTrue(detail.charges().get(0).lines().isEmpty(), "header level only");
        assertEquals(NOT_FOUND, service().getPeriod("2026-10").status());
        assertEquals(NOT_FOUND, service().getInvoice("HD-202610-P1204").status());
        assertEquals(NOT_FOUND, service().getInvoice("x' OR '1'='1").status(), "injection literal is just data");
    }

    @Test void apartmentHistoryReadsOnlyTheStoredSnapshot() throws Exception {
        assertTrue(service().getApartmentHistory("P1204").data().orElseThrow().isEmpty());
        service().generatePeriod("2026-11");
        sql("UPDATE apartments SET dien_tich = 120.00 WHERE so_can_ho = 'P1204'");
        var history = service().getApartmentHistory("P1204").data().orElseThrow();
        assertEquals(1, history.size());
        var row = history.get(0);
        assertEquals("2026-11", row.kyThu());
        assertEquals(ChargeStatus.CHUA_DONG, row.trangThai());
        assertEquals(dao.findInvoice("HD-202611-P1204").orElseThrow().tongTien(), row.tongTien());
        assertEquals(0, row.dienTich().compareTo(new java.math.BigDecimal("80")), "snapshot area, not today's");
        assertTrue(row.lines().isEmpty());
        assertTrue(service().getApartmentHistory("P1204' OR '1'='1").data().orElseThrow().isEmpty());
        assertEquals(1, count("invoices"));
    }

    @Test void br02RealLookupAsksForConfirmationOnlyAfterBilling() throws Exception {
        var households = new com.bluemoon.services.HouseholdServiceImpl(new HouseholdDAO(connections), dao,
                HouseholdTestAuthorization.admin(), java.time.Clock.systemDefaultZone());
        var change = new com.bluemoon.models.HouseholdUpdateForm("12", "95", "2024-03-15",
                com.bluemoon.models.ApartmentStatus.DANG_O);
        var before = households.updateHousehold("HK-1204", change, false);
        assertEquals(com.bluemoon.services.HouseholdResult.Status.SUCCESS, before.status());
        sql("UPDATE apartments SET dien_tich = 80.00 WHERE so_can_ho = 'P1204'");
        service().generatePeriod("2026-11");
        var guarded = households.updateHousehold("HK-1204", change, false);
        assertEquals(com.bluemoon.services.HouseholdResult.Status.CONFIRMATION_REQUIRED, guarded.status());
        assertEquals(com.bluemoon.services.HouseholdResult.Status.SUCCESS,
                households.updateHousehold("HK-1204", change, true).status());
    }

    // ---- rollback ----
    @Test void failureWhileWritingLinesRollsBackPeriodAndInvoices() throws Exception {
        var failing = new BillingDAO(() -> failAfter(connections.getConnection(), "INSERT INTO invoice_lines"));
        var result = service(failing).generatePeriod("2026-11");
        assertEquals(SYSTEM_ERROR, result.status());
        assertNothingStored();
        assertEquals(SUCCESS, service().generatePeriod("2026-11").status());
    }

    @Test void unknownCreatorFailsClosedWithoutPartialData() throws Exception {
        var svc = new BillingServiceImpl(dao, HouseholdTestAuthorization.admin(), CLOCK,
                () -> Optional.of(new Account(99999, "ghost", "Ghost", Role.ACCOUNTANT)));
        assertEquals(SYSTEM_ERROR, svc.generatePeriod("2026-11").status());
        assertNothingStored();
    }

    // ---- concurrency ----
    @Test void manyThreadsGeneratingTheSamePeriodProduceExactlyOne() throws Exception {
        home("P1301", "HK-1301", "60.00", "DANG_O", 2);
        for (int round = 0; round < 5; round++) {
            for (String table : MY_TABLES.subList(0, 3)) { sql("DELETE FROM " + table); }
            int threads = 8;
            var pool = Executors.newFixedThreadPool(threads);
            try {
                var start = new CountDownLatch(1);
                List<Future<BillingResult<BillingPeriod>>> futures = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    futures.add(pool.submit(() -> {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        return service().generatePeriod("2026-11");
                    }));
                }
                start.countDown();
                int success = 0, duplicate = 0;
                var others = new ArrayList<String>();
                for (var future : futures) {
                    var r = future.get(60, TimeUnit.SECONDS);
                    if (r.status() == SUCCESS) { success++; }
                    else if (r.status() == DUPLICATE) { duplicate++; }
                    else { others.add(r.status() + ": " + r.message()); }
                }
                assertEquals(1, success, "round " + round + " others=" + others);
                assertEquals(threads - 1, duplicate, "round " + round + " others=" + others);
                assertTrue(others.isEmpty(), others.toString());
                assertEquals(1, count("billing_periods"));
                assertEquals(2, count("invoices"));
                assertEquals(6, count("invoice_lines"));
            } finally { pool.shutdownNow(); }
        }
    }

    @Test void differentPeriodsDoNotBlockOrDeadlockEachOtherAtRepositoryLevel() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (String ky : List.of("2026-10", "2026-11")) {
                futures.add(pool.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return dao.inTransaction(tx -> {
                        tx.insertPeriod(new BillingPeriod(ky, LocalDate.parse(ky + "-01").plusMonths(1).withDayOfMonth(15),
                                0, 0, BigDecimal.ZERO, Instant.EPOCH), 7);
                        return true;
                    });
                }));
            }
            start.countDown();
            for (var f : futures) { assertTrue(f.get(30, TimeUnit.SECONDS)); }
            assertEquals(2, count("billing_periods"));
        } finally { pool.shutdownNow(); }
    }

    @Test void inputsChangingWhileGeneratingNeverProduceAnInternallyInconsistentInvoice() throws Exception {
        for (int i = 1; i <= 12; i++) { home(String.format("P%02d%02d", 10 + i % 15, i), "HK-X" + i, "80.00", "DANG_O", 1); }
        var stop = new AtomicBoolean();
        var writer = Executors.newSingleThreadExecutor();
        try {
            var changing = writer.submit(() -> {
                boolean big = false;
                while (!stop.get()) {
                    sql("UPDATE apartments SET dien_tich = " + (big ? "90.00" : "80.00") + " WHERE so_can_ho <> 'P1204'");
                    big = !big;
                }
                return null;
            });
            var result = service().generatePeriod("2026-11");
            stop.set(true);
            changing.get(30, TimeUnit.SECONDS);
            assertEquals(SUCCESS, result.status());
        } finally { stop.set(true); writer.shutdownNow(); }
        for (MonthlyCharge header : dao.findCharges("2026-11", true)) {
            var area = header.dienTich();
            assertTrue(area.compareTo(money("80")) == 0 || area.compareTo(money("90")) == 0, "area " + area);
            for (var line : header.lines()) {
                if (line.maKhoanThu().equals("PHI_QL")) {
                    assertEquals(0, line.soLuong().compareTo(area), header.maHoaDon() + " line area == header area");
                    assertEquals(0, line.thanhTien().compareTo(area.multiply(money("7000"))), header.maHoaDon());
                }
            }
        }
        assertEquals(13, count("invoices"));
    }

    @Test void lockWaitTimeoutIsBusyAndTheRequestSucceedsOnceTheLockIsReleased() throws Exception {
        try (var holder = connections.getConnection()) {
            holder.setAutoCommit(false);
            try (var s = holder.createStatement()) {
                s.execute("INSERT INTO billing_periods (ky_thu, han_nop, created_by, created_at) "
                        + "VALUES ('2026-11', '2026-12-15', 7, NOW(6))");
            }
            var impatient = new BillingDAO(() -> {
                var c = connections.getConnection();
                try (var s = c.createStatement()) { s.execute("SET SESSION innodb_lock_wait_timeout = 1"); }
                return c;
            });
            var busy = service(impatient).generatePeriod("2026-11");
            assertEquals(BUSY, busy.status());
            assertNotEquals(DUPLICATE, busy.status());
            holder.rollback();
        }
        assertNothingStored();
        assertEquals(SUCCESS, service().generatePeriod("2026-11").status());
    }

    @Test void waiterBecomesDuplicateWhenTheHolderCommits() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try (var holder = connections.getConnection()) {
            holder.setAutoCommit(false);
            try (var s = holder.createStatement()) {
                s.execute("INSERT INTO billing_periods (ky_thu, han_nop, created_by, created_at) "
                        + "VALUES ('2026-11', '2026-12-15', 7, NOW(6))");
            }
            var waiting = pool.submit(() -> service().generatePeriod("2026-11"));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> waiting.get(500, TimeUnit.MILLISECONDS),
                    "second request waits on the unique key");
            holder.commit();
            assertEquals(DUPLICATE, waiting.get(30, TimeUnit.SECONDS).status());
        } finally { pool.shutdownNow(); }
    }

    // ---- failure injection ----
    /** Runs the real statement first, then throws: proves rollback of work that really reached the database. */
    private static Connection failAfter(Connection real, String sqlPrefix) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (p, method, args) -> {
                    Object result = invoke(real, method, args);
                    if (!method.getName().equals("prepareStatement") || !((String) args[0]).startsWith(sqlPrefix)) {
                        return result;
                    }
                    var statement = (PreparedStatement) result;
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                            new Class<?>[] {PreparedStatement.class}, (s, action, values) -> {
                                Object output = invoke(statement, action, values);
                                if (action.getName().equals("executeBatch")) {
                                    throw new SQLException("Injected failure after database batch");
                                }
                                return output;
                            });
                });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
