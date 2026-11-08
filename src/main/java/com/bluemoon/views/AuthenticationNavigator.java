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
import java.util.List;
import com.bluemoon.services.RoleBasedAuthorizationService;

/** Replaces the view root while preserving the window scene and user-selected geometry. */
public final class AuthenticationNavigator {
    private final Stage stage;
    private final AuthenticationService auth;
    private final List<ModuleRegistration> modules;
    private DashboardController dashboard;
    private LoginController login;
    public AuthenticationNavigator(Stage stage, AuthenticationService auth) {
        this(stage, auth, List.of());
    }
    public AuthenticationNavigator(Stage stage, AuthenticationService auth, List<ModuleRegistration> modules) {
        this.stage = stage;
        this.auth = auth;
        this.modules = List.copyOf(modules);
        stage.setOnCloseRequest(event -> {
            if (dashboard != null && dashboard.isBusy() || login != null && login.isBusy()) { event.consume(); return; }
            if (dashboard != null) { dashboard.close(); dashboard = null; }
            if (auth != null) auth.logout();
        });
        stage.setOnHidden(event -> {
            if (dashboard != null) { dashboard.close(); dashboard = null; }
            boolean authenticating = login != null && login.isBusy();
            if (login != null) { login.dispose(); login = null; }
            if (auth != null && !authenticating) auth.logout();
        });
    }
    public static void showUnavailable(Stage stage) {
        new AuthenticationNavigator(stage, null).show("LoginView.fxml", LoginController.unavailable());
    }
    public void showLogin() {
        if (dashboard != null && dashboard.isBusy()) return;
        if (dashboard != null) { dashboard.close(); dashboard = null; }
        if (login != null) login.dispose();
        login = new LoginController(auth, this::showDashboard);
        show("LoginView.fxml", login);
    }
    private void showDashboard(Account account) {
        if (auth.currentUser().filter(account::equals).isEmpty()) { showLogin(); return; }
        if (login != null) { login.dispose(); login = null; }
        dashboard = new DashboardController(auth, account, this::showLogin,
            new RoleBasedAuthorizationService(null), modules);
        show("DashboardView.fxml", dashboard);
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
