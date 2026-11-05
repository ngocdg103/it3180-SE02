package com.bluemoon.services;

import com.bluemoon.config.DatabaseConfig;
import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.dao.AccountDAO;
import com.bluemoon.dao.AccountRepository;
import com.bluemoon.dao.AccountRepository.State;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mindrot.jbcrypt.BCrypt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in, real MySQL 8.x, dedicated *_test schema only. */
@EnabledIfEnvironmentVariable(named = "BLUEMOON_MYSQL_TEST", matches = "1")
class AuthMySqlIT {
    private static DatabaseConnection connections;
    private static AccountDAO dao;
    private String username;

    @BeforeAll static void setup() throws Exception {
        connections = new DatabaseConnection(DatabaseConfig.load());
        try (var c = connections.getConnection()) {
            assertTrue(c.getCatalog().endsWith("_test"), "Use a dedicated *_test schema");
            assertEquals(8, c.getMetaData().getDatabaseMajorVersion(), "MySQL 8.x required");
            assertTrue(c.getMetaData().getDatabaseProductName().contains("MySQL"));
        }
        runScript("database/schema.sql");
        runScript("database/seed.sql");
        dao = new AccountDAO(connections);
    }

    @BeforeEach void addFixture() throws Exception {
        username = "test" + UUID.randomUUID().toString().replace("-", "");
        try (var c = connections.getConnection(); var s = c.prepareStatement(
                "INSERT INTO users (username, password_hash, ho_ten, role) VALUES (?, ?, ?, ?)")) {
            s.setString(1, username);
            s.setString(2, BCrypt.hashpw("fixture-password", BCrypt.gensalt(4)));
            s.setString(3, "Fixture");
            s.setString(4, "ACCOUNTANT");
            s.executeUpdate();
        }
    }

    @AfterEach void cleanup() throws Exception {
        new AuthService(dao).logout();
        if (username == null) { return; }
        try (var c = connections.getConnection(); var s = c.prepareStatement("DELETE FROM users WHERE username = ?")) {
            s.setString(1, username);
            s.executeUpdate();
        }
    }

    @Test void schemaAndSeedCanBeReappliedAndSrsAdminCanLogin() throws Exception {
        runScript("database/schema.sql");
        runScript("database/seed.sql");
        var result = new AuthService(dao).login("admin", "admin@123");
        assertTrue(result.isSuccess());
        assertEquals("Admin", result.account().orElseThrow().hoTen());
        try (var c = connections.getConnection(); var s = c.prepareStatement("SELECT COUNT(*) FROM users WHERE username = ?")) {
            s.setString(1, "admin");
            try (var rows = s.executeQuery()) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
        }
    }

    @Test void jdbcLoginLockPersistsAcrossServicesAndExpiresAtDeadline() throws Exception {
        var now = Instant.parse("2026-11-05T00:00:00Z");
        var auth = new AuthService(dao, Clock.fixed(now, ZoneOffset.UTC));
        assertTrue(auth.login(username, "fixture-password").isSuccess());
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertEquals(AuthStatus.INVALID_CREDENTIALS, auth.login(username, "wrong-password").status());
        }
        assertEquals(AuthStatus.ACCOUNT_LOCKED, auth.login(username, "wrong-password").status());
        var stored = dao.inTransaction(username, (user, updater) -> user.orElseThrow());
        assertEquals(6, stored.failedAttempts());
        assertEquals(now.plusSeconds(300), stored.lockedUntil());
        assertEquals(AuthStatus.ACCOUNT_LOCKED, new AuthService(dao,
                Clock.fixed(now.plusSeconds(299), ZoneOffset.UTC)).login(username, "fixture-password").status());
        assertTrue(new AuthService(dao, Clock.fixed(now.plusSeconds(300), ZoneOffset.UTC))
                .login(username, "fixture-password").isSuccess());
    }

    @Test void transactionalFailureRollsBackFailureCounter() throws Exception {
        assertThrows(SQLException.class, () -> dao.inTransaction(username, (user, updater) -> {
            updater.update(user.orElseThrow().account().userId(), State.ACTIVE, 3, null);
            throw new SQLException("forced rollback");
        }));
        assertEquals(0, dao.inTransaction(username, (user, updater) -> user.orElseThrow().failedAttempts()).intValue());
    }

    @Test void databaseRowLockPreventsLostUpdatesAcrossConnections() throws Exception {
        var pool = Executors.newFixedThreadPool(3);
        try {
            var work = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 6; i++) {
                work.add(() -> dao.inTransaction(username, (user, updater) -> {
                    var account = user.orElseThrow();
                    updater.update(account.account().userId(), State.ACTIVE, account.failedAttempts() + 1, null);
                    return null;
                }));
            }
            for (var future : pool.invokeAll(work)) { future.get(); }
        } finally { pool.shutdownNow(); }
        assertEquals(6, dao.inTransaction(username, (user, updater) -> user.orElseThrow().failedAttempts()).intValue());
    }

    @Test void preparedLookupTreatsInjectionAsLiteralUsername() throws Exception {
        assertTrue(dao.inTransaction("' OR 1=1 --", (user, updater) -> user).isEmpty());
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
