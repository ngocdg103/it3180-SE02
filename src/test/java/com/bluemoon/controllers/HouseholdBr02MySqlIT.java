package com.bluemoon.controllers;

import com.bluemoon.config.*;
import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import com.bluemoon.views.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Real FXML + production composition + Service/DAO/MySQL. Owns P2981/BR02_UI_2981 and period 2097-01. */
@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST", matches="1")
class HouseholdBr02MySqlIT {
    private static DatabaseConnection db;
    private static final List<String> created=new ArrayList<>();
    private Stage stage;
    private HouseholdNavigator navigator;
    private final AuthenticationService auth=new AuthenticationService() {
        public AuthResult login(String u,String p) { throw new UnsupportedOperationException(); }
        public Optional<Account> currentUser() { return SessionContext.getCurrentUser(); }
        public void logout() { FeeCategorySessionFixture.use(null); }
    };
    @BeforeAll static void schema() throws Exception {
        ApartmentHistoryPanelTest.toolkit(); db=new DatabaseConnection(DatabaseConfig.load());
        try(var c=db.getConnection()) {
            assertTrue(c.getCatalog().endsWith("_test")); assertEquals("MySQL",c.getMetaData().getDatabaseProductName());
            for(String table:List.of("phieu_thu","apartment_credit","invoice_lines","invoices","billing_periods")) {
                try(var rows=c.getMetaData().getTables(c.getCatalog(),null,table,null)) { if(!rows.next())created.add(table); }
            }
        }
        for(String path:List.of("database/schema.sql","database/migrations/us001_household_apartment.sql",
                "database/migrations/us002_residents.sql","database/migrations/us005_fee_types.sql",
                "database/migrations/us006_fee_rates.sql","database/migrations/us007_billing.sql","database/migrations/us008_receipts.sql")) {
            for(String sql:Files.readString(Path.of(path)).replaceAll("(?m)^\\s*--.*$", "").split(";"))
                if(!sql.isBlank())sql(sql);
        }
    }
    private static void sql(String sql) throws Exception {
        try(var c=db.getConnection()) { assertTrue(c.getCatalog().endsWith("_test"));
            try(var command=c.createStatement()) { command.execute(sql); } }
    }
    private static void cleanup() throws Exception {
        sql("DELETE FROM phieu_thu WHERE ma_hoa_don='HD-209701-P2981'");
        sql("DELETE FROM apartment_credit WHERE so_can_ho='P2981'");
        sql("DELETE FROM invoice_lines WHERE ma_hoa_don='HD-209701-P2981'");
        sql("DELETE FROM invoices WHERE ma_hoa_don='HD-209701-P2981'");
        sql("DELETE FROM billing_periods WHERE ky_thu='2097-01'");
        sql("DELETE FROM residents WHERE ma_ho='BR02_UI_2981'");
        sql("DELETE FROM households WHERE ma_ho='BR02_UI_2981'");
        sql("DELETE FROM apartments WHERE so_can_ho='P2981'");
        sql("DELETE FROM users WHERE id=880002 AND username='BR02UI880002'");
    }
    @BeforeEach void seed() throws Exception {
        cleanup(); FeeCategorySessionFixture.use(Role.ADMIN);
        sql("INSERT INTO users(id,username,password_hash,ho_ten,role) VALUES(880002,'BR02UI880002','"
                +org.mindrot.jbcrypt.BCrypt.hashpw("br02-fixture-password",org.mindrot.jbcrypt.BCrypt.gensalt(4))+"','BR02 UI','ACCOUNTANT')");
        sql("INSERT INTO apartments VALUES('P2981',29,80.00,'DANG_O')");
        sql("INSERT INTO households VALUES('BR02_UI_2981','P2981','2024-01-01')");
        sql("INSERT INTO billing_periods(ky_thu,han_nop,so_hoa_don,tong_tien,created_by,created_at) VALUES('2097-01','2097-01-31',1,123000,880002,NOW(6))");
        sql("INSERT INTO invoices(ma_hoa_don,ky_thu,ma_ho,so_can_ho,dien_tich,so_nhan_khau,tong_tien,han_nop,created_at) VALUES('HD-209701-P2981','2097-01','BR02_UI_2981','P2981',80,2,123000,'2097-01-31',NOW(6))");
    }
    @AfterEach void close() throws Exception {
        ApartmentHistoryPanelTest.fx(() -> { if(stage!=null)stage.hide(); if(navigator!=null)navigator.close(); });
        FeeCategorySessionFixture.use(null); cleanup();
    }
    @AfterAll static void releaseOwnedTables() throws Exception {
        for(String table:created)sql("DROP TABLE IF EXISTS " + table); created.clear();
    }
    private void fx(Runnable action) throws Exception { ApartmentHistoryPanelTest.fx(action); }
    private void until(BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<end) { boolean[] done={false}; fx(() -> done[0]=condition.getAsBoolean()); if(done[0])return; Thread.sleep(15); }
        fail("BR-02 UI did not settle");
    }
    private <T extends Node> T node(String id,Class<T> type) { return type.cast(stage.getScene().lookup("#"+id)); }
    private DialogPane dialog() {
        return Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getOwner()==stage)
                .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElse(null);
    }
    @Test void productionHouseholdConfirmationCancelApprovalAndHistoryPreserveSnapshot() throws Exception {
        var before=new BillingDAO(db).findInvoice("HD-209701-P2981").orElseThrow();
        fx(() -> {
            var registration=ProductionModules.create(db).stream().filter(m -> m.id().equals("households")).findFirst().orElseThrow();
            navigator=(HouseholdNavigator)registration.open().get(); stage=new Stage(); stage.setScene(new Scene(navigator.view(),1250,900)); stage.show();
        });
        until(() -> !navigator.busyProperty().get());
        var profile=new HouseholdDAO(db).findByMaHo("BR02_UI_2981").orElseThrow();
        fx(() -> { navigator.showEdit(profile); stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            node("dienTich",TextField.class).setText("95.50"); node("saveButton",Button.class).fire(); });
        until(() -> dialog()!=null);
        fx(() -> { assertTrue(navigator.busyProperty().get()); assertTrue(node("saveButton",Button.class).isDisabled());
            ((Button)dialog().lookupButton(ButtonType.CANCEL)).fire(); });
        assertEquals(0,new HouseholdDAO(db).findByMaHo("BR02_UI_2981").orElseThrow().dienTich().compareTo(new java.math.BigDecimal("80")));
        fx(() -> node("saveButton",Button.class).fire()); until(() -> dialog()!=null);
        fx(() -> ((Button)dialog().lookupButton(ButtonType.OK)).fire());
        until(() -> stage.getScene().lookup("#households")!=null && !navigator.busyProperty().get());
        assertEquals(0,new HouseholdDAO(db).findByMaHo("BR02_UI_2981").orElseThrow().dienTich().compareTo(new java.math.BigDecimal("95.50")));
        assertEquals(before,new BillingDAO(db).findInvoice(before.maHoaDon()).orElseThrow());
        fx(() -> navigator.showDetail("BR02_UI_2981"));
        until(() -> stage.getScene().lookup("#apartmentHistory")!=null && !navigator.busyProperty().get());
        fx(() -> {
            var table=node("apartmentHistory",TableView.class); assertEquals(1,table.getItems().size());
            var snapshot=(MonthlyCharge)table.getItems().get(0); assertEquals("2097-01",snapshot.kyThu());
            assertEquals(before.dienTich(),snapshot.dienTich()); assertEquals(before.tongTien(),snapshot.tongTien());
            assertEquals(before.hanNop(),snapshot.hanNop());
            assertEquals(ChargeStatus.CHUA_DONG,snapshot.trangThai());
        });
    }
    @Test void accountantSeesSnapshotAndResidentCannotOpenHouseholdFromDashboard() throws Exception {
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);
        var authorization=new RoleBasedAuthorizationService(null);
        fx(() -> {
            navigator=new HouseholdNavigator(new HouseholdServiceImpl(new HouseholdDAO(db),new BillingDAO(db)),
                    new ResidentServiceImpl(new ResidentDAO(db),new HouseholdDAO(db),authorization),
                    new BillingServiceImpl(new BillingDAO(db),authorization,Clock.systemUTC()));
            stage=new Stage(); stage.setScene(new Scene(navigator.view(),1250,900)); stage.show();
        });
        until(() -> !navigator.busyProperty().get()); fx(() -> navigator.showDetail("BR02_UI_2981"));
        until(() -> stage.getScene().lookup("#apartmentHistory")!=null && !navigator.busyProperty().get());
        fx(() -> { assertEquals(1,node("apartmentHistory",TableView.class).getItems().size()); navigator.close(); });
        FeeCategorySessionFixture.use(Role.RESIDENT);
        fx(() -> {
            var controller=new DashboardController(auth,auth.currentUser().orElseThrow(),()->{},authorization,ProductionModules.create(db));
            var loader=new javafx.fxml.FXMLLoader(getClass().getResource("/com/bluemoon/views/DashboardView.fxml")); loader.setControllerFactory(t -> controller);
            try { stage.getScene().setRoot(loader.load()); } catch(Exception e) { throw new RuntimeException(e); }
            controller.openModule("households"); assertNull(stage.getScene().lookup("#apartmentHistory"));
            assertFalse(node("householdsButton",Button.class).isVisible()); controller.close();
        });
    }
    @Test void historyShowsCommittedPaymentWithoutChangingBillingInputs() throws Exception {
        var login=new AuthService(new AccountDAO(db)).login("BR02UI880002","br02-fixture-password");
        assertTrue(login.isSuccess());
        var before=new BillingDAO(db).findInvoice("HD-209701-P2981").orElseThrow();
        var payments=new ReceiptServiceImpl(new ReceiptDAO(db),new RoleBasedAuthorizationService(null),Clock.systemUTC());
        var paid=payments.recordPayment(new ReceiptForm(java.util.UUID.randomUUID().toString(),before.maHoaDon(),
                "BR02 fixture","23000",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")),PaymentMethod.TIEN_MAT,"BR02 UI"));
        assertTrue(paid.isSuccess(),paid.message());
        fx(() -> {
            navigator=(HouseholdNavigator)ProductionModules.create(db).stream().filter(m -> m.id().equals("households"))
                    .findFirst().orElseThrow().open().get();
            stage=new Stage(); stage.setScene(new Scene(navigator.view(),1250,900)); stage.show();
        });
        until(() -> !navigator.busyProperty().get()); fx(() -> navigator.showDetail("BR02_UI_2981"));
        until(() -> stage.getScene().lookup("#apartmentHistory")!=null && !navigator.busyProperty().get());
        fx(() -> {
            var table=node("apartmentHistory",TableView.class); var row=(MonthlyCharge)table.getItems().get(0);
            assertEquals(ChargeStatus.DONG_THIEU,row.trangThai());
            assertEquals(0,row.daThanhToan().compareTo(new java.math.BigDecimal("23000")));
            assertEquals(0,row.conThieu().compareTo(new java.math.BigDecimal("100000")));
            assertEquals(before.tongTien(),row.tongTien()); assertEquals(before.dienTich(),row.dienTich());
            assertEquals(before.soNhanKhau(),row.soNhanKhau()); assertEquals(before.hanNop(),row.hanNop());
            var status=(TableColumn<?,?>)table.getColumns().stream().filter(c -> ((TableColumn<?,?>)c).getText().equals("Trạng thái thanh toán")).findFirst().orElseThrow();
            assertEquals("Đóng thiếu",status.getCellData(0));
        });
    }
}
