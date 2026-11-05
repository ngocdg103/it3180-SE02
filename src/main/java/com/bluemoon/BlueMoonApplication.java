package com.bluemoon;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Objects;

/** Application shell. Dev-2 owns the login view and controller. */
public final class BlueMoonApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        var resource = Objects.requireNonNull(
                getClass().getResource("/com/bluemoon/views/FoundationView.fxml"),
                "Missing FoundationView.fxml");
        stage.setTitle("BlueMoon");
        stage.setScene(new Scene(FXMLLoader.load(resource), 640, 360));
        stage.show();
    }

    public static void main(String[] args) { launch(args); }
}
