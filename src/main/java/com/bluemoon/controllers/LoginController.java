package com.bluemoon.controllers;

import com.bluemoon.models.Account;
import com.bluemoon.services.*;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.Objects;
import java.util.function.Consumer;

public final class LoginController {
    public static final String MISSING = "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!";
    public static final String SYSTEM = "Không thể đăng nhập do lỗi hệ thống. Vui lòng thử lại sau!";
    private final AuthenticationService auth;
    private final Consumer<Account> onSuccess;
    private boolean busy;
    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private Button loginButton;
    @FXML private Label status;

    public LoginController(AuthenticationService auth, Consumer<Account> onSuccess) {
        this.auth = Objects.requireNonNull(auth);
        this.onSuccess = Objects.requireNonNull(onSuccess);
    }

    private LoginController() { auth = null; onSuccess = null; }

    /** Configuration failure: show the form without enabling authentication. */
    public static LoginController unavailable() { return new LoginController(); }

    @FXML private void initialize() {
        if (auth == null) { setBusy(true); status.setText(SYSTEM); }
    }

    @FXML private void login() {
        if (busy) return;
        String user = username.getText();
        String secret = password.getText();
        if (user.isBlank() || secret.isBlank()) {
            status.setText(MISSING);
            return;
        }
        setBusy(true);
        status.setText("Đang đăng nhập…");
        Task<AuthResult> task = new Task<>() {
            @Override protected AuthResult call() { return auth.login(user, secret); }
        };
        task.setOnSucceeded(event -> {
            password.clear();
            setBusy(false);
            AuthResult result = task.getValue();
            if (result == null) { status.setText(SYSTEM); return; }
            switch (result.status()) {
                case SUCCESS -> {
                    status.setText("");
                    onSuccess.accept(result.account().orElseThrow());
                }
                case INVALID_CREDENTIALS -> {
                    String message = "Tài khoản hoặc mật khẩu không chính xác!";
                    status.setText(message);
                    Alert warning = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
                    warning.initOwner(loginButton.getScene().getWindow());
                    warning.initModality(javafx.stage.Modality.WINDOW_MODAL);
                    warning.setTitle("Cảnh báo đăng nhập");
                    warning.setHeaderText(null);
                    warning.show();
                }
                case ACCOUNT_LOCKED -> status.setText("Tài khoản của bạn đã bị tạm khóa!");
                case SYSTEM_ERROR -> status.setText(SYSTEM);
                case VALIDATION_ERROR -> status.setText(result.message());
            }
        });
        task.setOnFailed(event -> {
            password.clear();
            setBusy(false);
            status.setText(SYSTEM);
        });
        Thread worker = new Thread(task, "bluemoon-login");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean value) {
        busy = value;
        loginButton.setDisable(value);
        username.setDisable(value);
        password.setDisable(value);
    }
}
