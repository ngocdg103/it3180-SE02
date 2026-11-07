package com.bluemoon.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.config.DatabaseConfig;
import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.dao.ResidentRepository.Added;
import com.bluemoon.dao.ResidentRepository.ApartmentVacant;
import com.bluemoon.dao.ResidentRepository.FirstMemberMustBeHead;
import com.bluemoon.dao.ResidentRepository.HeadChanged;
import com.bluemoon.dao.ResidentRepository.HeadExists;
import com.bluemoon.dao.ResidentRepository.HeadPolicy;
import com.bluemoon.dao.ResidentRepository.HouseholdMissing;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.Gender;
import com.bluemoon.models.RelationToHead;
import com.bluemoon.models.Resident;
import com.bluemoon.models.ResidentForm;
import com.bluemoon.services.ResidentResult.Status;
import com.bluemoon.services.ResidentServiceImpl;
import com.bluemoon.services.HouseholdTestAuthorization;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Explicit opt-in, real MySQL 8.x, dedicated *_test schema only (same rule as HouseholdMySqlIT).
 * The residents/households/apartments tables are dropped and rebuilt for every test: never point this at a
 * shared database.
 */
@EnabledIfEnvironmentVariable(named = "BLUEMOON_MYSQL_TEST", matches = "1")
class ResidentMySqlIT {
    private static DatabaseConnection connections;
    private ResidentDAO dao;
    private HouseholdDAO householdDao;

    @BeforeAll static void connect() throws Exception {
        connections = new DatabaseConnection(DatabaseConfig.load());
        try (var c = connections.getConnection()) {
            assertTrue(c.getCatalog().endsWith("_test"), "Use a dedicated *_test schema");
            assertEquals(8, c.getMetaData().getDatabaseMajorVersion(), "MySQL 8.x required");
        }
    }

    @BeforeEach void freshSchema() throws Exception {
        runSql("DROP TABLE IF EXISTS residents");
        runSql("DROP TABLE IF EXISTS households");
        runSql("DROP TABLE IF EXISTS apartments");
        runScript("database/migrations/us001_household_apartment.sql");
        runScript("database/migrations/us001_seed.sql");
        runScript("database/migrations/us002_residents.sql");
        runScript("database/migrations/us002_seed.sql");
        dao = new ResidentDAO(connections);
        householdDao = new HouseholdDAO(connections);
    }

    private static Resident draft(String maHo, String name, String birth, String cccd, RelationToHead relation) {
        return new Resident(0, maHo, name, LocalDate.parse(birth), Gender.NU, cccd, relation, LocalDate.parse("2024-02-01"));
    }

    private Resident add(Resident draft) throws Exception {
        var outcome = dao.insert(draft, HeadPolicy.REJECT_IF_HEAD_EXISTS, null);
        assertInstanceOf(Added.class, outcome, outcome.toString());
        return ((Added) outcome).resident();
    }

    // ---- migration ----

    @Test void migrationAndSeedCanBeReappliedWithoutChangesOrDuplicates() throws Exception {
        runScript("database/migrations/us002_residents.sql");
        runScript("database/migrations/us002_residents.sql");
        runScript("database/migrations/us002_seed.sql");
        runScript("database/migrations/us002_seed.sql");
        assertEquals(5, scalar("SELECT COUNT(*) FROM residents"));
        assertEquals(3, dao.countByMaHo("HK-1002"));
        assertEquals(0, dao.countByMaHo("HK-1502"));
    }

