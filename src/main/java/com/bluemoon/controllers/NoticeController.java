package com.bluemoon.controllers;

import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.NoticeSlip;
import com.bluemoon.models.Permission;
import com.bluemoon.services.AccessDeniedException;
import com.bluemoon.services.AuthorizationService;
import com.bluemoon.services.BillingService;
import com.bluemoon.services.NoticeFormatter;
import com.bluemoon.services.NoticeService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * US-011 screen: view the fee notice of each household and export printable HTML (per notice or the whole period).
 * It shows stored US-007 invoices only (NoticeService), never recomputes money, and sends nothing to residents.
 * Service and file writes run off the FX thread; after dispose no callback touches the screen.
 */
public final class NoticeController {
    /** Chooses the file to write (FX thread). Production opens a FileChooser; tests inject a path. */
    @FunctionalInterface public interface FileSelector { Optional<Path> choose(String suggestedName); }

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String DENIED = "Bạn không có quyền xem thông báo thu phí.";
    static final String SYSTEM = "Không thực hiện được. Vui lòng thử lại sau.";
    static final String BANK_REQUIRED = "Vui lòng nhập thông tin tài khoản nhận (ngân hàng, số tài khoản, chủ tài khoản)!";

    private final NoticeService notices;
    private final BillingService billing;
    private final AuthorizationService authorization;
    private final Clock clock;
    private final Executor executor;
    private final boolean ownsExecutor;
    private final FileSelector selector;
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private final Set<Task<?>> tasks = new HashSet<>();
    private final List<NoticeSlip> slips = new ArrayList<>();
    private boolean disposed, updatingList;
    private long loadRequest;
    private int pending;

    @FXML private TextField periodField;
    @FXML private ComboBox<String> periodBox;
    @FXML private Button loadButton, viewButton, exportOneButton, exportAllButton;
    @FXML private ListView<String> apartmentList;
    @FXML private TextArea bankField, previewArea;
    @FXML private Label periodError, bankError, messageLabel;
    @FXML private ProgressIndicator loadingIndicator;

    public NoticeController(NoticeService notices, BillingService billing, AuthorizationService authorization,
                            Clock clock, Supplier<Window> owner) {
        this(notices, billing, authorization, clock, defaultExecutor(), true, name -> choose(owner, name));
    }

    /** Test seam. */
    NoticeController(NoticeService notices, BillingService billing, AuthorizationService authorization, Clock clock,
                     Executor executor, boolean ownsExecutor, FileSelector selector) {
        this.notices = Objects.requireNonNull(notices);
        this.billing = Objects.requireNonNull(billing);
        this.authorization = Objects.requireNonNull(authorization);
        this.clock = Objects.requireNonNull(clock);
        this.executor = Objects.requireNonNull(executor);
        this.ownsExecutor = ownsExecutor;
        this.selector = Objects.requireNonNull(selector);
    }

    public static Parent load(NoticeController controller) {
        var loader = new FXMLLoader(NoticeController.class.getResource("/com/bluemoon/views/NoticeView.fxml"));
        loader.setControllerFactory(type -> controller);
        try { return loader.load(); }
        catch (IOException failure) { controller.dispose(); throw new UncheckedIOException(failure); }
    }

    public ReadOnlyBooleanProperty busyProperty() { return busy.getReadOnlyProperty(); }

