package com.bluemoon.controllers;

import com.bluemoon.models.MonthlyCharge;
import com.bluemoon.models.Permission;
import com.bluemoon.services.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Read-only snapshots and committed invoice payment fields. No inference or JDBC on the FX thread. */
final class ApartmentHistoryPanel implements AutoCloseable {
    private final BillingService service;
    private final String apartment;
    private final Consumer<Boolean> busy;
    private final AuthorizationService authorization = new RoleBasedAuthorizationService(null);
    private final VBox root = new VBox(10);
    private final TableView<MonthlyCharge> table = new TableView<>();
    private final Label status = new Label();
    private final Button retry = new Button("Tải lại lịch sử");
    private Task<BillingResult<List<MonthlyCharge>>> running;
    private boolean closed;

    ApartmentHistoryPanel(BillingService service, String apartment, Consumer<Boolean> busy) {
        this.service=Objects.requireNonNull(service); this.apartment=Objects.requireNonNull(apartment);
        this.busy=Objects.requireNonNull(busy);
        table.setId("apartmentHistory"); table.setPrefHeight(220);
        table.setPlaceholder(new Label("Chưa có hóa đơn cho căn hộ này."));
        column("Kỳ thu", MonthlyCharge::kyThu);
        column("Mã hóa đơn", MonthlyCharge::maHoaDon);
        column("Tổng tiền (VNĐ)", row -> row.tongTien().toPlainString());
        column("Hạn nộp", row -> row.hanNop().toString());
        column("Diện tích lúc lập kỳ", row -> row.dienTich().toPlainString());
        column("Nhân khẩu lúc lập kỳ", row -> Integer.toString(row.soNhanKhau()));
        column("Đã thanh toán (VNĐ)", row -> row.daThanhToan().toPlainString());
        column("Còn thiếu (VNĐ)", row -> row.conThieu().toPlainString());
        column("Trạng thái thanh toán", row -> switch(row.trangThai()) {
            case CHUA_DONG -> "Chưa đóng";
            case DONG_THIEU -> "Đóng thiếu";
            case DA_DONG_DU -> "Đã đóng đủ";
            case DONG_THUA -> "Đóng thừa";
        });
        status.setId("historyStatus"); status.setWrapText(true); retry.setId("historyRetry");
        retry.setOnAction(event -> load());
        root.getChildren().addAll(new Label("Lịch sử hóa đơn theo căn hộ (snapshot)"), table,
                new Label("Thanh toán đọc từ dữ liệu invoice đã commit bởi Payment; không tính lại hóa đơn."), status, retry);
        load();
    }
    private void column(String title, Function<MonthlyCharge,String> value) {
        var column=new TableColumn<MonthlyCharge,String>(title);
        column.setCellValueFactory(row -> new ReadOnlyStringWrapper(value.apply(row.getValue())));
        column.setPrefWidth(150); table.getColumns().add(column);
    }
    Parent view() { return root; }
    private boolean allowed() { return authorization.canAccess(Permission.DEBT_REPORT_VIEW); }
    private void deny() { table.getItems().clear(); status.setText("Bạn không có quyền xem lịch sử hóa đơn."); }
    private void load() {
        if (closed || running!=null) return;
        if (!allowed()) { deny(); return; }
        table.getItems().clear(); status.setText("Đang tải lịch sử…"); retry.setDisable(true); busy.accept(true);
        var task=new Task<BillingResult<List<MonthlyCharge>>>() {
            @Override protected BillingResult<List<MonthlyCharge>> call() { return service.getApartmentHistory(apartment); }
        };
        running=task;
        task.setOnSucceeded(event -> {
            if (closed) return;
            finish();
            if (!allowed()) { deny(); return; }
            var result=task.getValue();
            if (result!=null && result.isSuccess()) {
                table.getItems().setAll(result.data().orElseThrow());
                status.setText(table.getItems().isEmpty() ? "Chưa có hóa đơn cho căn hộ này." : "Hóa đơn mới nhất trước; giữ nguyên dữ liệu lúc lập kỳ.");
            } else status.setText("Không tải được lịch sử hóa đơn. Vui lòng thử lại.");
        });
        task.setOnFailed(event -> { if (!closed) { finish(); if (!allowed()) deny();
            else status.setText("Không tải được lịch sử hóa đơn. Vui lòng thử lại."); } });
        var worker=new Thread(task,"bluemoon-apartment-history"); worker.setDaemon(true); worker.start();
    }
    private void finish() { running=null; retry.setDisable(false); busy.accept(false); }
    @Override public void close() {
        if (closed) return;
        closed=true; if (running!=null) running.cancel(true); running=null;
        table.getItems().clear(); status.setText(""); retry.setOnAction(null); busy.accept(false);
    }
}
