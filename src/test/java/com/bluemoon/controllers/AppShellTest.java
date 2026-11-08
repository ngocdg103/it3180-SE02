package com.bluemoon.controllers;

import com.bluemoon.config.AppShellDatabaseFixture;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import com.bluemoon.views.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class AppShellTest {
    private Stage stage;
    private final Auth auth = new Auth();
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(10,TimeUnit.SECONDS);
    }
    private static void fx(Runnable action) throws Exception {
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> { try { action.run(); done.complete(null); } catch(Throwable t) { done.completeExceptionally(t); } });
        done.get(10,TimeUnit.SECONDS);
    }
    private void until(BooleanSupplier test) throws Exception {
        long end = System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime()<end) {
            boolean[] ok={false}; fx(() -> ok[0]=test.getAsBoolean()); if(ok[0]) return; Thread.sleep(15);
        }
        fail("UI did not settle");
    }
    private Button button(String id) { return (Button)stage.getScene().lookup("#"+id); }
    private Label label(String id) { return (Label)stage.getScene().lookup("#"+id); }
    private void login(List<ModuleRegistration> modules, Role role) throws Exception {
        auth.role=role;
        fx(() -> {
            stage=new Stage(); new AuthenticationNavigator(stage,auth,modules).showLogin(); stage.show();
            ((TextField)stage.getScene().lookup("#username")).setText("tester");
            ((PasswordField)stage.getScene().lookup("#password")).setText("secret123");
            button("loginButton").fire();
        });
        until(() -> stage.getScene().lookup("#greeting")!=null);
    }
    @AfterEach void close() throws Exception {
        fx(() -> { if(stage!=null) stage.hide(); }); FeeCategorySessionFixture.use(null);
    }
    @Test void loginModuleHomeLogoutPreserveSceneAndReleaseListeners() throws Exception {
        var opens=new AtomicInteger(); var module=new Module();
        var modules=List.of(new ModuleRegistration("fees","Khoản thu",Permission.FEE_CATEGORY_VIEW,() -> { opens.incrementAndGet(); return module; }));
        login(modules,Role.ADMIN);
        fx(() -> {
            Scene scene=stage.getScene(); double w=stage.getWidth(), h=stage.getHeight();
            button("feesButton").fire(); button("feesButton").fire();
            assertEquals(1,opens.get()); assertSame(scene,stage.getScene());
            assertEquals(w,stage.getWidth(),1); assertEquals(h,stage.getHeight(),1);
            module.busy.set(true);
            assertTrue(button("homeButton").isDisabled()); assertTrue(button("logoutButton").isDisabled());
            button("homeButton").getOnAction().handle(new javafx.event.ActionEvent());
            assertEquals(0,module.closes); assertEquals(1,opens.get());
            module.busy.set(false); button("homeButton").fire();
            assertEquals(1,module.closes); assertNull(module.view.getScene());
            module.busy.set(true); assertFalse(button("logoutButton").isDisabled(),"Listener detached on close");
            button("logoutButton").fire();
            assertTrue(SessionContext.getCurrentUser().isEmpty()); assertSame(scene,stage.getScene());
            assertNotNull(button("loginButton")); assertNull(scene.lookup("#greeting"));
        });
    }
    @Test void residentVisibilityAndDirectOpenDeniedBeforeFactory() throws Exception {
        var count=new AtomicInteger();
        var registration=new ModuleRegistration("fees","Khoản thu",Permission.FEE_CATEGORY_VIEW,() -> { count.incrementAndGet(); return new Module(); });
        login(List.of(registration),Role.RESIDENT);
        fx(() -> { assertFalse(button("feesButton").isVisible()); assertFalse(button("feesButton").isManaged()); });
        // Independent controller access path, not just hidden Button.fire().
        fx(() -> {
            var controller=new DashboardController(auth,SessionContext.getCurrentUser().orElseThrow(),() -> {},new RoleBasedAuthorizationService(null),List.of(registration));
            var loader=new FXMLLoader(getClass().getResource("/com/bluemoon/views/DashboardView.fxml"));
            loader.setControllerFactory(t -> controller);
            try { loader.load(); } catch(Exception e) { throw new RuntimeException(e); }
            controller.openModule("fees"); assertEquals(0,count.get()); controller.close();
        });
    }
    @Test void accountChangedAfterLoginCannotOpenProtectedModule() throws Exception {
        var count=new AtomicInteger();
        login(List.of(new ModuleRegistration("fees","Khoản thu",Permission.FEE_CATEGORY_VIEW,() -> { count.incrementAndGet(); return new Module(); })),Role.ADMIN);
        fx(() -> { FeeCategorySessionFixture.use(null); button("feesButton").fire(); assertEquals(0,count.get()); assertNotNull(button("loginButton")); });
    }
    @Test void loginSuccessWithoutAuthenticatedSessionCannotEnterDashboard() throws Exception {
        auth.forgetSession=true;
        fx(() -> {
            stage=new Stage(); new AuthenticationNavigator(stage,auth,List.of()).showLogin(); stage.show();
            ((TextField)stage.getScene().lookup("#username")).setText("tester");
            ((PasswordField)stage.getScene().lookup("#password")).setText("secret123"); button("loginButton").fire();
        });
        until(() -> button("loginButton")!=null && !button("loginButton").isDisabled());
        fx(() -> { assertNull(stage.getScene().lookup("#greeting")); assertTrue(SessionContext.getCurrentUser().isEmpty()); });
    }
    @Test void permissionRevokedAfterMenuRenderIsRechecked() throws Exception {
        var count=new AtomicInteger();
        login(List.of(new ModuleRegistration("fees","Khoản thu",Permission.FEE_CATEGORY_VIEW,() -> { count.incrementAndGet(); return new Module(); })),Role.ADMIN);
        // Keep auth identity unchanged but change production authorization source to a denied role.
        fx(() -> { auth.fixed=auth.currentUser().orElseThrow(); FeeCategorySessionFixture.use(Role.RESIDENT); button("feesButton").fire(); assertEquals(0,count.get()); assertTrue(label("shellStatus").getText().contains("không có quyền")); });
    }
    @Test void moduleFactoryFailureIsSanitizedAndRetryable() throws Exception {
        login(List.of(new ModuleRegistration("broken","Lỗi",Permission.FEE_CATEGORY_VIEW,() -> { throw new IllegalStateException("SQL password=secret"); })),Role.ACCOUNTANT);
        fx(() -> {
            button("brokenButton").fire(); assertTrue(label("shellStatus").getText().contains("Không mở được"));
            assertFalse(label("shellStatus").getText().contains("secret")); assertFalse(button("logoutButton").isDisabled());
            button("homeButton").fire(); button("logoutButton").fire(); assertTrue(SessionContext.getCurrentUser().isEmpty());
        });
    }
    @Test void actualProductionModulesHandleUnavailableDatabaseAndSwitchCleanly() throws Exception {
        login(ProductionModules.create(AppShellDatabaseFixture.unavailable()),Role.ACCOUNTANT);
        fx(() -> button("feeCategoriesButton").fire());
        until(() -> stage.getScene().lookup("#retryButton")!=null && button("retryButton").isVisible() && !button("homeButton").isDisabled());
        fx(() -> { assertFalse(label("shellStatus").getText().contains("jdbc")); button("householdsButton").fire(); });
        until(() -> stage.getScene().lookup("#households")!=null && !button("homeButton").isDisabled());
        fx(() -> { assertTrue(button("createButton").isDisabled()); button("homeButton").fire(); button("logoutButton").fire(); assertTrue(SessionContext.getCurrentUser().isEmpty()); });
    }
    @SuppressWarnings("unchecked")
    @Test void feeCreateThroughShellUsesRealServiceAndBlocksExitUntilWriteCompletes() throws Exception {
        var repository=new Fees();
        login(List.of(new ModuleRegistration("fees","Danh mục khoản thu",Permission.FEE_CATEGORY_VIEW,
            () -> new FeeCategoryModule(new FeeTypeServiceImpl(repository)))),Role.ADMIN);
        fx(() -> { stage.setWidth(1250); stage.setHeight(850); button("feesButton").fire(); });
        until(() -> !button("homeButton").isDisabled());
        fx(() -> {
            ((TextField)stage.getScene().lookup("#codeField")).setText("SHELL_FEE");
            ((TextField)stage.getScene().lookup("#nameField")).setText("Phí tích hợp");
            ((ComboBox<FeeKind>)stage.getScene().lookup("#kindBox")).setValue(FeeKind.BAT_BUOC);
            ((ComboBox<ChargeMethod>)stage.getScene().lookup("#methodBox")).setValue(ChargeMethod.THEO_CAN_HO);
            button("saveButton").fire();
        });
        assertTrue(repository.entered.await(5,TimeUnit.SECONDS));
        try {
            fx(() -> {
                assertTrue(button("logoutButton").isDisabled()); assertTrue(button("homeButton").isDisabled());
                button("logoutButton").getOnAction().handle(new javafx.event.ActionEvent());
                assertTrue(SessionContext.getCurrentUser().isPresent());
            });
        } finally { repository.release.countDown(); }
        until(() -> !button("homeButton").isDisabled() && ((TableView<?>)stage.getScene().lookup("#feeTable")).getItems().size()==1);
        pulse(); fx(() -> { snapshot("fee-category"); button("homeButton").fire(); snapshot("dashboard"); button("logoutButton").fire(); });
        assertEquals(1,repository.rows.size()); assertFalse(repository.onFx);
    }
    @Test void windowCloseBlockedWhileBusyThenClosesModuleAndClearsSession() throws Exception {
        var module=new Module();
        login(List.of(new ModuleRegistration("fees","Khoản thu",Permission.FEE_CATEGORY_VIEW,() -> module)),Role.ADMIN);
        fx(() -> {
            button("feesButton").fire(); module.busy.set(true);
            stage.fireEvent(new WindowEvent(stage,WindowEvent.WINDOW_CLOSE_REQUEST));
            assertTrue(stage.isShowing()); assertEquals(0,module.closes);
            module.busy.set(false); stage.fireEvent(new WindowEvent(stage,WindowEvent.WINDOW_CLOSE_REQUEST));
            assertEquals(1,module.closes); assertTrue(SessionContext.getCurrentUser().isEmpty());
        });
    }
    @Test void moduleTransitionsPreserveMaximizedAndRestoredGeometry() throws Exception {
        login(List.of(new ModuleRegistration("fees","Khoản thu",Permission.FEE_CATEGORY_VIEW,Module::new)),Role.ADMIN);
        fx(() -> { stage.setWidth(1040); stage.setHeight(810); }); pulse();
        double[] normal=new double[2]; Scene[] scene=new Scene[1];
        fx(() -> { normal[0]=stage.getWidth(); normal[1]=stage.getHeight(); scene[0]=stage.getScene(); stage.setMaximized(true); }); pulse();
        fx(() -> {
            double width=stage.getWidth(),height=stage.getHeight();
            button("feesButton").fire(); button("homeButton").fire();
            assertTrue(stage.isMaximized()); assertSame(scene[0],stage.getScene());
            assertEquals(width,stage.getWidth(),1); assertEquals(height,stage.getHeight(),1);
            stage.setMaximized(false);
        }); pulse();
        fx(() -> { assertEquals(normal[0],stage.getWidth(),1); assertEquals(normal[1],stage.getHeight(),1); });
    }
    private static void pulse() throws Exception {
        var done=new CompletableFuture<Void>();
        fx(() -> new javafx.animation.AnimationTimer() {
            int frames;
            public void handle(long now) { if(++frames==5) { stop(); done.complete(null); } }
        }.start()); done.get(10,TimeUnit.SECONDS);
    }
    private void snapshot(String name) {
        stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
        var image=stage.getScene().snapshot(null);
        var bitmap=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<bitmap.getHeight();y++) for(int x=0;x<bitmap.getWidth();x++) bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        try {
            var path=java.nio.file.Path.of("target","app-shell-previews",name+".png");
            java.nio.file.Files.createDirectories(path.getParent()); javax.imageio.ImageIO.write(bitmap,"png",path.toFile());
        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private static final class Fees implements com.bluemoon.dao.FeeTypeRepository {
        final List<FeeType> rows=new CopyOnWriteArrayList<>();
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1); volatile boolean onFx;
        public List<FeeType> findAll(FeeStatus status) { onFx|=Platform.isFxApplicationThread(); return List.copyOf(rows); }
        public Optional<FeeType> findByCode(String code) { return rows.stream().filter(f -> f.maKhoanThu().equals(code)).findFirst(); }
        public void insert(FeeType fee) throws java.sql.SQLException {
            onFx|=Platform.isFxApplicationThread(); entered.countDown();
            try { if(!release.await(10,TimeUnit.SECONDS)) throw new java.sql.SQLException("test timeout"); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new java.sql.SQLException(e); }
            rows.add(fee);
        }
        public Optional<FeeType> update(FeeType fee) { throw new AssertionError(); }
        public Optional<FeeType> deactivate(String code) { throw new AssertionError(); }
    }
    private static final class Module implements DashboardModule {
        final SimpleBooleanProperty busy=new SimpleBooleanProperty(); final Parent view=new javafx.scene.layout.StackPane(new Label("Module")); int closes;
        public Parent view() { return view; }
        public SimpleBooleanProperty busyProperty() { return busy; }
        public void close() { closes++; }
    }
    private static final class Auth implements AuthenticationService {
        Role role; Account fixed; boolean forgetSession;
        public AuthResult login(String user,String password) {
            if(forgetSession) return AuthResult.success(new Account(7,"tester","Test",Role.ADMIN));
            FeeCategorySessionFixture.use(role); return AuthResult.success(currentUser().orElseThrow());
        }
        public Optional<Account> currentUser() { return fixed==null ? SessionContext.getCurrentUser() : Optional.of(fixed); }
        public void logout() { fixed=null; FeeCategorySessionFixture.use(null); }
    }
}
