package com.bluemoon.controllers;

import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptControllerTest {
    private final Repository repository=new Repository();
    private ReceiptController controller;
    private Parent root;
    @BeforeAll static void toolkit() throws Exception {
        var ready=new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); } catch(IllegalStateException started) { ready.complete(null); }
        ready.get(10,TimeUnit.SECONDS);
    }
    @AfterEach void cleanup() throws Exception {
        repository.release.countDown();
        if(controller!=null) { fx(() -> { controller.dispose(); return null; }); assertTrue(pool().awaitTermination(5,TimeUnit.SECONDS)); fx(() -> null); }
        FeeCategorySessionFixture.use(null);
    }
    private ExecutorService pool() throws Exception { var f=ReceiptController.class.getDeclaredField("worker"); f.setAccessible(true); return (ExecutorService)f.get(controller); }
    private static <T> T fx(Callable<T> task) throws Exception {
        var done=new CompletableFuture<T>(); Platform.runLater(() -> { try { done.complete(task.call()); } catch(Throwable failure) { done.completeExceptionally(failure); } }); return done.get(10,TimeUnit.SECONDS);
    }
    @SuppressWarnings("unchecked") private <T> T node(String id) { return (T)root.lookup("#"+id); }
    private void until(BooleanSupplier condition) throws Exception { long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8); while(System.nanoTime()<end) { if(fx(condition::getAsBoolean)) { return; } Thread.sleep(10); } fail("UI callback missing"); }
    private String message() { return this.<Label>node("messageLabel").getText(); }
    private void open(Role role) throws Exception {
        FeeCategorySessionFixture.use(role);
        var auth=new RoleBasedAuthorizationService(null);
        root=fx(() -> { controller=new ReceiptController(new ReceiptServiceImpl(repository,auth,Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"),ZoneOffset.UTC)),auth);
            var view=ReceiptController.load(controller); new javafx.scene.Scene(view,1060,700); view.applyCss(); view.layout(); return view; });
    }
    private void ready() throws Exception { until(() -> this.<ComboBox<PaymentInvoice>>node("invoiceBox").getValue()!=null&&!controller.busyProperty().get()); }
    private void save(String amount) throws Exception { fx(() -> { this.<TextField>node("payerField").setText("Nguyễn A"); this.<TextField>node("amountField").setText(amount); this.<DatePicker>node("paymentDate").setValue(LocalDate.of(2026,11,8)); this.<Button>node("saveButton").fire(); return null; }); }
    @Test void successRefreshesReceiptsAndCreatesNewIntentWithoutFxJdbc() throws Exception {
        open(Role.ACCOUNTANT); ready(); String before=fx(() -> this.<TextField>node("requestField").getText()); save("1000");
        until(() -> this.<TableView<Receipt>>node("receiptTable").getItems().size()==1&&!controller.busyProperty().get());
        assertNotEquals(before,fx(() -> this.<TextField>node("requestField").getText())); assertEquals("",fx(() -> this.<TextField>node("amountField").getText()));
        assertEquals(1,repository.writes); assertFalse(repository.fxAccess);
    }
    @Test void validationKeepsIntentAndInputWithoutWrite() throws Exception {
        open(Role.ACCOUNTANT); ready(); String before=fx(() -> this.<TextField>node("requestField").getText()); save("1.5");
        until(() -> !this.<Label>node("errorLabel").getText().isBlank()); assertEquals(0,repository.writes);
        assertEquals(before,fx(() -> this.<TextField>node("requestField").getText())); assertEquals("1.5",fx(() -> this.<TextField>node("amountField").getText()));
    }
    @Test void adminCannotRecordAndResidentCannotLoad() throws Exception {
        open(Role.ADMIN); ready(); assertFalse(fx(() -> this.<Button>node("saveButton").isVisible()));
        fx(() -> { controller.dispose(); return null; }); assertTrue(pool().awaitTermination(5,TimeUnit.SECONDS));
        int reads=repository.reads; open(Role.RESIDENT); until(() -> message().contains("không có quyền")); assertEquals(reads,repository.reads); assertEquals(0,repository.writes);
    }
    @Test void unknownCommitFreezesSaveAndReconcilesWithoutRetry() throws Exception {
        open(Role.ACCOUNTANT); ready(); repository.lostReply=true;
        String before=fx(() -> this.<TextField>node("requestField").getText()); save("1000");
        until(() -> message().contains("chưa xác định")); assertTrue(fx(() -> this.<Button>node("saveButton").isDisabled()));
        assertEquals(before,fx(() -> this.<TextField>node("requestField").getText()));
        fx(() -> { this.<Button>node("saveButton").fire(); this.<Button>node("reconcileButton").fire(); return null; });
        until(() -> !before.equals(this.<TextField>node("requestField").getText())); assertEquals(1,repository.writes); assertEquals(1,repository.rows.size());
        assertFalse(fx(this::message).contains("secret"));
    }
    @Test void slowWriteKeepsFxResponsiveBlocksDoubleSubmitAndCloseDropsCallbacks() throws Exception {
        open(Role.ACCOUNTANT); ready(); repository.block=true; save("1000"); assertTrue(repository.entered.await(5,TimeUnit.SECONDS));
        assertEquals(42,fx(() -> 42)); assertTrue(fx(() -> controller.busyProperty().get()));
        String old=fx(this::message); fx(() -> { this.<Button>node("saveButton").fire(); controller.dispose(); return null; });
        repository.release.countDown(); assertTrue(pool().awaitTermination(5,TimeUnit.SECONDS)); fx(() -> null);
        assertEquals(1,repository.writes); assertEquals(old,fx(this::message));
    }
    @Test void logoutBeforeCallbackDoesNotPublishReceiptData() throws Exception {
        open(Role.ACCOUNTANT); ready(); repository.block=true; save("1000"); assertTrue(repository.entered.await(5,TimeUnit.SECONDS));
        FeeCategorySessionFixture.use(null); repository.release.countDown(); until(() -> message().contains("không có quyền"));
        assertTrue(fx(() -> this.<TableView<Receipt>>node("receiptTable").getItems().isEmpty()));
    }
    private static final class Repository implements ReceiptRepository {
        final Map<String,Receipt> rows=new ConcurrentHashMap<>();
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        volatile int writes,reads; volatile boolean fxAccess,block,lostReply;
        final PaymentInvoice invoice=new PaymentInvoice("HD-202611-P1204","2026-11","HK-1204","P1204",new BigDecimal("10000"),BigDecimal.ZERO,new BigDecimal("10000"),ChargeStatus.CHUA_DONG);
        void read() { reads++; fxAccess|=Platform.isFxApplicationThread(); }
        public ReceiptOutcome record(Receipt input) throws SQLException {
            writes++; fxAccess|=Platform.isFxApplicationThread(); entered.countDown();
            if(block) { try { if(!release.await(8,TimeUnit.SECONDS)) { throw new SQLException("timeout"); } } catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new SQLException(failure); } }
            var saved=new Receipt("PT-TEST-HK-1204",input.requestId(),input.maHoaDon(),input.nguoiNop(),input.soTienNop(),input.ngayNop(),input.hinhThuc(),input.ghiChu(),input.createdBy(),input.createdAt());
            rows.put(input.requestId(),saved); if(lostReply) { throw new SQLException("secret connection lost","08006"); }
            return new ReceiptOutcome(saved,invoice,false);
        }
        public List<PaymentInvoice> listOutstanding() { read(); return List.of(invoice); }
        public List<Receipt> listByHousehold(String code) { read(); return List.copyOf(rows.values()); }
        public Optional<Receipt> findByRequestId(String id) { read(); return Optional.ofNullable(rows.get(id)); }
    }
}
