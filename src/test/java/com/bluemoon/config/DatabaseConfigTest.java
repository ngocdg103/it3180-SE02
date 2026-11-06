package com.bluemoon.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {
    @TempDir Path directory;

    @Test void environmentOverridesLocalFileAndSecretsAreRedacted() throws IOException {
        Path file = directory.resolve("db.properties");
        Files.writeString(file, "db.url=jdbc:mysql://localhost/local\ndb.username=local\ndb.password=local-test-value\n");
        var config = DatabaseConfig.load(file, Map.of("BLUEMOON_DB_PASSWORD", "environment-test-value"));
        assertEquals("local", config.username());
        assertEquals("environment-test-value", config.password());
        assertFalse(config.toString().contains(config.password()));
        assertFalse(config.toString().contains(config.url()));
    }

    @Test void environmentOnlyConfigurationNeedsNoFile() throws IOException {
        var config = DatabaseConfig.load(directory.resolve("missing"), Map.of(
                "BLUEMOON_DB_URL", "jdbc:mysql://localhost/test",
                "BLUEMOON_DB_USERNAME", "test-user", "BLUEMOON_DB_PASSWORD", "test-value"));
        assertEquals("test-user", config.username());
    }

    @Test void missingConfigurationFailsWithoutExposingValues() {
        var failure = assertThrows(IOException.class,
                () -> DatabaseConfig.load(directory.resolve("missing"), Map.of("BLUEMOON_DB_PASSWORD", "private-test-value")));
        assertFalse(failure.getMessage().contains("private-test-value"));
    }

    @Test void rejectsNonMysqlUrl() {
        assertThrows(IOException.class, () -> DatabaseConfig.load(directory.resolve("missing"), Map.of(
                "BLUEMOON_DB_URL", "jdbc:other:test", "BLUEMOON_DB_USERNAME", "test",
                "BLUEMOON_DB_PASSWORD", "test-value")));
    }
}
