package com.bluemoon.controllers;

import com.bluemoon.models.Account;
import com.bluemoon.services.*;
import com.bluemoon.views.*;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

public final class DashboardController implements AutoCloseable {
    private final AuthenticationService auth;
    private final Account account;
    private final Runnable onLogout;
    private final AuthorizationService authorization;
    private final List<ModuleRegistration> modules;
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private DashboardModule current;
    private String currentId;
    private boolean opening, closed;
    private final ChangeListener<Boolean> busyListener = (o, old, value) -> refreshBusy();
    @FXML private Label shellStatus;
    @FXML private VBox moduleMenu;
    @FXML private StackPane moduleContent;
    @FXML private Button homeButton, logoutButton;
    @FXML private Label greeting;
    @FXML private Label role;

    public DashboardController(AuthenticationService auth, Account account, Runnable onLogout) {
        this(auth, account, onLogout, new RoleBasedAuthorizationService(null), List.of());
    }
    public DashboardController(AuthenticationService auth, Account account, Runnable onLogout,
                               AuthorizationService authorization, List<ModuleRegistration> modules) {
        this.auth = Objects.requireNonNull(auth); this.account = Objects.requireNonNull(account);
        this.onLogout = Objects.requireNonNull(onLogout); this.authorization = Objects.requireNonNull(authorization);
        this.modules = List.copyOf(modules);
    }
    @FXML private void initialize() {
        greeting.setText("Xin chào: " + account.hoTen());
        role.setText("Vai trò: " + account.role().name());
        for (var registration : modules) {
            Button button = new Button(registration.title());
            button.setId(registration.id() + "Button"); button.setMaxWidth(Double.MAX_VALUE);
            boolean allowed = authorization.canAccess(registration.permission());
            button.setVisible(allowed); button.setManaged(allowed);
            button.setOnAction(e -> openModule(registration.id()));
            buttons.put(registration.id(), button); moduleMenu.getChildren().add(button);
        }
        showHome();
    }
    private boolean authenticated() { return auth.currentUser().filter(account::equals).isPresent(); }
    public boolean isBusy() { return opening || current != null && current.busyProperty().get(); }
    /** Rechecks session and permission even when invoked without a visible menu button. */
    public void openModule(String id) {
        if (closed || isBusy()) return;
        if (!authenticated()) { logout(); return; }
        var registration = modules.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
        if (registration == null) { shellStatus.setText("Module chưa khả dụng."); return; }
        try { authorization.requirePermission(registration.permission()); }
        catch (AccessDeniedException denied) { shellStatus.setText("Bạn không có quyền mở module này."); return; }
        if (id.equals(currentId)) return;
        opening = true; refreshBusy(); shellStatus.setText("Đang mở " + registration.title() + "…");
        try {
            releaseModule();
            current = Objects.requireNonNull(registration.open().get()); currentId = id;
            moduleContent.getChildren().setAll(current.view());
            current.busyProperty().addListener(busyListener);
            shellStatus.setText("");
        } catch (RuntimeException failure) {
            releaseModule();
            moduleContent.getChildren().setAll(new Label("Không mở được module. Vui lòng thử lại."));
            shellStatus.setText("Không mở được module. Vui lòng thử lại.");
        } finally { opening = false; refreshBusy(); }
    }
    @FXML public void showHome() {
        if (closed || isBusy()) return;
        if (!authenticated()) { logout(); return; }
        releaseModule();
        VBox home = new VBox(16, new Label("Tổng quan BlueMoon"),
            new Label("Chọn chức năng trong menu để bắt đầu."),
            new Label("Các chức năng khả dụng được hiển thị trong menu theo quyền của bạn."));
        home.getStyleClass().add("card");
        for (var node : home.getChildren()) { ((Label) node).setWrapText(true); }
        moduleContent.getChildren().setAll(home); shellStatus.setText(""); refreshBusy();
    }
    @FXML private void logout() {
        if (closed || isBusy()) return;
        close(); auth.logout(); onLogout.run();
    }
    private void refreshBusy() {
        boolean busy = isBusy();
        homeButton.setDisable(busy); logoutButton.setDisable(busy);
        buttons.forEach((id, button) -> button.setDisable(busy));
        if (!opening && busy) shellStatus.setText("Đang xử lý… Vui lòng chờ trước khi chuyển trang hoặc đăng xuất.");
        else if (!opening && shellStatus.getText().startsWith("Đang xử lý…")) shellStatus.setText("");
    }
    private void releaseModule() {
        if (current != null) {
            current.busyProperty().removeListener(busyListener);
            current.close(); current = null; currentId = null;
        }
        moduleContent.getChildren().clear();
    }
    @Override public void close() {
        if (closed) return;
        closed = true; releaseModule();
    }
}
