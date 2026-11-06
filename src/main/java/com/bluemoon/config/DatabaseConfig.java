package com.bluemoon.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/** Environment variables override individual local properties. */
public final class DatabaseConfig {
    private final String url;
    private final String username;
    private final String password;

    private DatabaseConfig(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public static DatabaseConfig load() throws IOException {
        var environment = System.getenv();
        return load(Path.of(environment.getOrDefault("BLUEMOON_DB_CONFIG", "config/db.properties")), environment);
    }

    static DatabaseConfig load(Path path, Map<String, String> environment) throws IOException {
        var properties = new Properties();
        if (Files.exists(path)) {
            try (var input = Files.newInputStream(path)) { properties.load(input); }
        }
        String url = value(properties, environment, "db.url", "BLUEMOON_DB_URL");
        String username = value(properties, environment, "db.username", "BLUEMOON_DB_USERNAME");
        String password = value(properties, environment, "db.password", "BLUEMOON_DB_PASSWORD");
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IOException("Database configuration requires db.url, db.username and db.password");
        }
        if (!url.startsWith("jdbc:mysql:")) { throw new IOException("Database URL must use JDBC MySQL"); }
        return new DatabaseConfig(url, username, password);
    }

    private static String value(Properties properties, Map<String, String> environment,
                                String property, String variable) {
        return environment.getOrDefault(variable, properties.getProperty(property, ""));
    }

    public String url() { return url; }
    public String username() { return username; }
    public String password() { return password; }
    @Override public String toString() { return "DatabaseConfig[redacted]"; }
}
