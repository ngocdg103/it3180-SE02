package com.bluemoon.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.config.DatabaseConfig;
import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.Apartment;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.Household;
import com.bluemoon.models.HouseholdProfile;
import com.bluemoon.services.HouseholdTestAuthorization;
import com.bluemoon.services.HouseholdResult;
import com.bluemoon.services.HouseholdServiceImpl;
import com.bluemoon.models.HouseholdForm;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Explicit opt-in, real MySQL 8.x, dedicated *_test schema only (same rule as AuthMySqlIT).
 * The schema is dropped and rebuilt for every test: never point this at a shared database.
 */
@EnabledIfEnvironmentVariable(named = "BLUEMOON_MYSQL_TEST", matches = "1")
class HouseholdMySqlIT {
    private static DatabaseConnection connections;
    private HouseholdDAO dao;

    @BeforeAll static void connect() throws Exception {
        connections = new DatabaseConnection(DatabaseConfig.load());
        try (var c = connections.getConnection()) {
            assertTrue(c.getCatalog().endsWith("_test"), "Use a dedicated *_test schema");
            assertEquals(8, c.getMetaData().getDatabaseMajorVersion(), "MySQL 8.x required");
        }
    }

    @BeforeEach void freshSchema() throws Exception {
        runSql("DROP TABLE IF EXISTS residents"); // US-002 FK: must go before households
        runSql("DROP TABLE IF EXISTS households");
        runSql("DROP TABLE IF EXISTS apartments");
        runScript("database/migrations/us001_household_apartment.sql");
        runScript("database/migrations/us001_seed.sql");
        dao = new HouseholdDAO(connections);
    }

    private static HouseholdProfile profile(String soCanHo, String maHo, int tang, String area,
                                            ApartmentStatus status) {
        return new HouseholdProfile(new Apartment(soCanHo, tang, new BigDecimal(area), status),
                new Household(maHo, soCanHo, LocalDate.parse("2024-03-15")));
    }

    @Test void migrationAndSeedCanBeReappliedWithoutDuplicates() throws Exception {
        runScript("database/migrations/us001_household_apartment.sql");
        runScript("database/migrations/us001_seed.sql");
        assertEquals(6, dao.findAll(null).size());
    }

    @Test void seedMatchesSrsScenarios() throws Exception {
        var p1204 = dao.findBySoCanHo("P1204").orElseThrow();
        assertEquals("HK-1204", p1204.maHo());
        assertEquals(ApartmentStatus.CAN_HO_TRONG, p1204.trangThai());
        assertEquals(0, new BigDecimal("80").compareTo(p1204.dienTich()));
        assertEquals(12, p1204.tang());
    }

    @Test void insertThenReadBackBothWays() throws Exception {
        dao.insert(profile("P1205", "HK-1205", 12, "85.50", ApartmentStatus.DANG_O));

        var byId = dao.findByMaHo("HK-1205").orElseThrow();
        assertEquals("P1205", byId.soCanHo());
        assertEquals(new BigDecimal("85.50"), byId.dienTich());
        assertEquals(LocalDate.parse("2024-03-15"), byId.ngayNhanNha());
        assertEquals(byId, dao.findBySoCanHo("P1205").orElseThrow());
        assertTrue(dao.findByMaHo("HK-NOPE").isEmpty());
        assertTrue(dao.findBySoCanHo("P9999").isEmpty());
    }

    @Test void duplicateApartmentIsReportedAsSoCanHo() {
        var e = assertThrows(DuplicateRecordException.class,
                () -> dao.insert(profile("P1204", "HK-NEW", 12, "50", ApartmentStatus.DANG_O)));
        assertEquals(DuplicateRecordException.Field.SO_CAN_HO, e.field());
    }

    @Test void duplicateHouseholdCodeIsReportedAsMaHoAndRollsBackTheApartment() throws Exception {
        var e = assertThrows(DuplicateRecordException.class,
                () -> dao.insert(profile("P1205", "HK-1204", 12, "50", ApartmentStatus.DANG_O)));
        assertEquals(DuplicateRecordException.Field.MA_HO, e.field());
        assertTrue(dao.findBySoCanHo("P1205").isEmpty());
        try (var c = connections.getConnection(); var s = c.prepareStatement(
                "SELECT COUNT(*) FROM apartments WHERE so_can_ho = ?")) {
            s.setString(1, "P1205");
            try (var rows = s.executeQuery()) { rows.next(); assertEquals(0, rows.getInt(1), "orphan apartment"); }
        }
    }

    @Test void databaseConstraintsBackUpTheServiceRules() {
        assertThrows(SQLException.class, () -> dao.insert(profile("1205", "HK-A", 12, "50", ApartmentStatus.DANG_O)));
        assertThrows(SQLException.class, () -> dao.insert(profile("P1205", "HK-A", 0, "50", ApartmentStatus.DANG_O)));
        assertThrows(SQLException.class, () -> dao.insert(profile("P1205", "HK-A", 31, "50", ApartmentStatus.DANG_O)));
        assertThrows(SQLException.class, () -> dao.insert(profile("P1205", "HK-A", 12, "0", ApartmentStatus.DANG_O)));
    }

