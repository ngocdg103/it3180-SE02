package com.bluemoon.controllers;

import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Service-only screen. Every service call runs off the FX thread; stale/disposed results are discarded. */
public final class FeeRateController {
    static final String DENIED = "Bạn không có quyền xem đơn giá.";
    static final String SYSTEM = "Không thực hiện được. Vui lòng thử lại sau.";
    private final FeeRateService service;
    private final AuthorizationService authorization;
    private final ExecutorService worker;
    private boolean disposed, saving, updatingChoices;
    private long choiceRequest, historyRequest;
    @FXML private ComboBox<FeeType> feeBox;
    @FXML private TableView<FeeRate> historyTable;
    @FXML private TableColumn<FeeRate, String> dateColumn, priceColumn, noteColumn;
    @FXML private TextField priceField;
    @FXML private TextArea noteField;
    @FXML private DatePicker effectiveDate, lookupDate;
    @FXML private Button saveButton, refreshButton;
    @FXML private Label messageLabel, codeError, priceError, dateError, noteError, effectiveLabel, statusLabel;
    @FXML private ProgressIndicator loadingIndicator;
    private final Label tableState = new Label("Chọn khoản thu để xem lịch sử.");

    public FeeRateController(FeeRateService service, AuthorizationService authorization) {
        this.service = Objects.requireNonNull(service);
        this.authorization = Objects.requireNonNull(authorization);
        worker = Executors.newSingleThreadExecutor(job -> {
            var thread = new Thread(job, "fee-rate-worker"); thread.setDaemon(true); return thread;
        });
    }
    public static Parent load(FeeRateController controller) {
        var loader = new FXMLLoader(FeeRateController.class.getResource("/com/bluemoon/views/FeeRateView.fxml"));
        loader.setControllerFactory(type -> controller);
        try { return loader.load(); }
        catch (IOException failure) { controller.dispose(); throw new UncheckedIOException(failure); }
    }
    @FXML private void initialize() {
        dateColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().ngayApDung().toString()));
        priceColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().donGia().toPlainString()));
        noteColumn.setCellValueFactory(row -> new SimpleStringProperty(Objects.toString(row.getValue().ghiChu(), "")));
        historyTable.setPlaceholder(tableState);
        feeBox.setCellFactory(list -> feeCell());
        feeBox.setButtonCell(feeCell());
        // Read-only editors prevent an uncommitted date string from silently retaining an older selected date.
        effectiveDate.setEditable(false); lookupDate.setEditable(false);
        effectiveDate.setValue(LocalDate.now()); lookupDate.setValue(LocalDate.now());
        feeBox.valueProperty().addListener((obs, old, value) -> {
            if (updatingChoices) { return; }
            clearErrors(); message(""); priceField.clear(); noteField.clear();
            updateControls(); reloadHistory();
        });
        lookupDate.valueProperty().addListener((obs, old, value) -> reloadHistory());
        saveButton.setOnAction(event -> save());
        refreshButton.setOnAction(event -> reloadChoices());
        updateControls(); reloadChoices();
    }
    private static ListCell<FeeType> feeCell() {
        return new ListCell<>() {
            @Override protected void updateItem(FeeType fee, boolean empty) {
                super.updateItem(fee, empty);
                setText(empty || fee == null ? null : fee.maKhoanThu() + " — " + fee.tenKhoanThu()
                        + (fee.trangThai() == FeeStatus.NGUNG_AP_DUNG ? " (Ngừng áp dụng)" : ""));
            }
        };
    }
    private boolean canView() { return authorization.canAccess(Permission.FEE_RATE_VIEW); }
    private void reloadChoices() {
        if (disposed || saving) { return; }
        if (!canView()) { deny(); return; }
        long request = ++choiceRequest;
        ++historyRequest;
        FeeType selected = feeBox.getValue();
        message("");
        historyTable.getItems().clear(); effectiveLabel.setText("");
        loading(true); refreshButton.setDisable(true);
        run(service::listFeeTypes, result -> {
            if (request != choiceRequest) { return; }
            loading(false); refreshButton.setDisable(false);
            if (!result.isSuccess()) {
                feeBox.getItems().clear();
                message(result.message()); tableState.setText("Không tải được danh mục khoản thu.");
                updateControls(); return;
            }
            updatingChoices = true;
            feeBox.getItems().setAll(result.data().orElseThrow());
            FeeType next = feeBox.getItems().stream().filter(fee -> selected != null
                    && fee.maKhoanThu().equals(selected.maKhoanThu())).findFirst().orElse(null);
            if (next == null && !feeBox.getItems().isEmpty()) { next = feeBox.getItems().get(0); }
            feeBox.setValue(next);
            updatingChoices = false;
            if (selected == null || next == null || !selected.maKhoanThu().equals(next.maKhoanThu())) {
                clearErrors(); priceField.clear(); noteField.clear();
            }
            if (next == null) { tableState.setText("Chưa có khoản thu bắt buộc."); }
            else { reloadHistory(); }
            updateControls();
        }, failure -> { if (request == choiceRequest) { loading(false); refreshButton.setDisable(false); failed(failure); } });
    }
    private record History(FeeRateResult<List<FeeRate>> rows, FeeRateResult<Optional<FeeRate>> effective) { }
    private void reloadHistory() {
        long request = ++historyRequest;
        historyTable.getItems().clear(); effectiveLabel.setText("");
        if (disposed) { return; }
        if (!canView()) { deny(); return; }
        FeeType fee = feeBox.getValue();
        if (fee == null) { loading(false); tableState.setText("Chọn khoản thu để xem lịch sử."); return; }
        LocalDate date = lookupDate.getValue();
        loading(true); tableState.setText("Đang tải lịch sử...");
        run(() -> {
            var rows = service.listHistory(fee.maKhoanThu());
            return new History(rows, rows.isSuccess() ? service.getEffectiveRate(fee.maKhoanThu(), date) : null);
        }, result -> {
            if (request != historyRequest) { return; }
            loading(false);
            if (!result.rows().isSuccess()) {
                tableState.setText("Không tải được lịch sử."); message(result.rows().message()); return;
            }
            historyTable.getItems().setAll(result.rows().data().orElseThrow());
            tableState.setText("Chưa có đơn giá. Thêm đơn giá mới để bắt đầu.");
            var applied = result.effective();
            if (!applied.isSuccess()) { effectiveLabel.setText(applied.message()); }
            else {
                effectiveLabel.setText(applied.data().orElseThrow().map(rate -> "Đơn giá hiệu lực: "
                        + rate.donGia().toPlainString() + " VNĐ (từ " + rate.ngayApDung() + ")")
                        .orElse("Chưa có đơn giá hiệu lực tại ngày đã chọn."));
            }
        }, failure -> { if (request == historyRequest) { loading(false); tableState.setText("Không tải được lịch sử."); failed(failure); } });
    }
    private void save() {
        if (disposed || saving) { return; }
        if (!canView() || !authorization.canAccess(Permission.FEE_RATE_CREATE)) { deny(); return; }
        FeeType fee = feeBox.getValue();
        if (fee == null || fee.trangThai() != FeeStatus.DANG_AP_DUNG) {
            message("Chọn khoản thu bắt buộc đang áp dụng."); return;
        }
        clearErrors(); message("");
        var form = new FeeRateForm(fee.maKhoanThu(), priceField.getText(), effectiveDate.getValue(), noteField.getText());
        saving = true; updateControls();
        run(() -> service.createRate(form), result -> {
            saving = false; updateControls(); message(result.message());
            result.fieldErrors().forEach((field, error) -> {
                Label label = switch (field) {
                    case "ma_khoan_thu" -> codeError;
                    case "don_gia" -> priceError;
                    case "ngay_ap_dung" -> dateError;
                    case "ghi_chu" -> noteError;
                    default -> null;
                };
                if (label != null) { label.setText(error); }
            });
            if (result.isSuccess()) { priceField.clear(); noteField.clear(); reloadHistory(); }
            // A failed save retains the input, including duplicate/uncertain commit outcomes. No auto retry.
        }, failure -> { saving = false; updateControls(); failed(failure); });
    }
    private void updateControls() {
        boolean view = canView();
        FeeType fee = feeBox.getValue();
        boolean create = view && authorization.canAccess(Permission.FEE_RATE_CREATE);
        boolean editable = create && fee != null && fee.trangThai() == FeeStatus.DANG_AP_DUNG;
        feeBox.setDisable(saving || !view); lookupDate.setDisable(saving || !view);
        refreshButton.setDisable(saving || !view);
        priceField.setDisable(saving || !editable); effectiveDate.setDisable(saving || !editable);
        noteField.setDisable(saving || !editable); saveButton.setDisable(saving || !editable);
        saveButton.setVisible(create); saveButton.setManaged(create);
        statusLabel.setText(fee != null && fee.trangThai() == FeeStatus.NGUNG_AP_DUNG
                ? "Khoản thu đã ngừng áp dụng. Chỉ xem lịch sử." : "Giá mới được thêm vào lịch sử; không ghi đè giá cũ.");
    }
    private void clearErrors() { for (var label : List.of(codeError, priceError, dateError, noteError)) { label.setText(""); } }
    private void message(String text) { messageLabel.setText(text); }
    private void loading(boolean value) { loadingIndicator.setVisible(value); loadingIndicator.setManaged(value); }
    private void deny() {
        ++choiceRequest; ++historyRequest; loading(false);
        historyTable.getItems().clear(); feeBox.getItems().clear(); effectiveLabel.setText("");
        updateControls(); tableState.setText(DENIED); message(DENIED);
    }
    private void failed(Throwable failure) { if (failure instanceof AccessDeniedException) { deny(); } else { message(SYSTEM); } }
    private <T> void run(Callable<T> call, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return call.call(); } };
        task.setOnSucceeded(event -> {
            if (disposed) { return; }
            if (!canView()) { deny(); return; }
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            if (disposed) { return; }
            if (!canView()) { deny(); return; }
            failure.accept(task.getException());
        });
        try { worker.execute(task); }
        catch (RejectedExecutionException rejected) { if (!disposed) { failure.accept(rejected); } }
    }
    /** FX thread only. In-flight writes may commit; late callbacks never touch a removed screen. */
    public void dispose() {
        if (!Platform.isFxApplicationThread()) { throw new IllegalStateException("Fee rate lifecycle requires FX thread"); }
        if (disposed) { return; }
        disposed = true; ++choiceRequest; ++historyRequest; worker.shutdown();
    }
}
