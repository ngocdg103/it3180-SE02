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
            try (var s = c.prepareStatement("DELETE FROM fee_types WHERE ma_loai_thu IN (?, ?, ?, ?, ?)")) {
                // Exact reserved codes only; never remove fee_rates or another suite's parent rows.
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
