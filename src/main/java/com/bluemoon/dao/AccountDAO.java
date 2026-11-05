package com.bluemoon.dao;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.Account;
import com.bluemoon.models.Role;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;

/** JDBC implementation; each login state transition is one InnoDB transaction. */
public final class AccountDAO implements AccountRepository {
    private final DatabaseConnection connections;

    public AccountDAO(DatabaseConnection connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public <T> T inTransaction(String username, Work<T> work) throws SQLException {
        try (var connection = connections.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var account = findForUpdate(connection, username);
                T result = work.apply(account, (id, state, failures, until) -> {
                    try (var statement = connection.prepareStatement(
                            "UPDATE users SET trang_thai = ?, so_lan_sai = ?, locked_until = ? WHERE id = ?")) {
                        statement.setString(1, state.name());
                        statement.setInt(2, failures);
                        statement.setTimestamp(3, until == null ? null : Timestamp.from(until), utc());
                        statement.setLong(4, id);
                        if (statement.executeUpdate() != 1) {
                            throw new SQLException("Account state update did not affect one row");
                        }
                    }
                });
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); }
                catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
        }
    }

    private Optional<StoredAccount> findForUpdate(Connection connection, String username) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT id, username, password_hash, ho_ten, role, trang_thai, so_lan_sai, locked_until "
                        + "FROM users WHERE username = ? FOR UPDATE")) {
            statement.setString(1, username);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) { return Optional.empty(); }
                var account = new Account(rows.getLong("id"), rows.getString("username"),
                        rows.getString("ho_ten"), Role.valueOf(rows.getString("role")));
                var until = rows.getTimestamp("locked_until", utc());
                return Optional.of(new StoredAccount(account, rows.getString("password_hash"),
                        State.valueOf(rows.getString("trang_thai")), rows.getInt("so_lan_sai"),
                        until == null ? null : until.toInstant()));
            }
        }
    }

    private static Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }
}
