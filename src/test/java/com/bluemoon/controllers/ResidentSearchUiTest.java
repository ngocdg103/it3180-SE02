package com.bluemoon.controllers;

import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ResidentSearchUiTest {
    private Stage stage;private ResidentSearchController controller;
    private final CountDownLatch gate=new CountDownLatch(1),entered=new CountDownLatch(1);
    private final AtomicInteger calls=new AtomicInteger();private boolean busy;private volatile boolean onFx;
    @BeforeAll static void toolkit() throws Exception {var ready=new CompletableFuture<Void>();try{Platform.startup(() -> {Platform.setImplicitExit(false);ready.complete(null);});}catch(IllegalStateException started){ready.complete(null);}ready.get(10,TimeUnit.SECONDS);}
    private static void fx(Runnable work) throws Exception {var done=new CompletableFuture<Void>();Platform.runLater(() -> {try{work.run();done.complete(null);}catch(Throwable e){done.completeExceptionally(e);}});done.get(10,TimeUnit.SECONDS);}
    private void open(ResidentSearchService service) throws Exception {fx(() -> {controller=new ResidentSearchController(service,v -> busy=v,k -> {},() -> {});stage=new Stage();stage.setScene(new Scene(controller.view(),1000,700));stage.show();});}
    private Button button(){return (Button)stage.getScene().lookup("#residentSearchButton");}
    private void submit(){((TextField)stage.getScene().lookup("#searchKeyword")).setText("ngoc");button().fire();}
    private void settled() throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<end){boolean[]done={false};fx(() -> done[0]=!busy);if(done[0])return;Thread.sleep(10);}fail("Search did not settle");}
    @AfterEach void close() throws Exception {gate.countDown();fx(() -> {if(controller!=null)controller.close();if(stage!=null)stage.close();});}
    @Test void blockingSearchIsOffFxAndDuplicateEnterCannotSubmitAgain() throws Exception {
        open((field,text) -> {calls.incrementAndGet();onFx=Platform.isFxApplicationThread();entered.countDown();try{gate.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return ResidentResult.success("Không tìm thấy hồ sơ cư dân phù hợp với từ khóa!",List.of());});
        fx(() -> {submit();((TextField)stage.getScene().lookup("#searchKeyword")).getOnAction().handle(new javafx.event.ActionEvent());assertTrue(button().isDisabled());});
        assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(1,calls.get());assertFalse(onFx);gate.countDown();settled();
    }
    @Test void unexpectedErrorDoesNotLeakAndAllowsRetry() throws Exception {
        open((field,text) -> {throw new IllegalStateException("SQL password=secret");});fx(this::submit);settled();
        fx(() -> {String text=((Label)stage.getScene().lookup("#residentSearchMessage")).getText();assertFalse(text.contains("secret"));assertFalse(button().isDisabled());});
    }
    @Test void closeDisablesOldHandlersAndIgnoresLateResult() throws Exception {
        open((field,text) -> {entered.countDown();try{gate.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return ResidentResult.success("late secret",List.of());});
        fx(this::submit);assertTrue(entered.await(5,TimeUnit.SECONDS));fx(() -> controller.close());gate.countDown();Thread.sleep(100);
        fx(() -> {assertTrue(button().isDisabled());assertFalse(((Label)stage.getScene().lookup("#residentSearchMessage")).getText().contains("late"));assertFalse(busy);});
    }
}
