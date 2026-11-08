package com.bluemoon.config;

import java.nio.file.Path;
import java.util.Map;

/** Test-only endpoint that cannot reach a real MySQL schema. No credentials/config file modified. */
public final class AppShellDatabaseFixture {
    private AppShellDatabaseFixture() { }
    public static DatabaseConnection unavailable() throws Exception {
        return new DatabaseConnection(DatabaseConfig.load(Path.of("absent-app-shell.properties"), Map.of(
            "BLUEMOON_DB_URL", "jdbc:mysql://127.0.0.1:1/bluemoon_shell_test",
            "BLUEMOON_DB_USERNAME", "shell-test", "BLUEMOON_DB_PASSWORD", "test-only")));
    }
}
