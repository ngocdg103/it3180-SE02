package com.bluemoon.controllers;

import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.config.DatabaseConfig;
import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.dao.BillingDAO;
import com.bluemoon.dao.ResidentDAO;
import com.bluemoon.models.MonthlyCharge;
import com.bluemoon.models.Permission;
import com.bluemoon.models.Resident;
import com.bluemoon.models.Role;
import com.bluemoon.services.BillingServiceImpl;
import com.bluemoon.services.FeeCategorySessionFixture;
import com.bluemoon.services.NoticeServiceImpl;
import com.bluemoon.services.RoleBasedAuthorizationService;
import com.bluemoon.views.DashboardModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Billing and Notice modules against a real MySQL (disposable *_test schema): real DAOs, real services, real
 * RBAC, real FXML. Uses the same registrations the App Shell owner will add; ProductionModules and the App Shell are
 * not touched. Billing uses the production clock, so the period under test is the current month.
 */
@EnabledIfEnvironmentVariable(named = "BLUEMOON_MYSQL_TEST", matches = "1")
@Timeout(120)
class BillingNoticeModulesMySqlIT {
    private static final List<String> MY_TABLES = List.of("invoice_lines", "invoices", "billing_periods", "fee_rates");
    private static final AtomicInteger CCCD = new AtomicInteger();
    private static DatabaseConnection connections;
    private DashboardModule module;
    private volatile boolean confirm = true;

    @BeforeAll static void connect() throws Exception {
        connections = new DatabaseConnection(DatabaseConfig.load());
        try (var c = connections.getConnection()) {
            assertTrue(c.getCatalog().endsWith("_test"), "Dedicated disposable *_test schema required");
            assertEquals("MySQL", c.getMetaData().getDatabaseProductName());
        }
        var ready = new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @AfterAll static void dropOwnTables() throws Exception {
        for (String table : MY_TABLES) { sql("DROP TABLE IF EXISTS " + table); }
    }

    @BeforeEach void schemaAndData() throws Exception {
        for (String table : MY_TABLES) { sql("DROP TABLE IF EXISTS " + table); }
        for (String table : List.of("residents", "households", "apartments", "fee_types")) {
            sql("DROP TABLE IF EXISTS " + table);
        }
        for (String file : List.of("database/schema.sql", "database/migrations/us001_household_apartment.sql",
                "database/migrations/us002_residents.sql", "database/migrations/us005_fee_types.sql",
                "database/migrations/us006_fee_rates.sql", "database/migrations/us007_billing.sql")) {
            script(file);
        }
        sql("INSERT IGNORE INTO users (id, username, password_hash, ho_ten, role) "
                + "VALUES (71, 'module-it-71', 'x', 'Module IT', 'ACCOUNTANT')");
        fee("PHI_DV", "THEO_DIEN_TICH", "10000");
        fee("PHI_QL", "THEO_DIEN_TICH", "7000");
        fee("PHI_VS", "THEO_NHAN_KHAU", "6000");
        sql("INSERT INTO apartments (so_can_ho, tang, dien_tich, trang_thai) VALUES ('P1204', 12, 80.00, 'DANG_O')");
        sql("INSERT INTO households (ma_ho, so_can_ho, ngay_nhan_nha) VALUES ('HK-1204', 'P1204', '2024-01-01')");
        for (int i = 0; i < 4; i++) {
            sql("INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, ngay_chuyen_den) "
                    + "VALUES ('HK-1204', 'Nguyen Van " + (char) ('A' + i) + "', '1990-01-01', 'NAM', '"
                    + String.format("%012d", 200000000000L + CCCD.incrementAndGet()) + "', '"
                    + (i == 0 ? "CHU_HO" : "CON") + "', '2024-01-01')");
        }
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);
    }

    @AfterEach void closeModule() throws Exception {
        if (module != null) { var m = module; module = null; fx(() -> { m.close(); return null; }); }
        FeeCategorySessionFixture.use(null);
    }

    // ---- helpers ----
    private static void fee(String code, String method, String price) throws Exception {
        sql("INSERT INTO fee_types (ma_loai_thu, ten_loai_thu, loai_thu, phuong_thuc_tinh, so_tien_mac_dinh, trang_thai) "
                + "VALUES ('" + code + "', 'Khoan " + code + "', 'BAT_BUOC', '" + method + "', 0, 'DANG_AP_DUNG')");
        sql("INSERT INTO fee_rates (ma_loai_thu, don_gia, ngay_ap_dung) VALUES ('" + code + "', " + price + ", '2026-01-01')");
    }

