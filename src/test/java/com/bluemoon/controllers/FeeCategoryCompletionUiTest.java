package com.bluemoon.controllers;

import com.bluemoon.dao.FeeTypeRepository;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class FeeCategoryCompletionUiTest {
    protected static final String CODE="UI_FEE_1";
    protected FeeTypeRepository repository;
    protected FeeCategoryModule module;
    protected Stage stage;
    protected void repository() throws Exception {repository=new Rows();}
    @BeforeAll static void toolkit() throws Exception {
        var ready=new CompletableFuture<Void>();try {Platform.startup(() -> {Platform.setImplicitExit(false);ready.complete(null);});}catch(IllegalStateException e){ready.complete(null);}ready.get(10,TimeUnit.SECONDS);
    }
    @BeforeEach void setup() throws Exception {
        FeeCategorySessionFixture.use(Role.ADMIN);repository();
        repository.insert(new FeeType(CODE,"Phí ban đầu",FeeKind.BAT_BUOC,ChargeMethod.THEO_CAN_HO,new BigDecimal("0"),FeeStatus.DANG_AP_DUNG));
        fx(() -> {module=new FeeCategoryModule(new FeeTypeServiceImpl(repository));stage=new Stage();stage.setScene(new Scene(module.view(),1100,800));stage.show();});
        until(() -> !module.busyProperty().get());
    }
    @AfterEach void teardown() throws Exception {fx(() -> {module.close();stage.close();});FeeCategorySessionFixture.use(null);cleanup();}
    protected void cleanup() throws Exception { }
    protected static void fx(Runnable work) throws Exception {
        var done=new CompletableFuture<Void>();Platform.runLater(() -> {try {work.run();done.complete(null);}catch(Throwable e){done.completeExceptionally(e);}});done.get(15,TimeUnit.SECONDS);
    }
    protected void until(BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<end){boolean[]ok={false};fx(() -> ok[0]=condition.getAsBoolean());if(ok[0])return;Thread.sleep(15);}fail("UI timeout");
    }
    protected <T> T node(String id,Class<T> type){return type.cast(stage.getScene().lookup("#"+id));}
    protected void click(String id){node(id,Button.class).fire();}
    @SuppressWarnings("unchecked") protected void select(){var table=(TableView<FeeType>)node("feeTable",TableView.class);table.getSelectionModel().select(table.getItems().stream().filter(f -> f.maKhoanThu().equals(CODE)).findFirst().orElseThrow());}
    protected DialogPane confirmation(){return Window.getWindows().stream().filter(w -> w.isShowing() && w.getScene().getRoot() instanceof DialogPane p && p.getButtonTypes().contains(ButtonType.OK)).map(w -> (DialogPane)w.getScene().getRoot()).findFirst().orElse(null);}
    protected void answer(ButtonType button) throws Exception {until(() -> confirmation()!=null);fx(() -> ((Button)confirmation().lookupButton(button)).fire());}
    @Test void editsAllowedFieldsKeepsCodeImmutableAndRefreshes() throws Exception {
        fx(() -> {select();click("editFeeButton");assertFalse(node("codeField",TextField.class).isEditable());node("nameField",TextField.class).setText("Phí đã cập nhật");click("saveButton");click("saveButton");});
        until(() -> !module.busyProperty().get() && node("messageLabel",Label.class).getText().contains("thành công"));
        assertEquals("Phí đã cập nhật",repository.findByCode(CODE).orElseThrow().tenKhoanThu());
    }
    @Test void deactivateRequiresConfirmationCancelDoesNothingThenRefreshes() throws Exception {
        fx(() -> {select();click("deactivateFeeButton");assertTrue(module.busyProperty().get());});answer(ButtonType.CANCEL);
        assertEquals(FeeStatus.DANG_AP_DUNG,repository.findByCode(CODE).orElseThrow().trangThai());
        fx(() -> click("deactivateFeeButton"));answer(ButtonType.OK);until(() -> !module.busyProperty().get());
        assertEquals(FeeStatus.NGUNG_AP_DUNG,repository.findByCode(CODE).orElseThrow().trangThai());
        fx(() -> {select();assertTrue(node("deactivateFeeButton",Button.class).isDisabled());});
    }
    @SuppressWarnings("unchecked") @Test void statusChangeInEditAlsoRequiresConfirmation() throws Exception {
        fx(() -> {select();click("editFeeButton");node("statusBox",ComboBox.class).setValue(FeeStatus.NGUNG_AP_DUNG);click("saveButton");});
        answer(ButtonType.CANCEL);assertEquals(FeeStatus.DANG_AP_DUNG,repository.findByCode(CODE).orElseThrow().trangThai());
        fx(() -> click("saveButton"));answer(ButtonType.OK);until(() -> !module.busyProperty().get());
        assertEquals(FeeStatus.NGUNG_AP_DUNG,repository.findByCode(CODE).orElseThrow().trangThai());
    }
    @Test void revokedPermissionBlocksEditAndDeactivate() throws Exception {
        fx(() -> {FeeCategorySessionFixture.use(Role.RESIDENT);select();assertTrue(node("editFeeButton",Button.class).isDisabled());assertTrue(node("deactivateFeeButton",Button.class).isDisabled());click("editFeeButton");click("deactivateFeeButton");});
        assertEquals(FeeStatus.DANG_AP_DUNG,repository.findByCode(CODE).orElseThrow().trangThai());
    }
    private static final class Rows implements FeeTypeRepository {
        private final Map<String,FeeType> rows=new ConcurrentHashMap<>();
        public List<FeeType> findAll(FeeStatus status){return rows.values().stream().filter(f -> status==null || status==f.trangThai()).toList();}
        public Optional<FeeType> findByCode(String code){return Optional.ofNullable(rows.get(code));}
        public void insert(FeeType fee){rows.put(fee.maKhoanThu(),fee);}
        public Optional<FeeType> update(FeeType fee){return rows.replace(fee.maKhoanThu(),fee)==null?Optional.empty():Optional.of(fee);}
        public Optional<FeeType> deactivate(String code){return findByCode(code).flatMap(f -> update(new FeeType(code,f.tenKhoanThu(),f.loai(),f.hinhThuc(),f.soTienMacDinh(),FeeStatus.NGUNG_AP_DUNG)));}
    }
}
