package com.bluemoon.controllers;

import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ApartmentHistoryPanelTest {
    private ApartmentHistoryPanel panel;
    private final AtomicBoolean busy=new AtomicBoolean();
    private final CountDownLatch release=new CountDownLatch(1);
    @BeforeAll static void toolkit() throws Exception {
        var ready=new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch(IllegalStateException started) { ready.complete(null); }
        ready.get(10,TimeUnit.SECONDS);
    }
    static void fx(Runnable action) throws Exception {
        var done=new CompletableFuture<Void>();
        Platform.runLater(() -> { try { action.run(); done.complete(null); } catch(Throwable e) { done.completeExceptionally(e); } });
        done.get(10,TimeUnit.SECONDS);
    }
    @AfterEach void close() throws Exception {
        release.countDown(); fx(() -> { if(panel!=null) panel.close(); }); FeeCategorySessionFixture.use(null);
    }
    private BillingService service(java.util.concurrent.Callable<BillingResult<List<MonthlyCharge>>> result) {
        return (BillingService)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{BillingService.class},
                (proxy,method,args) -> { assertEquals("getApartmentHistory",method.getName());
                    assertFalse(Platform.isFxApplicationThread()); assertEquals("P1204",args[0]); return result.call(); });
    }
    private void settled() throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(busy.get() && System.nanoTime()<end) Thread.sleep(10);
        assertFalse(busy.get()); fx(() -> {});
    }
    @Test void emptyAndDatabaseErrorCanRetryWithoutLeakingSql() throws Exception {
        FeeCategorySessionFixture.use(Role.ACCOUNTANT); var calls=new AtomicInteger();
        fx(() -> panel=new ApartmentHistoryPanel(service(() -> {
            if(calls.incrementAndGet()==1) throw new IllegalStateException("jdbc password=secret");
            return BillingResult.success("OK",List.of());
        }),"P1204",busy::set));
        settled(); fx(() -> {
            var status=(Label)panel.view().lookup("#historyStatus");
            assertFalse(status.getText().contains("secret")); assertTrue(status.getText().contains("Không tải"));
            ((Button)panel.view().lookup("#historyRetry")).fire();
        });
        settled(); fx(() -> assertTrue(((Label)panel.view().lookup("#historyStatus")).getText().contains("Chưa có")));
        assertEquals(2,calls.get());
    }
    @Test void residentDeniedBeforeServiceCall() throws Exception {
        FeeCategorySessionFixture.use(Role.RESIDENT);
        fx(() -> panel=new ApartmentHistoryPanel(service(() -> { fail("Must deny before service"); return null; }),"P1204",busy::set));
        fx(() -> assertTrue(((Label)panel.view().lookup("#historyStatus")).getText().contains("không có quyền")));
    }
    @Test void disposeDuringLoadClearsBusyAndIgnoresLateResult() throws Exception {
        FeeCategorySessionFixture.use(Role.ADMIN); var entered=new CountDownLatch(1);
        fx(() -> panel=new ApartmentHistoryPanel(service(() -> { entered.countDown(); release.await();
            return BillingResult.success("OK",List.of()); }),"P1204",busy::set));
        assertTrue(entered.await(5,TimeUnit.SECONDS)); assertTrue(busy.get());
        fx(() -> { panel.close(); assertFalse(busy.get()); }); release.countDown();
        fx(() -> assertTrue(((Label)panel.view().lookup("#historyStatus")).getText().isBlank()));
    }
    @Test void revokedPermissionDiscardsCompletedResult() throws Exception {
        FeeCategorySessionFixture.use(Role.ADMIN); var entered=new CountDownLatch(1);
        fx(() -> panel=new ApartmentHistoryPanel(service(() -> { entered.countDown(); release.await();
            return BillingResult.success("OK",List.of()); }),"P1204",busy::set));
        assertTrue(entered.await(5,TimeUnit.SECONDS)); FeeCategorySessionFixture.use(Role.RESIDENT); release.countDown();
        settled(); fx(() -> assertTrue(((Label)panel.view().lookup("#historyStatus")).getText().contains("không có quyền")));
    }
}
