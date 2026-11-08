package com.bluemoon.controllers;

import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.concurrent.Task;
import javafx.fxml.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** No JDBC here. Immutable intents, no automatic retry; unknown outcome requires explicit reconciliation. */
public final class ReceiptController {
    private final ReceiptService service;
    private final AuthorizationService authorization;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(job -> { var t = new Thread(job,"receipt-worker"); t.setDaemon(true); return t; });
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private int pending;
    private boolean disposed, saving, uncertain;
    private long listRequest, historyRequest;
    @FXML private ComboBox<PaymentInvoice> invoiceBox;
    @FXML private ComboBox<PaymentMethod> methodBox;
    @FXML private TextField payerField, amountField, householdField, requestField;
    @FXML private DatePicker paymentDate;
    @FXML private TextArea noteField;
    @FXML private Button saveButton, reloadButton, historyButton, reconcileButton, newButton;
    @FXML private Label messageLabel, errorLabel;
    @FXML private TableView<Receipt> receiptTable;
    @FXML private TableColumn<Receipt,String> codeColumn, amountColumn, payerColumn, dateColumn, methodColumn;
    public ReceiptController(ReceiptService service, AuthorizationService authorization) { this.service=Objects.requireNonNull(service); this.authorization=Objects.requireNonNull(authorization); }
    public static Parent load(ReceiptController controller) {
        var loader = new FXMLLoader(ReceiptController.class.getResource("/com/bluemoon/views/ReceiptView.fxml")); loader.setControllerFactory(type -> controller);
        try { return loader.load(); } catch (IOException failure) { controller.dispose(); throw new UncheckedIOException(failure); }
    }
    @FXML private void initialize() {
        codeColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().maPhieuThu()));
        amountColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().soTienNop().toPlainString()));
        payerColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().nguoiNop()));
        dateColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().ngayNop().toString()));
        methodColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().hinhThuc().toString()));
        receiptTable.setPlaceholder(new Label("Chưa có phiếu thu phù hợp."));
        invoiceBox.setCellFactory(list -> invoiceCell()); invoiceBox.setButtonCell(invoiceCell());
        methodBox.getItems().setAll(PaymentMethod.values()); methodBox.setValue(PaymentMethod.TIEN_MAT);
        paymentDate.setEditable(false); paymentDate.setValue(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        requestField.setEditable(false); newIntent();
        invoiceBox.valueProperty().addListener((o,old,value) -> { if(value!=null) { householdField.setText(value.maHo()); history(); } controls(); });
        saveButton.setOnAction(e -> save()); reloadButton.setOnAction(e -> reload()); historyButton.setOnAction(e -> history());
        reconcileButton.setOnAction(e -> reconcile());
        newButton.setOnAction(e -> {
            if(uncertain) {
                var alert = new Alert(Alert.AlertType.CONFIRMATION,"Chỉ tạo yêu cầu mới sau khi đã đối soát mã cũ để tránh thu hai lần. Bạn đã đối soát?",ButtonType.OK,ButtonType.CANCEL);
                if(alert.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK) { return; }
            }
            newIntent(); reload();
        });
        controls(); reload();
    }
    private static ListCell<PaymentInvoice> invoiceCell() {
        return new ListCell<>() { @Override protected void updateItem(PaymentInvoice item, boolean empty) { super.updateItem(item,empty);
            setText(empty||item==null?null:item.soCanHo()+" / "+item.kyThu()+" — Còn phải thu: "+item.conThieu().toPlainString()+" VNĐ"); } };
    }
    private boolean view() { return authorization.canAccess(Permission.PAYMENT_HISTORY_VIEW_ALL); }
    private void newIntent() { uncertain=false; requestField.setText(UUID.randomUUID().toString()); amountField.clear(); errorLabel.setText(""); controls(); }
    private void controls() {
        boolean canView=view(), canRecord=canView&&authorization.canAccess(Permission.PAYMENT_RECORD);
        saveButton.setVisible(canRecord); saveButton.setManaged(canRecord);
        saveButton.setDisable(saving||uncertain||!canRecord||invoiceBox.getValue()==null);
        for(var field:List.of(payerField,amountField,paymentDate,methodBox,noteField,invoiceBox)) { field.setDisable(saving||uncertain||!canRecord); }
        // ADMIN may select an invoice to inspect history, but never enter a payment.
        invoiceBox.setDisable(saving||uncertain||!canView);
        reloadButton.setDisable(saving||uncertain||!canView); historyButton.setDisable(!canView); householdField.setDisable(!canView);
        reconcileButton.setDisable(saving||!canView); newButton.setDisable(saving||!canRecord);
    }
    private void reload() {
        if(disposed||saving||uncertain) { return; } if(!view()) { deny(); return; }
        long request=++listRequest; String selected=invoiceBox.getValue()==null?null:invoiceBox.getValue().maHoaDon();
        run(service::listOutstanding,result -> {
            if(request!=listRequest) { return; }
            if(!result.isSuccess()) { show(result); return; }
            invoiceBox.getItems().setAll(result.data().orElseThrow());
            var next=invoiceBox.getItems().stream().filter(i -> i.maHoaDon().equals(selected)).findFirst().orElse(null);
            if(next==null&&!invoiceBox.getItems().isEmpty()) { next=invoiceBox.getItems().get(0); }
            invoiceBox.setValue(next); controls();
            if(next==null&&messageLabel.getText().isBlank()) { messageLabel.setText("Không còn hóa đơn cần thanh toán."); }
        });
    }
    private void history() {
        if(disposed) { return; } if(!view()) { deny(); return; }
        long request=++historyRequest; String code=householdField.getText(); receiptTable.getItems().clear();
        run(() -> service.listByHousehold(code),result -> { if(request==historyRequest) { if(result.isSuccess()) { receiptTable.getItems().setAll(result.data().orElseThrow()); } else { show(result); } } });
    }
    private void save() {
        if(disposed||saving||uncertain) { return; }
        if(!authorization.canAccess(Permission.PAYMENT_RECORD)) { messageLabel.setText("Bạn không có quyền lập phiếu thu."); controls(); return; }
        var invoice=invoiceBox.getValue();
        var form=new ReceiptForm(requestField.getText(),invoice==null?null:invoice.maHoaDon(),payerField.getText(),amountField.getText(),paymentDate.getValue(),methodBox.getValue(),noteField.getText());
        saving=true; errorLabel.setText(""); controls();
        run(() -> service.recordPayment(form),result -> {
            saving=false; show(result);
            if(result.isSuccess()) {
                messageLabel.setText("Đã ghi nhận phiếu "+result.data().orElseThrow().receipt().maPhieuThu()); newIntent(); reload(); history();
            } else if(result.status()==ReceiptResult.Status.SYSTEM_ERROR) { uncertain=true; }
            else if(result.status()==ReceiptResult.Status.DUPLICATE||result.status()==ReceiptResult.Status.CONFLICT) { uncertain=true; }
            controls();
        });
    }
    private void reconcile() {
        if(disposed||saving) { return; } final String id=requestField.getText();
        run(() -> service.findByRequestId(id),result -> {
            if(!result.isSuccess()) { show(result); return; }
            var receipt=result.data().orElseThrow();
            if(receipt.isPresent()) { messageLabel.setText("Đã ghi nhận: "+receipt.get().maPhieuThu()+". Không gửi lại yêu cầu cũ."); uncertain=false; newIntent(); reload(); history(); }
            else { messageLabel.setText("Chưa tìm thấy phiếu cho mã này. Hãy đối soát trước khi tạo yêu cầu mới."); }
            controls();
        });
    }
    private void show(ReceiptResult<?> result) { messageLabel.setText(result.message()); errorLabel.setText(String.join("\n",result.fieldErrors().values())); }
    private void deny() { invoiceBox.getItems().clear(); receiptTable.getItems().clear(); controls(); messageLabel.setText("Bạn không có quyền xem thanh toán."); }
    private <T> void run(Callable<T> call, Consumer<T> callback) {
        Task<T> task=new Task<>() { @Override protected T call() throws Exception { return call.call(); } };
        pending++; busy.set(true);
        task.setOnSucceeded(e -> { finish(); if(!disposed) { if(view()) { callback.accept(task.getValue()); } else { deny(); } } });
        task.setOnFailed(e -> { finish(); if(!disposed) { saving=false; if(task.getException() instanceof AccessDeniedException||!view()) { deny(); }
            else { uncertain=true; messageLabel.setText("Lỗi hệ thống. Đối soát mã yêu cầu trước khi tiếp tục."); controls(); } } });
        try { worker.execute(task); } catch(RejectedExecutionException failure) { finish(); if(!disposed) { saving=false; uncertain=true; controls(); messageLabel.setText("Không gửi được yêu cầu. Hãy đối soát trước khi tiếp tục."); } }
    }
    private void finish() { pending--; busy.set(pending>0); }
    public ReadOnlyBooleanProperty busyProperty() { return busy.getReadOnlyProperty(); }
    public void dispose() {
        if(!Platform.isFxApplicationThread()) { throw new IllegalStateException("FX thread required"); }
        if(disposed) { return; } disposed=true; ++listRequest; ++historyRequest; worker.shutdown();
    }
}
