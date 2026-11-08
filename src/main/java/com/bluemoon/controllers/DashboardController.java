package com.bluemoon.controllers;

import com.bluemoon.models.Account;
import com.bluemoon.services.AuthenticationService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class DashboardController {
    private final AuthenticationService auth;
    private final Account account;
    private final Runnable onLogout;
    @FXML private Label greeting;
    @FXML private Label role;

    public DashboardController(AuthenticationService auth, Account account, Runnable onLogout) {
        this.auth = auth;
        this.account = account;
        this.onLogout = onLogout;
    }
    @FXML private void initialize() {
        greeting.setText("Xin chào: " + account.hoTen());
        role.setText("Vai trò: " + account.role().name());
    }
    @FXML private void logout() {
        auth.logout();
        onLogout.run();
    }
}
