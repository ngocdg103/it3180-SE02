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
    @Test void unexpectedExceptionIsSanitizedAndCanRetry() throws Exception {
        auth.throwError = true;
        fx(this::submit); settled();
        fx(() -> assertEquals(LoginController.SYSTEM, message()));
        auth.throwError = false;
        fx(this::submit); settled();
        assertEquals(2, auth.calls.get());
    }
    private static final class FakeAuth implements AuthenticationService {
        final AtomicInteger calls = new AtomicInteger();
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
        public void logout() { session = null; }
        public Optional<Account> currentUser() { return Optional.ofNullable(session); }
    }
}
