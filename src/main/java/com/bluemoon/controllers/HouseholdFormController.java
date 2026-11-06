package com.bluemoon.controllers;

import com.bluemoon.models.*;
import com.bluemoon.services.*;
import com.bluemoon.views.HouseholdNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import java.time.LocalDate;
import java.util.Map;

public final class HouseholdFormController extends HouseholdController {
    private final HouseholdProfile original;
    @FXML private Label title;
    @FXML private TextField soCanHo, maHo, tang, dienTich;
    @FXML private DatePicker ngayNhanNha;
    @FXML private ComboBox<ApartmentStatus> trangThai;
    @FXML private Label soCanHoError, maHoError, tangError, dienTichError, ngayNhanNhaError, trangThaiError;
    @FXML private GridPane fields;
    @FXML private Button saveButton, cancelButton;
    private Map<String, Label> errors;
    public HouseholdFormController(HouseholdService service, HouseholdNavigator navigator, HouseholdProfile original) {
        super(service, navigator); this.original = original;
    }
    @FXML private void initialize() {
        errors = Map.of("so_can_ho", soCanHoError, "ma_ho", maHoError, "tang", tangError,
                "dien_tich", dienTichError, "ngay_nhan_nha", ngayNhanNhaError, "trang_thai", trangThaiError);
        trangThai.getItems().setAll(ApartmentStatus.values());
        ngayNhanNha.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate date) { return date == null ? "" : date.toString(); }
            @Override public LocalDate fromString(String text) {
                try { return LocalDate.parse(text); } catch (RuntimeException invalid) { return null; }
            }
        });
        title.setText(original == null ? "Tạo hồ sơ hộ/căn hộ" : "Cập nhật hồ sơ hộ/căn hộ");
        soCanHo.setEditable(original == null); maHo.setEditable(original == null);
        if (original != null) {
            soCanHo.setText(original.soCanHo()); maHo.setText(original.maHo());
            tang.setText(Integer.toString(original.tang())); dienTich.setText(original.dienTich().toPlainString());
            ngayNhanNha.setValue(original.ngayNhanNha()); trangThai.setValue(original.trangThai());
        }
    }
    @FXML private void save() {
        if (busy) return;
        errors.values().forEach(label -> label.setText(""));
        // Keep the editor text, including malformed dates, for backend validation.
        String date = ngayNhanNha.getEditor().getText();
        if (original == null) {
            HouseholdForm form = new HouseholdForm(soCanHo.getText(), tang.getText(), dienTich.getText(),
                    maHo.getText(), date, trangThai.getValue());
            execute(() -> service.createHousehold(form), result -> handle(result, null, false));
        } else {
            HouseholdUpdateForm form = new HouseholdUpdateForm(tang.getText(), dienTich.getText(), date, trangThai.getValue());
            update(form, false);
        }
    }
    private void update(HouseholdUpdateForm form, boolean confirmed) {
        execute(() -> service.updateHousehold(original.maHo(), form, confirmed),
                result -> handle(result, form, confirmed));
    }
    private void handle(HouseholdResult<HouseholdProfile> result, HouseholdUpdateForm payload, boolean confirmed) {
        result.fieldErrors().forEach((key, value) -> { if (errors.containsKey(key)) errors.get(key).setText(value); });
        if (result.isSuccess()) { navigator.showList(result.message()); return; }
        if (result.status() == HouseholdResult.Status.DUPLICATE) {
            alert(Alert.AlertType.WARNING, result.message()).show();
        } else if (result.status() == HouseholdResult.Status.CONFIRMATION_REQUIRED && payload != null && !confirmed) {
            Alert confirmation = alert(Alert.AlertType.CONFIRMATION, result.message());
            confirmation.setOnHidden(event -> {
                if (confirmation.getResult() == ButtonType.OK) update(payload, true);
            });
            confirmation.show();
        }
    }
    private Alert alert(Alert.AlertType type, String text) {
        Alert alert = new Alert(type, text);
        alert.initOwner(saveButton.getScene().getWindow());
        alert.setHeaderText(null); alert.setTitle("BlueMoon");
        navigator.trackDialog(alert);
        return alert;
    }
    @FXML private void cancel() {
        if (!busy) { if (original == null) navigator.showList(); else navigator.showDetail(original.maHo()); }
    }
    @Override protected void busyChanged(boolean value) {
        fields.setDisable(value); saveButton.setDisable(value); cancelButton.setDisable(value);
    }
    @Override protected void clearData() {
        soCanHo.clear(); maHo.clear(); tang.clear(); dienTich.clear();
        ngayNhanNha.setValue(null); ngayNhanNha.getEditor().clear(); trangThai.setValue(null);
        if (errors != null) errors.values().forEach(label -> label.setText(""));
    }
}
