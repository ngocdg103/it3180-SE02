package com.bluemoon.controllers;

import com.bluemoon.config.AppShellDatabaseFixture;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import com.bluemoon.views.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual production factories, FXML and RBAC; unavailable JDBC exercises safe failure/cleanup. */
class ProductionNavigationTest {
    private DashboardController controller;
    private Scene scene;
    private javafx.stage.Stage stage;
    private final AuthenticationService auth = new AuthenticationService() {
        public AuthResult login(String u, String p) { throw new UnsupportedOperationException(); }
        public Optional<Account> currentUser() { return SessionContext.getCurrentUser(); }
        public void logout() { FeeCategorySessionFixture.use(null); }
    };
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }
    static void fx(Runnable action) throws Exception {
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> { try { action.run(); done.complete(null); }
            catch (Throwable t) { done.completeExceptionally(t); } });
        done.get(15, TimeUnit.SECONDS);
    }
    private Button button(String id) { return (Button)scene.lookup("#" + id); }
    protected com.bluemoon.config.DatabaseConnection connections() throws Exception {
        return AppShellDatabaseFixture.unavailable();
    }
    protected void verifyLoaded(Scene scene, String id) { }
    private void open(Role role) throws Exception {
        FeeCategorySessionFixture.use(role);
        var connections = connections();
        fx(() -> {
            controller = new DashboardController(auth, auth.currentUser().orElseThrow(), () -> {},
                    new RoleBasedAuthorizationService(null), ProductionModules.create(connections));
            var loader = new FXMLLoader(getClass().getResource("/com/bluemoon/views/DashboardView.fxml"));
            loader.setControllerFactory(type -> controller);
            try { scene = new Scene((Parent)loader.load()); }
            catch (Exception e) { throw new RuntimeException(e); }
            stage = new javafx.stage.Stage(); stage.setScene(scene); stage.show();
        });
    }
    private void settled() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            boolean[] ready = {false}; fx(() -> ready[0] = !controller.isBusy());
            if (ready[0]) return;
            Thread.sleep(20);
        }
        fail("Production module did not release shell busy state");
    }
    @AfterEach void close() throws Exception {
        fx(() -> { if (controller != null) controller.close(); if (stage != null) stage.hide(); });
        FeeCategorySessionFixture.use(null);
    }
    @Test void registrationsUseRequiredPermissionsAndUniqueIds() throws Exception {
        var modules = ProductionModules.create(connections());
        assertEquals(modules.size(), modules.stream().map(ModuleRegistration::id).distinct().count());
        for (String id : List.of("billing", "notices", "receipts", "debt", "feeRates")) {
            assertEquals(id.equals("feeRates") ? Permission.FEE_RATE_VIEW : id.equals("receipts") ? Permission.PAYMENT_HISTORY_VIEW_ALL : Permission.DEBT_REPORT_VIEW, modules.stream().filter(m -> m.id().equals(id))
                    .findFirst().orElseThrow().permission());
        }
        assertEquals(Permission.FEE_RATE_VIEW,modules.stream().filter(m -> m.id().equals("feeRates")).findFirst().orElseThrow().permission());
    }
    @Test void adminCanOpenActualModulesAndRecoverFromUnavailableDatabase() throws Exception { staff(Role.ADMIN); }
    @Test void accountantCanOpenActualModulesAndRecoverFromUnavailableDatabase() throws Exception { staff(Role.ACCOUNTANT); }
    private static String selector(String id) { return switch(id) { case "billing" -> "#chargeTable"; case "notices" -> "#previewArea"; case "receipts" -> "#receiptTable"; case "debt" -> "#debtTable"; case "feeRates" -> "#historyTable"; default -> throw new IllegalArgumentException(id); }; }
    private void staff(Role role) throws Exception {
        open(role);
        for (String id : List.of("billing", "notices", "receipts", "debt", "feeRates")) {
            fx(() -> {
                assertTrue(button(id + "Button").isVisible());
                button(id + "Button").fire();
                scene.getRoot().applyCss(); scene.getRoot().layout();
                assertNotNull(scene.lookup(selector(id)));
                var root = scene.lookup(selector(id));
                controller.openModule(id); // Repeated open must retain the current module.
                assertSame(root, scene.lookup(selector(id)));
                if (controller.isBusy()) {
                    assertTrue(button("logoutButton").isDisabled());
                    controller.showHome();
                    assertNotNull(scene.lookup(selector(id)));
                }
            });
            settled();
            fx(() -> { verifyLoaded(scene, id); controller.showHome(); assertNull(scene.lookup(selector(id))); });
        }
        fx(() -> { button("logoutButton").fire(); assertTrue(SessionContext.getCurrentUser().isEmpty()); });
    }
    @Test void residentCannotOpenEitherModuleEvenThroughDirectControllerCall() throws Exception {
        open(Role.RESIDENT);
        fx(() -> {
            for (String id : List.of("billing", "notices", "receipts", "debt", "feeRates")) {
                assertFalse(button(id + "Button").isVisible());
                assertFalse(button(id + "Button").isManaged());
                controller.openModule(id);
                assertNull(scene.lookup(selector(id)));
            }
        });
    }
}
