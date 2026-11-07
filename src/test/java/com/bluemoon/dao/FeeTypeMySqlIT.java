package com.bluemoon.dao;

import com.bluemoon.config.*;
import com.bluemoon.models.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.SQLException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only; rebuilds fee_types in a dedicated *_test schema. */
@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST", matches="1")
class FeeTypeMySqlIT {
    private static DatabaseConnection connections;
    private FeeTypeDAO dao;
    @BeforeAll static void connect() throws Exception {
        connections = new DatabaseConnection(DatabaseConfig.load());
        try (var c = connections.getConnection()) {
            assertTrue(c.getCatalog().endsWith("_test"), "Dedicated *_test schema required");
            assertEquals("MySQL", c.getMetaData().getDatabaseProductName());
            assertTrue(c.getMetaData().getDatabaseMajorVersion() >= 8);
        }
    }
    @BeforeEach void migrate() throws Exception {
        sql("DROP TABLE IF EXISTS fee_types");
        String script = Files.readString(Path.of("database/migrations/us005_fee_types.sql"))
                .replaceAll("(?m)^\\s*--.*$", "");
        for (String command : script.split(";")) { if (!command.isBlank()) { sql(command); } }
        dao = new FeeTypeDAO(connections);
    }
    private static void sql(String command) throws Exception {
        try (var c = connections.getConnection(); var s = c.createStatement()) { s.execute(command); }
    }
    private static FeeType fee(String code, FeeKind kind, ChargeMethod method, String amount) {
        return new FeeType(code, "Phí quản lý", kind, method, new BigDecimal(amount), FeeStatus.DANG_AP_DUNG);
    }
    @Test void lifecycleDuplicateFilterAndNoHardDelete() throws Exception {
        var original = fee("PHI_QL", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, "99999999999999999.99");
        dao.insert(original);
        assertEquals(original, dao.findByCode("PHI_QL").orElseThrow());
        assertEquals(1062, assertThrows(SQLException.class, () -> dao.insert(original)).getErrorCode());
        assertEquals(1, dao.findAll(null).size());
        assertTrue(dao.update(original).isPresent(), "unchanged update is still found");
        assertTrue(dao.deactivate("PHI_QL").isPresent());
        assertTrue(dao.deactivate("PHI_QL").isPresent());
        assertTrue(dao.findAll(FeeStatus.DANG_AP_DUNG).isEmpty());
        assertEquals(1, dao.findAll(FeeStatus.NGUNG_AP_DUNG).size());
        assertTrue(dao.findByCode("PHI_QL").isPresent());
        assertTrue(dao.update(fee("NONE", FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "0")).isEmpty());
        assertTrue(dao.deactivate("NONE").isEmpty());
        assertTrue(dao.findByCode("' OR 1=1 --").isEmpty());
    }
    @Test void constraintsProtectDirectJdbcWritesAndRollback() throws Exception {
        assertThrows(SQLException.class, () -> dao.insert(fee("lower", FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "0")));
        assertThrows(SQLException.class, () -> dao.insert(fee("PHÍ", FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "0")));
        assertThrows(SQLException.class, () -> dao.insert(fee("PHI", FeeKind.BAT_BUOC, ChargeMethod.TU_NGUYEN, "0")));
        assertThrows(SQLException.class, () -> dao.insert(fee("QUY", FeeKind.DONG_GOP, ChargeMethod.TU_NGUYEN, "1")));
        assertThrows(SQLException.class, () -> dao.insert(fee("PHI", FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "-1")));
        assertThrows(SQLException.class, () -> sql("INSERT INTO fee_types VALUES ('X', 'ab', 'BAT_BUOC', 'THEO_CAN_HO', 0, 'DANG_AP_DUNG')"));
        assertTrue(dao.findAll(null).isEmpty());
        dao.insert(fee("QUY", FeeKind.DONG_GOP, ChargeMethod.TU_NGUYEN, "0"));
        assertThrows(SQLException.class, () -> dao.update(fee("QUY", FeeKind.DONG_GOP, ChargeMethod.TU_NGUYEN, "5")));
        assertEquals(0, dao.findByCode("QUY").orElseThrow().soTienMacDinh().signum());
    }
}
