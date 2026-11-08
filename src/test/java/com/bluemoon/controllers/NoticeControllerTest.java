package com.bluemoon.controllers;

import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.dao.BillingInputs;
import com.bluemoon.dao.BillingInputs.FeeInput;
import com.bluemoon.dao.BillingInputs.HouseholdInput;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.AppliedRate;
import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.Role;
import com.bluemoon.services.BillingServiceImpl;
import com.bluemoon.services.FeeCategorySessionFixture;
import com.bluemoon.services.NoticeServiceImpl;
import com.bluemoon.services.RoleBasedAuthorizationService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real FXML + NoticeController + NoticeServiceImpl + BillingServiceImpl + RBAC over a memory repository. */
class NoticeControllerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneOffset.UTC);
    private final MemoryBillingRepository repository = new MemoryBillingRepository();
    private NoticeModule module;
    private volatile Optional<Path> chosen = Optional.empty();
    private volatile String chosenName = "";

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @AfterEach void cleanup() throws Exception {
        if (module != null) { fx(() -> { module.close(); return null; }); }
        FeeCategorySessionFixture.use(null);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var done = new CompletableFuture<T>();
        Platform.runLater(() -> { try { done.complete(action.call()); } catch (Throwable t) { done.completeExceptionally(t); } });
        return done.get(10, TimeUnit.SECONDS);
    }

    private void until(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < end) { if (fx(condition::getAsBoolean)) { return; } Thread.sleep(10); }
        fail("UI result did not arrive");
    }

    private static FeeInput fee(String code, ChargeMethod method, String price) {
        return new FeeInput(code, "Khoan " + code, method,
                Optional.of(new AppliedRate(code, new BigDecimal(price), LocalDate.of(2026, 1, 1))));
    }

    /** Generates 2026-11 through the real billing service, as the accountant would. */
    private void seedGeneratedPeriod() {
        repository.inputs = new BillingInputs(List.of(fee("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "10000"),
                fee("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "7000"), fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "6000")),
                List.of(new HouseholdInput("HK-1101", "P1101", new BigDecimal("50"), ApartmentStatus.DANG_O, 1),
                        new HouseholdInput("HK-1204", "P1204", new BigDecimal("80"), ApartmentStatus.DANG_O, 4)));
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);
        assertTrue(new BillingServiceImpl(repository, new RoleBasedAuthorizationService(null), CLOCK)
                .generatePeriod("2026-11").isSuccess());
    }

    private void open(Role role) throws Exception {
        FeeCategorySessionFixture.use(role);
        var auth = new RoleBasedAuthorizationService(null);
        var billing = new BillingServiceImpl(repository, auth, CLOCK);
        var notices = new NoticeServiceImpl(billing, maHo -> Optional.of("Chủ hộ " + maHo), auth, CLOCK);
        module = fx(() -> NoticeModule.over(notices, billing, auth, CLOCK, name -> { chosenName = name; return chosen; }));
        until(this::idle);
    }

    @SuppressWarnings("unchecked") private <T> T node(String id) { return (T) module.view().lookup("#" + id); }
    private String text(String id) { return ((Label) node(id)).getText(); }
    private boolean idle() { return !module.busyProperty().get(); }
    private void click(String id) throws Exception { fx(() -> { ((Button) node(id)).fire(); return null; }); }
    private void type(String id, String value) throws Exception {
        fx(() -> { if (node(id) instanceof TextField f) { f.setText(value); } else { ((TextArea) node(id)).setText(value); } return null; });
    }
    private ListView<String> apartments() { return node("apartmentList"); }
    private String preview() { return ((TextArea) node("previewArea")).getText(); }

    private void loadPeriod(String period) throws Exception {
        type("periodField", period);
        click("loadButton");
        until(this::idle);
    }

    @Test void ac01ShowsTheFullNoticeThatMatchesTheInvoice() throws Exception {
        seedGeneratedPeriod();
        open(Role.ACCOUNTANT);
        assertEquals("2026-11", fx(() -> ((TextField) node("periodField")).getText()));
        assertEquals(List.of("2026-11"), fx(() -> List.copyOf(this.<javafx.scene.control.ComboBox<String>>node("periodBox").getItems())));
        loadPeriod("2026-11");
        assertEquals(List.of("P1101", "P1204"), fx(() -> List.copyOf(apartments().getItems())));
        fx(() -> { apartments().getSelectionModel().select("P1204"); return null; });
        until(() -> preview().contains("P1204"));
        String shown = fx(this::preview);
        for (String expected : List.of("GIẤY THÔNG BÁO THU PHÍ THÁNG 11/2026", "TB-202611-P1204", "Chủ hộ HK-1204",
                "80 m² x 10.000 = 800.000 VNĐ", "80 m² x 7.000 = 560.000 VNĐ", "4 người x 6.000 = 24.000 VNĐ",
                "TỔNG TIỀN PHẢI NỘP: 1.384.000 VNĐ", "Ngày 15/12/2026", "P1204 Dong phi thang 11/2026")) {
            assertTrue(shown.contains(expected), "missing: " + expected);
        }
    }

    @Test void periodNotGeneratedIsRefusedWithTheSrsMessage() throws Exception {
        seedGeneratedPeriod();
        open(Role.ACCOUNTANT);
        loadPeriod("2026-12");
        assertEquals("Chưa có dữ liệu hóa đơn cho kỳ thu này. Vui lòng lập kỳ thu trước!", text("messageLabel"));
        assertTrue(fx(() -> apartments().getItems().isEmpty()));
        assertTrue(fx(() -> ((Button) node("exportAllButton")).isDisabled()));
    }

    @Test void invalidPeriodTextIsReported() throws Exception {
        seedGeneratedPeriod();
        open(Role.ACCOUNTANT);
        loadPeriod("2026-13");
        assertEquals("Kỳ thu phải theo định dạng YYYY-MM!", text("periodError"));
    }

    @Test void exportNeedsBankInfoThenWritesEscapedPrintableHtml(@TempDir Path dir) throws Exception {
        seedGeneratedPeriod();
        open(Role.ACCOUNTANT);
        loadPeriod("2026-11");
        chosen = Optional.of(dir.resolve("all.html"));
        click("exportAllButton");
        assertEquals(NoticeControllerTestAccess.bankRequired(), text("bankError"));
        assertFalse(Files.exists(dir.resolve("all.html")), "nothing written without bank info");
        type("bankField", "Ngân hàng <X> - STK 123 - BQT");
        click("exportAllButton");
        until(() -> text("messageLabel").contains("Đã xuất 2 thông báo"));
        String html = Files.readString(chosen.get());
        assertEquals("thong-bao-2026-11.html", chosenName);
        assertEquals(2, html.split("class=\"slip\"", -1).length - 1);
        assertTrue(html.contains("Ngân hàng &lt;X&gt; - STK 123 - BQT"));
        assertFalse(html.contains("<X>"));
        assertTrue(html.contains("1.384.000 VNĐ") && html.contains("TB-202611-P1101"));
    }

    @Test void exportOneUsesTheSelectedNoticeAndCancelWritesNothing(@TempDir Path dir) throws Exception {
        seedGeneratedPeriod();
        open(Role.ACCOUNTANT);
        loadPeriod("2026-11");
        type("bankField", "BQT - 123");
        fx(() -> { apartments().getSelectionModel().select("P1204"); return null; });
        chosen = Optional.empty();
        click("exportOneButton");
        until(() -> text("messageLabel").contains("Đã hủy xuất"));
        chosen = Optional.of(dir.resolve("one.html"));
        click("exportOneButton");
        until(() -> text("messageLabel").contains("Đã xuất 1 thông báo"));
        String html = Files.readString(chosen.get());
        assertTrue(html.contains("TB-202611-P1204") && !html.contains("TB-202611-P1101"));
        assertEquals("thong-bao-TB-202611-P1204.html", chosenName);
    }

    @Test void unwritableTargetShowsAGenericMessage(@TempDir Path dir) throws Exception {
        seedGeneratedPeriod();
        open(Role.ACCOUNTANT);
        loadPeriod("2026-11");
        type("bankField", "BQT - 123");
        chosen = Optional.of(dir.resolve("missing-folder").resolve("x.html"));
        click("exportAllButton");
        until(() -> text("messageLabel").contains("Không ghi được tệp"));
    }

    @Test void residentAndNoSessionSeeNothingAndCannotLoad() throws Exception {
        seedGeneratedPeriod();
        for (Role role : new Role[] {Role.RESIDENT, null}) {
            open(role);
            assertTrue(fx(() -> ((Button) node("loadButton")).isDisabled()));
            assertTrue(fx(() -> ((Button) node("exportAllButton")).isDisabled()));
            assertTrue(fx(() -> apartments().getItems().isEmpty()));
            assertTrue(fx(() -> ((Label) node("messageLabel")).getText().contains("không có quyền")));
            fx(() -> { module.close(); return null; });
            module = null;
        }
    }

    @Test void closeIsIdempotentAndModuleOwnsNoScene() throws Exception {
        seedGeneratedPeriod();
        open(Role.ACCOUNTANT);
        assertNull(fx(() -> module.view().getScene()));
        fx(() -> { module.close(); module.close(); return null; });
        assertThrows(IllegalStateException.class, () -> module.close());
    }

    /** Keeps the expected SRS-adjacent message in one place without widening production visibility. */
    private static final class NoticeControllerTestAccess {
        static String bankRequired() {
            return "Vui lòng nhập thông tin tài khoản nhận (ngân hàng, số tài khoản, chủ tài khoản)!";
        }
    }
}
