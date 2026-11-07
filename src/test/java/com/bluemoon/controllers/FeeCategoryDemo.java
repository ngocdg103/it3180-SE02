package com.bluemoon.controllers;

import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.FeeKind;
import com.bluemoon.models.FeeStatus;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * DEMO ONLY, FAKE DATA. Shows the US-005 screen against FakeFeeTypeService (in memory, no database,
 * no login, no validation). Nothing saved here is real, and this is not evidence that AC-01/02 pass.
 * Run it from the IDE (main class FeeCategoryDemo, test classpath with JavaFX modules).
 */
public final class FeeCategoryDemo extends Application {
    @Override public void start(Stage stage) {
        FakeFeeTypeService fake = new FakeFeeTypeService();
        fake.stored.add(FakeFeeTypeService.fee("PHI_QL", "Phí quản lý chung cư", FeeKind.BAT_BUOC,
                ChargeMethod.THEO_DIEN_TICH, "0", FeeStatus.DANG_AP_DUNG));
        fake.stored.add(FakeFeeTypeService.fee("QUY_NGHEO", "Quỹ vì người nghèo", FeeKind.DONG_GOP,
                ChargeMethod.TU_NGUYEN, "0", FeeStatus.NGUNG_AP_DUNG));
        Label banner = new Label("DEMO: dữ liệu giả, không dùng database, không phải nghiệp vụ thật");
        banner.setStyle("-fx-background-color:#fff3cd;-fx-padding:6;-fx-font-weight:bold;");
        BorderPane root = new BorderPane(FeeCategoryController.load(fake));
        root.setTop(banner);
        stage.setTitle("BlueMoon US-005 (DEMO, fake service)");
        stage.setScene(new Scene(root));
        stage.show();
    }

    public static void main(String[] args) { launch(args); }
}
