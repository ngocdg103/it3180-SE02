package com.bluemoon.dao;

import com.bluemoon.config.DatabaseConfig;
import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable *_test only. Gates delegate every operation to actual MySQL JDBC. */
@EnabledIfEnvironmentVariable(named = "BLUEMOON_MYSQL_TEST", matches = "1")
@Timeout(60)
class HouseholdQ17MySqlIT {
    private static final String HOUSEHOLD_LOCK = "SELECT ma_ho FROM households WHERE ma_ho = ? FOR UPDATE";
    private static final String PRESENCE_LOCK = "SELECT ma_nhan_khau FROM residents WHERE ma_ho = ? LIMIT 1 FOR UPDATE";
    private static DatabaseConnection connections;
    private HouseholdDAO households;
    private ResidentDAO residents;

    @BeforeAll static void connect() throws Exception {
        connections = new DatabaseConnection(DatabaseConfig.load());
        try (var c = connections.getConnection()) {
            assertTrue(c.getCatalog().endsWith("_test"), "Dedicated disposable *_test schema required");
            assertEquals("MySQL", c.getMetaData().getDatabaseProductName());
            assertEquals(8, c.getMetaData().getDatabaseMajorVersion());
        }
    }

    @BeforeEach void reset() throws Exception {
        sql("DROP TABLE IF EXISTS residents");
        sql("DROP TABLE IF EXISTS households");
        sql("DROP TABLE IF EXISTS apartments");
        script("database/migrations/us001_household_apartment.sql");
        script("database/migrations/us002_residents.sql");
        households = new HouseholdDAO(connections);
        residents = new ResidentDAO(connections);
        households.insert(new HouseholdProfile(new Apartment("P1701", 17, new BigDecimal("80.00"), ApartmentStatus.DANG_O),
                new Household("HK-1701", "P1701", LocalDate.of(2024, 1, 1))));
    }

    @Test void occupiedApartmentRefusesEntireUpdateAndPreservesResidents() throws Exception {
        var outcome = residents.insert(draft(1), ResidentRepository.HeadPolicy.REJECT_IF_HEAD_EXISTS, null);
        assertInstanceOf(ResidentRepository.Added.class, outcome);
        var before = households.findByMaHo("HK-1701");
        assertThrows(HouseholdHasResidentsException.class, () -> vacant(households));
        assertEquals(before, households.findByMaHo("HK-1701"));
        assertEquals(1, residents.countByMaHo("HK-1701"));
        assertTrue(households.update("HK-1701", 18, new BigDecimal("90"), LocalDate.of(2024, 2, 1), ApartmentStatus.CHO_THUE));
        invariant();
    }

    @Test void emptyApartmentCanBecomeVacantAndUnchangedUpdateStillReportsFound() throws Exception {
        assertTrue(vacant(households));
        assertTrue(vacant(households));
        assertInstanceOf(ResidentRepository.ApartmentVacant.class,
                residents.insert(draft(1), ResidentRepository.HeadPolicy.REJECT_IF_HEAD_EXISTS, null));
        assertEquals(0, residents.countByMaHo("HK-1701"));
        invariant();
    }

