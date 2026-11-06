package com.bluemoon.controllers;

import com.bluemoon.models.*;
import com.bluemoon.services.HouseholdService;
import com.bluemoon.views.HouseholdNavigator;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.function.Function;

public final class HouseholdListController extends HouseholdController {
    @FXML private TableView<HouseholdProfile> households;
    @FXML private TableColumn<HouseholdProfile, Object> apartmentColumn, householdColumn, floorColumn,
            areaColumn, dateColumn, statusColumn;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button reloadButton, createButton, detailButton;
    private final String notice;
    public HouseholdListController(HouseholdService service, HouseholdNavigator navigator, String notice) {
        super(service, navigator); this.notice = notice;
    }
    @FXML private void initialize() {
        column(apartmentColumn, HouseholdProfile::soCanHo);
        column(householdColumn, HouseholdProfile::maHo);
        column(floorColumn, HouseholdProfile::tang);
        column(areaColumn, p -> p.dienTich().toPlainString());
        column(dateColumn, HouseholdProfile::ngayNhanNha);
        column(statusColumn, HouseholdProfile::trangThai);
        households.setPlaceholder(new Label("Chưa có hồ sơ hộ/căn hộ."));
        statusFilter.getItems().add("Tất cả");
        for (ApartmentStatus status : ApartmentStatus.values()) statusFilter.getItems().add(status.label());
        statusFilter.getSelectionModel().selectFirst();
        households.getSelectionModel().selectedItemProperty().addListener((o, old, value) ->
                detailButton.setDisable(busy || value == null));
        reload();
    }
    private void column(TableColumn<HouseholdProfile, Object> column, Function<HouseholdProfile, Object> value) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
    }
    @FXML private void reload() {
        if (busy) return;
        int index = statusFilter.getSelectionModel().getSelectedIndex();
        ApartmentStatus filter = index <= 0 ? null : ApartmentStatus.values()[index - 1];
        clearData();
        execute(() -> service.listHouseholds(filter), result -> {
            if (result.isSuccess()) {
                households.getItems().setAll(result.data().orElseThrow());
                message.setText(notice != null ? notice : households.getItems().isEmpty()
                        ? "Chưa có hồ sơ hộ/căn hộ." : "Đã tải " + households.getItems().size() + " hồ sơ.");
            }
        });
    }
    @FXML private void create() { if (!busy) navigator.showCreate(); }
    @FXML private void details() {
        HouseholdProfile selected = households.getSelectionModel().getSelectedItem();
        if (!busy && selected != null) navigator.showDetail(selected.maHo());
    }
    @Override protected void busyChanged(boolean value) {
        reloadButton.setDisable(value); createButton.setDisable(value); statusFilter.setDisable(value);
        detailButton.setDisable(value || households.getSelectionModel().getSelectedItem() == null);
    }
    @Override protected void clearData() { households.getItems().clear(); }
}
