package com.bluemoon.controllers;

import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.FeeKind;
import com.bluemoon.models.FeeStatus;
import com.bluemoon.models.FeeType;
import com.bluemoon.models.FeeTypeForm;
import com.bluemoon.services.AccessDeniedException;
import com.bluemoon.services.FeeTypeResult;
import com.bluemoon.services.FeeTypeService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * US-005 fee category screen (list + create). Talks only to {@link FeeTypeService}; no SQL, no DAO.
 * UI validation is a convenience: the Service must validate again. Service calls run on a worker
 * thread and results are applied on the JavaFX Application Thread.
 */
public final class FeeCategoryController {
    // Messages copied verbatim from docs/srs/EP03_ThuPhi.md section 1.1.
    static final String MSG_CODE = "Mã khoản thu không được để trống, không chứa khoảng trắng và không được trùng!";
    static final String MSG_NAME = "Tên khoản thu phải từ 3 đến 100 ký tự!";
    static final String MSG_KIND = "Vui lòng chọn loại khoản thu!";
    static final String MSG_METHOD = "Vui lòng chọn hình thức tính phí!";
    static final String MSG_AMOUNT = "Số tiền mặc định phải lớn hơn hoặc bằng 0!";
    static final String MSG_STATUS = "Vui lòng chọn trạng thái!";
    static final String MSG_DENIED = "Bạn không có quyền quản lý danh mục khoản thu.";
    static final String MSG_SYSTEM = "Không thực hiện được. Vui lòng thử lại sau.";
    static final String MSG_LOAD_ERROR = "Không tải được danh mục khoản thu.";
    static final String MSG_EMPTY = "Chưa có khoản thu nào.";
    static final String MSG_LOADING = "Đang tải...";

    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{1,20}");
    private static final String ERROR_STYLE = "error-text";

    /** Status filter choices; "all" maps to a null filter in the Service call. */
    enum Filter {
        ALL("Tất cả", null),
        ACTIVE("Đang áp dụng", FeeStatus.DANG_AP_DUNG),
        STOPPED("Ngừng áp dụng", FeeStatus.NGUNG_AP_DUNG);

        private final String label;
        final FeeStatus status;
        Filter(String label, FeeStatus status) { this.label = label; this.status = status; }
        @Override public String toString() { return label; }
    }

    private final FeeTypeService service;
    private final Executor executor;
    private boolean saving;
    private boolean denied;
    private long listRequest; // only the newest list response may update the table

    @FXML private TableView<FeeType> feeTable;
    @FXML private TableColumn<FeeType, String> codeColumn;
    @FXML private TableColumn<FeeType, String> nameColumn;
    @FXML private TableColumn<FeeType, String> kindColumn;
    @FXML private TableColumn<FeeType, String> methodColumn;
    @FXML private TableColumn<FeeType, String> amountColumn;
    @FXML private TableColumn<FeeType, String> statusColumn;
    @FXML private ComboBox<Filter> filterBox;
    @FXML private ProgressIndicator loadingIndicator;
    private final Label tableStateLabel = new Label(); // table placeholder: loading / empty / error text
    @FXML private Button retryButton;
    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private ComboBox<FeeKind> kindBox;
    @FXML private ComboBox<ChargeMethod> methodBox;
    @FXML private TextField amountField;
    @FXML private ComboBox<FeeStatus> statusBox;
    @FXML private Label codeError;
    @FXML private Label nameError;
    @FXML private Label kindError;
    @FXML private Label methodError;
    @FXML private Label amountError;
    @FXML private Label statusError;
    @FXML private Label messageLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    public FeeCategoryController(FeeTypeService service) { this(service, defaultExecutor()); }

    /** The executor must run work off the FX thread (tests may pass their own). */
    public FeeCategoryController(FeeTypeService service, Executor executor) {
        this.service = Objects.requireNonNull(service);
        this.executor = Objects.requireNonNull(executor);
    }

    /** Loads FeeCategoryView.fxml with this controller. Call on the FX thread. */
    public static Parent load(FeeTypeService service) { return load(new FeeCategoryController(service)); }

    static Parent load(FeeCategoryController controller) {
        FXMLLoader loader = new FXMLLoader(
                FeeCategoryController.class.getResource("/com/bluemoon/views/FeeCategoryView.fxml"));
        loader.setControllerFactory(type -> controller);
        try { return loader.load(); }
        catch (IOException failure) { throw new UncheckedIOException("Cannot load FeeCategoryView.fxml", failure); }
    }

