package com.bluemoon.controllers;

import com.bluemoon.config.*;
import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Actual US-005 create -> committed MySQL -> actual US-006 history and append UI. */
@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST",matches="1")
class InitialFeeRateUiMySqlIT {
    private static final String CODE="FEE_INIT_UI";
    private DatabaseConnection connections;
    private FeeCategoryController category;
    private FeeRateController rate;
    private Parent view;
    private Stage stage;
    private static <T> T fx(Callable<T> action) throws Exception {
        var done=new CompletableFuture<T>();
        Platform.runLater(()->{try{done.complete(action.call());}catch(Throwable e){done.completeExceptionally(e);}});
        return done.get(10,TimeUnit.SECONDS);
    }
    @BeforeAll static void toolkit() throws Exception {
        var ready=new CompletableFuture<Void>();
        try{Platform.startup(()->{Platform.setImplicitExit(false);ready.complete(null);});}
        catch(IllegalStateException already){ready.complete(null);}ready.get(10,TimeUnit.SECONDS);
    }
    private void sql(String text) throws Exception {
        try(var c=connections.getConnection()){
            assertTrue(c.getCatalog().endsWith("_test"));
            assertEquals("MySQL",c.getMetaData().getDatabaseProductName());
            try(var s=c.createStatement()){s.execute(text);}
        }
    }
    @BeforeEach void fixture() throws Exception {
        connections=new DatabaseConnection(DatabaseConfig.load());
        for(String file:new String[]{"us005_fee_types.sql","us006_fee_rates.sql"}) {
            String script=Files.readString(Path.of("database/migrations",file)).replaceAll("(?m)^\\s*--.*$","");
            for(String command:script.split(";"))if(!command.isBlank())sql(command);
        }
        cleanupRows();FeeCategorySessionFixture.use(Role.ADMIN);
    }
    private void cleanupRows() throws Exception {
        sql("DELETE FROM fee_rates WHERE ma_loai_thu='FEE_INIT_UI'");
        sql("DELETE FROM fee_types WHERE ma_loai_thu='FEE_INIT_UI'");
    }
    @AfterEach void close() throws Exception {
        fx(()->{if(category!=null)category.dispose();if(rate!=null)rate.dispose();if(stage!=null)stage.close();return null;});
        FeeCategorySessionFixture.use(null);cleanupRows();
    }
    private void until(BooleanSupplier check) throws Exception {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<limit){if(fx(check::getAsBoolean))return;Thread.sleep(20);}fail("UI did not settle");
    }
    @SuppressWarnings("unchecked") private <T> T node(String id){return (T)view.lookup("#"+id);}
    @Test void createRequiresDateAndUs006ShowsOneCommittedInitialThenPreservesItOnAppend() throws Exception {
        var types=new FeeTypeDAO(connections);var rates=new FeeRateDAO(connections);
        var auth=new RoleBasedAuthorizationService(null);
        fx(()->{
            category=new FeeCategoryController(new FeeTypeServiceImpl(types,auth));
            view=FeeCategoryController.load(category);stage=new Stage();stage.setScene(new Scene(view,1366,768));stage.show();return null;
        });
        until(()->!this.<ProgressIndicator>node("loadingIndicator").isVisible());
        fx(()->{
            this.<TextField>node("codeField").setText(CODE);
            this.<TextField>node("nameField").setText("Initial fee UI fixture");
            this.<ComboBox<FeeKind>>node("kindBox").setValue(FeeKind.BAT_BUOC);
            this.<ComboBox<ChargeMethod>>node("methodBox").setValue(ChargeMethod.THEO_CAN_HO);
            this.<TextField>node("amountField").setText("7000.50");
            assertNull(this.<DatePicker>node("initialRateDate").getValue());
            this.<Button>node("saveButton").fire();return null;
        });
        until(()->!this.<Label>node("initialDateError").getText().isEmpty());
        assertTrue(types.findByCode(CODE).isEmpty());assertTrue(rates.findHistory(CODE).isEmpty());
        fx(()->{this.<DatePicker>node("initialRateDate").setValue(LocalDate.of(2026,11,1));this.<Button>node("saveButton").fire();return null;});
        until(()->this.<TableView<FeeType>>node("feeTable").getItems().stream().anyMatch(f->CODE.equals(f.maKhoanThu())));
        var initial=rates.findHistory(CODE);assertEquals(1,initial.size());assertEquals("INITIAL",initial.get(0).ghiChu());
        assertEquals(new BigDecimal("7000.50"),initial.get(0).donGia());
        fx(()->{
            category.dispose();rate=new FeeRateController(new FeeRateServiceImpl(rates,types,auth),auth);
            view=FeeRateController.load(rate);stage.setScene(new Scene(view,1366,768));return null;
        });
        until(()->!this.<ProgressIndicator>node("loadingIndicator").isVisible()&&!this.<ComboBox<FeeType>>node("feeBox").getItems().isEmpty());
        fx(()->{var box=this.<ComboBox<FeeType>>node("feeBox");box.setValue(box.getItems().stream().filter(f->CODE.equals(f.maKhoanThu())).findFirst().orElseThrow());return null;});
        until(()->this.<TableView<FeeRate>>node("historyTable").getItems().equals(initial));
        fx(()->{this.<TextField>node("priceField").setText("8000");this.<DatePicker>node("effectiveDate").setValue(LocalDate.of(2027,1,1));this.<Button>node("saveButton").fire();return null;});
        until(()->this.<TableView<FeeRate>>node("historyTable").getItems().size()==2);
        assertEquals(initial.get(0),rates.findHistory(CODE).get(1));
    }
}
