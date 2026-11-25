package com.bluemoon.dao;

import com.bluemoon.config.*;
import com.bluemoon.models.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.SQLException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only; owns US005_IT_* rows, never drops a shared parent table. */
@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST", matches="1")
@org.junit.jupiter.api.parallel.ResourceLock("fee-fixture-lifecycle")
class FeeTypeMySqlIT {
    private static DatabaseConnection connections;
    private static final String PREFIX = "US005_IT_";
    private FeeTypeDAO dao;
    @BeforeAll static void connect() throws Exception {
        connections = new DatabaseConnection(DatabaseConfig.load());
        try (var c = connections.getConnection()) {
            safe(c);
            assertEquals("MySQL", c.getMetaData().getDatabaseProductName());
            assertTrue(c.getMetaData().getDatabaseMajorVersion() >= 8);
        }
    }
    @BeforeEach void migrate() throws Exception {
        String script = Files.readString(Path.of("database/migrations/us005_fee_types.sql"))
                .replaceAll("(?m)^\\s*--.*$", "");
        for (String command : script.split(";")) { if (!command.isBlank()) { sql(command); } }
        script = Files.readString(Path.of("database/migrations/us006_fee_rates.sql")).replaceAll("(?m)^\\s*--.*$", "");
        for (String command : script.split(";")) { if (!command.isBlank()) { sql(command); } }
        cleanup();
        dao = new FeeTypeDAO(connections);
    }
    private static void sql(String command) throws Exception {
        try (var c = connections.getConnection()) {
            safe(c);
            try (var s = c.createStatement()) { s.execute(command); }
        }
    }
    private static void safe(java.sql.Connection c) throws SQLException {
        assertNotNull(c.getCatalog(), "Catalog is required before fixture writes");
        assertTrue(c.getCatalog().endsWith("_test"), "Dedicated *_test schema required");
    }
    @AfterEach void cleanup() throws Exception {
        try (var c = connections.getConnection()) {
            safe(c);
            try (var rates = c.prepareStatement("DELETE FROM fee_rates WHERE ma_loai_thu IN (?, ?, ?, ?, ?)")) {
                String[] codes = {"PHI_QL", "PHI", "QUY", "X", "NONE"};
                for (int i = 0; i < codes.length; i++) rates.setString(i + 1, PREFIX + codes[i]);
                rates.executeUpdate();
            }
            try (var s = c.prepareStatement("DELETE FROM fee_types WHERE ma_loai_thu IN (?, ?, ?, ?, ?)")) {
                // Exact fixture-owned parent and rate codes only; preserve other suites.
                String[] codes = {"PHI_QL", "PHI", "QUY", "X", "NONE"};
                for (int i = 0; i < codes.length; i++) s.setString(i + 1, PREFIX + codes[i]);
                s.executeUpdate();
            }
        }
    }
    private java.util.List<FeeType> ownRows(FeeStatus status) throws SQLException {
        return dao.findAll(status).stream().filter(f -> f.maKhoanThu().startsWith(PREFIX)).toList();
    }
    private static FeeType fee(String code, FeeKind kind, ChargeMethod method, String amount) {
        return new FeeType(PREFIX + code, "Phí quản lý", kind, method, new BigDecimal(amount), FeeStatus.DANG_AP_DUNG);
    }
    private com.bluemoon.services.FeeTypeService service(FeeTypeDAO types) {
        var auth = com.bluemoon.services.HouseholdTestAuthorization.admin();
        return new com.bluemoon.services.FeeTypeServiceImpl(types, auth);
    }
    private FeeTypeForm initialForm(String price) {
        return new FeeTypeForm(PREFIX + "PHI", "Initial fixture", FeeKind.BAT_BUOC,
            ChargeMethod.THEO_CAN_HO, price, FeeStatus.DANG_AP_DUNG, java.time.LocalDate.of(2026,11,1));
    }
    @Test void createInitialHistoryDuplicateAndAppendPreserveFirstPrice() throws Exception {
        var svc = service(dao);
        assertTrue(svc.createFeeType(initialForm("7000.50")).isSuccess());
        var rates = new FeeRateDAO(connections);
        var history = rates.findHistory(PREFIX + "PHI");
        assertEquals(1, history.size());
        var first = history.get(0);
        assertEquals(new BigDecimal("7000.50"), first.donGia());
        assertEquals("INITIAL", first.ghiChu());
        assertEquals(java.time.LocalDate.of(2026,11,1), first.ngayApDung());
        assertEquals(com.bluemoon.services.FeeTypeResult.Status.DUPLICATE, svc.createFeeType(initialForm("8000")).status());
        assertEquals(history, rates.findHistory(PREFIX + "PHI"));
        assertEquals(new BigDecimal("7000.50"), dao.findByCode(PREFIX + "PHI").orElseThrow().soTienMacDinh());
        var auth = com.bluemoon.services.HouseholdTestAuthorization.admin();
        var rateService = new com.bluemoon.services.FeeRateServiceImpl(rates, dao, auth);
        assertTrue(rateService.createRate(new FeeRateForm(PREFIX + "PHI", "8000", java.time.LocalDate.of(2027,1,1), "Update")).isSuccess());
        assertEquals(2, rateService.listHistory(PREFIX + "PHI").data().orElseThrow().size());
        assertEquals(first, rates.findHistory(PREFIX + "PHI").get(1));
        assertEquals(first, rates.findEffectiveRate(PREFIX + "PHI",java.time.LocalDate.of(2026,12,1)).orElseThrow());
        assertEquals(new BigDecimal("8000.00"), rates.findEffectiveRate(PREFIX + "PHI",java.time.LocalDate.of(2027,1,1)).orElseThrow().donGia());
    }
    @Test void initialRateWriteFailureRollsBackNewCategory() throws Exception {
        assertRollback("INSERT INTO fee_rates");
    }
    @Test void categoryWriteFailureLeavesNoOrphanRate() throws Exception {
        assertRollback("INSERT INTO fee_types");
    }
    @Test void failureAfterBothInsertsRollsBackBoth() throws Exception {
        assertRollback("commit");
    }
    private void assertRollback(String failAt) throws Exception {
        var failing = new FeeTypeDAO(() -> {
            var real = connections.getConnection(); safe(real);
            return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(
                java.sql.Connection.class.getClassLoader(), new Class<?>[]{java.sql.Connection.class}, (p,m,a) -> {
                    if (m.getName().equals(failAt) || m.getName().equals("prepareStatement")
                            && ((String)a[0]).startsWith(failAt)) {
                        throw new SQLException("Injected initial-rate failure");
                    }
                    try { return m.invoke(real,a); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
        });
        assertEquals(com.bluemoon.services.FeeTypeResult.Status.SYSTEM_ERROR,
            service(failing).createFeeType(initialForm("7000.50")).status());
        assertTrue(dao.findByCode(PREFIX + "PHI").isEmpty());
        assertTrue(new FeeRateDAO(connections).findHistory(PREFIX + "PHI").isEmpty());
        assertTrue(service(dao).createFeeType(initialForm("7000.50")).isSuccess(), "Retry after known rollback succeeds once");
        assertEquals(1, new FeeRateDAO(connections).findHistory(PREFIX + "PHI").size());
    }

    @Test void lifecycleDuplicateFilterAndNoHardDelete() throws Exception {
        var original = fee("PHI_QL", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, "99999999999999999.99");
        dao.insert(original);
        assertEquals(original, dao.findByCode(PREFIX + "PHI_QL").orElseThrow());
        assertEquals(1062, assertThrows(SQLException.class, () -> dao.insert(original)).getErrorCode());
        assertEquals(1, ownRows(null).size());
        assertTrue(dao.update(original).isPresent(), "unchanged update is still found");
        assertTrue(dao.deactivate(PREFIX + "PHI_QL").isPresent());
        assertTrue(dao.deactivate(PREFIX + "PHI_QL").isPresent());
        assertTrue(ownRows(FeeStatus.DANG_AP_DUNG).isEmpty());
        assertEquals(1, ownRows(FeeStatus.NGUNG_AP_DUNG).size());
        assertTrue(dao.findByCode(PREFIX + "PHI_QL").isPresent());
        assertTrue(dao.update(fee("NONE", FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "0")).isEmpty());
        assertTrue(dao.deactivate(PREFIX + "NONE").isEmpty());
        assertTrue(dao.findByCode("' OR 1=1 --").isEmpty());
    }
    @Test void constraintsProtectDirectJdbcWritesAndRollback() throws Exception {
        assertThrows(SQLException.class, () -> dao.insert(fee("lower", FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "0")));
        assertThrows(SQLException.class, () -> dao.insert(fee("PHÍ", FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "0")));
        assertThrows(SQLException.class, () -> dao.insert(fee("PHI", FeeKind.BAT_BUOC, ChargeMethod.TU_NGUYEN, "0")));
        assertThrows(SQLException.class, () -> dao.insert(fee("QUY", FeeKind.DONG_GOP, ChargeMethod.TU_NGUYEN, "1")));
        assertThrows(SQLException.class, () -> dao.insert(fee("PHI", FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "-1")));
        assertThrows(SQLException.class, () -> sql("INSERT INTO fee_types VALUES ('US005_IT_X', 'ab', 'BAT_BUOC', 'THEO_CAN_HO', 0, 'DANG_AP_DUNG')"));
        assertTrue(ownRows(null).isEmpty());
        dao.insert(fee("QUY", FeeKind.DONG_GOP, ChargeMethod.TU_NGUYEN, "0"));
        assertThrows(SQLException.class, () -> dao.update(fee("QUY", FeeKind.DONG_GOP, ChargeMethod.TU_NGUYEN, "5")));
        assertEquals(0, dao.findByCode(PREFIX + "QUY").orElseThrow().soTienMacDinh().signum());
    }
}
