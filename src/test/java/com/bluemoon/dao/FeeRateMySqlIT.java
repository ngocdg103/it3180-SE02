package com.bluemoon.dao;

import com.bluemoon.config.*;
import com.bluemoon.models.FeeRate;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable *_test only; owns US006_IT parent/rate rows, not the shared fee_types table. */
@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST", matches="1")
@org.junit.jupiter.api.parallel.ResourceLock("fee-fixture-lifecycle")
class FeeRateMySqlIT {
    private static DatabaseConnection connections;
    private final String code="US006_IT";
    private final LocalDate day=LocalDate.of(2026,11,1);
    private FeeRateDAO dao;
    @BeforeAll static void safeDatabase() throws Exception {
        connections=new DatabaseConnection(DatabaseConfig.load());
        try (var c=connections.getConnection()) {
            safe(c);
            var md=c.getMetaData();
            assertEquals("MySQL",md.getDatabaseProductName());
            assertTrue(md.getDatabaseMajorVersion()>8 || md.getDatabaseMajorVersion()==8
                    && (md.getDatabaseMinorVersion()>0 || md.getDatabaseProductVersion().matches("8\\.0\\.(1[6-9]|[2-9][0-9]).*")));
            try (var s=c.prepareStatement("SELECT @@sql_mode, @@default_storage_engine"); var r=s.executeQuery()) {
                assertTrue(r.next()); assertTrue(r.getString(1).contains("STRICT_")); assertEquals("InnoDB",r.getString(2));
            }
        }
        migrate("us005_fee_types.sql"); migrate("us006_fee_rates.sql");
    }
    private static void safe(Connection c) throws SQLException {
        assertNotNull(c.getCatalog()); assertTrue(c.getCatalog().endsWith("_test"),"Dedicated disposable *_test catalog required");
    }
    private static void execute(String sql,Object... values) throws Exception {
        try (var c=connections.getConnection()) {
            safe(c);
            try (var s=c.prepareStatement(sql)) {
                for (int i=0;i<values.length;i++) { s.setObject(i+1,values[i]); }
                s.executeUpdate();
            }
        }
    }
    private static void migrate(String file) throws Exception {
        String script=Files.readString(Path.of("database/migrations",file)).replaceAll("(?m)^\\s*--.*$","");
        for (String command:script.split(";")) { if (!command.isBlank()) { execute(command); } }
    }
    @BeforeEach void fixture() throws Exception {
        // Only test-owned rows are removed; no shared table DROP and no disabled FK checks.
        execute("DELETE FROM fee_rates WHERE ma_loai_thu = ?",code);
        execute("DELETE FROM fee_types WHERE ma_loai_thu = ?",code);
        execute("INSERT INTO fee_types (ma_loai_thu,ten_loai_thu,loai_thu,phuong_thuc_tinh,trang_thai) VALUES (?, 'US006 fixture', 'BAT_BUOC', 'THEO_CAN_HO', 'DANG_AP_DUNG')",code);
        dao=new FeeRateDAO(connections);
    }
    @AfterEach void cleanup() throws Exception {
        execute("DELETE FROM fee_rates WHERE ma_loai_thu = ?",code);
        execute("DELETE FROM fee_types WHERE ma_loai_thu = ?",code);
    }
    private FeeRate rate(String price,LocalDate date) { return new FeeRate(null,code,new BigDecimal(price),date,"Quyết định 😀"); }
    @Test void feeCategoryFixturePreservesExistingRateAndParentAcrossRepeatedCleanup() throws Exception {
        var saved=dao.insert(rate("7000.50",day));
        var types=new FeeTypeDAO(connections);
        var parent=types.findByCode(code).orElseThrow();
        FeeTypeMySqlIT.connect();
        var categoryFixture=new FeeTypeMySqlIT();
        try {
            for (int round=0;round<2;round++) {
                // Exercise the actual US-005 lifecycle, not a copy of its cleanup SQL.
                categoryFixture.migrate();
                types.insert(new com.bluemoon.models.FeeType("US005_IT_PHI_QL", "Lifecycle fixture",
                    com.bluemoon.models.FeeKind.BAT_BUOC, com.bluemoon.models.ChargeMethod.THEO_CAN_HO,
                    BigDecimal.ONE, com.bluemoon.models.FeeStatus.DANG_AP_DUNG));
                categoryFixture.cleanup();
                assertTrue(types.findByCode("US005_IT_PHI_QL").isEmpty());
                assertEquals(parent,types.findByCode(code).orElseThrow());
                assertEquals(List.of(saved),dao.findHistory(code));
            }
        } finally { categoryFixture.cleanup(); }
    }
    @Test void migrationRepeatConstraintsAndCollation() throws Exception {
        var saved=dao.insert(rate("7000",day));
        migrate("us006_fee_rates.sql"); migrate("us006_fee_rates.sql");
        assertEquals(saved,dao.findHistory(code).get(0));
        try (var c=connections.getConnection(); var s=c.prepareStatement("SHOW CREATE TABLE fee_rates"); var r=s.executeQuery()) {
            assertTrue(r.next()); String ddl=r.getString(2);
            assertTrue(ddl.contains("uq_fee_rates_type_date")); assertTrue(ddl.contains("ck_fee_rates_amount"));
            assertTrue(ddl.contains("fk_fee_rates_fee_type")); assertTrue(ddl.contains("ascii_bin"));
            assertTrue(ddl.contains("ENGINE=InnoDB")); assertTrue(ddl.contains("utf8mb4"));
            // SHOW CREATE may omit explicit RESTRICT because it is MySQL's default.
        }
        try (var c=connections.getConnection(); var s=c.prepareStatement("SELECT UPDATE_RULE, DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='fee_rates' AND CONSTRAINT_NAME='fk_fee_rates_fee_type'"); var r=s.executeQuery()) {
            assertTrue(r.next()); assertEquals("RESTRICT",r.getString(1)); assertEquals("RESTRICT",r.getString(2));
        }
        try (var c=connections.getConnection(); var s=c.prepareStatement("SELECT CHARACTER_SET_NAME, COLLATION_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='fee_rates' AND COLUMN_NAME='ma_loai_thu'"); var r=s.executeQuery()) {
            assertTrue(r.next()); assertEquals("ascii",r.getString(1)); assertEquals("ascii_bin",r.getString(2)); assertEquals("varchar(20)",r.getString(3));
        }
        assertEquals(1062,assertThrows(SQLException.class, () -> dao.insert(rate("9",day))).getErrorCode());
        assertEquals(1452,assertThrows(SQLException.class, () -> execute("INSERT INTO fee_rates(ma_loai_thu,don_gia,ngay_ap_dung) VALUES ('US006_MISSING',1,'2026-11-01')")).getErrorCode());
        assertEquals(3819,assertThrows(SQLException.class, () -> execute("INSERT INTO fee_rates(ma_loai_thu,don_gia,ngay_ap_dung) VALUES (?,0,'2026-12-01')",code)).getErrorCode());
        assertEquals(1048,assertThrows(SQLException.class, () -> execute("INSERT INTO fee_rates(ma_loai_thu,don_gia,ngay_ap_dung) VALUES (?,1,NULL)",code)).getErrorCode());
        assertEquals(1451,assertThrows(SQLException.class, () -> execute("DELETE FROM fee_types WHERE ma_loai_thu = ?",code)).getErrorCode());
        assertEquals(1451,assertThrows(SQLException.class, () -> execute("UPDATE fee_types SET ma_loai_thu='US006_MOVED' WHERE ma_loai_thu = ?",code)).getErrorCode());
    }
    @Test void effectiveHistoryBackdateAndExactDecimals() throws Exception {
        var old=dao.insert(rate("7000.50",day));
        var future=dao.insert(rate("99999999999999999.99",LocalDate.of(2027,1,1)));
        var back=dao.insert(rate("0.01",day.minusMonths(1)));
        assertEquals(List.of(future,old,back),dao.findHistory(code));
        assertTrue(dao.findEffectiveRate(code,day.minusMonths(2)).isEmpty());
        assertEquals(back,dao.findEffectiveRate(code,day.minusDays(1)).orElseThrow());
        assertEquals(old,dao.findEffectiveRate(code,day).orElseThrow());
        assertEquals(old,dao.findEffectiveRate(code,day.plusDays(1)).orElseThrow());
        assertEquals(future,dao.findEffectiveRate(code,LocalDate.of(2027,1,1)).orElseThrow());
        assertEquals(future,dao.findEffectiveRate(code,LocalDate.of(2028,1,1)).orElseThrow());
    }
    @Test void twoWritersSameDateProduceExactlyOneRate() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        Callable<Integer> writer=() -> { start.await(); try { dao.insert(rate("1",day)); return 0; } catch(SQLException e) { return e.getErrorCode(); } };
        try {
            var a=pool.submit(writer); var b=pool.submit(writer); start.countDown();
            var results=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));
            Collections.sort(results); assertEquals(List.of(0,1062),results); assertEquals(1,dao.findHistory(code).size());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS)); }
    }
    @Test void rollbackAfterRealInsertAndEligibilityChecks() throws Exception {
        // Delegate real JDBC but simulate a failure before commit to exercise DAO rollback.
        var failing=new FeeRateDAO(() -> {
            Connection real=connections.getConnection(); safe(real);
            return (Connection)java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a) -> {
                if (m.getName().equals("commit")) { throw new SQLException("Injected pre-commit failure"); }
                try { return m.invoke(real,a); } catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
            });
        });
        assertThrows(SQLException.class, () -> failing.insert(rate("1",day)));
        assertTrue(dao.findHistory(code).isEmpty());
        execute("UPDATE fee_types SET trang_thai='NGUNG_AP_DUNG' WHERE ma_loai_thu=?",code);
        assertEquals("45000",assertThrows(SQLException.class, () -> dao.insert(rate("1",day))).getSQLState());
        execute("UPDATE fee_types SET loai_thu='DONG_GOP',phuong_thuc_tinh='TU_NGUYEN',trang_thai='DANG_AP_DUNG' WHERE ma_loai_thu=?",code);
        assertEquals("45000",assertThrows(SQLException.class, () -> dao.insert(rate("1",day))).getSQLState());
    }
    @Test void concurrentStatusChangeCommitsBeforeInsertEligibilityRead() throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try (var parent=connections.getConnection()) {
            safe(parent); parent.setAutoCommit(false);
            try {
                try (var s=parent.prepareStatement("UPDATE fee_types SET trang_thai='NGUNG_AP_DUNG' WHERE ma_loai_thu=?")) {
                    s.setString(1,code); s.executeUpdate();
                }
                var entered=new CountDownLatch(1);
                var lockedDao=new FeeRateDAO(() -> { var c=connections.getConnection(); entered.countDown(); return c; });
                var writer=pool.submit(() -> { try { lockedDao.insert(rate("1",day)); return "SUCCESS"; } catch(SQLException e) { return e.getSQLState(); } });
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> writer.get(250,TimeUnit.MILLISECONDS));
                parent.commit();
                assertEquals("45000",writer.get(15,TimeUnit.SECONDS));
                assertTrue(dao.findHistory(code).isEmpty());
            } finally { parent.rollback(); }
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS)); }
    }
}
