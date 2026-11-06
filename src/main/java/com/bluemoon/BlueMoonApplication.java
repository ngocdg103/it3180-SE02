package com.bluemoon;

import com.bluemoon.config.*;
import com.bluemoon.dao.AccountDAO;
import com.bluemoon.services.AuthService;
import com.bluemoon.views.AuthenticationNavigator;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class BlueMoonApplication extends Application {
    @Override public void start(Stage stage) {
        Label status = new Label("Đang khởi động BlueMoon…");
        stage.setTitle("BlueMoon");
        stage.setMinWidth(560);
        stage.setMinHeight(600);
        stage.setScene(new Scene(new StackPane(status), 720, 600));
        stage.show();
        // Configuration and AuthService's BCrypt initialization run away from the FX thread.
        Task<AuthService> setup = new Task<>() {
            @Override protected AuthService call() throws Exception {
                return new AuthService(new AccountDAO(new DatabaseConnection(DatabaseConfig.load())));
            }
        };
        setup.setOnSucceeded(event -> new AuthenticationNavigator(stage, setup.getValue()).showLogin());
        setup.setOnFailed(event -> AuthenticationNavigator.showUnavailable(stage));
        Thread worker = new Thread(setup, "bluemoon-auth-setup");
        worker.setDaemon(true);
        worker.start();
    }
    public static void main(String[] args) { launch(args); }
}
