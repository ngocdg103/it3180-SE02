package com.bluemoon;

import com.bluemoon.config.*;
import com.bluemoon.dao.AccountDAO;
import com.bluemoon.services.AuthService;
import com.bluemoon.views.AuthenticationNavigator;
import com.bluemoon.views.ProductionModules;
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
        Task<Startup> setup = new Task<>() {
            @Override protected Startup call() throws Exception {
                var connections = new DatabaseConnection(DatabaseConfig.load());
                return new Startup(connections, new AuthService(new AccountDAO(connections)));
            }
        };
        setup.setOnSucceeded(event -> {
            if (!stage.isShowing()) return;
            var ready = setup.getValue();
            new AuthenticationNavigator(stage, ready.auth(), ProductionModules.create(ready.connections())).showLogin();
        });
        setup.setOnFailed(event -> { if (stage.isShowing()) AuthenticationNavigator.showUnavailable(stage); });
        stage.setOnHidden(event -> setup.cancel(true));
        Thread worker = new Thread(setup, "bluemoon-auth-setup");
        worker.setDaemon(true);
        worker.start();
    }
    private record Startup(DatabaseConnection connections, AuthService auth) { }
    public static void main(String[] args) { launch(args); }
}