    private static Executor defaultExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fee-category-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @FXML
    private void initialize() {
        DecimalFormat money = new DecimalFormat("#,##0.##");
        codeColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().maKhoanThu()));
        nameColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().tenKhoanThu()));
        kindColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().loai().label()));
        methodColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().hinhThuc().label()));
        amountColumn.setCellValueFactory(row -> new SimpleStringProperty(money.format(row.getValue().soTienMacDinh())));
        statusColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().trangThai().label()));
        tableStateLabel.getStyleClass().add("state-text");
        feeTable.setPlaceholder(tableStateLabel);

        filterBox.setItems(FXCollections.observableArrayList(Filter.values()));
        filterBox.getSelectionModel().select(Filter.ALL);
        filterBox.valueProperty().addListener((obs, old, now) -> reload());

        kindBox.setItems(FXCollections.observableArrayList(FeeKind.values()));
        statusBox.setItems(FXCollections.observableArrayList(FeeStatus.values()));
        kindBox.valueProperty().addListener((obs, old, now) -> onKindChanged(old, now));
        // UI convenience: codes are upper case. The Service still validates on its own.
        codeField.setTextFormatter(new TextFormatter<String>(change -> {
            change.setText(change.getText().toUpperCase());
            return change;
        }));
        retryButton.setOnAction(event -> reload());
        saveButton.setOnAction(event -> save());
        cancelButton.setOnAction(event -> resetForm());
        resetForm();
        reload();
    }

    // ---- list ----

    private void reload() {
        if (denied) { return; }
        Filter filter = filterBox.getValue() == null ? Filter.ALL : filterBox.getValue();
        long request = ++listRequest;
        showTableState(true, MSG_LOADING, false);
        feeTable.getItems().clear();
        run(() -> service.listFeeTypes(filter.status),
                result -> { if (request == listRequest) { onListed(result); } },
                failure -> { if (request == listRequest) { onListFailed(failure); } });
    }

    private void onListed(FeeTypeResult<List<FeeType>> result) {
        if (!result.isSuccess() || result.data().isEmpty()) {
            showTableState(false, MSG_LOAD_ERROR, true);
            return;
        }
        List<FeeType> rows = result.data().get();
        feeTable.getItems().setAll(rows);
        showTableState(false, rows.isEmpty() ? MSG_EMPTY : "", false);
    }

    private void onListFailed(Throwable failure) {
        if (failure instanceof AccessDeniedException) { denyAccess(); return; }
        showTableState(false, MSG_LOAD_ERROR, true);
    }

    private void showTableState(boolean loading, String text, boolean canRetry) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        tableStateLabel.setText(text);
        retryButton.setVisible(canRetry);
        retryButton.setManaged(canRetry);
    }

    // ---- form behaviour (BR-06) ----

    private void onKindChanged(FeeKind old, FeeKind now) {
        clearErrors();
        methodBox.setItems(FXCollections.observableArrayList(ChargeMethod.forKind(now)));
        boolean contribution = now == FeeKind.DONG_GOP;
        methodBox.getSelectionModel().clearSelection();
        if (contribution) {
            methodBox.getSelectionModel().select(ChargeMethod.TU_NGUYEN);
            amountField.setText("0");
        } else if (old == FeeKind.DONG_GOP) {
            amountField.clear();
        }
        methodBox.setDisable(contribution || denied);
        amountField.setDisable(contribution || denied);
    }

    private void resetForm() {
        codeField.clear();
        nameField.clear();
        kindBox.getSelectionModel().clearSelection();
        methodBox.setItems(FXCollections.observableArrayList());
        methodBox.getSelectionModel().clearSelection();
        amountField.clear();
        statusBox.getSelectionModel().select(FeeStatus.DANG_AP_DUNG); // default for a new fee type
        methodBox.setDisable(denied);
        amountField.setDisable(denied);
        clearErrors();
        showMessage("", false);
    }

    // ---- save ----

    private void save() {
        if (saving || denied) { return; }
        clearErrors();
        showMessage("", false);
        FeeTypeForm form = readForm();
        Map<String, String> errors = validate(form);
        if (!errors.isEmpty()) { showFieldErrors(errors); return; }
        setSaving(true);
        run(() -> service.createFeeType(form), this::onSaved, this::onSaveFailed);
    }

    private FeeTypeForm readForm() {
        FeeKind kind = kindBox.getValue();
        String amount = kind == FeeKind.DONG_GOP ? "0" : text(amountField);
        if (kind == FeeKind.BAT_BUOC && amount.isEmpty()) { amount = "0"; } // not yet priced; US-006 sets the rate
        ChargeMethod method = kind == FeeKind.DONG_GOP ? ChargeMethod.TU_NGUYEN : methodBox.getValue();
        return new FeeTypeForm(text(codeField), text(nameField), kind, method, amount, statusBox.getValue());
    }

    /** UI-side checks only (same SRS rules); the Service validates independently. */
    static Map<String, String> validate(FeeTypeForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!CODE.matcher(form.maKhoanThu()).matches()) { errors.put("ma_khoan_thu", MSG_CODE); }
        int nameLength = form.tenKhoanThu().length();
        if (nameLength < 3 || nameLength > 100) { errors.put("ten_khoan_thu", MSG_NAME); }
        if (form.loai() == null) {
            errors.put("loai_khoan_thu", MSG_KIND);
        } else if (form.hinhThuc() == null || !ChargeMethod.forKind(form.loai()).contains(form.hinhThuc())) {
            errors.put("hinh_thuc_thu", MSG_METHOD);
        }
        if (!isNonNegativeNumber(form.soTienMacDinh())) { errors.put("so_tien_mac_dinh", MSG_AMOUNT); }
        if (form.trangThai() == null) { errors.put("trang_thai", MSG_STATUS); }
        return errors;
    }

    private static boolean isNonNegativeNumber(String value) {
        try { return new BigDecimal(value).signum() >= 0; }
        catch (NumberFormatException notANumber) { return false; }
    }

    private void onSaved(FeeTypeResult<FeeType> result) {
        setSaving(false);
        if (result.isSuccess()) {
            resetForm();
            showMessage(result.message(), false);
            reload();
            return;
        }
        // Keep what the user typed; show the Service's per-field errors.
        showFieldErrors(result.fieldErrors());
        showMessage(result.status() == FeeTypeResult.Status.SYSTEM_ERROR ? MSG_SYSTEM : result.message(), true);
    }

    private void onSaveFailed(Throwable failure) {
        setSaving(false);
        if (failure instanceof AccessDeniedException) { denyAccess(); return; }
        showMessage(MSG_SYSTEM, true); // never show exception text
    }

    private void setSaving(boolean value) {
        saving = value;
        saveButton.setDisable(value || denied);
        cancelButton.setDisable(value);
    }

    private void denyAccess() {
        denied = true;
        saveButton.setDisable(true);
        for (var control : List.of(codeField, nameField, kindBox, methodBox, amountField, statusBox, filterBox)) {
            control.setDisable(true);
        }
        feeTable.getItems().clear();
        showTableState(false, MSG_DENIED, false);
        showMessage(MSG_DENIED, true);
    }

    // ---- messages ----

    private void showMessage(String text, boolean error) {
        messageLabel.getStyleClass().remove(ERROR_STYLE);
        if (error) { messageLabel.getStyleClass().add(ERROR_STYLE); }
        messageLabel.setText(text);
    }

    private void showFieldErrors(Map<String, String> errors) {
        errors.forEach((key, message) -> {
            Label label = switch (key) {
                case "ma_khoan_thu" -> codeError;
                case "ten_khoan_thu" -> nameError;
                case "loai_khoan_thu" -> kindError;
                case "hinh_thuc_thu" -> methodError;
                case "so_tien_mac_dinh" -> amountError;
                case "trang_thai" -> statusError;
                default -> null;
            };
            if (label != null) { label.setText(message); }
        });
    }

    private void clearErrors() {
        for (Label label : List.of(codeError, nameError, kindError, methodError, amountError, statusError)) {
            label.setText("");
        }
    }

    private static String text(TextField field) { return field.getText() == null ? "" : field.getText().trim(); }

    // ---- threading ----

    private <T> void run(Callable<T> call, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return call.call(); }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        executor.execute(task);
    }
}
