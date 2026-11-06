package com.bluemoon.services;

import com.bluemoon.dao.AccountRepository;
import com.bluemoon.dao.AccountRepository.State;
import com.bluemoon.models.Account;
import com.bluemoon.models.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private static final String PASSWORD = "admin@123"; // Public SRS test fixture, not a DB credential.
    private static final String HASH = BCrypt.hashpw(PASSWORD, BCrypt.gensalt(4));
    private final MutableClock clock = new MutableClock();
    private final MemoryAccounts accounts = new MemoryAccounts();
    private final AuthService auth = new AuthService(accounts, clock);

    @AfterEach void logout() { auth.logout(); }

    @Test void validLoginPublishesSafeGlobalSessionAndLogoutClearsIt() {
        var result = auth.login("admin", PASSWORD);
        assertTrue(result.isSuccess());
        assertEquals(Role.ADMIN, result.account().orElseThrow().role());
        assertEquals(result.account(), auth.currentUser());
        assertEquals(Role.ADMIN, SessionContext.getCurrentRole());
        assertEquals("Admin", result.account().orElseThrow().hoTen());
        auth.logout();
        assertTrue(auth.currentUser().isEmpty());
        assertThrows(IllegalStateException.class, SessionContext::getCurrentRole);
    }

    @Test void wrongPasswordAndUnknownAccountHaveSameErrorAndNoIdentity() {
        var wrong = auth.login("admin", "wrong-password");
        var absent = auth.login("unknown", "wrong-password");
        assertEquals(AuthStatus.INVALID_CREDENTIALS, wrong.status());
        assertEquals(wrong.status(), absent.status());
        assertEquals("Tài khoản hoặc mật khẩu không chính xác!", wrong.message());
        assertEquals(wrong.message(), absent.message());
        assertTrue(wrong.account().isEmpty());
        assertTrue(absent.account().isEmpty());
        assertTrue(auth.currentUser().isEmpty());
    }

    @Test void missingAndMalformedInputsNeverQueryDatabase() {
        for (String[] input : new String[][] {
                {null, PASSWORD}, {"admin", null}, {" ", PASSWORD}, {"admin", ""},
                {"abc", PASSWORD}, {"a".repeat(51), PASSWORD}, {"ad_min", PASSWORD},
                {"ádmin", PASSWORD}, {"admin", "short"}}) {
            assertEquals(AuthStatus.VALIDATION_ERROR, auth.login(input[0], input[1]).status());
        }
        assertEquals(0, accounts.calls);
        assertEquals("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!", auth.login("", "").message());
    }

    @Test void srsLocksOnSixthConsecutiveFailureAndExpiresAtExactlyFiveMinutes() {
        for (int i = 1; i <= 5; i++) {
            assertEquals(AuthStatus.INVALID_CREDENTIALS, auth.login("admin", "wrong-password").status());
            assertEquals(i, accounts.user.failedAttempts());
        }
        assertEquals(AuthStatus.ACCOUNT_LOCKED, auth.login("admin", "wrong-password").status());
        var deadline = accounts.user.lockedUntil();
        assertEquals(clock.instant().plusSeconds(300), deadline);
        assertEquals(AuthStatus.ACCOUNT_LOCKED, auth.login("admin", PASSWORD).status());
        assertEquals(6, accounts.user.failedAttempts());
        clock.now = deadline.minusMillis(1);
        assertEquals(AuthStatus.ACCOUNT_LOCKED, auth.login("admin", PASSWORD).status());
        clock.now = deadline;
        assertTrue(auth.login("admin", PASSWORD).isSuccess());
        assertEquals(0, accounts.user.failedAttempts());
        assertEquals(State.ACTIVE, accounts.user.state());
        assertNull(accounts.user.lockedUntil());
    }

    @Test void successfulLoginBreaksConsecutiveFailureStreak() {
        auth.login("admin", "wrong-password");
        assertTrue(auth.login("admin", PASSWORD).isSuccess());
        assertEquals(0, accounts.user.failedAttempts());
        assertEquals(AuthStatus.INVALID_CREDENTIALS, auth.login("admin", "wrong-password").status());
        assertEquals(1, accounts.user.failedAttempts());
        assertTrue(auth.currentUser().isEmpty());
    }

    @Test void wrongPasswordAfterLockExpiresStartsNewStreak() {
        accounts.user = new AccountRepository.StoredAccount(accounts.user.account(), HASH, State.LOCKED,
                6, clock.instant());
        assertEquals(AuthStatus.INVALID_CREDENTIALS, auth.login("admin", "wrong-password").status());
        assertEquals(1, accounts.user.failedAttempts());
        assertNull(accounts.user.lockedUntil());
        assertEquals(State.ACTIVE, accounts.user.state());
    }

    @Test void administrativeLockWithoutDeadlineDoesNotAutoUnlock() {
        accounts.user = new AccountRepository.StoredAccount(accounts.user.account(), HASH, State.LOCKED, 0, null);
        assertEquals(AuthStatus.ACCOUNT_LOCKED, auth.login("admin", PASSWORD).status());
        assertTrue(auth.currentUser().isEmpty());
    }

    @Test void databaseFailureClearsEarlierSessionAndDoesNotExposeSqlOrSecrets() {
        assertTrue(auth.login("admin", PASSWORD).isSuccess());
        accounts.fail = true;
        var failure = auth.login("admin", PASSWORD);
        assertEquals(AuthStatus.SYSTEM_ERROR, failure.status());
        assertTrue(failure.account().isEmpty());
        assertTrue(auth.currentUser().isEmpty());
        assertFalse(failure.message().contains("private-database-detail"));
    }

    @Test void commitFailureDoesNotPublishAnAuthenticatedSession() {
        var repository = new AccountRepository() {
            @Override public <T> T inTransaction(String username, Work<T> work) throws SQLException {
                work.apply(Optional.of(accounts.user), (id, state, failures, until) -> { });
                throw new SQLException("commit failed");
            }
        };
        assertEquals(AuthStatus.SYSTEM_ERROR, new AuthService(repository).login("admin", PASSWORD).status());
        assertTrue(auth.currentUser().isEmpty());
    }

    @Test void invalidStoredHashIsSystemErrorAndCredentialToStringIsRedacted() {
        for (String hash : new String[] { "malformed", "" }) {
            accounts.user = new AccountRepository.StoredAccount(accounts.user.account(), hash, State.ACTIVE, 0, null);
            assertEquals(AuthStatus.SYSTEM_ERROR, auth.login("admin", PASSWORD).status());
            assertEquals("StoredAccount[redacted]", accounts.user.toString());
        }
    }

    @Test void everyDefinedRoleCanAuthenticateWithoutChangingRole() {
        for (Role role : Role.values()) {
            accounts.user = new AccountRepository.StoredAccount(new Account(1, "admin", "Test", role), HASH, State.ACTIVE, 0, null);
            assertEquals(role, auth.login("admin", PASSWORD).account().orElseThrow().role());
        }
    }

    @Test void passwordIsNotTrimmed() {
        assertEquals(AuthStatus.INVALID_CREDENTIALS, auth.login("admin", PASSWORD + " ").status());
    }

    private static final class MemoryAccounts implements AccountRepository {
        StoredAccount user = new StoredAccount(new Account(1, "admin", "Admin", Role.ADMIN), HASH, State.ACTIVE, 0, null);
        int calls;
        boolean fail;
        @Override public <T> T inTransaction(String username, Work<T> work) throws SQLException {
            calls++;
            if (fail) { throw new SQLException("private-database-detail"); }
            return work.apply("admin".equals(username) ? Optional.of(user) : Optional.empty(),
                    (id, state, failures, until) -> user = new StoredAccount(user.account(), user.passwordHash(), state, failures, until));
        }
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-05T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
