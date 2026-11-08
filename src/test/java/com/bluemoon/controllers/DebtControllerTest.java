package com.bluemoon.controllers;

import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class DebtControllerTest {
    private final Repository repository=new Repository();
    private DebtController controller;
    private Parent root;
    @BeforeAll static void toolkit() throws Exception {
        var ready=new CompletableFuture<Void>(); try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); } catch(IllegalStateException started) { ready.complete(null); } ready.get(10,TimeUnit.SECONDS);
    }
    @AfterEach void cleanup() throws Exception {
        repository.release.countDown(); if(controller!=null) { fx(() -> { controller.dispose(); return null; }); assertTrue(pool().awaitTermination(5,TimeUnit.SECONDS)); fx(() -> null); } FeeCategorySessionFixture.use(null);
    }
    ExecutorService pool() throws Exception { var f=DebtController.class.getDeclaredField("worker"); f.setAccessible(true); return (ExecutorService)f.get(controller); }
    static <T> T fx(Callable<T> action) throws Exception { var done=new CompletableFuture<T>(); Platform.runLater(() -> { try { done.complete(action.call()); } catch(Throwable failure) { done.completeExceptionally(failure); } }); return done.get(10,TimeUnit.SECONDS); }
    @SuppressWarnings("unchecked") <T> T node(String id) { return (T)root.lookup("#"+id); }
    void until(BooleanSupplier condition) throws Exception { long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8); while(System.nanoTime()<end) { if(fx(condition::getAsBoolean)) { return; } Thread.sleep(10); } fail("UI callback missing"); }
    void open(Role role) throws Exception {
        FeeCategorySessionFixture.use(role); var auth=new RoleBasedAuthorizationService(null);
        root=fx(() -> { controller=new DebtController(new DebtServiceImpl(repository,auth),auth); var view=DebtController.load(controller); new javafx.scene.Scene(view,1150,650); view.applyCss(); view.layout(); return view; });
    }
    void ready() throws Exception { until(() -> this.<TableView<DebtRow>>node("debtTable").getItems().size()==3&&!controller.busyProperty().get()); }
    String message() { return this.<Label>node("messageLabel").getText(); }
    void reload() throws Exception { fx(() -> { this.<Button>node("filterButton").fire(); return null; }); }
    @Test void initialLatestAndOutstandingFloorFiltersShowWholePeriodKpisAndFilteredFooter() throws Exception {
        open(Role.ADMIN); ready(); assertEquals("2026-11",fx(() -> this.<TextField>node("periodField").getText())); assertEquals("300.00",fx(() -> this.<Label>node("billedLabel").getText()));
        fx(() -> { this.<ComboBox<DebtController.StatusChoice>>node("statusBox").setValue(DebtController.StatusChoice.OUTSTANDING); this.<TextField>node("fromField").setText("12"); this.<TextField>node("toField").setText("15"); this.<Button>node("filterButton").fire(); return null; });
        until(() -> this.<TableView<DebtRow>>node("debtTable").getItems().size()==1&&!controller.busyProperty().get());
        assertEquals("170.00",fx(() -> this.<Label>node("debtLabel").getText())); assertTrue(fx(() -> this.<Label>node("footerLabel").getText().contains("70.00"))); assertFalse(repository.fxAccess);
    }
    @Test void invalidFloorAndPeriodDoNotQueryAndNoDataClearsOldKpis() throws Exception {
        open(Role.ACCOUNTANT); ready(); int before=repository.calls;
        fx(() -> { this.<TextField>node("fromField").setText("abc"); return null; }); reload(); until(() -> message().contains("số nguyên")); assertEquals(before,repository.calls);
        fx(() -> { this.<TextField>node("fromField").clear(); this.<TextField>node("periodField").setText("2026-13"); return null; }); reload(); until(() -> message().contains("YYYY-MM")); assertEquals(before,repository.calls);
        repository.empty=true; fx(() -> { this.<TextField>node("periodField").setText("2026-11"); return null; }); reload(); until(() -> message().contains("Không có dữ liệu"));
        assertEquals("—",fx(() -> this.<Label>node("billedLabel").getText())); assertTrue(fx(() -> this.<TableView<DebtRow>>node("debtTable").getItems().isEmpty()));
    }
    @Test void residentAndNoSessionCannotRead() throws Exception {
        open(Role.RESIDENT); until(() -> message().contains("không có quyền")); assertEquals(0,repository.calls);
        fx(() -> { controller.dispose(); return null; }); assertTrue(pool().awaitTermination(5,TimeUnit.SECONDS)); open(null); until(() -> message().contains("không có quyền")); assertEquals(0,repository.calls);
    }
    @Test void sqlFailureIsGenericAndCanReload() throws Exception {
        repository.failure=true; open(Role.ADMIN); until(() -> message().contains("Không tải được")); assertFalse(fx(this::message).contains("secret"));
        repository.failure=false; reload(); ready();
    }
    @Test void slowOldRequestCannotOverwriteNewFilterAndFxRemainsResponsive() throws Exception {
        repository.block=true; open(Role.ADMIN); assertTrue(repository.entered.await(5,TimeUnit.SECONDS)); assertEquals(42,fx(() -> 42));
        fx(() -> { this.<TextField>node("periodField").setText("2026-10"); this.<Button>node("filterButton").fire(); return null; }); repository.release.countDown();
        until(() -> this.<Label>node("periodLabel").getText().contains("2026-10")&&!controller.busyProperty().get()); assertEquals("2026-10",repository.period);
    }
    @Test void closeDropsLateResultAndLogoutPreventsPublication() throws Exception {
        repository.block=true; open(Role.ADMIN); assertTrue(repository.entered.await(5,TimeUnit.SECONDS));
        fx(() -> { controller.dispose(); return null; }); repository.release.countDown(); assertTrue(pool().awaitTermination(5,TimeUnit.SECONDS)); fx(() -> null);
        assertTrue(fx(() -> this.<TableView<DebtRow>>node("debtTable").getItems().isEmpty()));
    }
    @Test void logoutDuringPendingReadClearsDataAndDisablesFilters() throws Exception {
        repository.block=true; open(Role.ACCOUNTANT); assertTrue(repository.entered.await(5,TimeUnit.SECONDS)); FeeCategorySessionFixture.use(null); repository.release.countDown();
        until(() -> message().contains("không có quyền")); assertTrue(fx(() -> this.<Button>node("filterButton").isDisabled())); assertTrue(fx(() -> this.<TableView<DebtRow>>node("debtTable").getItems().isEmpty()));
    }
    static DebtRow row(String apartment,int floor,String paid,ChargeStatus status) {
        var amount=new BigDecimal(paid); return new DebtRow("HD-202611-"+apartment,"HK-"+apartment,apartment,floor,"Chủ hộ",new BigDecimal("80"),4,new BigDecimal("100"),amount,new BigDecimal("100").subtract(amount),status,amount.signum()==0?null:LocalDate.of(2026,11,8));
    }
    private static class Repository implements DebtRepository {
        volatile int calls; volatile boolean fxAccess,empty,failure,block; volatile String period;
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        public Optional<PeriodData> loadPeriod(String input) throws SQLException {
            calls++; fxAccess|=Platform.isFxApplicationThread(); period=input;
            if(block&&calls==1) { entered.countDown(); try { if(!release.await(8,TimeUnit.SECONDS)) { throw new SQLException("gate timeout"); } } catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new SQLException(error); } }
            if(failure) { throw new SQLException("secret SQL"); }
            return empty?Optional.empty():Optional.of(new PeriodData(input==null?"2026-11":input,List.of(row("P1001",10,"0",ChargeStatus.CHUA_DONG),row("P1201",12,"30",ChargeStatus.DONG_THIEU),row("P2001",20,"100",ChargeStatus.DA_DONG_DU))));
        }
        public Optional<HouseholdDebt> getHouseholdBalance(String code) { throw new AssertionError("Not used by period screen"); }
    }
}
