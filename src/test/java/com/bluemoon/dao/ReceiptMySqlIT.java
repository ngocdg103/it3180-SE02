package com.bluemoon.dao;

import com.bluemoon.config.*;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static com.bluemoon.services.ReceiptResult.Status.*;

/** Exclusive disposable *_test schema. Real Billing + Receipt DAO/service; no production fake. */
@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST",matches="1")
@Timeout(60)
class ReceiptMySqlIT {
    private static DatabaseConnection db;
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"),ZoneOffset.UTC);
    private ReceiptDAO dao;
    private ReceiptService service;
    private final String invoice="HD-202611-P1204";
    @BeforeAll static void schema() throws Exception {
        db=new DatabaseConnection(DatabaseConfig.load());
        try(var c=db.getConnection()) { safe(c); assertEquals("MySQL",c.getMetaData().getDatabaseProductName()); assertTrue(c.getMetaData().getDatabaseMajorVersion()>=8); }
        for(String path:List.of("database/schema.sql","database/migrations/us001_household_apartment.sql","database/migrations/us002_residents.sql","database/migrations/us005_fee_types.sql","database/migrations/us006_fee_rates.sql","database/migrations/us007_billing.sql","database/migrations/us008_receipts.sql")) {
            String text=Files.readString(Path.of(path)).replaceAll("(?m)^\\s*--.*$",""); for(String command:text.split(";")) { if(!command.isBlank()) { sql(command); } }
        }
    }
    static void safe(Connection c) throws SQLException { assertNotNull(c.getCatalog()); assertTrue(c.getCatalog().endsWith("_test"),"Disposable test schema only"); }
    static void sql(String command) throws SQLException { try(var c=db.getConnection()) { safe(c); try(var s=c.createStatement()) { s.execute(command); } } }
    static BigDecimal scalar(String query) throws SQLException { try(var c=db.getConnection()) { safe(c); try(var s=c.createStatement();var r=s.executeQuery(query)) { assertTrue(r.next()); return r.getBigDecimal(1); } } }
    @BeforeEach void fixture() throws Exception {
        for(String table:List.of("phieu_thu","apartment_credit","invoice_lines","invoices","billing_periods","residents","households","apartments","fee_rates","fee_types")) { sql("DELETE FROM "+table); }
        sql("INSERT IGNORE INTO users(id,username,password_hash,ho_ten,role) VALUES(71,'receipt-it','x','Receipt IT','ACCOUNTANT')");
        sql("INSERT INTO apartments(so_can_ho,tang,dien_tich,trang_thai) VALUES('P1204',12,80,'DANG_O')");
        sql("INSERT INTO households(ma_ho,so_can_ho,ngay_nhan_nha) VALUES('HK-1204','P1204','2020-01-01')");
        sql("INSERT INTO fee_types(ma_loai_thu,ten_loai_thu,loai_thu,phuong_thuc_tinh,trang_thai) VALUES('US008_IT','Receipt fixture','BAT_BUOC','THEO_CAN_HO','DANG_AP_DUNG')");
        new FeeRateDAO(db).insert(new FeeRate(null,"US008_IT",new BigDecimal("1384000"),LocalDate.of(2026,1,1),null));
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);
        assertTrue(new BillingServiceImpl(new BillingDAO(db),CLOCK).generatePeriod("2026-11").isSuccess());
        dao=new ReceiptDAO(db); service=service(dao);
    }
    @AfterEach void logout() { FeeCategorySessionFixture.use(null); }
    @AfterAll static void cleanup() throws Exception {
        for(String table:List.of("phieu_thu","apartment_credit","invoice_lines","invoices","billing_periods","fee_rates")) { sql("DROP TABLE IF EXISTS "+table); }
    }
    ReceiptService service(ReceiptRepository repo) { return new ReceiptServiceImpl(repo,new RoleBasedAuthorizationService(null),CLOCK); }
    ReceiptForm form(String id,String amount) { return new ReceiptForm(id,invoice,"Nguyễn A",amount,LocalDate.of(2026,11,8),PaymentMethod.TIEN_MAT,"Đối soát"); }
    ReceiptResult<ReceiptOutcome> pay(String amount) { return service.recordPayment(form(UUID.randomUUID().toString(),amount)); }
    @Test void partialThenFullAc01Ac02AndSnapshotNeverChanges() throws Exception {
        var before=new BillingDAO(db).findInvoice(invoice).orElseThrow();
        var first=pay("1000000"); assertEquals(SUCCESS,first.status()); assertEquals(ChargeStatus.DONG_THIEU,first.data().orElseThrow().invoice().trangThai());
        assertEquals(0,new BigDecimal("384000").compareTo(first.data().orElseThrow().invoice().conThieu()));
        var second=service.recordPayment(new ReceiptForm(UUID.randomUUID().toString(),invoice,"Nguyễn A","384000",LocalDate.of(2026,11,8),PaymentMethod.CHUYEN_KHOAN,null));
        assertEquals(SUCCESS,second.status()); assertEquals(ChargeStatus.DA_DONG_DU,second.data().orElseThrow().invoice().trangThai());
        assertTrue(service.listOutstanding().data().orElseThrow().isEmpty()); assertEquals(2,service.listByHousehold("HK-1204").data().orElseThrow().size());
        var after=new BillingDAO(db).findInvoice(invoice).orElseThrow(); assertEquals(before.lines(),after.lines()); assertEquals(before.tongTien(),after.tongTien()); assertEquals(before.dienTich(),after.dienTich());
        assertEquals(ALREADY_SETTLED,pay("1").status());
    }
    @Test void duplicateAndPayloadConflictCreateNoExtraReceipt() throws Exception {
        String id=UUID.randomUUID().toString(); assertEquals(SUCCESS,service.recordPayment(form(id,"1000")).status());
        assertEquals(DUPLICATE,service.recordPayment(form(id,"1000")).status()); assertEquals(CONFLICT,service.recordPayment(form(id,"2000")).status());
        assertEquals(1,scalar("SELECT COUNT(*) FROM phieu_thu").intValueExact()); assertTrue(service.findByRequestId(id).data().orElseThrow().isPresent());
        assertEquals(0,new BigDecimal("1000").compareTo(scalar("SELECT da_thanh_toan FROM invoices")));
    }
    @Test void overpaymentPersistsCreditAtomically() throws Exception {
        var result=pay("1400000"); assertEquals(SUCCESS,result.status()); assertEquals(ChargeStatus.DONG_THUA,result.data().orElseThrow().invoice().trangThai());
        assertEquals(0,new BigDecimal("-16000").compareTo(result.data().orElseThrow().invoice().conThieu()));
        assertEquals(0,new BigDecimal("16000").compareTo(scalar("SELECT so_du FROM apartment_credit")));
    }
    @Test void rollbackAfterRealInvoiceUpdateLeavesNoReceiptOrPaidAmount() throws Exception {
        var failing=new ReceiptDAO(() -> wrap(db.getConnection(),"UPDATE invoices",false));
        assertEquals(SYSTEM_ERROR,service(failing).recordPayment(form(UUID.randomUUID().toString(),"1000")).status());
        assertEquals(0,scalar("SELECT COUNT(*) FROM phieu_thu").intValueExact()); assertEquals(0,scalar("SELECT da_thanh_toan FROM invoices").signum());
        assertEquals(SUCCESS,pay("1000").status());
    }
    @Test void rollbackAfterRealSurplusWriteLeavesNoCreditOrReceipt() throws Exception {
        var failing=new ReceiptDAO(() -> wrap(db.getConnection(),"INSERT INTO apartment_credit",false));
        assertEquals(SYSTEM_ERROR,service(failing).recordPayment(form(UUID.randomUUID().toString(),"1400000")).status());
        assertEquals(0,scalar("SELECT COUNT(*) FROM phieu_thu").intValueExact()); assertEquals(0,scalar("SELECT COUNT(*) FROM apartment_credit").intValueExact()); assertEquals(0,scalar("SELECT da_thanh_toan FROM invoices").signum());
    }
    @Test void commitReplyLostIsNotRetriedAndIntentCanBeReconciled() throws Exception {
        var opens=new AtomicInteger(); var failing=new ReceiptDAO(() -> { opens.incrementAndGet(); return wrap(db.getConnection(),"",true); });
        String id=UUID.randomUUID().toString(); assertEquals(SYSTEM_ERROR,service(failing).recordPayment(form(id,"1000")).status());
        assertEquals(1,opens.get()); assertEquals(1,scalar("SELECT COUNT(*) FROM phieu_thu").intValueExact()); assertTrue(service.findByRequestId(id).data().orElseThrow().isPresent());
        assertEquals(DUPLICATE,service.recordPayment(form(id,"1000")).status()); assertEquals(1,scalar("SELECT COUNT(*) FROM phieu_thu").intValueExact());
    }
    @Test void eightSameIntentRequestsProduceOneReceipt() throws Exception {
        String id=UUID.randomUUID().toString(); List<ReceiptResult<ReceiptOutcome>> results=concurrent(8,() -> service.recordPayment(form(id,"1000")));
        assertEquals(1,results.stream().filter(r -> r.status()==SUCCESS).count()); assertEquals(7,results.stream().filter(r -> r.status()==DUPLICATE).count());
        assertEquals(1,scalar("SELECT COUNT(*) FROM phieu_thu").intValueExact());
    }
    @Test void eightDistinctPaymentsDoNotLoseUpdates() throws Exception {
        for(var result:concurrent(8,() -> pay("1000"))) { assertEquals(SUCCESS,result.status()); }
        assertEquals(8,scalar("SELECT COUNT(*) FROM phieu_thu").intValueExact()); assertEquals(0,new BigDecimal("8000").compareTo(scalar("SELECT da_thanh_toan FROM invoices")));
    }
    @Test void concurrentFullSettlementRefusesSecondPaymentAndRbacStillApplies() throws Exception {
        var results=concurrent(2,() -> pay("1384000")); assertEquals(1,results.stream().filter(r -> r.status()==SUCCESS).count()); assertEquals(1,results.stream().filter(r -> r.status()==ALREADY_SETTLED).count());
        FeeCategorySessionFixture.use(Role.ADMIN); assertThrows(AccessDeniedException.class,() -> pay("1")); assertEquals(1,service.listByHousehold("HK-1204").data().orElseThrow().size());
        FeeCategorySessionFixture.use(Role.RESIDENT); assertThrows(AccessDeniedException.class,service::listOutstanding);
    }
    @Test void productionLockWaitReturnsBusyAndNeverLeavesPartialPayment() throws Exception {
        try(var holder=db.getConnection()) {
            holder.setAutoCommit(false);
            try(var s=holder.prepareStatement("SELECT ma_hoa_don FROM invoices WHERE ma_hoa_don=? FOR UPDATE")) { s.setString(1,invoice); try(var r=s.executeQuery()) { assertTrue(r.next()); } }
            assertEquals(BUSY,pay("1000").status());
            assertEquals(0,scalar("SELECT COUNT(*) FROM phieu_thu").intValueExact()); holder.rollback();
        }
        assertEquals(SUCCESS,pay("1000").status());
    }
    @Test void migrationCanRepeatAndDatabaseRejectsFractionalMoneyAndMissingInvoice() throws Exception {
        assertEquals(SUCCESS,pay("1000").status());
        String text=Files.readString(Path.of("database/migrations/us008_receipts.sql")).replaceAll("(?m)^\\s*--.*$","");
        for(String command:text.split(";")) { if(!command.isBlank()) { sql(command); } }
        assertEquals(1,scalar("SELECT COUNT(*) FROM phieu_thu").intValueExact());
        assertEquals(3819,assertThrows(SQLException.class,() -> sql("INSERT INTO phieu_thu(ma_phieu_thu,request_id,ma_hoa_don,nguoi_nop,so_tien_nop,ngay_nop,hinh_thuc,created_by,created_at) VALUES('BAD','11111111-1111-4111-8111-111111111111','HD-202611-P1204','Nguyen A',1.5,'2026-11-08','TIEN_MAT',71,NOW(6))")).getErrorCode());
        assertEquals(1452,assertThrows(SQLException.class,() -> sql("INSERT INTO phieu_thu(ma_phieu_thu,request_id,ma_hoa_don,nguoi_nop,so_tien_nop,ngay_nop,hinh_thuc,created_by,created_at) VALUES('MISSING','22222222-2222-4222-8222-222222222222','HD-202611-P9999','Nguyen A',1,'2026-11-08','TIEN_MAT',71,NOW(6))")).getErrorCode());
    }
    private static <T> List<T> concurrent(int count,Callable<T> call) throws Exception {
        var pool=Executors.newFixedThreadPool(count); var start=new CountDownLatch(1);
        try { var futures=new ArrayList<Future<T>>(); for(int i=0;i<count;i++) { futures.add(pool.submit(() -> { start.await(); return call.call(); })); } start.countDown(); var results=new ArrayList<T>(); for(var f:futures) { results.add(f.get(20,TimeUnit.SECONDS)); } return results; }
        finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }
    private static Connection wrap(Connection real,String prefix,boolean lostCommit) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a) -> {
            Object result=invoke(real,m,a);
            if(lostCommit&&m.getName().equals("commit")) { throw new SQLException("Lost commit reply","08006"); }
            if(m.getName().equals("prepareStatement")&&!prefix.isEmpty()&&((String)a[0]).startsWith(prefix)) {
                var statement=(PreparedStatement)result;
                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(s,action,args) -> { Object value=invoke(statement,action,args); if(action.getName().equals("executeUpdate")) { throw new SQLException("After actual write"); } return value; });
            }
            return result;
        });
    }
    private static Object invoke(Object target,Method method,Object[] args) throws Throwable { try { return method.invoke(target,args); } catch(InvocationTargetException failure) { throw failure.getCause(); } }
}
