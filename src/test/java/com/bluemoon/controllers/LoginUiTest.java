package com.bluemoon.controllers;

import com.bluemoon.models.*;
import com.bluemoon.services.*;
import com.bluemoon.views.AuthenticationNavigator;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.Modality;
import org.junit.jupiter.api.*;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LoginUiTest {
    private Stage stage;
    private FakeAuth auth;
    private static final Account ACCOUNT = new Account(7, "tester", "Nguyễn An", Role.RESIDENT);
    @BeforeAll static void toolkit() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException alreadyStarted) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }
    @BeforeEach void open() throws Exception {
        auth = new FakeAuth();
        fx(() -> { stage = new Stage(); new AuthenticationNavigator(stage, auth).showLogin(); stage.show(); });
    }
    @AfterEach void close() throws Exception {
        auth.release.countDown();
        fx(() -> {
            // Copy the list because hiding a dialog mutates Window.getWindows().
            for (Window window : java.util.List.copyOf(Window.getWindows())) {
                if (window instanceof Stage dialog && dialog.getOwner() == stage) dialog.close();
            }
            stage.close();
        });
    }
    private DialogPane warningPane() {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage dialog && dialog.getOwner() == stage)
                .map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast)
                .findFirst().orElse(null);
    }
    private void fx(Runnable action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { action.run(); done.complete(null); } catch (Throwable t) { done.completeExceptionally(t); } });
        done.get(10, TimeUnit.SECONDS);
    }
    private <T> T control(String id, Class<T> type) { return type.cast(stage.getScene().lookup("#" + id)); }
    private String message() { return control("status", Label.class).getText(); }
    private void submit() {
        control("username", TextField.class).setText("tester");
        control("password", PasswordField.class).setText("secret123");
        control("loginButton", Button.class).fire();
    }
    private void settled() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            boolean[] complete = {false};
            fx(() -> complete[0] = stage.getScene().lookup("#greeting") != null || !control("loginButton", Button.class).isDisabled());
            if (complete[0]) return;
            Thread.sleep(10);
        }
        fail("UI did not complete authentication");
    }
    @Test void missingConfigurationDisablesLoginWithoutLeakingDetails() throws Exception {
        fx(() -> {
            AuthenticationNavigator.showUnavailable(stage);
            assertTrue(control("loginButton", Button.class).isDisabled());
            assertEquals(LoginController.SYSTEM, message());
        });
    }
    @Test void fxmlAndMissingFields() throws Exception {
        fx(() -> {
            assertNotNull(control("password", PasswordField.class));
            assertTrue(control("loginButton", Button.class).isDefaultButton());
            for (String[] input : new String[][]{{"", ""}, {"tester", ""}, {"", "secret123"}}) {
                control("username", TextField.class).setText(input[0]);
                control("password", PasswordField.class).setText(input[1]);
                control("loginButton", Button.class).fire();
                assertEquals(LoginController.MISSING, message());
            }
            assertEquals(0, auth.calls.get());
        });
    }
    @Test void invalidCredentialsShowsOwnedWarningAndStaysOnLoginAfterDismissal() throws Exception {
        auth.result = AuthResult.failure(AuthStatus.INVALID_CREDENTIALS, "SQL password=secret");
        javafx.scene.Parent[] loginRoot = new javafx.scene.Parent[1];
        fx(() -> { loginRoot[0] = stage.getScene().getRoot(); submit(); });
        settled();
        fx(() -> {
            DialogPane pane = warningPane();
            assertNotNull(pane, "AC-02 requires a visible warning dialog");
            try {
                assertTrue(pane.getStyleClass().contains("warning"));
                assertEquals("Tài khoản hoặc mật khẩu không chính xác!", pane.getContentText());
                assertNull(pane.getHeaderText());
                assertEquals(Modality.WINDOW_MODAL, ((Stage) pane.getScene().getWindow()).getModality());
                assertSame(loginRoot[0], stage.getScene().getRoot());
                assertNull(stage.getScene().lookup("#greeting"));
            } finally {
                ((Button) pane.lookupButton(ButtonType.OK)).fire();
            }
            assertNull(warningPane());
            assertSame(loginRoot[0], stage.getScene().getRoot());
            assertFalse(control("loginButton", Button.class).isDisabled());
        });
    }
    @Test void failuresStayOnLoginAndRestoreControls() throws Exception {
        for (AuthStatus status : new AuthStatus[]{AuthStatus.INVALID_CREDENTIALS, AuthStatus.ACCOUNT_LOCKED, AuthStatus.SYSTEM_ERROR, AuthStatus.VALIDATION_ERROR}) {
            auth.result = AuthResult.failure(status, status == AuthStatus.VALIDATION_ERROR ? "Validation" : "SQL password=secret");
            fx(this::submit); settled();
            fx(() -> {
                String expected = switch (status) {
                    case INVALID_CREDENTIALS -> "Tài khoản hoặc mật khẩu không chính xác!";
                    case ACCOUNT_LOCKED -> "Tài khoản của bạn đã bị tạm khóa!";
                    case SYSTEM_ERROR -> LoginController.SYSTEM;
                    default -> "Validation";
                };
                assertEquals(expected, message());
                DialogPane pane = warningPane();
                if (status == AuthStatus.INVALID_CREDENTIALS) {
                    assertNotNull(pane);
                    ((Button) pane.lookupButton(ButtonType.OK)).fire();
                } else {
                    assertNull(pane, "Other statuses must not show the AC-02 warning");
                }
                assertNull(stage.getScene().lookup("#greeting"));
                assertFalse(control("username", TextField.class).isDisabled());
                assertEquals("", control("password", PasswordField.class).getText());
            });
        }
    }
    @Test void successAndLogoutClearSessionAndFields() throws Exception {
        auth.result = AuthResult.success(ACCOUNT);
        fx(this::submit); settled();
        fx(() -> {
            assertEquals("Xin chào: Nguyễn An", control("greeting", Label.class).getText());
            assertEquals("Vai trò: RESIDENT", control("role", Label.class).getText());
            control("logoutButton", Button.class).fire();
            assertTrue(auth.currentUser().isEmpty());
            assertEquals("", control("password", PasswordField.class).getText());
            assertNull(stage.getScene().lookup("#greeting"));
        });
    }
    @Test void backgroundCallBlocksDuplicateAndPreservesInput() throws Exception {
        auth.block = true;
        fx(() -> {
            submit();
            control("username", TextField.class).getOnAction().handle(new javafx.event.ActionEvent());
            assertTrue(control("loginButton", Button.class).isDisabled());
        });
        assertTrue(auth.entered.await(5, TimeUnit.SECONDS));
        assertEquals(1, auth.calls.get());
        assertFalse(auth.onFx);
        auth.release.countDown(); settled();
    }
    @Test void inputIsPassedWithoutTrimming() throws Exception {
        fx(() -> {
            control("username", TextField.class).setText(" tester ");
            control("password", PasswordField.class).setText(" secret123 ");
            control("password", PasswordField.class).getOnAction().handle(new javafx.event.ActionEvent());
        });
        settled();
        assertEquals(" tester ", auth.receivedUser);
        assertEquals(" secret123 ", auth.receivedPassword);
    }
    @Test void forcedHideDuringLoginDropsLateDashboardAndClearsLateSession() throws Exception {
        auth.block = true; auth.result = AuthResult.success(ACCOUNT);
        fx(this::submit);
        assertTrue(auth.entered.await(5, TimeUnit.SECONDS));
        fx(() -> stage.hide());
        auth.release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (auth.logouts.get() == 0 && System.nanoTime() < deadline) { fx(() -> {}); Thread.sleep(10); }
        fx(() -> {
            assertTrue(auth.logouts.get() > 0); assertTrue(auth.currentUser().isEmpty());
            assertFalse(stage.isShowing()); assertNull(stage.getScene().lookup("#greeting"));
        });
    }
    @Test void unexpectedExceptionIsSanitizedAndCanRetry() throws Exception {
        auth.throwError = true;
        fx(this::submit); settled();
        fx(() -> assertEquals(LoginController.SYSTEM, message()));
        auth.throwError = false;
        fx(this::submit); settled();
        assertEquals(2, auth.calls.get());
    }
    @Test void firstLoginCreatesSceneWithStyledVisibleControls() throws Exception {
        fx(() -> {
            stage.close();
            stage = new Stage();
            assertNull(stage.getScene());
            new AuthenticationNavigator(stage, auth).showLogin();
            assertNotNull(stage.getScene());
            assertEquals(720, stage.getScene().getWidth(), 1);
            assertEquals(600, stage.getScene().getHeight(), 1);
            stage.show();
        });
        pulse();
        fx(() -> assertLayout(false));
    }
    @Test void repeatedNormalNavigationPreservesCustomGeometrySceneCssAndHandlers() throws Exception {
        fx(() -> { stage.setWidth(1030); stage.setHeight(800); });
        pulse();
        javafx.scene.Scene[] scene = new javafx.scene.Scene[1];
        double[] size = new double[4];
        fx(() -> { scene[0] = stage.getScene(); captureSize(size); });
        cyclePreservingWindow(scene[0], size, false, 3);
        assertEquals(3, auth.calls.get());
        assertEquals(3, auth.logouts.get());
    }
    @Test void maximizedNavigationKeepsRealSceneLayoutAndRestoresCustomSize() throws Exception {
        fx(() -> { stage.setWidth(1010); stage.setHeight(790); });
        pulse();
        javafx.scene.Scene[] scene = new javafx.scene.Scene[1];
        double[] normalSize = new double[4];
        fx(() -> { scene[0] = stage.getScene(); captureSize(normalSize); stage.setMaximized(true); });
        pulse();
        double[] maximumSize = new double[4];
        fx(() -> {
            assertTrue(stage.isMaximized());
            var bounds = javafx.stage.Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                    stage.getWidth(), stage.getHeight()).get(0).getVisualBounds();
            // Verify actual desktop-sized client geometry, not just the maximized property.
            assertEquals(bounds.getWidth(), stage.getWidth(), 32);
            assertEquals(bounds.getHeight(), stage.getHeight(), 48);
            assertTrue(stage.getScene().getWidth() > 720);
            assertTrue(stage.getScene().getHeight() > 600);
            captureSize(maximumSize);
            System.out.printf("Issue5 maximized: stage=%.0fx%.0f scene=%.0fx%.0f%n",
                    maximumSize[0], maximumSize[1], maximumSize[2], maximumSize[3]);
        });
        cyclePreservingWindow(scene[0], maximumSize, true, 2);
        fx(() -> stage.setMaximized(false));
        pulse();
        fx(() -> assertWindow(scene[0], normalSize, false));
        cyclePreservingWindow(scene[0], normalSize, false, 1);
    }
    private void cyclePreservingWindow(javafx.scene.Scene scene, double[] size, boolean maximized, int times) throws Exception {
        for (int index = 0; index < times; index++) {
            javafx.scene.Parent[] previous = new javafx.scene.Parent[1];
            auth.result = AuthResult.success(ACCOUNT);
            fx(() -> { previous[0] = scene.getRoot(); submit(); });
            settled(); pulse();
            fx(() -> {
                assertWindow(scene, size, maximized);
                assertLayout(true);
                assertNull(previous[0].getScene(), "Old login root must detach with its handlers");
                previous[0] = scene.getRoot();
                saveSnapshot((maximized ? "maximized" : "normal") + "-dashboard");
                control("logoutButton", Button.class).fire();
            });
            pulse();
            fx(() -> {
                assertWindow(scene, size, maximized);
                assertLayout(false);
                assertNull(previous[0].getScene(), "Old dashboard must detach");
                assertTrue(auth.currentUser().isEmpty());
                assertEquals("", control("password", PasswordField.class).getText());
                saveSnapshot((maximized ? "maximized" : "normal") + "-login");
            });
        }
    }
    private void captureSize(double[] size) {
        size[0] = stage.getWidth(); size[1] = stage.getHeight();
        size[2] = stage.getScene().getWidth(); size[3] = stage.getScene().getHeight();
    }
    private void assertWindow(javafx.scene.Scene scene, double[] size, boolean maximized) {
        assertSame(scene, stage.getScene());
        assertEquals(maximized, stage.isMaximized());
        assertFalse(stage.isFullScreen());
        assertEquals(size[0], stage.getWidth(), 1); assertEquals(size[1], stage.getHeight(), 1);
        assertEquals(size[2], scene.getWidth(), 1); assertEquals(size[3], scene.getHeight(), 1);
        assertEquals(scene.getWidth(), scene.getRoot().getLayoutBounds().getWidth(), 1);
        assertEquals(scene.getHeight(), scene.getRoot().getLayoutBounds().getHeight(), 1);
    }
    private void assertLayout(boolean dashboard) {
        var scene = stage.getScene();
        var root = scene.getRoot();
        root.applyCss(); root.layout();
        assertEquals(1, root.getStylesheets().size());
        assertTrue(root.getStylesheets().get(0).endsWith("/auth.css"));
        assertTrue(scene.getStylesheets().isEmpty(), "Navigation must not append duplicate scene CSS");
        assertEquals(javafx.scene.paint.Color.WHITE,
                ((javafx.scene.layout.Region) root.lookup(".card")).getBackground().getFills().get(0).getFill());
        for (String id : dashboard ? new String[]{"greeting", "role", "logoutButton"}
                : new String[]{"username", "password", "loginButton", "status"}) {
            var node = scene.lookup("#" + id);
            assertNotNull(node); assertTrue(node.isVisible());
            var bounds = node.localToScene(node.getBoundsInLocal());
            assertTrue(bounds.getMinX() >= 0 && bounds.getMinY() >= 0, id + " clips above/left");
            assertTrue(bounds.getWidth() > 0 && bounds.getHeight() > 0, id + " has no layout");
            assertTrue(bounds.getMaxX() <= scene.getWidth() + 1 && bounds.getMaxY() <= scene.getHeight() + 1,
                    id + " clips below/right");
        }
    }
    private void pulse() throws Exception {
        CompletableFuture<Void> rendered = new CompletableFuture<>();
        fx(() -> new javafx.animation.AnimationTimer() {
            int frames;
            @Override public void handle(long now) {
                if (++frames >= 5) { stop(); rendered.complete(null); }
            }
        }.start());
        rendered.get(10, TimeUnit.SECONDS);
    }
    private void saveSnapshot(String name) {
        if (!Boolean.getBoolean("bluemoon.issue5.snapshots")) return;
        var image = stage.getScene().snapshot(null);
        var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
            bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        try {
            var path = java.nio.file.Path.of("target", "issue5-previews", name + ".png");
            java.nio.file.Files.createDirectories(path.getParent());
            javax.imageio.ImageIO.write(bitmap, "png", path.toFile());
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    private static final class FakeAuth implements AuthenticationService {
        final AtomicInteger calls = new AtomicInteger(), logouts = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile boolean block, onFx, throwError;
        volatile String receivedUser, receivedPassword;
        volatile Account session;
        volatile AuthResult result = AuthResult.failure(AuthStatus.INVALID_CREDENTIALS, "ignored");
        public AuthResult login(String username, String password) {
            receivedUser = username; receivedPassword = password;
            calls.incrementAndGet(); onFx = Platform.isFxApplicationThread(); entered.countDown();
            if (block) try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            if (throwError) throw new IllegalStateException("SQL credentials secret");
            session = result.account().orElse(null);
            return result;
        }
        public void logout() { logouts.incrementAndGet(); session = null; }
        public Optional<Account> currentUser() { return Optional.ofNullable(session); }
    }
}
