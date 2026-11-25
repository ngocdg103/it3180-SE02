package com.bluemoon.services;

import com.bluemoon.config.*;
import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import com.bluemoon.controllers.*;
import com.bluemoon.views.HouseholdNavigator;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only audit supplement. Production source remains exactly at the recorded integrated SHA. */
@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST",matches="1")
class Sprint1AuditProbeTest {
    static final Clock CLOCK=Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"),ZoneOffset.UTC);
    static DatabaseConnection db;
    final AuthorizationService auth=new RoleBasedAuthorizationService(null);
    HouseholdService households; ResidentService residents; FeeTypeService fees; FeeRateService rates;
    BillingService billing; ReceiptService payments; DebtService debt; NoticeService notices;
    Parent view; Stage stage; final List<Runnable> closes=new ArrayList<>();
    static <T> T fx(Callable<T> call)throws Exception{var f=new CompletableFuture<T>();Platform.runLater(()->{try{f.complete(call.call());}catch(Throwable e){f.completeExceptionally(e);}});return f.get(10,TimeUnit.SECONDS);}
    static void safe(java.sql.Connection c)throws Exception{assertEquals("bluemoon_audit28_probe_test",c.getCatalog());assertEquals("MySQL",c.getMetaData().getDatabaseProductName());}
    static void sql(String text)throws Exception{try(var c=db.getConnection()){safe(c);try(var s=c.createStatement()){s.execute(text);}}}
    @BeforeAll static void init()throws Exception{
        var f=new CompletableFuture<Void>();try{Platform.startup(()->{Platform.setImplicitExit(false);f.complete(null);});}catch(IllegalStateException started){f.complete(null);}f.get();
        db=new DatabaseConnection(DatabaseConfig.load());
        for(String file:List.of("database/schema.sql","database/migrations/us001_household_apartment.sql","database/migrations/us002_residents.sql","database/migrations/us005_fee_types.sql","database/migrations/us006_fee_rates.sql","database/migrations/us007_billing.sql","database/migrations/us008_receipts.sql")){
            String text=Files.readString(Path.of(file)).replaceAll("(?m)^\\s*--.*$","");for(String command:text.split(";"))if(!command.isBlank())sql(command);
        }
    }
    @BeforeEach void fixture()throws Exception{
        for(String table:List.of("phieu_thu","apartment_credit","invoice_lines","invoices","billing_periods","residents","households","apartments","fee_rates","fee_types","users"))sql("DELETE FROM "+table);
        sql("INSERT INTO users(id,username,password_hash,ho_ten,role) VALUES (71,'auditstaff','not-a-login-hash','Audit Staff','ACCOUNTANT')");
        households=new HouseholdServiceImpl(new HouseholdDAO(db),ApartmentChargeLookup.NONE,auth,CLOCK);
        residents=new ResidentServiceImpl(new ResidentDAO(db),new HouseholdDAO(db),auth,CLOCK);
        fees=new FeeTypeServiceImpl(new FeeTypeDAO(db),auth);rates=new FeeRateServiceImpl(new FeeRateDAO(db),new FeeTypeDAO(db),auth);
        billing=new BillingServiceImpl(new BillingDAO(db),auth,CLOCK);payments=new ReceiptServiceImpl(new ReceiptDAO(db),auth,CLOCK);
        debt=new DebtServiceImpl(new DebtDAO(db),auth);notices=new NoticeServiceImpl(billing,code->Optional.empty(),auth,CLOCK);
        FeeCategorySessionFixture.use(Role.ADMIN);
        assertTrue(households.createHousehold(new HouseholdForm("P1204","12","80","AUDIT_HH","2026-01-01",ApartmentStatus.DANG_O)).isSuccess());
        assertTrue(fees.createFeeType(new FeeTypeForm("AUDIT_FEE","Audit fee",FeeKind.BAT_BUOC,ChargeMethod.THEO_CAN_HO,"1000",FeeStatus.DANG_AP_DUNG,LocalDate.of(2026,1,1))).isSuccess());
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);assertTrue(billing.generatePeriod("2026-11").isSuccess());FeeCategorySessionFixture.use(Role.ADMIN);
    }
    @AfterEach void close()throws Exception{fx(()->{for(Runnable c:closes)c.run();if(stage!=null)stage.close();return null;});FeeCategorySessionFixture.use(null);}
    String fingerprint()throws Exception{
        var all=new ArrayList<String>();try(var c=db.getConnection()){safe(c);for(String table:List.of("users","apartments","households","residents","fee_types","fee_rates","billing_periods","invoices","invoice_lines","phieu_thu","apartment_credit")){
            try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM "+table)){int n=r.getMetaData().getColumnCount();while(r.next()){var row=new ArrayList<String>();for(int i=1;i<=n;i++)row.add(Objects.toString(r.getObject(i),"<NULL>"));all.add(table+row);}}
        }}Collections.sort(all);return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(all.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    static void invalid(HouseholdResult<?> r,String key){assertEquals(HouseholdResult.Status.VALIDATION_ERROR,r.status());assertTrue(r.fieldErrors().containsKey(key));}
    void until(BooleanSupplier check)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<end){if(fx(check::getAsBoolean))return;Thread.sleep(20);}fail("UI did not settle");}
    @SuppressWarnings("unchecked") <T>T node(String id){return (T)view.lookup("#"+id);}
    void show(Parent root){view=root;if(stage==null)stage=new Stage();stage.setScene(new Scene(root,1366,768));stage.show();root.applyCss();root.layout();}
    @Test void seededAdminAuthenticatesAndDashboardDisplaysStoredName()throws Exception{
        String script=Files.readString(Path.of("database/seed.sql")).replaceAll("(?m)^\\s*--.*$","");
        for(String command:script.split(";"))if(!command.isBlank())sql(command);
        var login=new AuthService(new AccountDAO(db),CLOCK);var result=login.login("admin","admin@123");
        assertTrue(result.isSuccess());var account=result.account().orElseThrow();assertEquals(Role.ADMIN,account.role());
        fx(()->{
            var loader=new javafx.fxml.FXMLLoader(getClass().getResource("/com/bluemoon/views/DashboardView.fxml"));
            var dashboard=new DashboardController(login,account,()->{});loader.setControllerFactory(type->dashboard);
            show(loader.load());closes.add(dashboard::close);
            assertEquals("Xin ch\u00e0o: "+account.hoTen(),this.<Label>node("greeting").getText());
            assertEquals("Vai tr\u00f2: ADMIN",this.<Label>node("role").getText());return null;
        });
        System.out.println("AUDIT seeded admin authentication and dynamic greeting verified; literal Admin-name expectation remains discrepant");
    }
    @Test void missingFieldsAreRejectedWithoutDatabaseMutation()throws Exception{
        String before=fingerprint();
        invalid(households.createHousehold(new HouseholdForm(null,null,null,null,null,null)),"so_can_ho");
        invalid(households.createHousehold(new HouseholdForm("P2201","22","0","AUDIT_NEW","2026-11-01",ApartmentStatus.DANG_O)),"dien_tich");
        for(ResidentForm f:List.of(new ResidentForm(null,"Audit Person","1990-01-01",Gender.NAM,"001200001234",RelationToHead.CHU_HO,"2026-01-01"),new ResidentForm("AUDIT_HH",null,null,null,null,null,null))){
            var r=residents.createForHousehold(f,null);assertEquals(ResidentResult.Status.VALIDATION_ERROR,r.status());assertFalse(r.fieldErrors().isEmpty());
        }
        for(FeeTypeForm f:List.of(new FeeTypeForm("AUDIT_NEW","",FeeKind.BAT_BUOC,ChargeMethod.THEO_CAN_HO,"0",FeeStatus.DANG_AP_DUNG),new FeeTypeForm("AUDIT_NEW","Audit new",null,ChargeMethod.THEO_CAN_HO,"0",FeeStatus.DANG_AP_DUNG))){assertEquals(FeeTypeResult.Status.VALIDATION_ERROR,fees.createFeeType(f).status());}
        for(FeeRateForm f:List.of(new FeeRateForm("AUDIT_FEE","",LocalDate.of(2027,1,1),null),new FeeRateForm("AUDIT_FEE","2000",null,null))){assertEquals(FeeRateResult.Status.VALIDATION_ERROR,rates.createRate(f).status());}
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);
        for(ReceiptForm f:List.of(new ReceiptForm(UUID.randomUUID().toString(),null,"Audit Payer","100",LocalDate.of(2026,11,8),PaymentMethod.TIEN_MAT,null),new ReceiptForm(UUID.randomUUID().toString(),"HD-202611-P1204","Audit Payer","",LocalDate.of(2026,11,8),PaymentMethod.TIEN_MAT,null),new ReceiptForm(UUID.randomUUID().toString(),"HD-202611-P1204","Audit Payer","100",null,PaymentMethod.TIEN_MAT,null))){assertEquals(ReceiptResult.Status.VALIDATION_ERROR,payments.recordPayment(f).status());}
        var login=new AuthService(new AccountDAO(db),CLOCK);
        for(String[] v:new String[][]{{"",""},{"auditstaff",""},{"","password"}})assertEquals(AuthStatus.VALIDATION_ERROR,login.login(v[0],v[1]).status());
        assertEquals(before,fingerprint());System.out.println("AUDIT missing-fields DB SHA256 unchanged="+before);
    }
    @Test void deniedServiceCallsLeaveAllTablesUnchanged()throws Exception{
        String before=fingerprint();
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);
        assertThrows(AccessDeniedException.class,()->households.createHousehold(new HouseholdForm("P2201","22","85","AUDIT_NEW","2026-11-01",ApartmentStatus.DANG_O)));
        assertThrows(AccessDeniedException.class,()->residents.createForHousehold(new ResidentForm("AUDIT_HH","Audit Person","1990-01-01",Gender.NAM,"001200001234",RelationToHead.CHU_HO,"2026-01-01"),null));
        FeeCategorySessionFixture.use(Role.ADMIN);
        assertThrows(AccessDeniedException.class,()->payments.recordPayment(new ReceiptForm(UUID.randomUUID().toString(),"HD-202611-P1204","Audit Payer","100",LocalDate.of(2026,11,8),PaymentMethod.TIEN_MAT,null)));
        FeeCategorySessionFixture.use(Role.RESIDENT);
        assertThrows(AccessDeniedException.class,()->fees.createFeeType(new FeeTypeForm("AUDIT_NEW","Audit fee",FeeKind.BAT_BUOC,ChargeMethod.THEO_CAN_HO,"0",FeeStatus.DANG_AP_DUNG)));
        assertThrows(AccessDeniedException.class,()->fees.deactivateFeeType("AUDIT_FEE"));
        assertThrows(AccessDeniedException.class,()->rates.createRate(new FeeRateForm("AUDIT_FEE","2000",LocalDate.of(2027,1,1),null)));
        assertThrows(AccessDeniedException.class,()->billing.generatePeriod("2026-11"));
        assertThrows(AccessDeniedException.class,()->billing.getInvoice("HD-202611-P1204"));
        assertThrows(AccessDeniedException.class,()->debt.getDebtByPeriod(new DebtQuery("2026-11",null,null,null)));
        assertThrows(AccessDeniedException.class,()->notices.getNotices("2026-11"));
        assertThrows(AccessDeniedException.class,()->new ResidentSearchServiceImpl(new ResidentDAO(db),auth).search(ResidentSearchCriteria.Field.APARTMENT,"1204"));
        assertEquals(before,fingerprint());System.out.println("AUDIT denied-services DB SHA256 unchanged="+before);
    }
    @Test void receiptRoundTripKeepsPayerInvoiceHouseholdFeesDateAndAmount()throws Exception{
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);String request=UUID.randomUUID().toString();
        var f=new ReceiptForm(request,"HD-202611-P1204","Audit Payer","100",LocalDate.of(2026,11,8),PaymentMethod.CHUYEN_KHOAN,"Audit note");
        assertTrue(payments.recordPayment(f).isSuccess());var r=payments.listByHousehold("AUDIT_HH").data().orElseThrow().get(0);
        assertEquals("Audit Payer",r.nguoiNop());assertEquals(new BigDecimal("100.00"),r.soTienNop());assertEquals(f.ngayNop(),r.ngayNop());assertEquals(f.hinhThuc(),r.hinhThuc());assertEquals("Audit note",r.ghiChu());assertEquals(71,r.createdBy());assertEquals(request,r.requestId());
        try(var c=db.getConnection();var s=c.prepareStatement("SELECT i.ma_ho,i.so_can_ho,l.ma_loai_thu FROM phieu_thu p JOIN invoices i ON i.ma_hoa_don=p.ma_hoa_don JOIN invoice_lines l ON l.ma_hoa_don=i.ma_hoa_don WHERE p.request_id=?")){s.setString(1,request);try(var rows=s.executeQuery()){assertTrue(rows.next());assertEquals("AUDIT_HH",rows.getString(1));assertEquals("P1204",rows.getString(2));assertEquals("AUDIT_FEE",rows.getString(3));}}
    }
    @Test void actualFeeAndRateUiShowMissingFields()throws Exception{
        String before=fingerprint();var controller=new FeeCategoryController(fees);
        fx(()->{show(FeeCategoryController.load(controller));closes.add(controller::dispose);return null;});
        until(()->!this.<ProgressIndicator>node("loadingIndicator").isVisible());
        fx(()->{this.<TextField>node("codeField").setText("AUDIT_NEW");this.<ComboBox<FeeKind>>node("kindBox").setValue(FeeKind.BAT_BUOC);this.<ComboBox<ChargeMethod>>node("methodBox").setValue(ChargeMethod.THEO_CAN_HO);this.<Button>node("saveButton").fire();return null;});
        until(()->!this.<Label>node("nameError").getText().isBlank());
        fx(()->{this.<TextField>node("nameField").setText("Audit new");this.<ComboBox<FeeKind>>node("kindBox").setValue(null);this.<Button>node("saveButton").fire();return null;});until(()->!this.<Label>node("kindError").getText().isBlank());
        var price=new FeeRateController(rates,auth);
        fx(()->{controller.dispose();show(FeeRateController.load(price));closes.add(price::dispose);return null;});
        until(()->!this.<ProgressIndicator>node("loadingIndicator").isVisible()&&this.<ComboBox<FeeType>>node("feeBox").getValue()!=null);
        fx(()->{this.<TextField>node("priceField").clear();this.<DatePicker>node("effectiveDate").setValue(LocalDate.of(2027,1,1));this.<Button>node("saveButton").fire();return null;});until(()->!this.<Label>node("priceError").getText().isBlank());
        fx(()->{this.<TextField>node("priceField").setText("2000");this.<DatePicker>node("effectiveDate").setValue(null);this.<Button>node("saveButton").fire();return null;});until(()->!this.<Label>node("dateError").getText().isBlank());assertEquals(before,fingerprint());
    }
    @Test void actualResidentUiReportsMissingRequiredFieldsWithoutCreatingPerson()throws Exception{
        String before=fingerprint();var panel=new ResidentPanelController(residents,"AUDIT_HH",b->{});
        fx(()->{show(panel.view());closes.add(panel::close);return null;});
        until(()->!this.<Button>node("addResidentButton").isDisabled());
        fx(()->{this.<Button>node("addResidentButton").fire();return null;});
        fx(()->{var dialog=javafx.stage.Window.getWindows().stream().map(w->w.getScene().getRoot()).filter(r->r instanceof DialogPane).map(r->(DialogPane)r).findFirst().orElseThrow();
            ((Button)dialog.lookup("#saveResidentButton")).fire();return null;});
        until(()->javafx.stage.Window.getWindows().stream().map(w->w.getScene().getRoot()).filter(r->r instanceof DialogPane).anyMatch(r->{var label=(Label)r.lookup("#residentError_ho_ten");return label!=null&&!label.getText().isBlank();}));
        fx(()->{var dialog=javafx.stage.Window.getWindows().stream().map(w->w.getScene().getRoot()).filter(r->r instanceof DialogPane).map(r->(DialogPane)r).findFirst().orElseThrow();
            for(String key:List.of("ho_ten","ngay_sinh","gioi_tinh","quan_he_chu_ho","ngay_chuyen_den"))assertFalse(((Label)dialog.lookup("#residentError_"+key)).getText().isBlank(),key);
            ((Button)dialog.lookupButton(ButtonType.CANCEL)).fire();return null;});
        assertEquals(before,fingerprint());
    }
    @Test void actualReceiptUiShowsMissingAmountAndDate()throws Exception{
        String before=fingerprint();FeeCategorySessionFixture.use(Role.ACCOUNTANT);var c=new ReceiptController(payments,auth);
        fx(()->{show(ReceiptController.load(c));closes.add(c::dispose);return null;});until(()->!c.busyProperty().get()&&this.<ComboBox<PaymentInvoice>>node("invoiceBox").getValue()!=null);
        fx(()->{this.<TextField>node("payerField").setText("Audit Payer");this.<DatePicker>node("paymentDate").setValue(LocalDate.of(2026,11,8));this.<TextField>node("amountField").clear();this.<Button>node("saveButton").fire();return null;});until(()->!c.busyProperty().get()&&!this.<Label>node("errorLabel").getText().isBlank());
        fx(()->{this.<TextField>node("amountField").setText("100");this.<DatePicker>node("paymentDate").setValue(null);this.<Button>node("saveButton").fire();return null;});until(()->!c.busyProperty().get()&&!this.<Label>node("errorLabel").getText().isBlank());
        fx(()->{this.<ComboBox<PaymentInvoice>>node("invoiceBox").setValue(null);assertTrue(this.<Button>node("saveButton").isDisabled());return null;});assertEquals(before,fingerprint());
    }
    @Test void actualHouseholdUiCreateAndInvalidFields()throws Exception{
        String before=fingerprint();var nav=new HouseholdNavigator(households,this::show);
        fx(()->{nav.showCreate();closes.add(nav::dispose);this.<Button>node("saveButton").fire();return null;});until(()->!this.<Label>node("soCanHoError").getText().isBlank());assertEquals(before,fingerprint());
        fx(()->{this.<TextField>node("soCanHo").setText("P2201");this.<TextField>node("tang").setText("22");this.<TextField>node("dienTich").setText("0");this.<TextField>node("maHo").setText("AUDIT_NEW");this.<DatePicker>node("ngayNhanNha").setValue(LocalDate.of(2026,11,1));this.<ComboBox<ApartmentStatus>>node("trangThai").setValue(ApartmentStatus.DANG_O);this.<Button>node("saveButton").fire();return null;});until(()->!this.<Label>node("dienTichError").getText().isBlank());assertEquals(before,fingerprint());
        fx(()->{this.<TextField>node("dienTich").setText("85");this.<Button>node("saveButton").fire();return null;});until(()->view.lookup("#households")!=null&&!this.<TableView<HouseholdProfile>>node("households").getItems().isEmpty());
        var saved=households.getHouseholdById("AUDIT_NEW").data().orElseThrow();assertEquals("P2201",saved.soCanHo());assertEquals(new BigDecimal("85.00"),saved.dienTich());assertTrue(fx(()->this.<TableView<HouseholdProfile>>node("households").getItems().stream().anyMatch(h->h.maHo().equals("AUDIT_NEW")&&h.soCanHo().equals("P2201"))));
    }
}
