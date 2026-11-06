package com.bluemoon.views;

import com.bluemoon.controllers.*;
import com.bluemoon.models.Account;
import com.bluemoon.services.AuthenticationService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Owns scene replacement; no dashboard history is retained after logout. */
public final class AuthenticationNavigator {
    private final Stage stage;
    private final AuthenticationService auth;
    public AuthenticationNavigator(Stage stage, AuthenticationService auth) {
        this.stage = stage;
        this.auth = auth;
    }
    public static void showUnavailable(Stage stage) {
        new AuthenticationNavigator(stage, null).show("LoginView.fxml", LoginController.unavailable());
    }
    public void showLogin() {
        show("LoginView.fxml", new LoginController(auth, this::showDashboard));
    }
    private void showDashboard(Account account) {
        if (auth.currentUser().filter(account::equals).isEmpty()) { showLogin(); return; }
        show("DashboardView.fxml", new DashboardController(auth, account, this::showLogin));
    }
    private void show(String name, Object controller) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/bluemoon/views/" + name));
            loader.setControllerFactory(type -> controller);
            Parent root = loader.load();
            stage.setScene(new Scene(root, 720, 600));
        } catch (IOException failure) { throw new UncheckedIOException("Cannot load authentication view", failure); }
    }
}
