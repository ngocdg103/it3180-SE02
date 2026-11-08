package com.bluemoon.controllers;

import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.*;

/** US-002 UI only. The existing service owns validation, uniqueness, Q17 and head replacement. */
public final class ResidentPanelController implements AutoCloseable {
    private final ResidentService service;
    private final String maHo;
    private final Consumer<Boolean> busyChanged;
    private final AuthorizationService authorization = new RoleBasedAuthorizationService(null);
    private final VBox root = new VBox(10);
    private final TableView<Resident> table = new TableView<>();
    private final Label status = new Label(), count = new Label();
    private final Button add = new Button("Thêm nhân khẩu"), reload = new Button("Tải lại nhân khẩu");
    private Dialog<ButtonType> editor;
    private Alert confirmation;
    private Task<?> task;
    private boolean busy, closed;
    private final Map<String,Label> errors = new LinkedHashMap<>();
    private final Map<String,TextField> fields = new LinkedHashMap<>();
    private ComboBox<Gender> gender;
    private ComboBox<RelationToHead> relation;
    private GridPane form;
    private Label formMessage;
    private Button save;

    public ResidentPanelController(ResidentService service, String maHo, Consumer<Boolean> busyChanged) {
        this.service=Objects.requireNonNull(service); this.maHo=Objects.requireNonNull(maHo);
        this.busyChanged=Objects.requireNonNull(busyChanged);
        table.setId("residentTable"); table.setPrefHeight(220); table.setPlaceholder(new Label("Chưa có nhân khẩu."));
        column("Họ tên",Resident::hoTen); column("Ngày sinh",Resident::ngaySinh);
        column("Giới tính",Resident::gioiTinh); column("CCCD",Resident::soCccd);
        column("Quan hệ",Resident::quanHeChuHo); column("Chuyển đến",Resident::ngayChuyenDen);
        status.setId("residentStatus"); status.setWrapText(true); count.setId("residentCount");
        add.setId("addResidentButton"); reload.setId("reloadResidentsButton");
        add.setOnAction(e -> showCreate()); reload.setOnAction(e -> reload());
        root.getChildren().addAll(new HBox(12,count,reload,add),table,status);
        refreshBusy(); reload();
    }
    private void column(String title, Function<Resident,Object> value) {
        var column=new TableColumn<Resident,Object>(title); column.setPrefWidth(150);
        column.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(value.apply(row.getValue()))); table.getColumns().add(column);
    }
    public Parent view() { return root; }
    private boolean allowed(Permission p) { return authorization.canAccess(p); }
    private void refreshBusy() {
        boolean modal=editor!=null && editor.isShowing();
        add.setDisable(busy || modal || !allowed(Permission.RESIDENT_PROFILE_CREATE));
        reload.setDisable(busy || modal || !allowed(Permission.RESIDENT_PROFILE_VIEW));
        if(form!=null) form.setDisable(busy);
        if(save!=null) save.setDisable(busy || !allowed(Permission.RESIDENT_PROFILE_CREATE));
        if(editor!=null) editor.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(busy);
        busyChanged.accept(!closed && (busy || modal));
    }
    private void reload() {
        if(closed || busy) return;
        run(() -> service.getHousehold(maHo), result -> {
            status.setText(result.message()); table.getItems().clear();
            if(result.isSuccess()) {
                var members=result.data().orElseThrow(); table.getItems().setAll(members.members());
                count.setText("Nhân khẩu: "+members.memberCount());
            } else count.setText("Nhân khẩu: chưa tải được");
        });
    }
    private void showCreate() {
        if(closed || busy || editor!=null || !allowed(Permission.RESIDENT_PROFILE_CREATE)) return;
        editor=new Dialog<>(); editor.setTitle("Thêm nhân khẩu — "+maHo);
        editor.initOwner(root.getScene().getWindow()); editor.setResizable(true);
        form=new GridPane(); form.setHgap(12); form.setVgap(8); fields.clear(); errors.clear();
        textRow("ho_ten","Họ tên",0); textRow("ngay_sinh","Ngày sinh (YYYY-MM-DD)",2);
        gender=new ComboBox<>(); gender.getItems().setAll(Gender.values()); gender.setId("residentGender");
        choiceRow("gioi_tinh","Giới tính",gender,4);
        textRow("so_cccd","CCCD/CMND",6);
        relation=new ComboBox<>(); relation.getItems().setAll(RelationToHead.values()); relation.setId("residentRelation");
        choiceRow("quan_he_chu_ho","Quan hệ với chủ hộ",relation,8);
        textRow("ngay_chuyen_den","Ngày chuyển đến (YYYY-MM-DD)",10);
        formMessage=new Label(); formMessage.setId("residentFormMessage"); formMessage.setWrapText(true);
        Label householdError=new Label(); householdError.setId("residentError_ma_ho"); errors.put("ma_ho",householdError);
        editor.getDialogPane().setContent(new VBox(10,form,householdError,formMessage));
        ButtonType saveType=new ButtonType("Lưu nhân khẩu",ButtonBar.ButtonData.OK_DONE);
        editor.getDialogPane().getButtonTypes().addAll(saveType,ButtonType.CANCEL);
        save=(Button)editor.getDialogPane().lookupButton(saveType); save.setId("saveResidentButton");
        save.addEventFilter(javafx.event.ActionEvent.ACTION,e -> { e.consume(); if(!busy) create(readForm(),null); });
        editor.setOnCloseRequest(e -> { if(busy) e.consume(); });
        editor.setOnHidden(e -> { editor=null; form=null; save=null; fields.clear(); errors.clear(); refreshBusy(); });
        editor.show(); refreshBusy();
    }
    private void textRow(String key,String title,int row) {
        TextField input=new TextField(); input.setId("resident_"+key); fields.put(key,input);
        choiceRow(key,title,input,row);
    }
    private void choiceRow(String key,String title,javafx.scene.Node input,int row) {
        Label error=new Label(); error.setId("residentError_"+key); error.setWrapText(true);
        error.setStyle("-fx-text-fill: #a12b35;"); errors.put(key,error);
        form.add(new Label(title),0,row); form.add(input,1,row); form.add(error,1,row+1);
    }
    private ResidentForm readForm() {
        return new ResidentForm(maHo,fields.get("ho_ten").getText(),fields.get("ngay_sinh").getText(),gender.getValue(),
            fields.get("so_cccd").getText(),relation.getValue(),fields.get("ngay_chuyen_den").getText());
    }
    private void create(ResidentForm payload,Long expectedHead) {
        if(!allowed(Permission.RESIDENT_PROFILE_CREATE)) { formMessage.setText("Bạn không có quyền thêm nhân khẩu."); return; }
        errors.values().forEach(label -> label.setText(""));
        run(() -> service.createForHousehold(payload,expectedHead), result -> {
            formMessage.setText(result.message());
            result.fieldErrors().forEach((key,message) -> { if(errors.containsKey(key)) errors.get(key).setText(message); });
            if(result.isSuccess()) { editor.close(); reload(); }
            else if(result.status()==ResidentResult.Status.CONFIRMATION_REQUIRED) {
                confirmation=new Alert(Alert.AlertType.CONFIRMATION,result.message(),ButtonType.OK,ButtonType.CANCEL);
                confirmation.initOwner(editor.getDialogPane().getScene().getWindow()); confirmation.setHeaderText(null);
                confirmation.setOnHidden(e -> {
                    var answer=confirmation.getResult(); confirmation=null;
                    if(!closed && editor!=null && answer==ButtonType.OK) {
                        // A stale result replaces, never reuses, the expected head from the previous attempt.
                        create(payload,result.currentHead().map(Resident::maNhanKhau).orElse(null));
                    }
                }); confirmation.show();
            }
        });
    }
    private <T> void run(Supplier<ResidentResult<T>> operation,Consumer<ResidentResult<T>> completed) {
        if(closed || busy) return;
        busy=true; status.setText("Đang tải/xử lý nhân khẩu…"); refreshBusy();
        Task<ResidentResult<T>> next=new Task<>() { protected ResidentResult<T> call() { return operation.get(); } };
        task=next;
        next.setOnSucceeded(e -> {
            if(closed) return;
            busy=false; refreshBusy();
            if(next.getValue()==null) failure(); else completed.accept(next.getValue());
        });
        next.setOnFailed(e -> { if(!closed) { busy=false; refreshBusy(); failure(); } });
        Thread worker=new Thread(next,"resident-ui-worker"); worker.setDaemon(true); worker.start();
    }
    private void failure() {
        String message="Không thực hiện được. Vui lòng thử lại hoặc kiểm tra quyền truy cập.";
        status.setText(message); if(formMessage!=null) formMessage.setText(message);
        if(!allowed(Permission.RESIDENT_PROFILE_VIEW)) table.getItems().clear();
    }
    @Override public void close() {
        if(closed) return; closed=true; busy=false;
        if(task!=null) task.cancel(true);
        if(confirmation!=null) { confirmation.setOnHidden(null); confirmation.close(); confirmation=null; }
        if(editor!=null) { editor.setOnCloseRequest(null); editor.close(); editor=null; }
        table.getItems().clear(); busyChanged.accept(false);
    }
}
