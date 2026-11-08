package com.bluemoon.controllers;

import com.bluemoon.config.DatabaseConfig;
import com.bluemoon.config.DatabaseConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Same Dashboard/menu/factory tests on real MySQL; no inserted rows. */
@EnabledIfEnvironmentVariable(named = "BLUEMOON_MYSQL_TEST", matches = "1")
class ProductionNavigationMySqlIT extends ProductionNavigationTest {
    private static DatabaseConnection database;
    private static final List<String> created = new ArrayList<>();
    @BeforeAll static void schema() throws Exception {
        database = new DatabaseConnection(DatabaseConfig.load());
        try (var connection = database.getConnection()) {
            assertTrue(connection.getCatalog().endsWith("_test"), "Disposable *_test schema required before DDL");
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
            // Only own billing tables absent on entry; never tear down another fixture's tables.
            for (String table : List.of("phieu_thu", "apartment_credit", "invoice_lines", "invoices", "billing_periods")) {
                try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, table, null)) {
                    if (!tables.next()) created.add(table);
                }
            }
            for (String file : List.of("database/schema.sql", "database/migrations/us001_household_apartment.sql",
                    "database/migrations/us002_residents.sql", "database/migrations/us005_fee_types.sql",
                    "database/migrations/us006_fee_rates.sql", "database/migrations/us007_billing.sql", "database/migrations/us008_receipts.sql")) {
                String sql = Files.readString(Path.of(file)).replaceAll("(?m)^\\s*--.*$", "");
                for (String statement : sql.split(";")) {
                    if (!statement.isBlank()) try (var command = connection.createStatement()) { command.execute(statement); }
                }
            }
        }
    }
    @AfterAll static void releaseOwnedTables() throws Exception {
        if (database == null) return;
        try (var connection = database.getConnection()) {
            assertTrue(connection.getCatalog().endsWith("_test"));
            for (String table : created) {
                try (var command = connection.createStatement()) { command.execute("DROP TABLE IF EXISTS " + table); }
            }
        }
        created.clear();
    }
    @Override protected DatabaseConnection connections() { return database; }
    @Override protected void verifyLoaded(javafx.scene.Scene scene, String id) {
        var message=((javafx.scene.control.Label)scene.lookup("#messageLabel")).getText().trim();
        if(id.equals("receipts") && message.equals("Không còn hóa đơn cần thanh toán.")) {
            assertTrue(((javafx.scene.control.ComboBox<?>)scene.lookup("#invoiceBox")).getItems().isEmpty());
            return;
        }
        assertTrue(message.isBlank() || id.equals("debt") && message.equals("Không có dữ liệu phù hợp."),
                "Initial load through real JDBC must succeed: module="+id+", banner="+message);
    }
    @Test void actualApplicationLogsInThroughMySqlVisitsAllModulesAndLogsOut() throws Exception {
        String user="shellit"+Long.toUnsignedString(System.nanoTime());
        try(var connection=database.getConnection(); var command=connection.prepareStatement(
                "INSERT INTO users(username,password_hash,ho_ten,role) VALUES(?,?,?,'ACCOUNTANT')")) {
            assertTrue(connection.getCatalog().endsWith("_test"));
            command.setString(1,user); command.setString(2,org.mindrot.jbcrypt.BCrypt.hashpw("shell-fixture-password",org.mindrot.jbcrypt.BCrypt.gensalt(4)));
            command.setString(3,"Shell integration fixture"); command.executeUpdate();
        }
        var window=new java.util.concurrent.atomic.AtomicReference<javafx.stage.Stage>();
        try {
            ProductionNavigationTest.fx(() -> {
                var stage=new javafx.stage.Stage(); window.set(stage); new com.bluemoon.BlueMoonApplication().start(stage);
            });
            await(() -> window.get().getScene().lookup("#loginButton")!=null);
            ProductionNavigationTest.fx(() -> {
                var scene=window.get().getScene();
                ((javafx.scene.control.TextField)scene.lookup("#username")).setText(user);
                ((javafx.scene.control.PasswordField)scene.lookup("#password")).setText("shell-fixture-password");
                ((javafx.scene.control.Button)scene.lookup("#loginButton")).fire();
            });
            await(() -> window.get().getScene().lookup("#greeting")!=null);
            var scene=window.get().getScene();
            for(String id:List.of("feeRates","billing","notices","receipts","debt")) {
                ProductionNavigationTest.fx(() -> {
                    assertSame(scene,window.get().getScene());
                    ((javafx.scene.control.Button)scene.lookup("#"+id+"Button")).fire();
                });
                await(() -> !((javafx.scene.control.Button)scene.lookup("#homeButton")).isDisabled());
                ProductionNavigationTest.fx(() -> {
                    assertSame(scene,window.get().getScene());
                    assertTrue(((javafx.scene.control.Label)scene.lookup("#shellStatus")).getText().isBlank());
                    verifyLoaded(scene,id);
                    ((javafx.scene.control.Button)scene.lookup("#homeButton")).fire();
                });
            }
            ProductionNavigationTest.fx(() -> {
                ((javafx.scene.control.Button)scene.lookup("#logoutButton")).fire();
                assertTrue(com.bluemoon.services.SessionContext.getCurrentUser().isEmpty());
                assertNotNull(scene.lookup("#loginButton")); assertSame(scene,window.get().getScene());
            });
        } finally {
            ProductionNavigationTest.fx(() -> { if(window.get()!=null)window.get().hide(); });
            try(var connection=database.getConnection();var command=connection.prepareStatement("DELETE FROM users WHERE username=?")) {
                command.setString(1,user); command.executeUpdate();
            }
        }
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<end) {
            boolean[] ready={false}; ProductionNavigationTest.fx(() -> ready[0]=condition.getAsBoolean());
            if(ready[0])return; Thread.sleep(20);
        }
        fail("Actual BlueMoonApplication did not complete navigation");
    }
}