    @FXML private void initialize() {
        periodField.setText(YearMonth.from(clock.instant().atZone(BUSINESS_ZONE)).toString());
        apartmentList.setPlaceholder(new Label("Tải hóa đơn của một kỳ đã lập."));
        apartmentList.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> { if (!updatingList) { render(); } });
        bankField.textProperty().addListener((obs, old, value) -> { bankError.setText(""); render(); });
        periodBox.valueProperty().addListener((obs, old, value) -> { if (value != null) { periodField.setText(value); } });
        loadButton.setOnAction(event -> load());
        viewButton.setOnAction(event -> render());
        exportOneButton.setOnAction(event -> export(false));
        exportAllButton.setOnAction(event -> export(true));
        updateControls();
        loadPeriods();
    }

    private boolean canView() { return authorization.canAccess(Permission.DEBT_REPORT_VIEW); }

    private void loadPeriods() {
        if (disposed) { return; }
        if (!canView()) { deny(); return; }
        run(billing::listPeriods, result -> {
            if (!result.isSuccess()) { return; }
            periodBox.getItems().setAll(result.data().orElseThrow().stream().map(BillingPeriod::kyThu).toList());
        }, failure -> failed(failure));
    }

    private void load() {
        if (disposed || busy.get()) { return; }
        if (!canView()) { deny(); return; }
        periodError.setText(""); message("");
        String period = periodField.getText() == null ? "" : periodField.getText().trim();
        long request = ++loadRequest;
        slips.clear(); updatingList = true; apartmentList.getItems().clear(); updatingList = false;
        previewArea.clear(); updateControls();
        run(() -> notices.getNotices(period), result -> {
            if (request != loadRequest) { return; }
            if (!result.isSuccess()) {
                message(result.message());
                String error = result.fieldErrors().get("ky_thu");
                if (error != null) { periodError.setText(error); }
                updateControls();
                return;
            }
            slips.addAll(result.data().orElseThrow());
            updatingList = true;
            apartmentList.getItems().setAll(slips.stream().map(NoticeSlip::soCanHo).toList());
            updatingList = false;
            message(slips.size() + " thông báo của kỳ " + period + ".");
            if (!slips.isEmpty()) { apartmentList.getSelectionModel().select(0); }
            updateControls();
        }, failure -> { failed(failure); updateControls(); });
    }

    private Optional<NoticeSlip> selected() {
        String apartment = apartmentList.getSelectionModel().getSelectedItem();
        return slips.stream().filter(s -> s.soCanHo().equals(apartment)).findFirst();
    }

    /** Pure presentation of the stored snapshot. No I/O, so it runs on the FX thread. */
    private void render() {
        if (disposed) { return; }
        previewArea.setText(selected().map(s -> NoticeFormatter.text(s, bankField.getText())).orElse(""));
        updateControls();
    }

    private void export(boolean all) {
        if (disposed || busy.get()) { return; }
        if (!canView()) { deny(); return; }
        if (bankField.getText() == null || bankField.getText().isBlank()) { bankError.setText(BANK_REQUIRED); return; }
        List<NoticeSlip> chosen = all ? List.copyOf(slips) : selected().map(List::of).orElse(List.of());
        if (chosen.isEmpty()) { message("Chưa có thông báo để xuất."); return; }
        String name = all ? "thong-bao-" + chosen.get(0).kyThu() + ".html"
                : "thong-bao-" + chosen.get(0).maThongBao() + ".html";
        Optional<Path> path = selector.choose(name);
        if (path.isEmpty()) { message("Đã hủy xuất."); return; }
        String html = NoticeFormatter.html(chosen, bankField.getText());
        run(() -> { Files.writeString(path.get(), html, StandardCharsets.UTF_8); return chosen.size(); },
                count -> message("Đã xuất " + count + " thông báo ra " + path.get()),
                failure -> message("Không ghi được tệp. Vui lòng chọn vị trí khác."));
    }

    private void updateControls() {
        boolean view = canView();
        boolean has = !slips.isEmpty();
        boolean locked = busy.get();
        periodField.setDisable(locked || !view); periodBox.setDisable(locked || !view);
        loadButton.setDisable(locked || !view);
        viewButton.setDisable(locked || !view || selected().isEmpty());
        exportOneButton.setDisable(locked || !view || selected().isEmpty());
        exportAllButton.setDisable(locked || !view || !has);
        bankField.setDisable(!view);
    }

    private void message(String text) { messageLabel.setText(text); }
    private void loading(boolean value) { loadingIndicator.setVisible(value); loadingIndicator.setManaged(value); }

    private void deny() {
        ++loadRequest;
        slips.clear(); updatingList = true; apartmentList.getItems().clear(); periodBox.getItems().clear(); updatingList = false;
        previewArea.clear(); updateControls(); message(DENIED);
    }

    private void failed(Throwable failure) {
        if (failure instanceof AccessDeniedException) { deny(); } else { message(SYSTEM); }
    }

    private <T> void run(Callable<T> call, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return call.call(); } };
        tasks.add(task); pending++; busy.set(true); loading(true); updateControls();
        task.setOnSucceeded(event -> {
            finish(task);
            if (disposed) { return; }
            if (!canView()) { deny(); return; }
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> { finish(task); if (!disposed) { failure.accept(task.getException()); } });
        task.setOnCancelled(event -> finish(task));
        try { executor.execute(task); }
        catch (RejectedExecutionException rejected) { finish(task); if (!disposed) { failure.accept(rejected); } }
    }

    private void finish(Task<?> task) {
        if (tasks.remove(task)) {
            pending--; busy.set(pending > 0); loading(pending > 0);
            if (!disposed) { updateControls(); }
        }
    }

    /** FX thread only, idempotent. */
    public void dispose() {
        if (!Platform.isFxApplicationThread()) { throw new IllegalStateException("Notice lifecycle requires FX thread"); }
        if (disposed) { return; }
        disposed = true; ++loadRequest;
        for (Task<?> task : List.copyOf(tasks)) { task.cancel(true); }
        if (ownsExecutor && executor instanceof ExecutorService pool) { pool.shutdown(); }
    }

    private static Executor defaultExecutor() {
        return Executors.newSingleThreadExecutor(job -> {
            var thread = new Thread(job, "notice-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static Optional<Path> choose(Supplier<Window> owner, String suggestedName) {
        var chooser = new FileChooser();
        chooser.setTitle("Xuất thông báo thu phí");
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Trang HTML (in được)", "*.html"));
        var file = chooser.showSaveDialog(owner == null ? null : owner.get());
        return Optional.ofNullable(file).map(java.io.File::toPath);
    }
}
