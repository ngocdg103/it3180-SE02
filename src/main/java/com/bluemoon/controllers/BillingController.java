package com.bluemoon.controllers;

import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.BillingPeriodDetail;
import com.bluemoon.models.BillingPreview;
import com.bluemoon.models.ChargeLine;
import com.bluemoon.models.ChargeStatus;
import com.bluemoon.models.MonthlyCharge;
import com.bluemoon.models.Permission;
import com.bluemoon.services.AccessDeniedException;
import com.bluemoon.services.AuthorizationService;
import com.bluemoon.services.BillingResult;
import com.bluemoon.services.BillingService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * US-007 screen. It only calls BillingService: no money is computed here, the UI shows what the service returned.
 * Every service call runs off the FX thread. Generate needs a successful preview of the same period and an explicit
 * confirmation. Once disposed, no callback touches the screen; an in-flight generate may still commit.
 */
public final class BillingController {
    /** Asks the user to confirm. Production shows a modal dialog; tests inject their own. */
    @FunctionalInterface public interface Confirmer { boolean confirm(String message); }

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String DENIED = "Bạn không có quyền thực hiện chức năng này.";
    static final String SYSTEM = "Không thực hiện được. Vui lòng thử lại sau.";

    private final BillingService service;
    private final AuthorizationService authorization;
    private final Clock clock;
    private final Executor executor;
    private final boolean ownsExecutor;
    private final Confirmer confirmer;
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private final Set<Task<?>> tasks = new HashSet<>();
    private final Set<Task<?>> writes = new HashSet<>();
    private boolean disposed, generating, updatingPeriods, previewMode;
    private long periodsRequest, viewRequest, invoiceRequest;
    private int pending;
    private String previewedPeriod;
    private BillingPreview lastPreview;

    @FXML private Label currentPeriodLabel, periodError, bannerLabel, summaryLabel, messageLabel;
    @FXML private Button previewButton, generateButton, refreshButton;
    @FXML private ComboBox<String> periodBox;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private TableView<MonthlyCharge> chargeTable;
    @FXML private TableView<ChargeLine> lineTable;
    @FXML private ListView<String> issuesList;
    @FXML private TableColumn<MonthlyCharge, String> apartmentColumn, householdColumn, areaColumn, peopleColumn,
            totalColumn, paidColumn, owedColumn, statusColumn, dueColumn;
    @FXML private TableColumn<ChargeLine, String> lineFeeColumn, lineQuantityColumn, lineRateColumn, lineAmountColumn;

    public BillingController(BillingService service, AuthorizationService authorization, Clock clock) {
        this(service, authorization, clock, defaultExecutor(), true, BillingController::askWithDialog);
    }

    /** Test seam: own executor and confirmation. */
    BillingController(BillingService service, AuthorizationService authorization, Clock clock, Executor executor,
                      boolean ownsExecutor, Confirmer confirmer) {
        this.service = Objects.requireNonNull(service);
        this.authorization = Objects.requireNonNull(authorization);
        this.clock = Objects.requireNonNull(clock);
        this.executor = Objects.requireNonNull(executor);
        this.ownsExecutor = ownsExecutor;
        this.confirmer = Objects.requireNonNull(confirmer);
    }

    /** Loads the FXML with the caller's controller so the caller can dispose it. FX thread. */
    public static Parent load(BillingController controller) {
        var loader = new FXMLLoader(BillingController.class.getResource("/com/bluemoon/views/BillingView.fxml"));
        loader.setControllerFactory(type -> controller);
        try { return loader.load(); }
        catch (IOException failure) { controller.dispose(); throw new UncheckedIOException(failure); }
    }

    public ReadOnlyBooleanProperty busyProperty() { return busy.getReadOnlyProperty(); }
    public boolean isDisposed() { return disposed; }

