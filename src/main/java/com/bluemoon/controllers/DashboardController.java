package com.bluemoon.controllers;

import com.bluemoon.models.Account;
import com.bluemoon.services.*;
import com.bluemoon.views.*;
import javafx.animation.FadeTransition;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
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

    // ---- Presentation only (no business data): icons, groups, copy for the monthly fee workflow ----
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final boolean REDUCE_MOTION = Boolean.getBoolean("bluemoon.reduceMotion");
    private static final Map<String, Feather> ICONS = Map.of("home", Feather.HOME, "households", Feather.USERS,
        "feeCategories", Feather.LIST, "feeRates", Feather.TAG, "billing", Feather.CALENDAR, "notices", Feather.MAIL,
        "receipts", Feather.CREDIT_CARD, "debt", Feather.BAR_CHART_2);
    private static final Map<String, String> GROUPS = Map.of("households", "Cư dân",
        "feeCategories", "Thu phí", "feeRates", "Thu phí", "billing", "Thu phí", "notices", "Thu phí", "receipts", "Thu phí",
        "debt", "Báo cáo");
    /** The monthly fee cycle, in the order the work is actually done. */
    private static final List<String[]> FEE_CYCLE = List.of(
        new String[]{"feeCategories", "Danh mục khoản thu", "Khai báo khoản phí bắt buộc và tự nguyện"},
        new String[]{"feeRates", "Đơn giá", "Cập nhật đơn giá theo ngày hiệu lực"},
        new String[]{"billing", "Lập kỳ thu", "Tính phí từng căn hộ và chốt hóa đơn"},
        new String[]{"notices", "Thông báo thu phí", "Xuất thông báo gửi từng hộ"},
        new String[]{"receipts", "Ghi nhận thanh toán", "Thu tiền và lập phiếu thu"},
        new String[]{"debt", "Công nợ", "Theo dõi hộ còn nợ và tỷ lệ thu"});
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
        greeting.setText(account.hoTen());
        role.setText(roleName());
        avatar.setText(initials(account.hoTen()));
        if (sidebar.getParent() instanceof Region shell) { // narrow window: keep topbar readable
            sidebar.prefWidthProperty().bind(javafx.beans.binding.Bindings.when(shell.widthProperty().lessThan(960))
                .then(220.0).otherwise(272.0));
        }
        // Status bar takes space only while it has something to say.
        shellStatus.managedProperty().bind(shellStatus.textProperty().isNotEmpty());
        shellStatus.visibleProperty().bind(shellStatus.managedProperty());
        homeButton.setGraphic(icon("home"));
        FontIcon exit = new FontIcon(Feather.LOG_OUT); exit.getStyleClass().add("bm-icon"); logoutButton.setGraphic(exit);
        Map<String, Label> groupHeaders = new HashMap<>();
        for (var registration : modules) {
            String group = GROUPS.get(registration.id());
            boolean allowed = authorization.canAccess(registration.permission());
            if (group != null && !groupHeaders.containsKey(group)) {
                Label header = new Label(group); header.getStyleClass().add("bm-nav-group");
                header.setVisible(false); header.setManaged(false);
                groupHeaders.put(group, header); moduleMenu.getChildren().add(header);
            }
            if (group != null && allowed) { groupHeaders.get(group).setVisible(true); groupHeaders.get(group).setManaged(true); }
            Button button = new Button(registration.title());
            button.setId(registration.id() + "Button"); button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("bm-nav-item"); button.setGraphic(icon(registration.id()));
            button.setWrapText(true);
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
            show(current.view());
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
        show(buildHome()); shellStatus.setText(""); refreshBusy();
        markActive("home", "Tổng quan");
    }
    /** One short fade answers the user's navigation; off with -Dbluemoon.reduceMotion=true. */
    private void show(Node view) {
        moduleContent.getChildren().setAll(view);
        if (REDUCE_MOTION) return;
        FadeTransition fade = new FadeTransition(Duration.millis(140), view);
        fade.setFromValue(0.0); fade.setToValue(1.0); fade.play();
    }

    /** Home: who/when context, then the fee cycle as numbered steps. Session facts only, no business KPIs. */
    private VBox buildHome() {
        var allowed = modules.stream().filter(m -> authorization.canAccess(m.permission())).toList();
        Label hello = new Label(salutation() + ", " + account.hoTen()); hello.getStyleClass().add("bm-display");
        hello.setWrapText(true);
        Label date = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, 'ngày' d 'tháng' M 'năm' yyyy",
            new Locale("vi", "VN")))); // e.g. "Thứ Tư, ngày 25 tháng 11 năm 2026"
        date.getStyleClass().add("bm-muted");
        FlowPane context = new FlowPane(8, 8,
            chip(roleName()), chip("Kỳ thu " + LocalDate.now().format(DateTimeFormatter.ofPattern("MM/yyyy"))),
            chip("Đăng nhập lúc " + sessionStart.format(DateTimeFormatter.ofPattern("HH:mm"))),
            chip(allowed.size() + "/" + modules.size() + " chức năng được cấp"));
        VBox header = new VBox(4, hello, date);
        VBox home = new VBox(header, context);
        home.getStyleClass().addAll("bm-home", "bm-ui");

        if (allowed.isEmpty()) {
            Label empty = new Label("Tài khoản này chưa được cấp chức năng quản lý nào. Liên hệ Ban quản lý để được phân quyền.");
            empty.setWrapText(true); empty.getStyleClass().add("bm-muted");
            VBox card = new VBox(empty); card.getStyleClass().add("bm-card");
            home.getChildren().add(card);
            return home;
        }
        var byId = new HashMap<String, ModuleRegistration>();
        modules.forEach(m -> byId.put(m.id(), m));
        if (FEE_CYCLE.stream().anyMatch(s -> byId.containsKey(s[0]))) {
            TilePane steps = responsiveTiles(150, 0);
            int number = 1;
            for (String[] step : FEE_CYCLE) {
                var registration = byId.get(step[0]);
                if (registration == null) continue;
                steps.getChildren().add(step(number++, step[1], step[2], registration,
                    authorization.canAccess(registration.permission()), number > countPresent(byId)));
            }
            home.getChildren().add(section("Quy trình thu phí tháng",
                "Làm lần lượt từ bước 1 đến bước " + (number - 1) + " cho mỗi kỳ thu.", steps));
        }
        var others = allowed.stream().filter(m -> FEE_CYCLE.stream().noneMatch(s -> s[0].equals(m.id()))).toList();
        if (!others.isEmpty()) {
            TilePane tiles = responsiveTiles(290, 16);
            others.forEach(m -> tiles.getChildren().add(tile(m)));
            boolean onlyResidents = others.stream().allMatch(m -> "households".equals(m.id()));
            home.getChildren().add(section(onlyResidents ? "Hồ sơ cư dân" : "Chức năng khác", null, tiles));
        }
        return home;
    }
    private static long countPresent(Map<String, ModuleRegistration> byId) {
        return FEE_CYCLE.stream().filter(s -> byId.containsKey(s[0])).count();
    }
    private static VBox section(String title, String note, Node body) {
        Label heading = new Label(title); heading.getStyleClass().add("bm-h2");
        VBox box = new VBox(4, heading);
        if (note != null) { Label n = new Label(note); n.getStyleClass().add("bm-muted"); box.getChildren().add(n); }
        VBox.setMargin(body, new javafx.geometry.Insets(10, 0, 0, 0));
        box.getChildren().add(body);
        return box;
    }
    private static Label chip(String text) {
        Label chip = new Label(text); chip.getStyleClass().add("bm-chip");
        return chip;
    }
    /** A numbered step: number on a track line (hidden on the last step), then name and what it is for. */
    private Button step(int number, String name, String purpose, ModuleRegistration registration, boolean allowed, boolean last) {
        Label num = new Label(String.valueOf(number)); num.getStyleClass().add("bm-step-num");
        num.setMinSize(30, 30); num.setPrefSize(30, 30); num.setAlignment(Pos.CENTER);
        Region track = new Region(); track.getStyleClass().add("bm-step-track"); track.setVisible(!last);
        HBox.setHgrow(track, Priority.ALWAYS);
        HBox top = new HBox(10, num, track); top.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(name); title.getStyleClass().add("bm-step-title"); title.setWrapText(true);
        Label desc = new Label(allowed ? purpose : "Ngoài phạm vi quyền của bạn");
        desc.getStyleClass().add("bm-step-desc"); desc.setWrapText(true);
        VBox content = new VBox(8, top, title, desc);
        Button button = new Button(null, content); button.getStyleClass().add("bm-step");
        button.setMaxWidth(Double.MAX_VALUE); button.setMaxHeight(Double.MAX_VALUE);
        button.setAccessibleText("Bước " + number + ": " + name + ". " + desc.getText());
        button.setDisable(!allowed);
        button.setOnAction(e -> openModule(registration.id()));
        return button;
    }
    /** Equal-width columns that fill the row and wrap below minTile (laptop, projector, small window). */
    private static TilePane responsiveTiles(double minTile, double gap) {
        TilePane pane = new TilePane(gap, 16);
        pane.setPrefColumns(1); pane.setTileAlignment(Pos.TOP_LEFT);
        pane.widthProperty().addListener((o, old, width) -> {
            double w = width.doubleValue();
            int columns = Math.max(1, Math.min(pane.getChildren().size(), (int) ((w + gap) / (minTile + gap))));
            double tileWidth = Math.floor((w - gap * (columns - 1)) / columns);
            pane.setPrefTileWidth(tileWidth);
            pane.setPrefColumns(columns); // keeps pref height to the real row count
            // Wrapped text needs the real width to report its height; otherwise tiles grow too tall.
            pane.setPrefTileHeight(pane.getChildren().stream().mapToDouble(c -> c.prefHeight(tileWidth)).max().orElse(0));
        });
        return pane;
    }
    private Button tile(ModuleRegistration registration) {
        Label name = new Label(registration.title()); name.getStyleClass().add("bm-tile-title");
        String about = "households".equals(registration.id()) ? "Căn hộ, hộ gia đình và nhân khẩu" : "";
        Label desc = new Label(about); desc.getStyleClass().add("bm-tile-desc");
        desc.setVisible(!about.isEmpty()); desc.setManaged(!about.isEmpty());
        StackPane chip = new StackPane(icon(registration.id())); chip.getStyleClass().add("bm-icon-chip");
        HBox content = new HBox(14, chip, new VBox(2, name, desc)); content.setAlignment(Pos.CENTER_LEFT);
        Button tile = new Button(null, content); tile.getStyleClass().add("bm-tile");
        tile.setMaxWidth(Double.MAX_VALUE); tile.setOnAction(e -> openModule(registration.id()));
        tile.setAccessibleText(about.isEmpty() ? registration.title() : registration.title() + ". " + about);
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
    private String salutation() {
        int hour = LocalTime.now().getHour();
        return hour < 11 ? "Chào buổi sáng" : hour < 14 ? "Chào buổi trưa" : hour < 18 ? "Chào buổi chiều" : "Chào buổi tối";
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
