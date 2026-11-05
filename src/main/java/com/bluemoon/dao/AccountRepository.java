package com.bluemoon.dao;

import com.bluemoon.models.Account;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** Persistence boundary. Callback runs while the user's database row is locked. */
public interface AccountRepository {
    <T> T inTransaction(String username, Work<T> work) throws SQLException;

    @FunctionalInterface
    interface Work<T> {
        T apply(Optional<StoredAccount> account, StateUpdater updater) throws SQLException;
    }

    @FunctionalInterface
    interface StateUpdater {
        void update(long userId, State state, int failedAttempts, Instant lockedUntil) throws SQLException;
    }

    enum State { ACTIVE, LOCKED }

    /** Backend-only credential data; never returned in an authentication result. */
    record StoredAccount(Account account, String passwordHash, State state,
                         int failedAttempts, Instant lockedUntil) {
        @Override public String toString() { return "StoredAccount[redacted]"; }
    }
}