    @Test void us001TablesAreUntouchedByTheMigration() throws Exception {
        assertEquals(6, scalar("SELECT COUNT(*) FROM households"));
        assertEquals(6, scalar("SELECT COUNT(*) FROM apartments"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name IN ('households', 'apartments') AND column_name IN ('ma_nhan_khau', 'head_ma_ho')"));
    }

    @Test void schemaHasGeneratedColumnsUniqueKeysForeignKeyAndCheck() throws Exception {
        assertEquals(2, scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'residents' AND column_name IN ('head_ma_ho', 'no_cccd_key') "
                + "AND extra LIKE '%STORED GENERATED%'"));
        for (var key : List.of("uq_residents_cccd", "uq_residents_one_head", "uq_residents_no_cccd_person")) {
            assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() "
                    + "AND table_name = 'residents' AND constraint_type = 'UNIQUE' AND constraint_name = '" + key + "'"), key);
        }
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE "
                + "constraint_schema = DATABASE() AND constraint_name = 'fk_residents_household' "
                + "AND referenced_table_name = 'households' AND delete_rule = 'RESTRICT' AND update_rule = 'RESTRICT'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() "
                + "AND table_name = 'residents' AND constraint_type = 'CHECK' AND constraint_name = 'chk_residents_cccd'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'residents' AND column_name = 'ma_nhan_khau' AND extra LIKE '%auto_increment%'"));
    }

    // ---- raw constraints (the last line of defence, independent of the DAO) ----

    private static SQLException insertFails(String values) {
        return assertThrows(SQLException.class, () -> runSql("INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, "
                + "so_cccd, quan_he_chu_ho, ngay_chuyen_den) VALUES " + values));
    }

    @Test void databaseAllowsAtMostOneHeadPerHousehold() throws Exception {
        var second = insertFails("('HK-1002', 'Người Khác', '1980-01-01', 'NAM', '001080000009', 'CHU_HO', '2024-01-01')");
        assertEquals(1062, second.getErrorCode());
        assertTrue(second.getMessage().contains("residents.uq_residents_one_head"), second.getMessage());
        // another household may have its own head
        runSql("INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, ngay_chuyen_den) "
                + "VALUES ('HK-1502', 'Chủ Hộ Mới', '1980-01-01', 'NAM', '001080000010', 'CHU_HO', '2024-01-01')");
        // demote, then a new head is allowed; head_ma_ho follows quan_he_chu_ho
        runSql("UPDATE residents SET quan_he_chu_ho = 'KHAC' WHERE ma_ho = 'HK-1002' AND quan_he_chu_ho = 'CHU_HO'");
        assertEquals(0, scalar("SELECT COUNT(*) FROM residents WHERE ma_ho = 'HK-1002' AND head_ma_ho IS NOT NULL"));
        runSql("INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, ngay_chuyen_den) "
                + "VALUES ('HK-1002', 'Người Khác', '1980-01-01', 'NAM', '001080000009', 'CHU_HO', '2024-01-01')");
        assertEquals(1, scalar("SELECT COUNT(*) FROM residents WHERE ma_ho = 'HK-1002' AND head_ma_ho = 'HK-1002'"));
    }

    @Test void databaseEnforcesCccdUniquenessFormatAndAllowsManyNulls() {
        var duplicate = insertFails("('HK-0101', 'Trùng Số', '1980-01-01', 'NAM', '001090000001', 'KHAC', '2024-01-01')");
        assertEquals(1062, duplicate.getErrorCode());
        assertTrue(duplicate.getMessage().contains("residents.uq_residents_cccd"), duplicate.getMessage());
        for (var bad : List.of("'12345678'", "'1234567890'", "'00109000000a'", "'1234567890123'")) {
            var failure = insertFails("('HK-0101', 'Sai Số', '1980-01-01', 'NAM', " + bad + ", 'KHAC', '2024-01-01')");
            assertTrue(failure.getErrorCode() == 3819 || failure.getErrorCode() == 1406, bad + " -> " + failure.getErrorCode());
        }
        assertDoesNotThrow(() -> runSql("INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, "
                + "ngay_chuyen_den) VALUES ('HK-0101', 'Bé Một', '2020-01-01', 'NAM', NULL, 'CON', '2024-01-01'), "
                + "('HK-0101', 'Bé Hai', '2021-01-01', 'NU', NULL, 'CON', '2024-01-01')"));
        assertDoesNotThrow(() -> runSql("INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, "
                + "ngay_chuyen_den) VALUES ('HK-0101', 'Chín Số', '1950-01-01', 'NAM', '123456789', 'BO_ME', '2024-01-01')"));
    }

    @Test void databaseRejectsTheSameChildWithoutCccdInOneHouseholdOnly() throws Exception {
        runSql("INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, ngay_chuyen_den) "
                + "VALUES ('HK-1002', 'Nguyễn Bé', '2020-05-05', 'NAM', NULL, 'CON', '2024-01-01')");
        var same = insertFails("('HK-1002', 'NGUYỄN BÉ', '2020-05-05', 'NAM', NULL, 'CON', '2024-01-01')");
        assertTrue(same.getMessage().contains("residents.uq_residents_no_cccd_person"), same.getMessage());
        // different accent, date, gender, household or a CCCD => different person
        runSql("INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, ngay_chuyen_den) VALUES "
                + "('HK-1002', 'Nguyên Bé', '2020-05-05', 'NAM', NULL, 'CON', '2024-01-01'), "
                + "('HK-1002', 'Nguyễn Bé', '2020-05-06', 'NAM', NULL, 'CON', '2024-01-01'), "
                + "('HK-1002', 'Nguyễn Bé', '2020-05-05', 'NU', NULL, 'CON', '2024-01-01'), "
                + "('HK-0101', 'Nguyễn Bé', '2020-05-05', 'NAM', NULL, 'CON', '2024-01-01'), "
                + "('HK-1002', 'Nguyễn Bé', '2020-05-05', 'NAM', '001120000077', 'CON', '2024-01-01')");
    }

    @Test void foreignKeyBlocksOrphansAndDeletesOfHouseholdsWithResidents() {
        assertEquals(1452, insertFails("('HK-NOPE', 'Mồ Côi', '1980-01-01', 'NAM', NULL, 'KHAC', '2024-01-01')").getErrorCode());
        assertThrows(SQLException.class, () -> runSql("DELETE FROM households WHERE ma_ho = 'HK-1002'"));
        assertThrows(SQLException.class, () -> runSql("UPDATE households SET ma_ho = 'HK-X' WHERE ma_ho = 'HK-1002'"));
    }

    // ---- DAO ----

    @Test void insertAndReadBackWithVietnameseTextHeadFirst() throws Exception {
        var saved = add(draft("HK-1502", "Đặng Thị Ánh", "1991-02-03", "001091000020", RelationToHead.CHU_HO));
        assertTrue(saved.maNhanKhau() > 0);
        add(draft("HK-1502", "Ngô Quang Hưng", "2015-06-07", null, RelationToHead.CON));
        var members = dao.findByMaHo("HK-1502");
        assertEquals(2, members.size());
        assertEquals("Đặng Thị Ánh", members.get(0).hoTen());
        assertTrue(members.get(0).isHead());
        assertEquals(LocalDate.parse("1991-02-03"), members.get(0).ngaySinh());
        assertNull(members.get(1).soCccd());
        assertEquals(saved.maNhanKhau(), dao.findHead("HK-1502").orElseThrow().maNhanKhau());
        assertEquals(2, dao.countByMaHo("HK-1502"));
        assertEquals(List.of(), dao.findByMaHo("HK-NOPE"));
        assertTrue(dao.findHead("HK-NOPE").isEmpty());
    }

    @Test void preconditionsAreDecidedInsideTheLockedTransaction() throws Exception {
        assertInstanceOf(HouseholdMissing.class,
                dao.insert(draft("HK-NOPE", "Không Có", "1980-01-01", "001080000030", RelationToHead.CHU_HO),
                        HeadPolicy.REJECT_IF_HEAD_EXISTS, null));
        assertInstanceOf(ApartmentVacant.class,
                dao.insert(draft("HK-1204", "Nhà Trống", "1980-01-01", "001080000031", RelationToHead.CHU_HO),
                        HeadPolicy.REJECT_IF_HEAD_EXISTS, null));
        assertInstanceOf(FirstMemberMustBeHead.class,
                dao.insert(draft("HK-1502", "Người Con", "2010-01-01", null, RelationToHead.CON),
                        HeadPolicy.REJECT_IF_HEAD_EXISTS, null));
        assertEquals(0, dao.countByMaHo("HK-1204") + dao.countByMaHo("HK-1502") + dao.countByMaHo("HK-NOPE"));
        // the apartment is not changed by a refused insert
        assertEquals(ApartmentStatus.CAN_HO_TRONG, householdDao.findByMaHo("HK-1204").orElseThrow().trangThai());
    }

    @Test void secondHeadNeedsConfirmationAndConfirmedReplacementIsAtomic() throws Exception {
        var oldHead = dao.findHead("HK-1002").orElseThrow();
        var newHead = draft("HK-1002", "Trần Thị Mới", "1988-04-04", "001088000040", RelationToHead.CHU_HO);

        var asked = dao.insert(newHead, HeadPolicy.REJECT_IF_HEAD_EXISTS, null);
        assertEquals(oldHead.maNhanKhau(), ((HeadExists) asked).currentHead().maNhanKhau());
        assertEquals(3, dao.countByMaHo("HK-1002"));
        assertTrue(dao.findHead("HK-1002").orElseThrow().maNhanKhau() == oldHead.maNhanKhau());

        var replaced = dao.insert(newHead, HeadPolicy.REPLACE_HEAD_IF_UNCHANGED, oldHead.maNhanKhau());
        var saved = ((Added) replaced).resident();
        assertEquals(4, dao.countByMaHo("HK-1002"));
        assertEquals(saved.maNhanKhau(), dao.findHead("HK-1002").orElseThrow().maNhanKhau());
        var old = dao.findByMaHo("HK-1002").stream().filter(r -> r.maNhanKhau() == oldHead.maNhanKhau()).findFirst().orElseThrow();
        assertEquals(RelationToHead.KHAC, old.quanHeChuHo());
        assertEquals(1, scalar("SELECT COUNT(*) FROM residents WHERE ma_ho = 'HK-1002' AND quan_he_chu_ho = 'CHU_HO'"));
    }

    @Test void staleConfirmationWritesNothing_otherPersonAndNoHead() throws Exception {
        var a = dao.findHead("HK-1002").orElseThrow();
        var b = ((Added) dao.insert(draft("HK-1002", "Trần Thị Mới", "1988-04-04", "001088000040", RelationToHead.CHU_HO),
                HeadPolicy.REPLACE_HEAD_IF_UNCHANGED, a.maNhanKhau())).resident();
        var attempt = draft("HK-1002", "Lê Người Ba", "1989-04-04", "001089000041", RelationToHead.CHU_HO);

        var stale = dao.insert(attempt, HeadPolicy.REPLACE_HEAD_IF_UNCHANGED, a.maNhanKhau());
        assertEquals(b.maNhanKhau(), ((HeadChanged) stale).currentHead().orElseThrow().maNhanKhau());
        assertEquals(4, dao.countByMaHo("HK-1002"));

        // the household now has NO head (demoted by an outside change): the old confirmation is still not accepted
        runSql("UPDATE residents SET quan_he_chu_ho = 'KHAC' WHERE ma_ho = 'HK-1002'");
        var headless = dao.insert(attempt, HeadPolicy.REPLACE_HEAD_IF_UNCHANGED, b.maNhanKhau());
        assertTrue(((HeadChanged) headless).currentHead().isEmpty());
        assertEquals(4, dao.countByMaHo("HK-1002"));
        assertTrue(dao.findHead("HK-1002").isEmpty());
        // resubmitting without a confirmation adds the head normally
        assertInstanceOf(Added.class, dao.insert(attempt, HeadPolicy.REJECT_IF_HEAD_EXISTS, null));
    }

    @Test void failedInsertAfterDemotionRollsBackTheDemotion() throws Exception {
        var oldHead = dao.findHead("HK-1002").orElseThrow();
        // CCCD of the spouse already exists: the INSERT fails after the old head was set to KHAC in the same transaction
        var failure = assertThrows(ResidentDuplicateException.class, () -> dao.insert(
                draft("HK-1002", "Trùng Số", "1988-04-04", "001092000002", RelationToHead.CHU_HO),
                HeadPolicy.REPLACE_HEAD_IF_UNCHANGED, oldHead.maNhanKhau()));
        assertEquals(ResidentDuplicateException.Field.SO_CCCD, failure.field());
        assertEquals(oldHead.maNhanKhau(), dao.findHead("HK-1002").orElseThrow().maNhanKhau());
        assertEquals(RelationToHead.CHU_HO, dao.findHead("HK-1002").orElseThrow().quanHeChuHo());
        assertEquals(3, dao.countByMaHo("HK-1002"));
        // same for a duplicate person without CCCD
        var person = assertThrows(ResidentDuplicateException.class, () -> dao.insert(
                new Resident(0, "HK-1002", "nguyễn minh c", LocalDate.parse("2018-03-01"), Gender.NAM, null,
                        RelationToHead.CHU_HO, LocalDate.parse("2024-02-01")),
                HeadPolicy.REPLACE_HEAD_IF_UNCHANGED, oldHead.maNhanKhau()));
        assertEquals(ResidentDuplicateException.Field.PERSON, person.field());
        assertEquals(oldHead.maNhanKhau(), dao.findHead("HK-1002").orElseThrow().maNhanKhau());
    }

    @Test void duplicateKeyMappingIsByConstraintNameAndNeverGuessed() {
        var cccd = new SQLException("Duplicate entry 'x' for key 'residents.uq_residents_cccd'", "23000", 1062);
        var head = new SQLException("Duplicate entry 'x' for key 'residents.uq_residents_one_head'", "23000", 1062);
        var person = new SQLException("Duplicate entry 'x' for key 'residents.uq_residents_no_cccd_person'", "23000", 1062);
        var unknown = new SQLException("Duplicate entry 'x' for key 'residents.PRIMARY'", "23000", 1062);
        var other = new SQLException("Deadlock found", "40001", 1213);
        assertEquals(ResidentDuplicateException.Field.SO_CCCD, ResidentDAO.duplicateField(cccd).orElseThrow());
        assertEquals(ResidentDuplicateException.Field.CHU_HO, ResidentDAO.duplicateField(head).orElseThrow());
        assertEquals(ResidentDuplicateException.Field.PERSON, ResidentDAO.duplicateField(person).orElseThrow());
        assertTrue(ResidentDAO.duplicateField(unknown).isEmpty());
        assertTrue(ResidentDAO.duplicateField(other).isEmpty());
    }

    @Test void injectionStringsAreTreatedAsLiterals() throws Exception {
        assertEquals(List.of(), dao.findByMaHo("' OR 1=1 --"));
        assertEquals(0, dao.countByMaHo("HK-1002' OR '1'='1"));
        assertEquals(5, scalar("SELECT COUNT(*) FROM residents"));
    }

    // ---- concurrency ----

    private static <T> List<Object> runTogether(List<Callable<T>> jobs) throws Exception {
        var pool = Executors.newFixedThreadPool(jobs.size());
        var ready = new CountDownLatch(jobs.size());
        var go = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<T>>();
        for (var job : jobs) {
            futures.add(pool.submit(() -> { ready.countDown(); go.await(); return job.call(); }));
        }
        ready.await();
        go.countDown();
        var results = new ArrayList<Object>();
        for (var future : futures) {
            try { results.add(future.get(60, TimeUnit.SECONDS)); }
            catch (java.util.concurrent.ExecutionException e) { results.add(e.getCause()); }
        }
        pool.shutdownNow();
        return results;
    }

    @Test void twoConfirmedReplacementsAtTheSameTimeLeaveExactlyOneHeadAndOneStaleResult() throws Exception {
        for (int round = 0; round < 15; round++) {
            runSql("DELETE FROM residents WHERE ma_ho = 'HK-1002'");
            var head = add(draft("HK-1002", "Chủ Hộ Cũ", "1970-01-01", "001070000050", RelationToHead.CHU_HO));
            int n = round;
            var results = runTogether(List.<Callable<ResidentRepository.InsertOutcome>>of(
                    () -> dao.insert(draft("HK-1002", "Người B", "1980-01-01", "00108000" + String.format("%04d", n), RelationToHead.CHU_HO),
                            HeadPolicy.REPLACE_HEAD_IF_UNCHANGED, head.maNhanKhau()),
                    () -> dao.insert(draft("HK-1002", "Người C", "1981-01-01", "00109000" + String.format("%04d", n), RelationToHead.CHU_HO),
                            HeadPolicy.REPLACE_HEAD_IF_UNCHANGED, head.maNhanKhau())));
            long added = results.stream().filter(r -> r instanceof Added).count();
            long stale = results.stream().filter(r -> r instanceof HeadChanged).count();
            assertEquals(1, added, results.toString());
            assertEquals(1, stale, results.toString());
            assertEquals(1, scalar("SELECT COUNT(*) FROM residents WHERE ma_ho = 'HK-1002' AND quan_he_chu_ho = 'CHU_HO'"));
            assertEquals(2, dao.countByMaHo("HK-1002"));
        }
    }

    @Test void twoHeadsAddedToAnEmptyHouseholdAtOnce_secondAsksForConfirmation() throws Exception {
        for (int round = 0; round < 15; round++) {
            runSql("DELETE FROM residents WHERE ma_ho = 'HK-1502'");
            int n = round;
            var results = runTogether(List.<Callable<ResidentRepository.InsertOutcome>>of(
                    () -> dao.insert(draft("HK-1502", "Đầu Tiên", "1980-01-01", "00108100" + String.format("%04d", n), RelationToHead.CHU_HO),
                            HeadPolicy.REJECT_IF_HEAD_EXISTS, null),
                    () -> dao.insert(draft("HK-1502", "Thứ Hai", "1981-01-01", "00108200" + String.format("%04d", n), RelationToHead.CHU_HO),
                            HeadPolicy.REJECT_IF_HEAD_EXISTS, null)));
            assertEquals(1, results.stream().filter(r -> r instanceof Added).count(), results.toString());
            assertEquals(1, results.stream().filter(r -> r instanceof HeadExists).count(), results.toString());
            assertEquals(1, dao.countByMaHo("HK-1502"));
        }
    }

    @Test void twoFirstMembersThatAreNotHeadsAtOnceAreBothRefused() throws Exception {
        var results = runTogether(List.<Callable<ResidentRepository.InsertOutcome>>of(
                () -> dao.insert(draft("HK-1502", "Con Một", "2010-01-01", null, RelationToHead.CON),
                        HeadPolicy.REJECT_IF_HEAD_EXISTS, null),
                () -> dao.insert(draft("HK-1502", "Con Hai", "2011-01-01", null, RelationToHead.CON),
                        HeadPolicy.REJECT_IF_HEAD_EXISTS, null)));
        assertTrue(results.stream().allMatch(r -> r instanceof FirstMemberMustBeHead), results.toString());
        assertEquals(0, dao.countByMaHo("HK-1502"));
    }

    @Test void sameCccdInTwoHouseholdsAtOnceSavesOnlyOne() throws Exception {
        for (int round = 0; round < 15; round++) {
            String cccd = "00107700" + String.format("%04d", round);
            var results = runTogether(List.<Callable<ResidentRepository.InsertOutcome>>of(
                    () -> dao.insert(draft("HK-1502", "Cạnh Tranh Một", "1980-01-01", cccd, RelationToHead.CHU_HO),
                            HeadPolicy.REJECT_IF_HEAD_EXISTS, null),
                    () -> dao.insert(draft("HK-0101", "Cạnh Tranh Hai", "1981-01-01", cccd, RelationToHead.KHAC),
                            HeadPolicy.REJECT_IF_HEAD_EXISTS, null)));
            assertEquals(1, results.stream().filter(r -> r instanceof Added).count(), results.toString());
            assertEquals(1, results.stream().filter(r -> r instanceof ResidentDuplicateException).count(), results.toString());
            assertEquals(1, scalar("SELECT COUNT(*) FROM residents WHERE so_cccd = '" + cccd + "'"));
            runSql("DELETE FROM residents WHERE so_cccd = '" + cccd + "'");
        }
    }

    @Test void sameChildWithoutCccdAtOnceSavesOnlyOne() throws Exception {
        var results = runTogether(List.<Callable<ResidentRepository.InsertOutcome>>of(
                () -> dao.insert(draft("HK-1002", "Bé Song Sinh", "2021-01-01", null, RelationToHead.CON),
                        HeadPolicy.REJECT_IF_HEAD_EXISTS, null),
                () -> dao.insert(draft("HK-1002", "bé song sinh", "2021-01-01", null, RelationToHead.CON),
                        HeadPolicy.REJECT_IF_HEAD_EXISTS, null)));
        assertEquals(1, results.stream().filter(r -> r instanceof Added).count(), results.toString());
        assertEquals(1, results.stream().filter(r -> r instanceof ResidentDuplicateException).count(), results.toString());
    }

    /** Lock ordering against the real US-001 UPDATE (apartments JOIN households): no deadlock may escape. */
    @Test void residentInsertsAndHouseholdUpdatesOnTheSameHouseholdNeverDeadlock() throws Exception {
        printExplainOfUs001Update();
        int rounds = 60;
        var jobs = new ArrayList<Callable<Object>>();
        jobs.add(() -> {
            for (int i = 0; i < rounds; i++) {
                householdDao.update("HK-1002", 10, new BigDecimal("75.50"), LocalDate.parse("2024-01-20"),
                        i % 2 == 0 ? ApartmentStatus.CHO_THUE : ApartmentStatus.DANG_O);
            }
            return "updates done";
        });
        jobs.add(() -> {
            for (int i = 0; i < rounds; i++) {
                dao.insert(draft("HK-1002", "Ngẫu Nhiên " + (char) (65 + i / 26) + (char) (65 + i % 26)
                        , "2019-01-01", null, RelationToHead.CON), HeadPolicy.REJECT_IF_HEAD_EXISTS, null);
            }
            return "inserts done";
        });
        var results = runTogether(jobs);
        assertEquals(List.of("updates done", "inserts done"), results,
                "a deadlock or error escaped: " + results);
        assertEquals(3 + rounds, dao.countByMaHo("HK-1002"));
    }

    private static void printExplainOfUs001Update() throws Exception {
        try (var c = connections.getConnection(); var s = c.createStatement();
             var rows = s.executeQuery("EXPLAIN UPDATE apartments a JOIN households h ON h.so_can_ho = a.so_can_ho "
                     + "SET a.tang = 10, h.ngay_nhan_nha = '2024-01-20' WHERE h.ma_ho = 'HK-1002'")) {
            var md = rows.getMetaData();
            while (rows.next()) {
                var line = new StringBuilder("EXPLAIN US-001 UPDATE:");
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    line.append(' ').append(md.getColumnLabel(i)).append('=').append(rows.getString(i));
                }
                System.out.println(line);
            }
        }
    }

    // ---- Service on the real database ----

    @Test void serviceOnRealDatabaseCoversTheAcceptanceCriteriaRbacAndDatabaseErrors() throws Exception {
        var households = new HouseholdDAO(connections);
        var service = new ResidentServiceImpl(dao, households, HouseholdTestAuthorization.admin(),
                java.time.Clock.systemDefaultZone());

        var first = service.createForHousehold(new ResidentForm("HK-1502", "Nguyễn Văn A", "1990-05-12", Gender.NAM,
                "001090000099", RelationToHead.CHU_HO, "2024-01-20"), null);
        assertTrue(first.isSuccess(), first.toString());
        var duplicate = service.createForHousehold(new ResidentForm("HK-1502", "Trần Thị B", "1992-08-30", Gender.NU,
                "001090000099", RelationToHead.VO, "2024-01-20"), null);
        assertEquals(Status.DUPLICATE, duplicate.status());
        var second = service.createForHousehold(new ResidentForm("HK-1502", "Trần Thị B", "1992-08-30", Gender.NU,
                "001092000098", RelationToHead.CHU_HO, "2024-01-20"), null);
        assertEquals(Status.CONFIRMATION_REQUIRED, second.status());
        assertTrue(second.message().contains("Nguyễn Văn A"));
        var replaced = service.createForHousehold(new ResidentForm("HK-1502", "Trần Thị B", "1992-08-30", Gender.NU,
                "001092000098", RelationToHead.CHU_HO, "2024-01-20"), first.data().orElseThrow().maNhanKhau());
        assertTrue(replaced.isSuccess(), replaced.toString());
        var members = service.getHousehold("HK-1502").data().orElseThrow();
        assertEquals(2, members.memberCount());
        assertEquals("Trần Thị B", members.head().orElseThrow().hoTen());
        assertEquals(Status.NOT_FOUND, service.getHousehold("HK-NOPE").status());
        assertEquals(Status.VALIDATION_ERROR, service.createForHousehold(new ResidentForm("HK-1204", "Nguyễn Văn A",
                "1990-05-12", Gender.NAM, "001090000097", RelationToHead.CHU_HO, "2024-01-20"), null).status());
        assertEquals(2, dao.countByMaHo("HK-1502"));

        // a database failure surfaces as a generic SYSTEM_ERROR, not an exception or SQL text
        runSql("DROP TABLE residents");
        var failed = service.createForHousehold(new ResidentForm("HK-1002", "Lê Văn D", "1985-11-02", Gender.NAM,
                "001085000096", RelationToHead.CHU_HO, "2024-01-20"), null);
        assertEquals(Status.SYSTEM_ERROR, failed.status());
        assertFalse(failed.message().toLowerCase().contains("table"));
        assertEquals(Status.SYSTEM_ERROR, service.getHousehold("HK-1002").status());
    }

    // ---- helpers ----

    private static long scalar(String sql) throws Exception {
        try (var c = connections.getConnection(); var s = c.createStatement(); var rows = s.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
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