    private static void sql(String text) throws Exception {
        try (var c = connections.getConnection(); var s = c.createStatement()) { s.execute(text); }
    }

    private static void script(String file) throws Exception {
        String text = Files.readString(Path.of(file)).replaceAll("(?m)^\\s*--.*$", "");
        for (String command : text.split(";")) { if (!command.isBlank()) { sql(command); } }
    }

    private static long count(String table) throws Exception {
        try (var c = connections.getConnection(); var s = c.createStatement(); var rows = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var done = new CompletableFuture<T>();
        Platform.runLater(() -> { try { done.complete(action.call()); } catch (Throwable t) { done.completeExceptionally(t); } });
        return done.get(15, TimeUnit.SECONDS);
    }

    private void until(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < end) { if (fx(condition::getAsBoolean)) { return; } Thread.sleep(20); }
        fail("UI result did not arrive");
    }

    private boolean idle() { return !module.busyProperty().get(); }
    @SuppressWarnings("unchecked") private <T> T node(String id) { return (T) module.view().lookup("#" + id); }
    private String text(String id) { return ((Label) node(id)).getText(); }
    private void click(String id) throws Exception { fx(() -> { ((Button) node(id)).fire(); return null; }); }

    /** Current month in the business zone, exactly like the production service. */
    private static String currentPeriod() {
        return YearMonth.from(Clock.systemUTC().instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh"))).toString();
    }

    /** Billing module over the real DAO; the confirmation is injected because a modal Alert cannot be driven here. */
    private BillingModule billingOverRealDatabase() throws Exception {
        var auth = new RoleBasedAuthorizationService(null);
        var service = new BillingServiceImpl(new BillingDAO(connections), auth, Clock.systemUTC());
        var controller = new BillingController(service, auth, Clock.systemUTC(),
                Executors.newSingleThreadExecutor(job -> { var t = new Thread(job, "it-billing"); t.setDaemon(true); return t; }),
                true, question -> confirm);
        return fx(() -> new BillingModule(controller));
    }

    // ---- tests ----
    @Test void registrationsMatchTheAppShellContract() throws Exception {
        var billing = BillingModule.registration(connections);
        var notice = NoticeModule.registration(connections);
        assertEquals("billing", billing.id());
        assertEquals("notices", notice.id());
        assertEquals(Permission.DEBT_REPORT_VIEW, billing.permission());
        assertEquals(Permission.DEBT_REPORT_VIEW, notice.permission());
        assertNotNull(billing.title());
        assertNotNull(notice.title());
        // Opening through the registration supplier (what the shell will do) works against the real database.
        module = fx(() -> billing.open().get());
        until(this::idle);
        assertEquals(currentPeriod(), text("currentPeriodLabel"));
        fx(() -> { module.close(); return null; });
        module = fx(() -> notice.open().get());
        until(this::idle);
        assertEquals(currentPeriod(), fx(() -> ((TextField) node("periodField")).getText()));
    }

    @Test void billingPreviewPublishAndStoredSnapshotOnRealMySql() throws Exception {
        module = billingOverRealDatabase();
        until(this::idle);
        click("previewButton");
        until(() -> idle() && !this.<TableView<MonthlyCharge>>node("chargeTable").getItems().isEmpty());
        assertTrue(text("summaryLabel").contains("1.384.000"), text("summaryLabel"));
        assertEquals(0, count("billing_periods"), "preview writes nothing");
        confirm = false;
        click("generateButton");
        until(() -> text("messageLabel").contains("hủy"));
        assertEquals(0, count("billing_periods"), "declined confirmation writes nothing");
        confirm = true;
        click("generateButton");
        until(() -> idle() && text("bannerLabel").contains("đã phát hành"));
        assertTrue(text("messageLabel").contains("thành công"));
        assertEquals(1, count("billing_periods"));
        assertEquals(1, count("invoices"));
        assertEquals(3, count("invoice_lines"));
        until(() -> this.<TableView<?>>node("lineTable").getItems().size() == 3);
        // The same period again: stored, closed, cannot be published a second time.
        click("previewButton");
        until(() -> idle() && text("bannerLabel").contains("ĐÃ phát hành"));
        assertTrue(fx(() -> ((Button) node("generateButton")).isDisabled()));
        assertEquals(1, count("billing_periods"));
    }

    @Test void noticeFromTheInvoicePublishedByTheBillingModuleAndExport(@TempDir Path dir) throws Exception {
        module = billingOverRealDatabase();
        until(this::idle);
        click("previewButton");
        until(() -> idle() && !this.<TableView<MonthlyCharge>>node("chargeTable").getItems().isEmpty());
        click("generateButton");
        until(() -> idle() && text("bannerLabel").contains("đã phát hành"));
        fx(() -> { module.close(); return null; });

        var auth = new RoleBasedAuthorizationService(null);
        var billing = new BillingServiceImpl(new BillingDAO(connections), auth, Clock.systemUTC());
        var residents = new ResidentDAO(connections);
        var notices = new NoticeServiceImpl(billing, maHo -> residents.findHead(maHo).map(Resident::hoTen), auth,
                Clock.systemUTC());
        var file = dir.resolve("notice.html");
        module = fx(() -> NoticeModule.over(notices, billing, auth, Clock.systemUTC(), name -> Optional.of(file)));
        until(this::idle);
        click("loadButton");
        until(() -> idle() && !this.<ListView<String>>node("apartmentList").getItems().isEmpty());
        String shown = fx(() -> ((TextArea) node("previewArea")).getText());
        for (String expected : List.of("TB-" + currentPeriod().replace("-", "") + "-P1204", "Nguyen Van A",
                "80 m² x 10.000 = 800.000 VNĐ", "80 m² x 7.000 = 560.000 VNĐ", "4 người x 6.000 = 24.000 VNĐ",
                "TỔNG TIỀN PHẢI NỘP: 1.384.000 VNĐ")) {
            assertTrue(shown.contains(expected), "missing: " + expected);
        }
        fx(() -> { ((TextArea) node("bankField")).setText("BQT BlueMoon - STK 000"); return null; });
        click("exportAllButton");
        until(() -> text("messageLabel").contains("Đã xuất 1 thông báo"));
        assertTrue(Files.readString(file).contains("TỔNG TIỀN PHẢI NỘP: 1.384.000 VNĐ"));
    }

    @Test void noticeRefusesAPeriodThatWasNeverGenerated() throws Exception {
        module = fx(() -> NoticeModule.registration(connections).open().get());
        until(this::idle);
        fx(() -> { ((TextField) node("periodField")).setText("2030-01"); return null; });
        click("loadButton");
        until(() -> idle() && text("messageLabel").contains("Chưa có dữ liệu hóa đơn"));
        assertEquals("Chưa có dữ liệu hóa đơn cho kỳ thu này. Vui lòng lập kỳ thu trước!", text("messageLabel"));
        assertTrue(fx(() -> this.<ListView<String>>node("apartmentList").getItems().isEmpty()));
    }

    @Test void missingRateIsShownAndNothingIsWrittenOnRealMySql() throws Exception {
        sql("INSERT INTO fee_types (ma_loai_thu, ten_loai_thu, loai_thu, phuong_thuc_tinh, so_tien_mac_dinh, trang_thai) "
                + "VALUES ('PHI_X', 'Khoan moi', 'BAT_BUOC', 'THEO_CAN_HO', 0, 'DANG_AP_DUNG')");
        module = billingOverRealDatabase();
        until(this::idle);
        click("previewButton");
        until(() -> idle() && text("messageLabel").contains("PHI_X"));
        assertTrue(fx(() -> ((Button) node("generateButton")).isDisabled()));
        assertEquals(0, count("billing_periods"));
    }

    @Test void residentSeesNothingThroughEitherModule() throws Exception {
        FeeCategorySessionFixture.use(Role.RESIDENT);
        module = fx(() -> BillingModule.registration(connections).open().get());
        until(this::idle);
        assertTrue(fx(() -> !((Button) node("previewButton")).isVisible()));
        assertTrue(fx(() -> this.<TableView<?>>node("chargeTable").getItems().isEmpty()));
        fx(() -> { module.close(); return null; });
        module = fx(() -> NoticeModule.registration(connections).open().get());
        until(this::idle);
        assertTrue(fx(() -> ((Button) node("loadButton")).isDisabled()));
        assertEquals(0, count("billing_periods"));
    }
}