    @Test void residentFirstHoldsHouseholdThenUpdateWaitsAndRefuses() throws Exception {
        Gate first = new Gate(HOUSEHOLD_LOCK);
        var secondStarted = new CountDownLatch(1);
        var resident = new ResidentDAO(connections, () -> intercept(connections.getConnection(), first, null, false));
        var household = new HouseholdDAO(() -> intercept(connections.getConnection(), null, secondStarted, false));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var adding = pool.submit(() -> resident.insert(draft(1), ResidentRepository.HeadPolicy.REJECT_IF_HEAD_EXISTS, null));
            assertTrue(first.held.await(10, TimeUnit.SECONDS), "Resident acquired household lock");
            var emptying = pool.submit(() -> vacant(household));
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> emptying.get(150, TimeUnit.MILLISECONDS));
            first.release.countDown();
            assertInstanceOf(ResidentRepository.Added.class, adding.get(10, TimeUnit.SECONDS));
            var refused = assertThrows(ExecutionException.class, () -> emptying.get(10, TimeUnit.SECONDS));
            assertInstanceOf(HouseholdHasResidentsException.class, refused.getCause());
            assertEquals(ApartmentStatus.DANG_O, households.findByMaHo("HK-1701").orElseThrow().trangThai());
            assertEquals(1, residents.countByMaHo("HK-1701"));
            invariant();
        } finally { first.release.countDown(); stop(pool); }
    }

    @Test void householdFirstPausesAfterZeroPresenceAndResidentWaitsThenRefuses() throws Exception {
        Gate first = new Gate(PRESENCE_LOCK);
        var secondStarted = new CountDownLatch(1);
        var household = new HouseholdDAO(() -> intercept(connections.getConnection(), first, null, false));
        var resident = new ResidentDAO(connections, () -> intercept(connections.getConnection(), null, secondStarted, false));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var emptying = pool.submit(() -> vacant(household));
            assertTrue(first.held.await(10, TimeUnit.SECONDS), "Household lock held and empty presence read executed");
            var adding = pool.submit(() -> resident.insert(draft(1), ResidentRepository.HeadPolicy.REJECT_IF_HEAD_EXISTS, null));
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> adding.get(150, TimeUnit.MILLISECONDS));
            first.release.countDown();
            assertTrue(emptying.get(10, TimeUnit.SECONDS));
            assertInstanceOf(ResidentRepository.ApartmentVacant.class, adding.get(10, TimeUnit.SECONDS));
            assertEquals(0, residents.countByMaHo("HK-1701"));
            invariant();
        } finally { first.release.countDown(); stop(pool); }
    }

    @Test void currentReadSeesResidentDespitePreviouslyEstablishedRepeatableReadSnapshot() throws Exception {
        try (var stale = connections.getConnection()) {
            stale.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            stale.setAutoCommit(false);
            try (var s = stale.createStatement(); var rows = s.executeQuery("SELECT COUNT(*) FROM residents WHERE ma_ho='HK-1701'")) {
                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
            }
            assertInstanceOf(ResidentRepository.Added.class,
                    residents.insert(draft(1), ResidentRepository.HeadPolicy.REJECT_IF_HEAD_EXISTS, null));
            var guarded = new HouseholdDAO(() -> stale);
            assertThrows(HouseholdHasResidentsException.class, () -> vacant(guarded));
        }
        assertEquals(1, residents.countByMaHo("HK-1701"));
        invariant();
    }

    @Test void failureAfterRealUpdateRollsBackApartmentAndHouseholdFields() throws Exception {
        var before = households.findByMaHo("HK-1701");
        var failed = new HouseholdDAO(() -> intercept(connections.getConnection(), null, null, true));
        assertThrows(SQLException.class, () -> vacant(failed));
        assertEquals(before, households.findByMaHo("HK-1701"));
        assertInstanceOf(ResidentRepository.Added.class,
                residents.insert(draft(1), ResidentRepository.HeadPolicy.REJECT_IF_HEAD_EXISTS, null));
        invariant();
    }

    @Test void bothRealDaosRacingNeverCommitVacantWithResidents() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 40; round++) {
                sql("DELETE FROM residents WHERE ma_ho='HK-1701'");
                households.update("HK-1701", 17, new BigDecimal("80"), LocalDate.of(2024, 1, 1), ApartmentStatus.DANG_O);
                var start = new CountDownLatch(1);
                final int number = round;
                var adding = pool.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return residents.insert(draft(number), ResidentRepository.HeadPolicy.REJECT_IF_HEAD_EXISTS, null);
                });
                var emptying = pool.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try { return vacant(households); }
                    catch (HouseholdHasResidentsException denied) { return false; }
                });
                start.countDown();
                var added = adding.get(10, TimeUnit.SECONDS);
                boolean emptied = emptying.get(10, TimeUnit.SECONDS);
                if (emptied) {
                    assertInstanceOf(ResidentRepository.ApartmentVacant.class, added);
                    assertEquals(0, residents.countByMaHo("HK-1701"));
                } else {
                    assertInstanceOf(ResidentRepository.Added.class, added);
                    assertEquals(1, residents.countByMaHo("HK-1701"));
                }
                invariant(); // Neither a deadlock/SQL error nor unexpected lost insert is swallowed.
            }
        } finally { stop(pool); }
    }

    private void invariant() throws Exception {
        var status = households.findByMaHo("HK-1701").orElseThrow().trangThai();
        assertFalse(status == ApartmentStatus.CAN_HO_TRONG && residents.countByMaHo("HK-1701") > 0);
    }

    private static boolean vacant(HouseholdDAO dao) throws SQLException {
        return dao.update("HK-1701", 18, new BigDecimal("90"), LocalDate.of(2024, 2, 1), ApartmentStatus.CAN_HO_TRONG);
    }

    private static Resident draft(int number) {
        return new Resident(0, "HK-1701", "Test Head", LocalDate.of(1990, 1, 1), Gender.NAM,
                String.format("001090%06d", number), RelationToHead.CHU_HO, LocalDate.of(2024, 1, 1));
    }

    private static void stop(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static final class Gate {
        final String sql;
        final CountDownLatch held = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean once = new AtomicBoolean();
        Gate(String sql) { this.sql = sql; }
    }

    /** Test-only transparent JDBC wrapper: pause only after the real database executed the locking read. */
    private static Connection intercept(Connection real, Gate gate, CountDownLatch beforeLock, boolean failAfterWrite) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (p, method, args) -> {
            Object result = invoke(real, method, args);
            if (!method.getName().equals("prepareStatement")) { return result; }
            String sql = (String) args[0];
            var statement = (PreparedStatement) result;
            return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[] {PreparedStatement.class}, (s, action, values) -> {
                if (action.getName().equals("executeQuery") && sql.equals(HOUSEHOLD_LOCK) && beforeLock != null) { beforeLock.countDown(); }
                Object output = invoke(statement, action, values);
                if (action.getName().equals("executeQuery") && gate != null && sql.equals(gate.sql) && gate.once.compareAndSet(false, true)) {
                    gate.held.countDown();
                    if (!gate.release.await(10, TimeUnit.SECONDS)) { throw new SQLException("Test gate timed out"); }
                }
                if (action.getName().equals("executeUpdate") && sql.startsWith("UPDATE apartments") && failAfterWrite) {
                    throw new SQLException("Injected failure after database update");
                }
                return output;
            });
        });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private static void sql(String text) throws Exception {
        try (var c = connections.getConnection(); var s = c.createStatement()) { s.execute(text); }
    }

    private static void script(String file) throws Exception {
        String text = Files.readString(Path.of(file)).replaceAll("(?m)^\\s*--.*$", "");
        for (String command : text.split(";")) { if (!command.isBlank()) { sql(command); } }
    }
}
