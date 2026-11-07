package com.bluemoon.controllers;

import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.FeeKind;
import com.bluemoon.models.FeeStatus;
import com.bluemoon.models.FeeType;
import com.bluemoon.services.AccessDeniedException;
import com.bluemoon.services.FeeTypeResult;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** UI behaviour of the US-005 screen against a fake service. No MySQL, no real backend. */
class FeeCategoryControllerTest {
    private FakeFeeTypeService service;
    private Stage stage;
    private Parent root;
    private FeeCategoryController controller;

    @BeforeAll static void toolkit() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException alreadyStarted) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @BeforeEach void setUp() {
        service = new FakeFeeTypeService();
        service.stored.add(FakeFeeTypeService.fee("PHI_QL", "Phí quản lý chung cư", FeeKind.BAT_BUOC,
                ChargeMethod.THEO_DIEN_TICH, "0", FeeStatus.DANG_AP_DUNG));
        service.stored.add(FakeFeeTypeService.fee("QUY_CU", "Quỹ cũ", FeeKind.DONG_GOP,
                ChargeMethod.TU_NGUYEN, "0", FeeStatus.NGUNG_AP_DUNG));
    }

    @AfterEach void tearDown() throws Exception {
        if (service.createGate != null) { service.createGate.countDown(); }
        if (service.listGate != null) { service.listGate.countDown(); }
        if (stage != null) { fx(() -> { stage.close(); return null; }); }
    }

    private void open() throws Exception {
        fx(() -> {
            controller = new FeeCategoryController(service);
            root = FeeCategoryController.load(controller);
            stage = new Stage();
            stage.setScene(new Scene(root));
            stage.show();
            return null;
        });
    }

    // ---- helpers ----

    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { done.complete(action.call()); } catch (Throwable t) { done.completeExceptionally(t); } });
        return done.get(10, TimeUnit.SECONDS);
    }

    private <T> T get(Supplier<T> reader) throws Exception { return fx(reader::get); }

    private void waitUntil(Supplier<Boolean> condition) throws Exception {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            if (get(condition)) { return; }
            Thread.sleep(20);
        }
        fail("Condition not reached within 5s");
    }

    @SuppressWarnings("unchecked") private <T> T node(String id) { return (T) root.lookup("#" + id); }
    private TableView<FeeType> table() { return node("feeTable"); }
    private TextField field(String id) { return node(id); }
    private Label label(String id) { return node(id); }
    private Button button(String id) { return node(id); }
    @SuppressWarnings("unchecked") private <T> ComboBox<T> combo(String id) { return node(id); }

    private void fill(String code, String name, FeeKind kind, ChargeMethod method, String amount) throws Exception {
        fx(() -> {
            field("codeField").setText(code);
            field("nameField").setText(name);
            combo("kindBox").setValue(kind);
            if (method != null) { combo("methodBox").setValue(method); }
            if (amount != null) { field("amountField").setText(amount); }
            return null;
        });
    }

    private void save() throws Exception { fx(() -> { button("saveButton").fire(); return null; }); }

    private void loaded() throws Exception { waitUntil(() -> service.listCalls.get() > 0 && !table().getItems().isEmpty()); }

    // ---- tests ----

    @Test void fxmlLoadsAndShowsDefaults() throws Exception {
        open();
        loaded();
        assertEquals(FeeStatus.DANG_AP_DUNG, get(() -> combo("statusBox").getValue()), "new fee defaults to DANG_AP_DUNG");
        assertTrue(get(() -> button("saveButton").getText().equals("Lưu")));
        assertNotNull(node("cancelButton"));
    }

    @Test void listShowsAllSixColumnsWithReadableValues() throws Exception {
        open();
        loaded();
        List<String> headers = get(() -> table().getColumns().stream().map(TableColumn::getText).toList());
        assertEquals(List.of("Mã", "Tên khoản thu", "Loại", "Hình thức tính", "Số tiền mặc định", "Trạng thái"), headers);
        List<String> firstRow = get(() -> table().getColumns().stream().map(c -> String.valueOf(c.getCellData(0))).toList());
        assertEquals(List.of("PHI_QL", "Phí quản lý chung cư", "Bắt buộc", "Theo diện tích (m²)", "0", "Đang áp dụng"), firstRow);
    }

    @Test void filterByStatusCallsServiceWithThatStatus() throws Exception {
        open();
        loaded();
        fx(() -> { combo("filterBox").getSelectionModel().select(FeeCategoryController.Filter.STOPPED); return null; });
        waitUntil(() -> service.listFilters.contains(FeeStatus.NGUNG_AP_DUNG) && table().getItems().size() == 1);
        assertEquals("QUY_CU", get(() -> table().getItems().get(0).maKhoanThu()));
        fx(() -> { combo("filterBox").getSelectionModel().select(FeeCategoryController.Filter.ALL); return null; });
        waitUntil(() -> table().getItems().size() == 2);
        assertNull(service.listFilters.get(service.listFilters.size() - 1), "all = null filter");
    }

    @Test void emptyListShowsEmptyState() throws Exception {
        service.stored.clear();
        open();
        waitUntil(() -> service.listCalls.get() > 0 && FeeCategoryController.MSG_EMPTY.equals(
                ((Label) table().getPlaceholder()).getText()));
        assertTrue(get(() -> table().getItems().isEmpty()));
    }

    @Test void loadFailureShowsErrorStateWithRetryAndNoExceptionText() throws Exception {
        service.listFailure = new IllegalStateException("jdbc:mysql://secret-host SQL error");
        open();
        waitUntil(() -> FeeCategoryController.MSG_LOAD_ERROR.equals(((Label) table().getPlaceholder()).getText()));
        assertTrue(get(() -> button("retryButton").isVisible()));
        service.listFailure = null;
        fx(() -> { button("retryButton").fire(); return null; });
        waitUntil(() -> table().getItems().size() == 2);
        assertFalse(get(() -> button("retryButton").isVisible()));
    }

    @Test void invalidCodeIsRejectedWithoutCallingService() throws Exception {
        open();
        loaded();
        for (String bad : new String[] {"", "PHI QL", "PHI-QL", "A234567890123456789012345"}) {
            fill(bad, "Phí quản lý", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, "0");
            save();
            assertEquals(FeeCategoryController.MSG_CODE, get(() -> label("codeError").getText()), "code '" + bad + "'");
        }
        assertEquals(0, service.createCalls.get());
    }

    @Test void codeFieldTurnsTypedTextIntoUpperCase() throws Exception {
        open();
        fx(() -> { field("codeField").setText("phi_ql"); return null; });
        assertEquals("PHI_QL", get(() -> field("codeField").getText()));
    }

    @Test void tooShortOrTooLongNameIsRejected() throws Exception {
        open();
        loaded();
        fill("PHI_X", "ab", FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "0");
        save();
        assertEquals(FeeCategoryController.MSG_NAME, get(() -> label("nameError").getText()));
        fill("PHI_X", "x".repeat(101), FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "0");
        save();
        assertEquals(FeeCategoryController.MSG_NAME, get(() -> label("nameError").getText()));
        assertEquals(0, service.createCalls.get());
    }

    @Test void negativeOrNonNumericAmountIsRejected() throws Exception {
        open();
        loaded();
        for (String bad : new String[] {"-1", "abc", "1,5"}) {
            fill("PHI_X", "Phí thử", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, bad);
            save();
            assertEquals(FeeCategoryController.MSG_AMOUNT, get(() -> label("amountError").getText()), "amount " + bad);
        }
        assertEquals(0, service.createCalls.get());
    }

    @Test void mandatoryKindOffersOnlyTheThreeCalculationMethods() throws Exception {
        open();
        fx(() -> { combo("kindBox").setValue(FeeKind.BAT_BUOC); return null; });
        assertEquals(List.of(ChargeMethod.THEO_DIEN_TICH, ChargeMethod.THEO_NHAN_KHAU, ChargeMethod.THEO_CAN_HO),
                get(() -> List.copyOf(combo("methodBox").getItems())));
        assertFalse(get(() -> combo("methodBox").isDisabled()));
        assertNull(get(() -> combo("methodBox").getValue()), "must choose a method explicitly");
        fill("PHI_X", "Phí thử", FeeKind.BAT_BUOC, null, "");
        save();
        assertEquals(FeeCategoryController.MSG_METHOD, get(() -> label("methodError").getText()));
        assertEquals(0, service.createCalls.get());
    }

    @Test void mandatoryWithBlankAmountSendsZero() throws Exception {
        open();
        loaded();
        fill("PHI_VS", "Phí vệ sinh", FeeKind.BAT_BUOC, ChargeMethod.THEO_NHAN_KHAU, "");
        save();
        waitUntil(() -> service.createCalls.get() == 1);
        assertEquals("0", service.createdForms.get(0).soTienMacDinh());
        assertEquals(ChargeMethod.THEO_NHAN_KHAU, service.createdForms.get(0).hinhThuc());
        assertEquals(FeeStatus.DANG_AP_DUNG, service.createdForms.get(0).trangThai());
    }

    @Test void contributionForcesVoluntaryMethodAndZeroAndLocksFields() throws Exception {
        open();
        fx(() -> { field("amountField").setText("5000"); combo("kindBox").setValue(FeeKind.DONG_GOP); return null; });
        assertEquals(ChargeMethod.TU_NGUYEN, get(() -> combo("methodBox").getValue()));
        assertEquals(List.of(ChargeMethod.TU_NGUYEN), get(() -> List.copyOf(combo("methodBox").getItems())));
        assertEquals("0", get(() -> field("amountField").getText()));
        assertTrue(get(() -> combo("methodBox").isDisabled()));
        assertTrue(get(() -> field("amountField").isDisabled()));
        fx(() -> { field("codeField").setText("QUY_NGHEO_2026"); field("nameField").setText("Quỹ vì người nghèo năm 2026"); return null; });
        save();
        waitUntil(() -> service.createCalls.get() == 1);
        assertEquals(ChargeMethod.TU_NGUYEN, service.createdForms.get(0).hinhThuc());
        assertEquals("0", service.createdForms.get(0).soTienMacDinh());
        assertEquals(FeeKind.DONG_GOP, service.createdForms.get(0).loai());
    }

    @Test void switchingBackToMandatoryUnlocksFieldsAndClearsForcedValues() throws Exception {
        open();
        fx(() -> { combo("kindBox").setValue(FeeKind.DONG_GOP); combo("kindBox").setValue(FeeKind.BAT_BUOC); return null; });
        assertFalse(get(() -> combo("methodBox").isDisabled()));
        assertFalse(get(() -> field("amountField").isDisabled()));
        assertNull(get(() -> combo("methodBox").getValue()));
        assertEquals("", get(() -> field("amountField").getText()));
    }

    @Test void duplicateCodeFromServiceIsShownAndInputKept() throws Exception {
        service.createScript = form -> FakeFeeTypeService.duplicate(form.maKhoanThu());
        open();
        loaded();
        fill("PHI_QL", "Phí quản lý chung cư", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, "7000");
        save();
        waitUntil(() -> !label("codeError").getText().isEmpty());
        assertEquals(FeeCategoryController.MSG_CODE, get(() -> label("codeError").getText()));
        assertEquals("PHI_QL", get(() -> field("codeField").getText()));
        assertEquals("Phí quản lý chung cư", get(() -> field("nameField").getText()));
        assertEquals("7000", get(() -> field("amountField").getText()));
        assertFalse(get(() -> button("saveButton").isDisabled()), "user may fix and retry");
        assertEquals(2, get(() -> table().getItems().size()), "list unchanged");
    }

    @Test void successSavesRefreshesListAndResetsForm() throws Exception {
        open();
        loaded();
        int listCallsBefore = service.listCalls.get();
        fill("PHI_DV", "Phí dịch vụ chung cư", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, "");
        save();
        waitUntil(() -> table().getItems().size() == 3);
        assertTrue(service.listCalls.get() > listCallsBefore, "list refreshed after create");
        assertEquals("Tạo khoản thu PHI_DV thành công", get(() -> label("messageLabel").getText()));
        assertEquals("", get(() -> field("codeField").getText()));
        assertNull(get(() -> combo("kindBox").getValue()));
        assertEquals(FeeStatus.DANG_AP_DUNG, get(() -> combo("statusBox").getValue()));
    }

    @Test void secondSaveWhileFirstIsRunningIsIgnored() throws Exception {
        service.createGate = new CountDownLatch(1);
        open();
        loaded();
        fill("PHI_DV", "Phí dịch vụ", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, "");
        save();
        waitUntil(() -> service.createCalls.get() == 1);
        assertTrue(get(() -> button("saveButton").isDisabled()));
        save();
        save();
        Thread.sleep(200);
        assertEquals(1, service.createCalls.get(), "no duplicate request while the first is in flight");
        service.createGate.countDown();
        waitUntil(() -> !button("saveButton").isDisabled());
        assertEquals(1, service.createCalls.get());
    }

    @Test void accessDeniedOnCreateDisablesFormWithSafeMessage() throws Exception {
        service.createFailure = new AccessDeniedException("secret internal detail");
        open();
        loaded();
        fill("PHI_DV", "Phí dịch vụ", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, "");
        save();
        waitUntil(() -> FeeCategoryController.MSG_DENIED.equals(label("messageLabel").getText()));
        assertTrue(get(() -> button("saveButton").isDisabled()));
        assertTrue(get(() -> field("codeField").isDisabled()));
        assertFalse(get(() -> label("messageLabel").getText().contains("secret")));
    }

    @Test void accessDeniedOnListShowsDeniedStateAndNoData() throws Exception {
        service.listFailure = new AccessDeniedException("resident");
        open();
        waitUntil(() -> FeeCategoryController.MSG_DENIED.equals(((Label) table().getPlaceholder()).getText()));
        assertTrue(get(() -> table().getItems().isEmpty()));
        assertTrue(get(() -> button("saveButton").isDisabled()));
        assertEquals(1, service.listCalls.get(), "no automatic retry after denial");
    }

    @Test void systemErrorOnCreateShowsGenericMessageAndKeepsInput() throws Exception {
        service.createScript = form -> FeeTypeResult.failure(FeeTypeResult.Status.SYSTEM_ERROR,
                "SQLSyntaxError near 'fee_types'", Map.of());
        open();
        loaded();
        fill("PHI_DV", "Phí dịch vụ", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, "");
        save();
        waitUntil(() -> !label("messageLabel").getText().isEmpty());
        assertEquals(FeeCategoryController.MSG_SYSTEM, get(() -> label("messageLabel").getText()));
        assertEquals("PHI_DV", get(() -> field("codeField").getText()));
    }

    @Test void serviceCallsDoNotBlockTheFxThreadAndRunOffIt() throws Exception {
        service.listGate = new CountDownLatch(1);
        open();
        waitUntil(() -> service.listCalls.get() == 1);
        // The service is blocked inside JDBC-like wait; the FX thread must still answer immediately.
        assertEquals(42, fx(() -> 42));
        assertNotNull(service.lastCallThread);
        assertFalse(service.lastCallThread.getName().contains("JavaFX Application Thread"));
        assertTrue(get(() -> table().getPlaceholder() instanceof Label l && FeeCategoryController.MSG_LOADING.equals(l.getText())));
        service.listGate.countDown();
        waitUntil(() -> table().getItems().size() == 2);
    }

    // ---- lifecycle (dispose) ----

    private void dispose() throws Exception { fx(() -> { controller.dispose(); return null; }); }

    @Test void leavingWhileListIsLoadingDropsLateResultAndKeepsLoadingState() throws Exception {
        service.listGate = new CountDownLatch(1);
        open();
        waitUntil(() -> service.listCalls.get() == 1);
        dispose();
        service.listGate.countDown();
        Thread.sleep(300);
        assertTrue(get(() -> table().getItems().isEmpty()), "late list result must not fill the old screen");
        assertEquals(FeeCategoryController.MSG_LOADING, get(() -> ((Label) table().getPlaceholder()).getText()));
        assertTrue(get(() -> controller.isDisposed()));
    }

    @Test void leavingWhileListFailsDoesNotShowErrorOrDenial() throws Exception {
        service.listGate = new CountDownLatch(1);
        service.listFailure = new AccessDeniedException("late");
        open();
        waitUntil(() -> service.listCalls.get() == 1);
        dispose();
        service.listGate.countDown();
        Thread.sleep(300);
        assertEquals(FeeCategoryController.MSG_LOADING, get(() -> ((Label) table().getPlaceholder()).getText()));
        assertFalse(get(() -> button("saveButton").isDisabled()), "late denial must not alter the old screen");
    }

    @Test void leavingWhileCreatingDropsCallbackButDoesNotCancelTheService() throws Exception {
        service.createGate = new CountDownLatch(1);
        open();
        loaded();
        fill("PHI_DV", "Phí dịch vụ", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, "");
        save();
        waitUntil(() -> service.createCalls.get() == 1);
        int listCalls = service.listCalls.get();
        dispose();
        service.createGate.countDown();
        waitUntil(() -> service.stored.size() == 3); // the Service call still ran to completion
        Thread.sleep(300);
        assertEquals(1, service.createCalls.get());
        assertEquals(listCalls, service.listCalls.get(), "no refresh after dispose");
        assertEquals("", get(() -> label("messageLabel").getText()), "no success message on a dead screen");
        assertEquals("PHI_DV", get(() -> field("codeField").getText()), "form not reset by a late callback");
        assertTrue(get(() -> button("saveButton").isDisabled()), "saving state is not changed behind the user");
        assertEquals(2, get(() -> table().getItems().size()));
    }

    @Test void oldScreenCallbackDoesNotTouchNewScreen() throws Exception {
        service.listGate = new CountDownLatch(1);
        open();
        waitUntil(() -> service.listCalls.get() == 1);
        FeeCategoryController old = controller;
        Parent oldRoot = root;
        dispose();
        service.listGate = null;
        FeeCategoryController fresh = fx(() -> new FeeCategoryController(service));
        Parent freshRoot = fx(() -> FeeCategoryController.load(fresh));
        controller = fresh;
        root = freshRoot;
        waitUntil(() -> table().getItems().size() == 2);
        service.stored.clear();
        assertTrue(get(() -> ((TableView<?>) oldRoot.lookup("#feeTable")).getItems().isEmpty()));
        assertTrue(get(() -> old.isDisposed()));
        assertFalse(get(() -> fresh.isDisposed()));
        assertEquals(2, get(() -> table().getItems().size()), "new screen keeps its own data");
    }

    @Test void disposedScreenIgnoresFurtherUserActions() throws Exception {
        open();
        loaded();
        dispose();
        int lists = service.listCalls.get();
        fill("PHI_DV", "Phí dịch vụ", FeeKind.BAT_BUOC, ChargeMethod.THEO_DIEN_TICH, "");
        save();
        fx(() -> { combo("filterBox").getSelectionModel().select(FeeCategoryController.Filter.STOPPED); return null; });
        Thread.sleep(200);
        assertEquals(0, service.createCalls.get());
        assertEquals(lists, service.listCalls.get());
        fx(() -> { controller.dispose(); return null; }); // idempotent
    }
}