    @FXML private void initialize() {
        apartmentColumn.setCellValueFactory(row -> text(row.getValue().soCanHo()));
        householdColumn.setCellValueFactory(row -> text(row.getValue().maHo()));
        areaColumn.setCellValueFactory(row -> text(plain(row.getValue().dienTich())));
        peopleColumn.setCellValueFactory(row -> text(String.valueOf(row.getValue().soNhanKhau())));
        totalColumn.setCellValueFactory(row -> text(money(row.getValue().tongTien())));
        paidColumn.setCellValueFactory(row -> text(money(row.getValue().daThanhToan())));
        owedColumn.setCellValueFactory(row -> text(money(row.getValue().conThieu())));
        statusColumn.setCellValueFactory(row -> text(statusText(row.getValue().trangThai())));
        dueColumn.setCellValueFactory(row -> text(String.valueOf(row.getValue().hanNop())));
        lineFeeColumn.setCellValueFactory(row -> text(row.getValue().maKhoanThu() + " — " + row.getValue().tenKhoanThu()));
        lineQuantityColumn.setCellValueFactory(row -> text(plain(row.getValue().soLuong())));
        lineRateColumn.setCellValueFactory(row -> text(money(row.getValue().donGia())));
        lineAmountColumn.setCellValueFactory(row -> text(money(row.getValue().thanhTien())));
        chargeTable.setPlaceholder(new Label("Chưa có dữ liệu. Bấm \"Xem trước\" hoặc chọn một kỳ đã lập."));
        lineTable.setPlaceholder(new Label("Chọn một căn hộ để xem chi tiết."));
        currentPeriodLabel.setText(currentPeriod().toString());
        chargeTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> showLines(row));
        periodBox.valueProperty().addListener((obs, old, value) -> {
            if (!updatingPeriods && value != null) { showStored(value, false); }
        });
        previewButton.setOnAction(event -> preview());
        generateButton.setOnAction(event -> generate());
        refreshButton.setOnAction(event -> loadPeriods(periodBox.getValue(), false));
        updateControls();
        loadPeriods(null, false);
    }

    // ---- permissions ----
    private boolean canView() { return authorization.canAccess(Permission.DEBT_REPORT_VIEW); }
    private boolean canCreate() { return authorization.canAccess(Permission.BILLING_PERIOD_CREATE); }

    private YearMonth currentPeriod() { return YearMonth.from(clock.instant().atZone(BUSINESS_ZONE)); }

    // ---- actions ----
    private void loadPeriods(String select, boolean openSelected) {
        if (disposed) { return; }
        if (!canView()) { deny(); return; }
        long request = ++periodsRequest;
        if (!openSelected) { message(""); }
        run(service::listPeriods, result -> {
            if (request != periodsRequest) { return; }
            if (!result.isSuccess()) { message(result.message()); return; }
            String keep = select != null ? select : periodBox.getValue();
            updatingPeriods = true;
            periodBox.getItems().setAll(result.data().orElseThrow().stream().map(BillingPeriod::kyThu).toList());
            periodBox.setValue(keep != null && periodBox.getItems().contains(keep) ? keep : null);
            updatingPeriods = false;
            if (openSelected && periodBox.getValue() != null) { showStored(periodBox.getValue(), true); }
        }, failure -> failed(failure), false);
    }

    private void preview() {
        if (disposed || busy.get()) { return; }
        if (!canCreate()) { deny(); return; }
        String period = currentPeriod().toString();
        clearErrors(); message(""); previewedPeriod = null; lastPreview = null; updateControls();
        long request = ++viewRequest;
        run(() -> service.previewPeriod(period), result -> {
            if (request != viewRequest) { return; }
            if (!result.isSuccess()) { showFailure(result); updateControls(); return; }
            var preview = result.data().orElseThrow();
            previewMode = !preview.alreadyGenerated();
            showCharges(preview.charges(), true);
            summary(preview);
            issuesList.getItems().setAll(issues(preview));
            if (preview.alreadyGenerated()) {
                banner("Kỳ " + preview.kyThu() + " ĐÃ phát hành (dữ liệu đã chốt, không tính lại).");
            } else {
                banner("Bản xem trước kỳ " + preview.kyThu() + " — CHƯA phát hành.");
                previewedPeriod = preview.kyThu();
                lastPreview = preview;
            }
            updateControls();
        }, failure -> { failed(failure); updateControls(); }, false);
    }

    private void generate() {
        if (disposed || generating || busy.get()) { return; }
        if (!canCreate()) { deny(); return; }
        String period = currentPeriod().toString();
        if (lastPreview == null || !period.equals(previewedPeriod)) {
            message("Vui lòng xem trước kỳ " + period + " trước khi phát hành."); return;
        }
        var preview = lastPreview;
        String question = "Phát hành kỳ thu " + period + " cho " + preview.charges().size() + " hộ, tổng "
                + money(preview.tongTien()) + " VNĐ?\nSau khi phát hành không thể sửa hoặc lập lại kỳ này.";
        if (!confirmer.confirm(question)) { message("Đã hủy phát hành."); return; }
        clearErrors(); message("");
        generating = true; updateControls();
        run(() -> service.generatePeriod(period), result -> {
            generating = false;
            switch (result.status()) {
                case SUCCESS -> {
                    previewedPeriod = null; lastPreview = null; message(result.message());
                    loadPeriods(period, true);
                }
                case DUPLICATE -> {
                    previewedPeriod = null; lastPreview = null; showFailure(result);
                    loadPeriods(period, true);
                }
                case BUSY -> showFailure(result); // preview kept: the user may retry
                default -> { previewedPeriod = null; lastPreview = null; showFailure(result); }
            }
            updateControls();
        }, failure -> { generating = false; failed(failure); updateControls(); }, true);
    }

    private void showStored(String period, boolean selectFirst) {
        if (disposed) { return; }
        if (!canView()) { deny(); return; }
        long request = ++viewRequest;
        previewedPeriod = null; lastPreview = null; previewMode = false;
        if (!selectFirst) { clearErrors(); message(""); }
        updateControls();
        run(() -> service.getPeriod(period), result -> {
            if (request != viewRequest) { return; }
            if (!result.isSuccess()) { showFailure(result); return; }
            BillingPeriodDetail detail = result.data().orElseThrow();
            showCharges(detail.charges(), false);
            var p = detail.period();
            banner("Kỳ " + p.kyThu() + " — đã phát hành, hạn nộp " + p.hanNop() + ".");
            summaryLabel.setText("Số hóa đơn: " + p.soHoaDon() + " · Hộ không lập: " + p.soHoBoQua()
                    + " · Tổng phải thu: " + money(p.tongTien()) + " VNĐ");
            issuesList.getItems().clear();
            if (selectFirst && !chargeTable.getItems().isEmpty()) { chargeTable.getSelectionModel().select(0); }
        }, failure -> failed(failure), false);
    }

    private void showLines(MonthlyCharge row) {
        long request = ++invoiceRequest;
        lineTable.getItems().clear();
        if (row == null || disposed) { return; }
        if (previewMode) { lineTable.getItems().setAll(row.lines()); return; }
        if (!canView()) { deny(); return; }
        run(() -> service.getInvoice(row.maHoaDon()), result -> {
            if (request != invoiceRequest) { return; }
            if (result.isSuccess()) { lineTable.getItems().setAll(result.data().orElseThrow().lines()); }
            else { message(result.message()); }
        }, failure -> failed(failure), false);
    }

    // ---- presentation (no arithmetic on money) ----
    private void showCharges(List<MonthlyCharge> charges, boolean preview) {
        previewMode = preview;
        chargeTable.getSelectionModel().clearSelection();
        lineTable.getItems().clear();
        chargeTable.getItems().setAll(charges);
    }

    private void summary(BillingPreview preview) {
        summaryLabel.setText("Số hóa đơn: " + preview.charges().size() + " · Hộ không lập: " + preview.skipped().size()
                + " · Tổng phải thu: " + money(preview.tongTien()) + " VNĐ · Hạn nộp: " + preview.hanNop());
    }

    private static List<String> issues(BillingPreview preview) {
        var lines = new ArrayList<String>();
        preview.warnings().forEach(w -> lines.add("⚠ " + w.message()));
        preview.skipped().forEach(s -> lines.add("✖ Không lập: " + s.message()));
        preview.rates().forEach(r -> lines.add("Đơn giá " + r.maKhoanThu() + ": " + money(r.donGia())
                + " VNĐ (hiệu lực từ " + r.ngayApDung() + ")"));
        return lines;
    }

    private void showFailure(BillingResult<?> result) {
        message(result.message());
        String periodMessage = result.fieldErrors().get("ky_thu");
        if (periodMessage != null) { periodError.setText(periodMessage); }
        if (result.status() == BillingResult.Status.MISSING_RATE) {
            issuesList.getItems().setAll(result.fieldErrors().keySet().stream()
                    .map(code -> "✖ Thiếu đơn giá hiệu lực: " + code).toList());
        }
    }

    private void updateControls() {
        boolean create = canCreate();
        boolean locked = busy.get() || generating;
        previewButton.setVisible(create); previewButton.setManaged(create);
        generateButton.setVisible(create); generateButton.setManaged(create);
        previewButton.setDisable(locked || !create);
        generateButton.setDisable(locked || !create || lastPreview == null);
        refreshButton.setDisable(locked || !canView());
        periodBox.setDisable(locked || !canView());
    }

    private void banner(String text) { bannerLabel.setText(text); }
    private void message(String text) { messageLabel.setText(text); }
    private void clearErrors() { periodError.setText(""); }
    private void loading(boolean value) { loadingIndicator.setVisible(value); loadingIndicator.setManaged(value); }

    private void deny() {
        ++periodsRequest; ++viewRequest; ++invoiceRequest;
        previewedPeriod = null; lastPreview = null; previewMode = false;
        chargeTable.getItems().clear(); lineTable.getItems().clear(); issuesList.getItems().clear();
        periodBox.getItems().clear(); bannerLabel.setText(""); summaryLabel.setText("");
        updateControls(); message(DENIED);
    }

    private void failed(Throwable failure) {
        if (failure instanceof AccessDeniedException) { deny(); } else { message(SYSTEM); }
    }

    // ---- threading ----
    private <T> void run(Callable<T> call, Consumer<T> success, Consumer<Throwable> failure, boolean write) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return call.call(); } };
        tasks.add(task); if (write) { writes.add(task); }
        pending++; busy.set(true); loading(true); updateControls();
        task.setOnSucceeded(event -> {
            finish(task);
            if (disposed) { return; }
            // A committed write is always reported; reads are dropped when the session lost its right.
            if (!write && !canView()) { deny(); return; }
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            finish(task);
            if (disposed) { return; }
            failure.accept(task.getException());
        });
        task.setOnCancelled(event -> finish(task));
        try { executor.execute(task); }
        catch (RejectedExecutionException rejected) { finish(task); if (!disposed) { failure.accept(rejected); } }
    }

    private void finish(Task<?> task) {
        writes.remove(task);
        if (tasks.remove(task)) {
            pending--; busy.set(pending > 0); loading(pending > 0);
            if (!disposed) { updateControls(); }
        }
    }

    /** FX thread only, idempotent. Cancels reads; an in-flight generate may still commit, its callback is dropped. */
    public void dispose() {
        if (!Platform.isFxApplicationThread()) { throw new IllegalStateException("Billing lifecycle requires FX thread"); }
        if (disposed) { return; }
        disposed = true;
        ++periodsRequest; ++viewRequest; ++invoiceRequest;
        for (Task<?> task : List.copyOf(tasks)) { if (!writes.contains(task)) { task.cancel(true); } }
        if (ownsExecutor && executor instanceof ExecutorService pool) { pool.shutdown(); }
    }

    private static Executor defaultExecutor() {
        return Executors.newSingleThreadExecutor(job -> {
            var thread = new Thread(job, "billing-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static boolean askWithDialog(String message) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Xác nhận phát hành kỳ thu");
        alert.setHeaderText("Phát hành kỳ thu");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    // ---- formatting only ----
    private static SimpleStringProperty text(String value) { return new SimpleStringProperty(value); }

    static String money(BigDecimal value) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(value);
    }

    static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    static String statusText(ChargeStatus status) {
        return switch (status) {
            case CHUA_DONG -> "Chưa đóng";
            case DONG_THIEU -> "Đóng thiếu";
            case DA_DONG_DU -> "Đã đóng đủ";
            case DONG_THUA -> "Đóng thừa";
        };
    }
}
