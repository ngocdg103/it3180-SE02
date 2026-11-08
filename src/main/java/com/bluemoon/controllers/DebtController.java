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
import java.util.*;
import java.util.concurrent.*;

/** Read-only financial report, latest request wins; no JDBC or locally cached debt totals. */
public final class DebtController {
    enum StatusChoice {
        ALL("Tất cả",Set.of()), UNPAID("Chưa đóng",Set.of(ChargeStatus.CHUA_DONG)), PARTIAL("Đóng thiếu",Set.of(ChargeStatus.DONG_THIEU)),
        PAID("Đóng đủ",Set.of(ChargeStatus.DA_DONG_DU)), OVERPAID("Đóng thừa",Set.of(ChargeStatus.DONG_THUA)),
        COMPLETED("Đã xong",Set.of(ChargeStatus.DA_DONG_DU,ChargeStatus.DONG_THUA)),
        OUTSTANDING("Chưa đóng hoặc Đóng thiếu",Set.of(ChargeStatus.CHUA_DONG,ChargeStatus.DONG_THIEU));
        final String label; final Set<ChargeStatus> states;
        StatusChoice(String label,Set<ChargeStatus> states) { this.label=label; this.states=states; }
        @Override public String toString() { return label; }
    }
    private final DebtService service;
    private final AuthorizationService authorization;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(job -> { var t=new Thread(job,"debt-report-worker"); t.setDaemon(true); return t; });
    private final ReadOnlyBooleanWrapper busy=new ReadOnlyBooleanWrapper();
    private int pending;
    private long request;
    private boolean disposed;
    @FXML private TextField periodField,fromField,toField;
    @FXML private ComboBox<StatusChoice> statusBox;
    @FXML private Button filterButton;
    @FXML private Label billedLabel,paidLabel,debtLabel,ratioLabel,completedLabel,footerLabel,messageLabel,periodLabel;
    @FXML private TableView<DebtRow> debtTable;
    @FXML private TableColumn<DebtRow,String> apartmentColumn,ownerColumn,areaColumn,peopleColumn,billedColumn,paidColumn,debtColumn,statusColumn,lastColumn;
    public DebtController(DebtService service,AuthorizationService authorization) { this.service=Objects.requireNonNull(service); this.authorization=Objects.requireNonNull(authorization); }
    public static Parent load(DebtController controller) {
        var loader=new FXMLLoader(DebtController.class.getResource("/com/bluemoon/views/DebtView.fxml")); loader.setControllerFactory(type -> controller);
        try { return loader.load(); } catch(IOException failure) { controller.dispose(); throw new UncheckedIOException(failure); }
    }
    @FXML private void initialize() {
        apartmentColumn.setCellValueFactory(r -> text(r.getValue().soCanHo())); ownerColumn.setCellValueFactory(r -> text(Objects.toString(r.getValue().tenChuHo(),"Chưa có chủ hộ")));
        areaColumn.setCellValueFactory(r -> text(r.getValue().dienTich().toPlainString())); peopleColumn.setCellValueFactory(r -> text(Integer.toString(r.getValue().soNguoi())));
        billedColumn.setCellValueFactory(r -> text(r.getValue().tongTien().toPlainString())); paidColumn.setCellValueFactory(r -> text(r.getValue().daThu().toPlainString()));
        debtColumn.setCellValueFactory(r -> text(r.getValue().conThieu().toPlainString())); lastColumn.setCellValueFactory(r -> text(Objects.toString(r.getValue().lanNopCuoi(),"—")));
        statusColumn.setCellValueFactory(r -> text(switch(r.getValue().trangThai()) { case CHUA_DONG -> "Chưa đóng"; case DONG_THIEU -> "Đóng thiếu"; case DA_DONG_DU -> "Đóng đủ"; case DONG_THUA -> "Đóng thừa"; }));
        debtTable.setPlaceholder(new Label("Không có dữ liệu phù hợp.")); statusBox.getItems().setAll(StatusChoice.values()); statusBox.setValue(StatusChoice.ALL);
        filterButton.setOnAction(e -> reload()); reload();
    }
    private static SimpleStringProperty text(String value) { return new SimpleStringProperty(value); }
    private boolean allowed() { return authorization.canAccess(Permission.DEBT_REPORT_VIEW); }
    private void clear() {
        debtTable.getItems().clear(); for(var label:List.of(billedLabel,paidLabel,debtLabel,ratioLabel,completedLabel,footerLabel,periodLabel)) { label.setText("—"); }
    }
    private void deny() { clear(); filterButton.setDisable(true); for(var control:List.of(periodField,fromField,toField,statusBox)) { control.setDisable(true); } messageLabel.setText("Bạn không có quyền xem công nợ."); }
    private void reload() {
        if(disposed) { return; } if(!allowed()) { deny(); return; }
        long token=++request; clear();
        Integer from,to;
        try { from=number(fromField.getText()); to=number(toField.getText()); }
        catch(NumberFormatException invalid) { messageLabel.setText("Khoảng tầng phải là số nguyên hợp lệ."); return; }
        var choice=statusBox.getValue()==null?StatusChoice.ALL:statusBox.getValue();
        var query=new DebtQuery(periodField.getText(),choice.states,from,to); messageLabel.setText("Đang tải...");
        Task<DebtResult<DebtReport>> task=new Task<>() { @Override protected DebtResult<DebtReport> call() { return service.getDebtByPeriod(query); } };
        pending++; busy.set(true);
        task.setOnSucceeded(e -> {
            finish(); if(disposed||token!=request) { return; } if(!allowed()) { deny(); return; }
            var result=task.getValue();
            if(!result.isSuccess()) { clear(); messageLabel.setText(result.message()+" "+String.join(" ",result.fieldErrors().values())); return; }
            var report=result.data().orElseThrow(); debtTable.getItems().setAll(report.rows());
            if(periodField.getText().isBlank()) { periodField.setText(report.kyThu()); }
            periodLabel.setText("Đang hiển thị kỳ "+report.kyThu()); billedLabel.setText(report.tongPhaiThu().toPlainString()); paidLabel.setText(report.tongDaThu().toPlainString());
            debtLabel.setText(report.tongNo().toPlainString()); ratioLabel.setText(report.tyLeThu().toPlainString()+"%"); completedLabel.setText(report.hoHoanThanh()+" / "+report.tongSoHo());
            footerLabel.setText("Tổng nợ trong danh sách: "+report.filteredDebt().toPlainString()+" VNĐ"); messageLabel.setText(result.message().equals("OK")?"":result.message());
        });
        task.setOnFailed(e -> { finish(); if(!disposed&&token==request) { if(!allowed()||task.getException() instanceof AccessDeniedException) { deny(); } else { clear(); messageLabel.setText("Không tải được công nợ. Vui lòng thử lại sau."); } } });
        try { worker.execute(task); } catch(RejectedExecutionException failure) { finish(); if(!disposed) { messageLabel.setText("Không gửi được yêu cầu tải dữ liệu."); } }
    }
    private static Integer number(String text) { return text==null||text.isBlank()?null:Integer.valueOf(text.trim()); }
    private void finish() { pending--; busy.set(pending>0); }
    public ReadOnlyBooleanProperty busyProperty() { return busy.getReadOnlyProperty(); }
    public void dispose() { if(!Platform.isFxApplicationThread()) { throw new IllegalStateException("FX thread required"); } if(disposed) { return; } disposed=true; ++request; worker.shutdown(); }
}