    @Test void householdCannotExistWithoutItsApartment() {
        assertThrows(SQLException.class, () -> runSql(
                "INSERT INTO households (ma_ho, so_can_ho, ngay_nhan_nha) VALUES ('HK-X', 'P7777', '2024-01-01')"));
        assertThrows(SQLException.class, () -> runSql("DELETE FROM apartments WHERE so_can_ho = 'P1204'"));
    }

    @Test void updateChangesApartmentAndHouseholdTogether() throws Exception {
        assertTrue(dao.update("HK-1204", 13, new BigDecimal("90.25"), LocalDate.parse("2024-04-01"),
                ApartmentStatus.DANG_O));
        var updated = dao.findByMaHo("HK-1204").orElseThrow();
        assertEquals(13, updated.tang());
        assertEquals(new BigDecimal("90.25"), updated.dienTich());
        assertEquals(LocalDate.parse("2024-04-01"), updated.ngayNhanNha());
        assertEquals(ApartmentStatus.DANG_O, updated.trangThai());
        assertEquals("P1204", updated.soCanHo());
        assertEquals(ApartmentStatus.DANG_O, dao.findBySoCanHo("P1204").orElseThrow().trangThai());
        // other households untouched
        assertEquals(ApartmentStatus.DANG_O, dao.findByMaHo("HK-1002").orElseThrow().trangThai());
        assertEquals(new BigDecimal("75.50"), dao.findByMaHo("HK-1002").orElseThrow().dienTich());
    }

    @Test void updatingWithIdenticalValuesStillReportsFound() throws Exception {
        var same = dao.findByMaHo("HK-1204").orElseThrow();
        assertTrue(dao.update("HK-1204", same.tang(), same.dienTich(), same.ngayNhanNha(), same.trangThai()));
        assertFalse(dao.update("HK-NOPE", 1, BigDecimal.ONE, LocalDate.parse("2024-01-01"), ApartmentStatus.DANG_O));
    }

    @Test void listIsSortedAndFilteredAndEmptyIsEmptyList() throws Exception {
        var all = dao.findAll(null);
        assertEquals(java.util.List.of("P0101", "P0508", "P1002", "P1204", "P1502", "P3015"),
                all.stream().map(HouseholdProfile::soCanHo).toList());
        assertEquals(2, dao.findAll(ApartmentStatus.CAN_HO_TRONG).size());
        assertEquals(1, dao.findAll(ApartmentStatus.CHO_THUE).size());
        runSql("DELETE FROM households");
        runSql("DELETE FROM apartments");
        assertTrue(dao.findAll(null).isEmpty());
    }

    @Test void injectionStringsAreTreatedAsLiterals() throws Exception {
        assertTrue(dao.findByMaHo("' OR 1=1 --").isEmpty());
        assertTrue(dao.findBySoCanHo("P1204' OR '1'='1").isEmpty());
        assertEquals(6, dao.findAll(null).size());
    }

    @Test void serviceOnRealDatabaseCoversAcceptanceCriteria() {
        var service = new HouseholdServiceImpl(dao, ApartmentChargeLookup.NONE, HouseholdTestAuthorization.admin(),
                java.time.Clock.systemDefaultZone());

        var created = service.createHousehold(
                new HouseholdForm("P1205", "12", "85.0", "HK-1205", "2024-03-15", ApartmentStatus.DANG_O));
        assertEquals("Tạo hồ sơ căn hộ P1205 thành công", created.message());          // AC-01

        var duplicate = service.createHousehold(
                new HouseholdForm("P1204", "12", "85.0", "HK-9999", "2024-03-15", ApartmentStatus.DANG_O));
        assertEquals(HouseholdResult.Status.DUPLICATE, duplicate.status());             // AC-02
        assertEquals("Số căn hộ đã tồn tại", duplicate.fieldErrors().get("so_can_ho"));
        assertEquals(HouseholdResult.Status.NOT_FOUND, service.getHouseholdById("HK-9999").status());

        var updated = service.updateHousehold("HK-1204", new com.bluemoon.models.HouseholdUpdateForm(
                "12", "80", "2024-03-15", ApartmentStatus.DANG_O), false);              // AC-03
        assertEquals(ApartmentStatus.DANG_O, updated.data().orElseThrow().trangThai());
    }

    private static void runSql(String sql) throws Exception {
        try (var c = connections.getConnection(); var s = c.createStatement()) { s.execute(sql); }
    }

    private static void runScript(String file) throws Exception {
        String sql = Files.readString(Path.of(file)).replaceAll("(?m)^\\s*--.*$", "");
        try (var c = connections.getConnection(); var s = c.createStatement()) {
            for (String command : sql.split(";")) {
                if (!command.isBlank()) { s.execute(command); }
            }
        }
    }
}
