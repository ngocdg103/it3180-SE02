package com.bluemoon.controllers;

import com.bluemoon.models.HouseholdProfile;
import com.bluemoon.services.HouseholdService;
import com.bluemoon.views.HouseholdNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class HouseholdDetailController extends HouseholdController {
    private final String maHo;
    private HouseholdProfile profile;
    @FXML private Label soCanHo, householdCode, tang, dienTich, ngayNhanNha, trangThai;
    @FXML private Button editButton, backButton;
    public HouseholdDetailController(HouseholdService service, HouseholdNavigator navigator, String maHo) {
        super(service, navigator); this.maHo = maHo;
    }
    @FXML private void initialize() {
        clearData();
        execute(() -> service.getHouseholdById(maHo), result -> {
            if (result.isSuccess()) {
                profile = result.data().orElseThrow();
                soCanHo.setText(profile.soCanHo()); householdCode.setText(profile.maHo());
                tang.setText(Integer.toString(profile.tang())); dienTich.setText(profile.dienTich().toPlainString());
                ngayNhanNha.setText(profile.ngayNhanNha().toString()); trangThai.setText(profile.trangThai().label());
                message.setText(""); editButton.setDisable(!navigator.can(com.bluemoon.models.Permission.HOUSEHOLD_UPDATE));
            }
        });
    }
    @FXML private void edit() { if (!busy && profile != null && navigator.can(com.bluemoon.models.Permission.HOUSEHOLD_UPDATE)) navigator.showEdit(profile); }
    @FXML private void back() { if (!busy) navigator.showList(); }
    @Override protected void busyChanged(boolean value) {
        backButton.setDisable(value); editButton.setDisable(value || profile == null || !navigator.can(com.bluemoon.models.Permission.HOUSEHOLD_UPDATE));
    }
    @Override protected void clearData() {
        profile = null;
        for (Label label : new Label[]{soCanHo, householdCode, tang, dienTich, ngayNhanNha, trangThai}) label.setText("");
        editButton.setDisable(true);
    }
}
