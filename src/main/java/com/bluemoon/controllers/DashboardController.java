package com.bluemoon.controllers;

import com.bluemoon.models.Account;
import com.bluemoon.services.*;
import com.bluemoon.views.*;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    /** Feather icons (Ikonli, bundled in the jar) and one-line descriptions; presentation only. */
    private static final Map<String, Feather> ICONS = Map.of("home", Feather.HOME, "households", Feather.USERS,
        "feeCategories", Feather.LIST, "feeRates", Feather.TAG, "billing", Feather.CALENDAR, "notices", Feather.MAIL,
        "receipts", Feather.CREDIT_CARD, "debt", Feather.BAR_CHART_2);
    private static final Map<String, String> DESCRIPTIONS = Map.of(
        "households", "Hồ sơ căn hộ, hộ gia đình và nhân khẩu",
        "feeCategories", "Khoản thu bắt buộc và tự nguyện",
        "feeRates", "Đơn giá áp dụng theo ngày hiệu lực",
        "billing", "Lập kỳ thu và tính phí tự động",
        "notices", "Xem và xuất thông báo thu phí",
        "receipts", "Ghi nhận nộp tiền, lập phiếu thu",
        "debt", "Công nợ và tỷ lệ thu theo kỳ");
    private final LocalTime sessionStart = LocalTime.now();
    @FXML private Label shellStatus;
    @FXML private VBox moduleMenu;
    @FXML private StackPane moduleContent;
    @FXML private Button homeButton, logoutButton;
    @FXML private Label greeting;
    @FXML private Label role;
    @FXML private Label pageTitle, avatar;
    @FXML private VBox sidebar;

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
        avatar.setText(initials(account.hoTen()));
        if (sidebar.getParent() instanceof Region shell) { // narrow window: keep topbar readable
            sidebar.prefWidthProperty().bind(javafx.beans.binding.Bindings.when(shell.widthProperty().lessThan(960))
                .then(220.0).otherwise(272.0));
        }
        homeButton.setGraphic(icon("home"));
        FontIcon exit = new FontIcon(Feather.LOG_OUT); exit.getStyleClass().add("bm-icon"); logoutButton.setGraphic(exit);
        for (var registration : modules) {
            Button button = new Button(registration.title());
            button.setId(registration.id() + "Button"); button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("bm-nav-item"); button.setGraphic(icon(registration.id()));
            button.setWrapText(true);
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
            markActive(id, registration.title());
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
        VBox home = buildHome();
        moduleContent.getChildren().setAll(home); shellStatus.setText(""); refreshBusy();
        markActive("home", "Tổng quan");
    }
    /** Home shows session/permission facts only; business KPIs need a read-only service first. */
    private VBox buildHome() {
        var allowed = modules.stream().filter(m -> authorization.canAccess(m.permission())).toList();
        Label title = new Label("Tổng quan"); title.getStyleClass().add("bm-title");
        Label subtitle = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            + " · Chào mừng trở lại, " + account.hoTen());
        subtitle.getStyleClass().add("bm-muted"); subtitle.setWrapText(true);
        TilePane kpis = responsiveTiles(200);
        kpis.getChildren().addAll(
            kpi("CHỨC NĂNG KHẢ DỤNG", allowed.size() + "/" + modules.size(), "Theo quyền của vai trò hiện tại", false),
            kpi("VAI TRÒ", roleName(), "Phân quyền tập trung (RBAC)", true),
            kpi("KỲ THU HIỆN TẠI", LocalDate.now().format(DateTimeFormatter.ofPattern("MM/yyyy")), "Theo lịch hệ thống", false),
            kpi("PHIÊN LÀM VIỆC", sessionStart.format(DateTimeFormatter.ofPattern("HH:mm")), "Thời điểm đăng nhập", true));
        Label section = new Label("Truy cập nhanh"); section.getStyleClass().add("bm-h2");
        VBox home = new VBox(title, subtitle, kpis, section);
        if (allowed.isEmpty()) {
            Label empty = new Label("Tài khoản của bạn chưa được cấp chức năng quản lý nào. Vui lòng liên hệ Ban quản lý.");
            empty.setWrapText(true); empty.getStyleClass().add("bm-muted");
            VBox card = new VBox(empty); card.getStyleClass().add("bm-card");
            home.getChildren().add(card);
        } else {
            TilePane tiles = responsiveTiles(290);
            for (var registration : allowed) tiles.getChildren().add(tile(registration));
            home.getChildren().add(tiles);
        }
        home.getStyleClass().addAll("bm-home", "bm-dark");
        VBox.setMargin(subtitle, new Insets(-14, 0, 0, 0));
        return home;
    }
    private VBox kpi(String caption, String value, String note, boolean teal) {
        Region accent = new Region(); accent.getStyleClass().add("bm-kpi-accent"); if (teal) accent.getStyleClass().add("teal");
        Label c = new Label(caption); c.getStyleClass().add("bm-caption");
        Label v = new Label(value); v.getStyleClass().add("bm-kpi-value");
        Label n = new Label(note); n.getStyleClass().add("bm-muted"); n.setStyle("-fx-font-size: 12px;");
        VBox card = new VBox(accent, c, v, n); card.getStyleClass().addAll("bm-card", "bm-kpi");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }
    /** Equal-width columns that fill the row and wrap below minTile (laptop, projector, small window). */
    private static TilePane responsiveTiles(double minTile) {
        TilePane pane = new TilePane(16, 16);
        pane.setPrefColumns(1); pane.setTileAlignment(Pos.TOP_LEFT);
        pane.widthProperty().addListener((o, old, width) -> {
            double gap = pane.getHgap(), w = width.doubleValue();
            int columns = Math.max(1, Math.min(pane.getChildren().size(), (int) ((w + gap) / (minTile + gap))));
            pane.setPrefTileWidth(Math.floor((w - gap * (columns - 1)) / columns));
        });
        return pane;
    }
    private Button tile(ModuleRegistration registration) {
        Label name = new Label(registration.title()); name.getStyleClass().add("bm-tile-title");
        Label desc = new Label(DESCRIPTIONS.getOrDefault(registration.id(), "")); desc.getStyleClass().add("bm-tile-desc");
        StackPane chip = new StackPane(icon(registration.id())); chip.getStyleClass().add("bm-icon-chip");
        HBox content = new HBox(14, chip, new VBox(2, name, desc)); content.setAlignment(Pos.CENTER_LEFT);
        Button tile = new Button(null, content); tile.getStyleClass().add("bm-tile");
        tile.setMaxWidth(Double.MAX_VALUE); tile.setOnAction(e -> openModule(registration.id()));
        return tile;
    }
    private static FontIcon icon(String id) {
        FontIcon glyph = new FontIcon(ICONS.getOrDefault(id, Feather.GRID)); glyph.getStyleClass().add("bm-icon");
        return glyph;
    }
    private static String initials(String name) {
        String[] parts = name == null ? new String[0] : name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        String first = parts[0].substring(0, 1), last = parts[parts.length - 1].substring(0, 1);
        return (parts.length == 1 ? first : first + last).toUpperCase(Locale.ROOT);
    }
    private String roleName() {
        return switch (account.role()) {
            case ADMIN -> "Quản trị viên";
            case ACCOUNTANT -> "Kế toán";
            case RESIDENT -> "Cư dân";
        };
    }
    private void markActive(String id, String title) {
        homeButton.pseudoClassStateChanged(SELECTED, "home".equals(id));
        buttons.forEach((key, button) -> button.pseudoClassStateChanged(SELECTED, key.equals(id)));
        pageTitle.setText(title);
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
