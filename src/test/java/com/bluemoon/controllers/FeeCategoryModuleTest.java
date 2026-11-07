package com.bluemoon.controllers;

import com.bluemoon.dao.FeeTypeRepository;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real FXML/controller + real service/RBAC; controlled persistence, no MySQL claim. */
class FeeCategoryModuleTest {
    private FeeCategoryModule module;
    private final Repository repository = new Repository();

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }
    @AfterEach void cleanup() throws Exception {
        repository.gate.countDown();
        if (module != null) { fx(() -> { module.close(); return null; }); }
        FeeCategorySessionFixture.use(null);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var done = new CompletableFuture<T>();
        Platform.runLater(() -> { try { done.complete(action.call()); } catch (Throwable t) { done.completeExceptionally(t); } });
        return done.get(10, TimeUnit.SECONDS);
    }
    private void until(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) { if (fx(condition::getAsBoolean)) { return; } Thread.sleep(10); }
        fail("UI result did not arrive");
    }
    private void open(Role role) throws Exception {
        FeeCategorySessionFixture.use(role);
        module = fx(() -> new FeeCategoryModule(new FeeTypeServiceImpl(repository)));
    }
    @SuppressWarnings("unchecked") private <T> T node(String id) { return (T) module.view().lookup("#" + id); }
    private String message() { return ((Label) node("messageLabel")).getText(); }
    private void fillAndSave(String amount) throws Exception {
        fx(() -> {
            ((TextField) node("codeField")).setText("PHI");
            ((TextField) node("nameField")).setText("Phí quản lý");
            ((ComboBox<FeeKind>) this.<ComboBox<FeeKind>>node("kindBox")).setValue(FeeKind.BAT_BUOC);
            this.<ComboBox<ChargeMethod>>node("methodBox").setValue(ChargeMethod.THEO_CAN_HO);
            this.<TextField>node("amountField").setText(amount);
            this.<Button>node("saveButton").fire();
            return null;
        });
    }

    @Test void realServiceSuccessDuplicateAndPrecisionValidationReachUi() throws Exception {
        open(Role.ACCOUNTANT);
        until(() -> repository.lists > 0);
        fillAndSave("1.001"); // accepted by UI convenience checks, rejected by the real service
        until(() -> !this.<Label>node("amountError").getText().isEmpty());
        assertEquals(0, repository.inserts);
        fillAndSave("1.25");
        until(() -> message().contains("thành công"));
        assertEquals(1, repository.inserts);
        fillAndSave("1.25");
        until(() -> !this.<Label>node("codeError").getText().isEmpty());
        assertEquals("PHI", fx(() -> this.<TextField>node("codeField").getText()));
        assertEquals(1, repository.rows.size());
        assertFalse(repository.fxAccess);
    }

    @Test void realDatabaseFailureDisplaysGenericErrorAndRetainsInput() throws Exception {
        open(Role.ADMIN);
        until(() -> repository.lists > 0);
        repository.fail = true;
        fillAndSave("0");
        until(() -> FeeCategoryController.MSG_SYSTEM.equals(message()));
        assertEquals("PHI", fx(() -> this.<TextField>node("codeField").getText()));
        assertFalse(fx(this::message).contains("secret"));
    }

    @Test void noSessionAndResidentAreDeniedByActualService() throws Exception {
        for (Role role : new Role[]{null, Role.RESIDENT}) {
            open(role);
            until(() -> FeeCategoryController.MSG_DENIED.equals(message()));
            assertTrue(fx(() -> this.<Button>node("saveButton").isDisabled()));
            fx(() -> { module.close(); return null; });
        }
        assertEquals(0, repository.lists);
    }

    @Test void closingDuringWriteAllowsCompletionButDropsCallbackAndRefresh() throws Exception {
        open(Role.ADMIN);
        until(() -> repository.lists > 0);
        repository.block = true;
        fillAndSave("0");
        assertTrue(repository.entered.await(5, TimeUnit.SECONDS));
        assertEquals(42, fx(() -> 42));
        int lists = repository.lists;
        fx(() -> { module.close(); module.close(); return null; });
        repository.gate.countDown();
        assertTrue(repository.finished.await(5, TimeUnit.SECONDS));
        // Drain worker and FX completion callbacks without timing assumptions.
        var field = FeeCategoryModule.class.getDeclaredField("controller"); field.setAccessible(true);
        var controller = (FeeCategoryController) field.get(module);
        var executorField = FeeCategoryController.class.getDeclaredField("executor"); executorField.setAccessible(true);
        var executor = (ExecutorService) executorField.get(controller);
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        fx(() -> null);
        assertTrue(executor.isShutdown());
        assertEquals(1, repository.rows.size());
        assertEquals(lists, repository.lists);
        assertEquals("", fx(this::message));
        assertEquals("PHI", fx(() -> this.<TextField>node("codeField").getText()));
        assertTrue(fx(controller::isDisposed));
        assertFalse(repository.fxAccess);
    }

    @Test void lifecycleRejectsCallsOffFxThread() throws Exception {
        assertThrows(IllegalStateException.class, () -> FeeCategoryModule.open(null));
        open(Role.ADMIN);
        assertThrows(IllegalStateException.class, module::close);
    }

    private static final class Repository implements FeeTypeRepository {
        final Map<String, FeeType> rows = new ConcurrentHashMap<>();
        final CountDownLatch entered = new CountDownLatch(1), gate = new CountDownLatch(1), finished = new CountDownLatch(1);
        volatile int lists, inserts;
        volatile boolean fail, block, fxAccess;
        void check() throws SQLException {
            fxAccess |= Platform.isFxApplicationThread();
            if (fail) { throw new SQLException("secret SQL connection details"); }
        }
        public List<FeeType> findAll(FeeStatus filter) throws SQLException { check(); lists++; return List.copyOf(rows.values()); }
        public Optional<FeeType> findByCode(String code) throws SQLException { check(); return Optional.ofNullable(rows.get(code)); }
        public void insert(FeeType fee) throws SQLException {
            check(); entered.countDown();
            try {
                if (block && !gate.await(5, TimeUnit.SECONDS)) { throw new SQLException("test gate timed out"); }
                if (rows.putIfAbsent(fee.maKhoanThu(), fee) != null) { throw new SQLException("duplicate", "23000", 1062); }
                inserts++;
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new SQLException(interrupted); }
            finally { finished.countDown(); }
        }
        public Optional<FeeType> update(FeeType fee) { throw new AssertionError("UI has no update action yet"); }
        public Optional<FeeType> deactivate(String code) { throw new AssertionError("UI has no deactivate action yet"); }
    }
}
