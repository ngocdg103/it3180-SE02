package com.bluemoon.controllers;

import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;
import java.util.function.*;

/** Search UI hosted inside Household; service is the only data-access boundary. */
public final class ResidentSearchController implements AutoCloseable {
    private final ResidentSearchService service;
    private final Consumer<Boolean> onBusy;
    private final Consumer<String> onDetail;
    private final Runnable onBack;
    private final VBox root=new VBox(12);
    private final ComboBox<ResidentSearchCriteria.Field> field=new ComboBox<>();
    private final TextField keyword=new TextField();
    private final TableView<ResidentSearchHit> table=new TableView<>();
    private final Button search=new Button("Tìm kiếm"),detail=new Button("Xem chi tiết hộ"),back=new Button("Quay lại danh sách hộ");
    private final Label message=new Label("Nhập tiêu chí và từ khóa để tra cứu.");
    private boolean busy,closed;
    private Task<?> task;
    public ResidentSearchController(ResidentSearchService service,Consumer<Boolean> onBusy,Consumer<String> onDetail,Runnable onBack) {
        this.service=service;this.onBusy=onBusy;this.onDetail=onDetail;this.onBack=onBack;
        root.getStyleClass().add("household-page");root.getStylesheets().add(getClass().getResource("/com/bluemoon/views/household.css").toExternalForm());
        field.getItems().setAll(ResidentSearchCriteria.Field.values());field.setValue(ResidentSearchCriteria.Field.APARTMENT);
        field.setId("searchCriterion");keyword.setId("searchKeyword");search.setId("residentSearchButton");table.setId("residentSearchResults");message.setId("residentSearchMessage");detail.setId("searchDetailButton");back.setId("searchBackButton");
        column("Căn hộ",ResidentSearchHit::soCanHo);column("Chủ hộ",ResidentSearchHit::tenChuHo);column("Cư dân",h -> h.resident()==null?"—":h.resident().hoTen());
        column("CCCD",h -> h.resident()==null?"—":h.resident().soCccd());column("Tầng",ResidentSearchHit::tang);column("Diện tích",ResidentSearchHit::dienTich);
        table.setPrefHeight(400);table.setPlaceholder(new Label("Chưa có kết quả."));message.setWrapText(true);
        search.setOnAction(e -> search());keyword.setOnAction(e -> search());back.setOnAction(e -> {if(!busy && !closed)onBack.run();});
        detail.setOnAction(e -> {var hit=table.getSelectionModel().getSelectedItem();if(!busy && !closed && hit!=null)onDetail.accept(hit.maHo());});
        table.getSelectionModel().selectedItemProperty().addListener((o,old,value) -> controls());
        root.getChildren().addAll(new Label("Tra cứu cư dân"),new HBox(10,field,keyword,search),message,table,new HBox(10,detail,back));controls();
        VBox.setVgrow(table,Priority.ALWAYS);
    }
    private void column(String title,Function<ResidentSearchHit,Object> value) {
        var c=new TableColumn<ResidentSearchHit,Object>(title);c.setPrefWidth(150);c.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(value.apply(row.getValue())));table.getColumns().add(c);
    }
    public Parent view(){return root;}
    private void controls(){boolean blocked=busy || closed;search.setDisable(blocked);keyword.setDisable(blocked);field.setDisable(blocked);back.setDisable(blocked);detail.setDisable(blocked || table.getSelectionModel().getSelectedItem()==null);}
    private void search() {
        if(busy || closed)return;
        var criterion=field.getValue();String text=keyword.getText();busy=true;controls();onBusy.accept(true);table.getItems().clear();message.setText("Đang tìm kiếm…");
        Task<ResidentResult<List<ResidentSearchHit>>> next=new Task<>() {protected ResidentResult<List<ResidentSearchHit>> call(){return service.search(criterion,text);}};task=next;
        next.setOnSucceeded(e -> {if(closed)return;busy=false;onBusy.accept(false);controls();var result=next.getValue();if(result==null){failure();return;}message.setText(result.message());if(result.isSuccess())table.getItems().setAll(result.data().orElseThrow());});
        next.setOnFailed(e -> {if(!closed){busy=false;onBusy.accept(false);controls();failure();}});
        Thread worker=new Thread(next,"resident-search-worker");worker.setDaemon(true);worker.start();
    }
    private void failure(){message.setText("Không tra cứu được. Vui lòng kiểm tra quyền hoặc thử lại sau.");table.getItems().clear();}
    @Override public void close(){closed=true;if(task!=null)task.cancel(true);table.getItems().clear();controls();onBusy.accept(false);}
}
