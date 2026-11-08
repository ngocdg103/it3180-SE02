package com.bluemoon.controllers;

import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real FXML, controller, service and RBAC; test-only persistence. No DB or App Shell E2E claim. */
class FeeRateControllerTest {
    private final Repository rates = new Repository();
    private final Types types = new Types();
    private FeeRateController controller;
    private Parent view;
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }
    @AfterEach void cleanup() throws Exception {
        types.release.countDown();
        rates.release.countDown();
        if (controller != null) {
            fx(() -> { controller.dispose(); return null; });
            assertTrue(pool().awaitTermination(10, TimeUnit.SECONDS)); fx(() -> null);
        }
        FeeCategorySessionFixture.use(null);
    }
    private ExecutorService pool() throws Exception {
        var field = FeeRateController.class.getDeclaredField("worker"); field.setAccessible(true);
        return (ExecutorService) field.get(controller);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
        return result.get(10, TimeUnit.SECONDS);
    }
    private void until(BooleanSupplier condition) throws Exception {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < limit) { if (fx(condition::getAsBoolean)) { return; } Thread.sleep(10); }
        fail("UI result did not arrive");
    }
    @SuppressWarnings("unchecked") private <T> T node(String id) { return (T) view.lookup("#" + id); }
    private String message() { return this.<Label>node("messageLabel").getText(); }
    private void open(Role role) throws Exception { open(role, new RoleBasedAuthorizationService(null)); }
    private void open(Role role, AuthorizationService authorization) throws Exception {
        FeeCategorySessionFixture.use(role);
        view = fx(() -> {
            controller = new FeeRateController(new FeeRateServiceImpl(rates, types, authorization), authorization);
            Parent root = FeeRateController.load(controller);
            new javafx.scene.Scene(root, 1050, 660);
            root.applyCss(); root.layout();
            return root;
        });
    }
    private void ready() throws Exception {
        until(() -> !this.<ProgressIndicator>node("loadingIndicator").isVisible()
                && !this.<TableView<FeeRate>>node("historyTable").getItems().isEmpty());
    }
    private void save(String price) throws Exception {
        fx(() -> {
            this.<TextField>node("priceField").setText(price);
            this.<DatePicker>node("effectiveDate").setValue(LocalDate.of(2027, 1, 1));
            this.<TextArea>node("noteField").setText("Quyết định mới");
            this.<Button>node("saveButton").fire(); return null;
        });
    }
    @Test void initialHistoryAndEffectivePriceUseServiceOffFxThreadAndStoppedTypeIsReadOnly() throws Exception {
        open(Role.ADMIN); ready();
        assertEquals(new BigDecimal("7000.00"), fx(() -> this.<TableView<FeeRate>>node("historyTable").getItems().get(0).donGia()));
        assertTrue(fx(() -> this.<Label>node("effectiveLabel").getText().contains("2026-01-01")));
        assertEquals(2, fx(() -> this.<ComboBox<FeeType>>node("feeBox").getItems().size()));
        assertFalse(fx(() -> this.<DatePicker>node("effectiveDate").isEditable()));
        fx(() -> { this.<ComboBox<FeeType>>node("feeBox").setValue(Types.OLD); return null; });
        until(() -> !this.<ProgressIndicator>node("loadingIndicator").isVisible());
        assertTrue(fx(() -> this.<Button>node("saveButton").isDisabled()));
        assertTrue(fx(() -> this.<Label>node("statusLabel").getText().contains("ngừng áp dụng")));
        assertFalse(rates.fxAccess || types.fxAccess);
    }
    @Test void successAppendsHistoryAndDuplicateRetainsInputAndShowsDateError() throws Exception {
        open(Role.ACCOUNTANT); ready(); save("8000.50");
        until(() -> message().contains("thành công") && this.<TableView<FeeRate>>node("historyTable").getItems().size() == 2);
        assertEquals(2, rates.rows.size()); assertEquals(new BigDecimal("7000.00"), rates.rows.get(0).donGia());
        assertEquals("", fx(() -> this.<TextField>node("priceField").getText()));
        save("9000");
        until(() -> !this.<Label>node("dateError").getText().isEmpty());
        assertEquals("9000", fx(() -> this.<TextField>node("priceField").getText()));
        assertEquals(2, rates.rows.size()); assertEquals(2, rates.inserts);
        assertFalse(rates.fxAccess);
    }
    @Test void invalidPriceDateAndLongNoteReachFieldErrorsWithoutInsert() throws Exception {
        open(Role.ADMIN); ready();
        fx(() -> {
            this.<TextField>node("priceField").setText("1.001");
            this.<DatePicker>node("effectiveDate").setValue(null);
            this.<TextArea>node("noteField").setText("😀".repeat(256));
            this.<Button>node("saveButton").fire(); return null;
        });
        until(() -> !this.<Label>node("priceError").getText().isEmpty());
        assertFalse(fx(() -> this.<Label>node("dateError").getText().isEmpty()));
        assertFalse(fx(() -> this.<Label>node("noteError").getText().isEmpty()));
        assertEquals(0, rates.inserts);
    }
    @ParameterizedTest @CsvSource({"45000,0,codeError", "02000,0,codeError", "23000,1062,dateError", "40001,1213,messageLabel", "HY000,1205,messageLabel", "08006,0,messageLabel"})
    void errorsAreMappedAndNeverExposeSqlOrLoseInput(String state, int code, String label) throws Exception {
        open(Role.ADMIN); ready(); rates.failure = new SQLException("secret database password", state, code);
        save("8000");
        until(() -> !this.<Label>node(label).getText().isEmpty());
        assertFalse(fx(this::message).contains("secret"));
        assertEquals("8000", fx(() -> this.<TextField>node("priceField").getText()));
        assertEquals(1, rates.inserts);
    }
    @Test void residentAndNoSessionHideCreateAndNeverCallRepositories() throws Exception {
        open(Role.RESIDENT); until(() -> FeeRateController.DENIED.equals(message()));
        assertFalse(fx(() -> this.<Button>node("saveButton").isVisible()));
        assertEquals(0, rates.calls + types.calls);
        fx(() -> { controller.dispose(); return null; }); assertTrue(pool().awaitTermination(5, TimeUnit.SECONDS));
        open(null); until(() -> FeeRateController.DENIED.equals(message()));
        assertEquals(0, rates.calls + types.calls);
    }
    @Test void viewOnlyPermissionHidesCreateWhileHistoryRemainsAvailable() throws Exception {
        AuthorizationService viewOnly = new AuthorizationService() {
            public boolean canAccess(Permission p) { return p == Permission.FEE_RATE_VIEW; }
            public void requirePermission(Permission p) { if (!canAccess(p)) { throw new AccessDeniedException("denied"); } }
            public boolean canAccessApartment(String a, Permission p) { return false; }
            public void requireApartmentPermission(String a, Permission p) { throw new AccessDeniedException("denied"); }
        };
        open(Role.ADMIN, viewOnly); ready();
        assertFalse(fx(() -> this.<Button>node("saveButton").isVisible()));
        assertTrue(fx(() -> this.<TextField>node("priceField").isDisabled())); assertEquals(0, rates.inserts);
    }
    @Test void slowWriteDoesNotBlockFxOrDoubleSubmitAndCloseDropsCallback() throws Exception {
        open(Role.ADMIN); ready(); rates.blockWrite = true; save("8000");
        assertTrue(rates.entered.await(5, TimeUnit.SECONDS));
        assertTrue(fx(() -> controller.busyProperty().get()));
        assertEquals(42, fx(() -> 42));
        assertTrue(fx(() -> this.<ComboBox<FeeType>>node("feeBox").isDisabled()));
        fx(() -> { this.<Button>node("saveButton").fire(); controller.dispose(); controller.dispose(); return null; });
        assertTrue(fx(() -> controller.busyProperty().get()), "closing must not report an active write as idle");
        rates.release.countDown(); assertTrue(pool().awaitTermination(5, TimeUnit.SECONDS)); fx(() -> null);
        assertEquals(1, rates.inserts); assertEquals(2, rates.rows.size());
        assertEquals("", fx(this::message));
        assertEquals("8000", fx(() -> this.<TextField>node("priceField").getText()));
        assertFalse(fx(() -> controller.busyProperty().get()));
    }
    @Test void selectionChangeDiscardsStaleHistoryAndLogoutDiscardsPendingData() throws Exception {
        rates.blockHistory = true; open(Role.ADMIN);
        assertTrue(rates.entered.await(5, TimeUnit.SECONDS));
        fx(() -> { this.<ComboBox<FeeType>>node("feeBox").setValue(Types.OLD); return null; });
        rates.release.countDown();
        until(() -> !this.<ProgressIndicator>node("loadingIndicator").isVisible());
        assertTrue(fx(() -> this.<TableView<FeeRate>>node("historyTable").getItems().isEmpty()));
        FeeCategorySessionFixture.use(null);
        fx(() -> { this.<Button>node("refreshButton").fire(); return null; });
        until(() -> FeeRateController.DENIED.equals(message()));
        assertTrue(fx(() -> this.<ComboBox<FeeType>>node("feeBox").getItems().isEmpty()));
    }
    @Test void loadFailureIsGenericAndRefreshRecovers() throws Exception {
        types.failure = true; open(Role.ADMIN);
        until(() -> message().contains("Lỗi hệ thống"));
        assertFalse(fx(this::message).contains("secret"));
        types.failure = false;
        fx(() -> { this.<Button>node("refreshButton").fire(); return null; }); ready();
    }
    @Test void logoutDuringPendingReadDoesNotPublishHistory() throws Exception {
        rates.blockHistory = true; open(Role.ADMIN);
        assertTrue(rates.entered.await(5, TimeUnit.SECONDS));
        FeeCategorySessionFixture.use(null); rates.release.countDown();
        until(() -> FeeRateController.DENIED.equals(message()));
        assertTrue(fx(() -> this.<TableView<FeeRate>>node("historyTable").getItems().isEmpty()));
        assertFalse(fx(() -> this.<Button>node("saveButton").isVisible()));
    }
    @Test void lifecycleRejectsOffFxThread() throws Exception {
        assertThrows(IllegalStateException.class, () -> FeeRateModule.open(null));
        open(Role.ADMIN); ready(); assertThrows(IllegalStateException.class, controller::dispose);
    }
    @Test void moduleEmbedsInSceneAndClosesWithoutOwningStageOrScene() throws Exception {
        FeeCategorySessionFixture.use(Role.ADMIN);
        var authorization = new RoleBasedAuthorizationService(null);
        var module = fx(() -> new FeeRateModule(new FeeRateServiceImpl(rates, types, authorization), authorization));
        com.bluemoon.views.DashboardModule contract = module;
        try {
            view = module.view();
            fx(() -> {
                new javafx.scene.Scene(view, 1050, 660);
                view.applyCss(); view.layout(); return null;
            });
            ready();
            until(() -> !contract.busyProperty().get());
            assertSame(view, contract.view());
            assertTrue(fx(() -> this.<TableView<FeeRate>>node("historyTable").getHeight() > 100));
            assertThrows(IllegalStateException.class, module::close);
        } finally { fx(() -> { module.close(); module.close(); return null; }); }
    }
    @Test void busyStaysTrueAcrossChainedLoadsAndQueuedRefreshes() throws Exception {
        types.block = true; rates.blockHistory = true;
        open(Role.ADMIN);
        assertTrue(types.entered.await(5, TimeUnit.SECONDS));
        assertTrue(fx(() -> controller.busyProperty().get()));
        var changes = new ArrayList<Boolean>();
        var fxOnly = new java.util.concurrent.atomic.AtomicBoolean(true);
        fx(() -> {
            controller.busyProperty().addListener((o, old, value) -> {
                fxOnly.compareAndSet(true, Platform.isFxApplicationThread()); changes.add(value);
            }); return null;
        });
        types.release.countDown();
        assertTrue(rates.entered.await(5, TimeUnit.SECONDS));
        assertTrue(fx(() -> controller.busyProperty().get()));
        assertTrue(fx(changes::isEmpty), "no idle gap between choices and history");
        fx(() -> { this.<DatePicker>node("lookupDate").setValue(LocalDate.of(2026, 2, 1)); return null; });
        assertTrue(fx(() -> controller.busyProperty().get()));
        rates.release.countDown();
        until(() -> !controller.busyProperty().get());
        assertEquals(List.of(false), fx(() -> List.copyOf(changes)));
        assertTrue(fxOnly.get());
    }
    @Test void busySettlesOnServiceFailureAndRejectedSubmission() throws Exception {
        types.failure = true; open(Role.ADMIN);
        until(() -> message().contains("Lỗi hệ thống") && !controller.busyProperty().get());
        types.failure = false;
        fx(() -> { this.<Button>node("refreshButton").fire(); return null; }); ready();
        until(() -> !controller.busyProperty().get());
        pool().shutdown(); assertTrue(pool().awaitTermination(5, TimeUnit.SECONDS));
        fx(() -> { this.<Button>node("refreshButton").fire(); return null; });
        assertFalse(fx(() -> controller.busyProperty().get()));
        assertEquals(FeeRateController.SYSTEM, fx(this::message));
    }
    @Test void deniedSessionNeverStartsTasksAndModuleCloseDuringLoadDropsUiCallback() throws Exception {
        open(Role.RESIDENT);
        until(() -> FeeRateController.DENIED.equals(message()));
        assertFalse(fx(() -> controller.busyProperty().get()));
        assertEquals(0, types.calls + rates.calls);
        fx(() -> { controller.dispose(); return null; }); assertTrue(pool().awaitTermination(5, TimeUnit.SECONDS));
        controller = null;
        rates.blockHistory = true; FeeCategorySessionFixture.use(Role.ACCOUNTANT);
        var authorization = new RoleBasedAuthorizationService(null);
        com.bluemoon.views.DashboardModule module = fx(() -> new FeeRateModule(new FeeRateServiceImpl(rates, types, authorization), authorization));
        try {
            view = module.view();
            fx(() -> { new javafx.scene.Scene(view, 1050, 660); view.applyCss(); view.layout(); return null; });
            assertTrue(rates.entered.await(5, TimeUnit.SECONDS));
            assertTrue(fx(() -> module.busyProperty().get()));
            String before = fx(this::message);
            fx(() -> { module.close(); module.close(); return null; });
            assertTrue(fx(() -> module.busyProperty().get()));
            rates.release.countDown(); until(() -> !module.busyProperty().get());
            assertTrue(fx(() -> this.<TableView<FeeRate>>node("historyTable").getItems().isEmpty()));
            assertEquals(before, fx(this::message));
        } finally { rates.release.countDown(); fx(() -> { module.close(); return null; }); }
    }
    private static final class Repository implements FeeRateRepository {
        final List<FeeRate> rows = new CopyOnWriteArrayList<>(List.of(new FeeRate(1L, "PHI_QL", new BigDecimal("7000.00"), LocalDate.of(2026, 1, 1), "Giá cũ")));
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile int calls, inserts; volatile boolean fxAccess, blockWrite, blockHistory;
        volatile SQLException failure;
        void check() { calls++; fxAccess |= Platform.isFxApplicationThread(); }
        void gate() throws SQLException {
            entered.countDown();
            try { if (!release.await(8, TimeUnit.SECONDS)) { throw new SQLException("Gate timed out"); } }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new SQLException(failure); }
        }
        public FeeRate insert(FeeRate rate) throws SQLException {
            check(); inserts++; if (blockWrite) { gate(); }
            if (failure != null) { throw failure; }
            if (rows.stream().anyMatch(row -> row.maLoaiThu().equals(rate.maLoaiThu()) && row.ngayApDung().equals(rate.ngayApDung()))) {
                throw new SQLException("duplicate", "23000", 1062);
            }
            var saved = new FeeRate((long) rows.size() + 1, rate.maLoaiThu(), rate.donGia(), rate.ngayApDung(), rate.ghiChu());
            rows.add(saved); return saved;
        }
        public List<FeeRate> findHistory(String code) throws SQLException {
            check(); if (blockHistory && code.equals("PHI_QL")) { gate(); }
            if (failure != null) { throw failure; }
            return rows.stream().filter(row -> row.maLoaiThu().equals(code)).sorted(Comparator.comparing(FeeRate::ngayApDung).reversed()).toList();
        }
        public Optional<FeeRate> findEffectiveRate(String code, LocalDate date) throws SQLException {
            check(); if (failure != null) { throw failure; }
            return rows.stream().filter(row -> row.maLoaiThu().equals(code) && !row.ngayApDung().isAfter(date)).max(Comparator.comparing(FeeRate::ngayApDung));
        }
    }
    private static final class Types implements FeeTypeRepository {
        static final FeeType ACTIVE = type("PHI_QL", FeeKind.BAT_BUOC, FeeStatus.DANG_AP_DUNG);
        static final FeeType OLD = type("OLD", FeeKind.BAT_BUOC, FeeStatus.NGUNG_AP_DUNG);
        volatile int calls; volatile boolean fxAccess, failure;
        volatile boolean block;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        static FeeType type(String code, FeeKind kind, FeeStatus state) { return new FeeType(code, code, kind, ChargeMethod.THEO_DIEN_TICH, BigDecimal.ZERO, state); }
        public List<FeeType> findAll(FeeStatus filter) throws SQLException {
            if (block) {
                entered.countDown();
                try { if (!release.await(8, TimeUnit.SECONDS)) { throw new SQLException("gate timeout"); } }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new SQLException(failure); }
            }
            calls++; fxAccess |= Platform.isFxApplicationThread(); if (failure) { throw new SQLException("secret"); }
            return List.of(ACTIVE, OLD, type("QUY", FeeKind.DONG_GOP, FeeStatus.DANG_AP_DUNG));
        }
        public Optional<FeeType> findByCode(String code) { throw new AssertionError(); }
        public void insert(FeeType fee) { throw new AssertionError(); }
        public Optional<FeeType> update(FeeType fee) { throw new AssertionError(); }
        public Optional<FeeType> deactivate(String code) { throw new AssertionError(); }
    }
}
