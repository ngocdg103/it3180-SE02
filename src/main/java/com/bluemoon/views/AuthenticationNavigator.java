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

/** Replaces the view root while preserving the window scene and user-selected geometry. */
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
            Scene scene = stage.getScene();
            if (scene == null) {
                stage.setScene(new Scene(root, 720, 600));
            } else {
                // Replacing the Scene with fixed dimensions shrinks maximized/custom windows.
                // Each FXML root owns its stylesheet; no scene CSS or handlers are appended.
                scene.setRoot(root);
            }
        } catch (IOException failure) { throw new UncheckedIOException("Cannot load authentication view", failure); }
    }
}
