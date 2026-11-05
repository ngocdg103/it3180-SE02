package com.bluemoon.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/** Fresh connection per call. Callers close it with try-with-resources. */
public final class DatabaseConnection {
    private final DatabaseConfig config;

    public DatabaseConnection(DatabaseConfig config) { this.config = Objects.requireNonNull(config); }

    public Connection getConnection() throws SQLException {
        var properties = new Properties();
        properties.setProperty("user", config.username());
        properties.setProperty("password", config.password());
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "10000");
        return DriverManager.getConnection(config.url(), properties);
    }
}
