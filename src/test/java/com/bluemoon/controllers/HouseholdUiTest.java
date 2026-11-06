package com.bluemoon.controllers;

import com.bluemoon.models.*;
import com.bluemoon.dao.*;
import com.bluemoon.services.*;
import com.bluemoon.views.HouseholdNavigator;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class HouseholdUiTest {
    private Stage stage;
    private HouseholdNavigator navigator;
    private FakeService fake;
    private static final HouseholdProfile PROFILE = new HouseholdProfile(
            new Apartment("P1204", 12, new BigDecimal("85.00"), ApartmentStatus.CAN_HO_TRONG),
            new Household("HK-1204", "P1204", LocalDate.of(2024, 3, 15)));
    @BeforeAll static void toolkit() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
        ready.get(10, TimeUnit.SECONDS);
    }
    @AfterAll static void shutdown() { Platform.exit(); }
    @BeforeEach void setup() throws Exception {
        fake = new FakeService();
        fx(() -> {
            stage = new Stage();
            navigator = new HouseholdNavigator(fake, root -> stage.setScene(new Scene(root, 1000, 760)));
        });
    }
    @AfterEach void close() throws Exception {
        fake.release.countDown();
        fx(() -> { navigator.dispose(); stage.close(); });
    }
    private void fx(Runnable action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { action.run(); done.complete(null); }
            catch (Throwable failure) { done.completeExceptionally(failure); }
        });
        done.get(10, TimeUnit.SECONDS);
    }
    private <T> T c(String id, Class<T> type) { return type.cast(stage.getScene().lookup("#" + id)); }
    @SuppressWarnings("unchecked") private TableView<HouseholdProfile> table() { return c("households", TableView.class); }
    private String message() { return c("message", Label.class).getText(); }
    private void click(String id) { c(id, Button.class).fire(); }
    private void list() throws Exception { fx(() -> { navigator.showList(); stage.show(); }); settled(); }
    private void settled() throws Exception {
        await(() -> !c("progress", ProgressIndicator.class).isVisible());
    }
    private void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            boolean[] yes = {false}; fx(() -> yes[0] = condition.getAsBoolean());
            if (yes[0]) return;
            Thread.sleep(10);
        }
        fail("Timed out waiting for UI");
    }
    private void fill() {
        c("soCanHo", TextField.class).setText("P1204");
        c("maHo", TextField.class).setText("HK-1204");
        c("tang", TextField.class).setText("12");
        c("dienTich", TextField.class).setText("85.00");
        c("ngayNhanNha", DatePicker.class).setValue(LocalDate.of(2024, 3, 15));
        c("trangThai", ComboBox.class).setValue(ApartmentStatus.CAN_HO_TRONG);
    }
    private void createForm() throws Exception { fx(() -> { navigator.showCreate(); stage.show(); }); }
    private DialogPane dialog() {
        return Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getOwner() == stage)
                .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).findFirst().orElse(null);
    }
    private void dismiss(ButtonType type) {
        DialogPane pane = dialog(); assertNotNull(pane);
        ((Button) pane.lookupButton(type)).fire();
    }
    @Test void listLoadsSixColumnsAndFiltersWithService() throws Exception {
        list();
        fx(() -> {
            assertEquals(6, table().getColumns().size());
            assertEquals(List.of(PROFILE), table().getItems());
            c("statusFilter", ComboBox.class).getSelectionModel().select(1);
        });
        settled();
        assertEquals(ApartmentStatus.DANG_O, fake.filter);
        fx(() -> assertTrue(table().getItems().isEmpty()));
    }
    @Test void emptyLoadingErrorAndRetry() throws Exception {
        fake.block = true;
        fx(() -> { navigator.showList(); stage.show(); });
        assertTrue(fake.entered.await(5, TimeUnit.SECONDS));
        fx(() -> {
            assertTrue(c("progress", ProgressIndicator.class).isVisible());
            assertTrue(c("reloadButton", Button.class).isDisabled());
            assertTrue(table().getItems().isEmpty());
        });
        fake.release.countDown(); settled();
        fake.listResult = HouseholdResult.success("OK", List.of());
        fx(() -> click("reloadButton")); settled();
        fx(() -> { assertTrue(table().getItems().isEmpty()); assertTrue(message().contains("Chưa có")); });
        fake.listResult = failure(HouseholdResult.Status.SYSTEM_ERROR);
        fx(() -> click("reloadButton")); settled();
        fx(() -> { assertEquals("Lỗi hệ thống, vui lòng thử lại sau.", message()); assertTrue(table().getItems().isEmpty()); });
        fake.listResult = HouseholdResult.success("OK", List.of(PROFILE));
        fx(() -> click("reloadButton")); settled();
        fx(() -> assertEquals(1, table().getItems().size()));
    }
    @Test void createAc01RefreshesListWithExactMessage() throws Exception {
        fake.listResult = HouseholdResult.success("OK", List.of());
        list(); fx(() -> click("createButton"));
        fx(() -> { fill(); click("saveButton"); });
        await(() -> stage.getScene().lookup("#households") != null); settled();
        fx(() -> {
            assertEquals("Tạo hồ sơ căn hộ P1204 thành công", message());
            assertEquals(List.of(PROFILE), table().getItems());
        });
        assertEquals(1, fake.creates.get());
        assertEquals("P1204", fake.created.soCanHo());
    }
    @Test void duplicateAc02ShowsWarningAndDoesNotNavigateOrAddData() throws Exception {
        fake.createResult = HouseholdResult.failure(HouseholdResult.Status.DUPLICATE,
                "Số căn hộ đã tồn tại", Map.of("so_can_ho", "Số căn hộ đã tồn tại"));
        createForm(); fx(() -> { fill(); click("saveButton"); }); settled();
        fx(() -> {
            DialogPane pane = dialog(); assertNotNull(pane);
            try {
                assertTrue(pane.getStyleClass().contains("warning"));
                assertEquals("Số căn hộ đã tồn tại", pane.getContentText());
                assertEquals("Số căn hộ đã tồn tại", c("soCanHoError", Label.class).getText());
                assertEquals("P1204", c("soCanHo", TextField.class).getText());
                assertNull(stage.getScene().lookup("#households"));
            } finally { dismiss(ButtonType.OK); }
        });
        assertEquals(1, fake.creates.get()); assertEquals(0, fake.lists.get());
    }
    @Test void validationKeepsRawInputAndDisplaysAllBackendFieldErrors() throws Exception {
        Map<String, String> errors = Map.of("so_can_ho", "Số căn hộ lỗi", "ma_ho", "Mã hộ lỗi",
                "tang", "Tầng lỗi", "dien_tich", "Diện tích lỗi", "ngay_nhan_nha", "Ngày lỗi", "trang_thai", "Trạng thái lỗi");
        fake.createResult = HouseholdResult.failure(HouseholdResult.Status.VALIDATION_ERROR, "Dữ liệu không hợp lệ", errors);
        createForm();
        fx(() -> {
            fill(); c("soCanHo", TextField.class).setText(" raw ");
            c("dienTich", TextField.class).setText("-5");
            c("ngayNhanNha", DatePicker.class).getEditor().setText("bad-date");
            click("saveButton");
        }); settled();
        fx(() -> {
            assertEquals(" raw ", c("soCanHo", TextField.class).getText());
            assertEquals("-5", c("dienTich", TextField.class).getText());
            assertEquals("bad-date", c("ngayNhanNha", DatePicker.class).getEditor().getText());
            for (String id : List.of("soCanHo", "maHo", "tang", "dienTich", "ngayNhanNha", "trangThai"))
                assertFalse(c(id + "Error", Label.class).getText().isEmpty());
            assertFalse(c("saveButton", Button.class).isDisabled()); assertNull(dialog());
        });
        assertEquals(" raw ", fake.created.soCanHo()); assertEquals("bad-date", fake.created.ngayNhanNha());
    }
    @Test void detailLoadsAndEditAc03KeepsIdentifiersReadOnly() throws Exception {
        list();
        fx(() -> { table().getSelectionModel().selectFirst(); click("detailButton"); }); settled();
        fx(() -> {
            assertEquals("P1204", c("soCanHo", Label.class).getText());
            assertEquals("HK-1204", c("householdCode", Label.class).getText());
            click("editButton");
            assertFalse(c("soCanHo", TextField.class).isEditable());
            assertFalse(c("maHo", TextField.class).isEditable());
            c("trangThai", ComboBox.class).setValue(ApartmentStatus.DANG_O);
            click("saveButton");
        });
        await(() -> stage.getScene().lookup("#households") != null); settled();
        assertEquals("HK-1204", fake.updatedId);
        assertEquals(ApartmentStatus.DANG_O, fake.updated.trangThai());
        assertEquals("12", fake.updated.tang()); assertEquals("85.00", fake.updated.dienTich());
        assertEquals("2024-03-15", fake.updated.ngayNhanNha()); assertEquals(List.of(false), fake.confirmations);
    }
    @Test void areaConfirmationReusesExactPayloadOnApproval() throws Exception {
        fake.confirmArea = true;
        fx(() -> { navigator.showEdit(PROFILE); stage.show(); c("dienTich", TextField.class).setText("90.50"); click("saveButton"); });
        settled();
        HouseholdUpdateForm first = fake.updated;
        fx(() -> {
            assertTrue(dialog().getStyleClass().contains("confirmation"));
            assertEquals("Xác nhận đổi diện tích", dialog().getContentText());
            dismiss(ButtonType.OK);
        });
        await(() -> stage.getScene().lookup("#households") != null); settled();
        assertEquals(List.of(false, true), fake.confirmations); assertSame(first, fake.updated);
    }
    @Test void areaConfirmationCancelDoesNotSubmitAgain() throws Exception {
        fake.confirmArea = true;
        fx(() -> { navigator.showEdit(PROFILE); stage.show(); click("saveButton"); }); settled();
        fx(() -> dismiss(ButtonType.CANCEL));
        fx(() -> { assertEquals(1, fake.confirmations.size()); assertNotNull(stage.getScene().lookup("#saveButton")); });
    }
    @Test void notFoundAndSystemErrorDoNotExposeDetail() throws Exception {
        for (HouseholdResult.Status status : List.of(HouseholdResult.Status.NOT_FOUND, HouseholdResult.Status.SYSTEM_ERROR)) {
            fake.detailResult = failure(status);
            fx(() -> { navigator.showDetail("missing"); stage.show(); }); settled();
            fx(() -> {
                assertEquals("", c("soCanHo", Label.class).getText());
                assertTrue(c("editButton", Button.class).isDisabled());
                assertFalse(c("backButton", Button.class).isDisabled());
                assertEquals(status == HouseholdResult.Status.NOT_FOUND ? "Không tìm thấy hồ sơ hộ/căn hộ" : "Lỗi hệ thống, vui lòng thử lại sau.", message());
            });
        }
    }
    @Test void accessDeniedClearsPreviouslyVisibleDataAndForm() throws Exception {
        list(); fake.deny = true;
        fx(() -> click("reloadButton")); settled();
        fx(() -> { assertTrue(table().getItems().isEmpty()); assertTrue(message().contains("không có quyền")); });
        fx(() -> { navigator.showDetail("HK-1204"); }); settled();
        fx(() -> assertEquals("", c("householdCode", Label.class).getText()));
        createForm(); fx(() -> { fill(); click("saveButton"); }); settled();
        fx(() -> {
            assertEquals("", c("soCanHo", TextField.class).getText());
            assertTrue(message().contains("không có quyền")); assertNull(dialog());
        });
    }
    @Test void doubleSubmissionIsBlockedAndFxThreadRemainsResponsive() throws Exception {
        fake.block = true;
        createForm(); fx(() -> { fill(); click("saveButton"); click("saveButton"); });
        assertTrue(fake.entered.await(5, TimeUnit.SECONDS));
        fx(() -> { assertTrue(c("saveButton", Button.class).isDisabled()); assertTrue(c("progress", ProgressIndicator.class).isVisible()); });
        assertEquals(1, fake.creates.get()); assertFalse(fake.onFx);
        fake.release.countDown(); await(() -> stage.getScene().lookup("#households") != null); settled();
    }
    @Test void unexpectedFailureIsSanitizedAndRestoresControls() throws Exception {
        fake.crash = true;
        createForm(); fx(() -> { fill(); click("saveButton"); }); settled();
        fx(() -> { assertEquals("Lỗi hệ thống, vui lòng thử lại sau.", message()); assertFalse(c("saveButton", Button.class).isDisabled()); });
    }
    @Test void detachedResultCannotOverwriteNewView() throws Exception {
        fake.block = true;
        fx(() -> { navigator.showList(); stage.show(); });
        assertTrue(fake.entered.await(5, TimeUnit.SECONDS));
        fx(() -> navigator.showCreate());
        fake.release.countDown();
        // A subsequent FX turn is safe even if the old worker has not returned yet.
        fx(() -> assertNotNull(stage.getScene().lookup("#saveButton")));
        assertEquals(1, fake.lists.get());
    }
    @Test void guiSnapshotsAndPrimaryControlsFit() throws Exception {
        list(); pulse();
        fx(() -> snapshot("household-list"));
        fx(() -> { table().getSelectionModel().selectFirst(); click("detailButton"); });
        settled(); pulse();
        fx(() -> snapshot("household-detail"));
        fx(() -> click("editButton"));
        pulse();
        fx(() -> snapshot("household-form"));
    }
    private void pulse() throws Exception {
        CompletableFuture<Void> rendered = new CompletableFuture<>();
        fx(() -> new javafx.animation.AnimationTimer() {
            int frames;
            @Override public void handle(long now) {
                if (++frames >= 3) { stop(); rendered.complete(null); }
            }
        }.start());
        rendered.get(10, TimeUnit.SECONDS);
    }
    private void snapshot(String name) {
        Parent root = stage.getScene().getRoot();
        root.applyCss();
        root.resize(stage.getScene().getWidth(), stage.getScene().getHeight());
        root.layout();
        for (String id : List.of("title", "message", name.equals("household-list") ? "createButton"
                : name.equals("household-detail") ? "backButton" : "saveButton")) {
            javafx.scene.Node node = stage.getScene().lookup("#" + id);
            var bounds = node.localToScene(node.getBoundsInLocal());
            assertTrue(bounds.getMinX() >= 0 && bounds.getMinY() >= 0);
            assertTrue(bounds.getMaxX() <= stage.getScene().getWidth() + 1);
            assertTrue(bounds.getMaxY() <= stage.getScene().getHeight() + 1);
        }
        if (!Boolean.getBoolean("bluemoon.ui.snapshots")) return;
        var image = stage.getScene().snapshot(null);
        var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
            bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        try {
            java.nio.file.Path output = java.nio.file.Path.of("target", "ui-previews", name + ".png");
            java.nio.file.Files.createDirectories(output.getParent());
            javax.imageio.ImageIO.write(bitmap, "png", output.toFile());
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    @Test void realServiceInMemoryIntegrationCoversCreateUpdateAndRead() throws Exception {
        HouseholdService service = new HouseholdServiceImpl(new InMemoryHouseholdRepository(),
                ApartmentChargeLookup.NONE, () -> Role.ADMIN, Clock.systemDefaultZone());
        fx(() -> {
            navigator.dispose();
            navigator = new HouseholdNavigator(service, root -> stage.setScene(new Scene(root, 1000, 760)));
            navigator.showCreate(); stage.show(); fill(); click("saveButton");
        });
        await(() -> stage.getScene().lookup("#households") != null); settled();
        fx(() -> { assertEquals(1, table().getItems().size()); table().getSelectionModel().selectFirst(); click("detailButton"); }); settled();
        fx(() -> { click("editButton"); c("trangThai", ComboBox.class).setValue(ApartmentStatus.DANG_O); click("saveButton"); });
        await(() -> stage.getScene().lookup("#households") != null); settled();
        fx(() -> { assertEquals(ApartmentStatus.DANG_O, table().getItems().get(0).trangThai()); assertEquals(new BigDecimal("85.00"), table().getItems().get(0).dienTich()); });
    }
    private static <T> HouseholdResult<T> failure(HouseholdResult.Status status) {
        return HouseholdResult.failure(status, status == HouseholdResult.Status.NOT_FOUND
                ? "Không tìm thấy hồ sơ hộ/căn hộ" : "Lỗi hệ thống, vui lòng thử lại sau.", Map.of());
    }
    private static final class FakeService implements HouseholdService {
        final AtomicInteger lists = new AtomicInteger(), creates = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final List<Boolean> confirmations = new CopyOnWriteArrayList<>();
        volatile boolean block, deny, crash, onFx, confirmArea;
        volatile ApartmentStatus filter;
        volatile HouseholdForm created;
        volatile HouseholdUpdateForm updated;
        volatile String updatedId;
        volatile HouseholdResult<List<HouseholdProfile>> listResult = HouseholdResult.success("OK", List.of(PROFILE));
        volatile HouseholdResult<HouseholdProfile> detailResult = HouseholdResult.success("OK", PROFILE);
        volatile HouseholdResult<HouseholdProfile> createResult;
        private void before() {
            onFx = Platform.isFxApplicationThread(); entered.countDown();
            if (block) try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            if (deny) throw new AccessDeniedException("SQL sensitive user info");
            if (crash) throw new IllegalStateException("SQL password=secret");
        }
        public HouseholdResult<List<HouseholdProfile>> listHouseholds(ApartmentStatus filter) {
            lists.incrementAndGet(); before(); this.filter = filter;
            if (filter != null && listResult.isSuccess()) return HouseholdResult.success("OK", listResult.data().orElseThrow().stream().filter(p -> p.trangThai() == filter).toList());
            return listResult;
        }
        public HouseholdResult<HouseholdProfile> createHousehold(HouseholdForm form) {
            creates.incrementAndGet(); created = form; before();
            if (createResult != null) return createResult;
            listResult = HouseholdResult.success("OK", List.of(PROFILE));
            return HouseholdResult.success("Tạo hồ sơ căn hộ P1204 thành công", PROFILE);
        }
        public HouseholdResult<HouseholdProfile> updateHousehold(String maHo, HouseholdUpdateForm form, boolean confirm) {
            updatedId = maHo; updated = form; confirmations.add(confirm); before();
            if (confirmArea && !confirm) return HouseholdResult.failure(HouseholdResult.Status.CONFIRMATION_REQUIRED, "Xác nhận đổi diện tích", Map.of("dien_tich", "Xác nhận đổi diện tích"));
            return HouseholdResult.success("Cập nhật hồ sơ căn hộ P1204 thành công", PROFILE);
        }
        public HouseholdResult<HouseholdProfile> getHouseholdById(String maHo) { before(); return detailResult; }
        public HouseholdResult<HouseholdProfile> getHouseholdByApartment(String soCanHo) { before(); return detailResult; }
    }
}
