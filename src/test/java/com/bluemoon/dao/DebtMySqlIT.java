package com.bluemoon.dao;

import com.bluemoon.config.*;
import com.bluemoon.controllers.DebtModule;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST",matches="1")
@Timeout(60)
class DebtMySqlIT {
    private static DatabaseConnection db;
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"),ZoneOffset.UTC);
    private DebtService service;
    private ReceiptService payments;
    @BeforeAll static void schema() throws Exception {
        db=new DatabaseConnection(DatabaseConfig.load());
        try(var c=db.getConnection()) { safe(c); assertEquals("MySQL",c.getMetaData().getDatabaseProductName()); }
        for(String path:List.of("database/schema.sql","database/migrations/us001_household_apartment.sql","database/migrations/us002_residents.sql","database/migrations/us005_fee_types.sql","database/migrations/us006_fee_rates.sql","database/migrations/us007_billing.sql","database/migrations/us008_receipts.sql")) {
            String text=Files.readString(Path.of(path)).replaceAll("(?m)^\\s*--.*$",""); for(String command:text.split(";")) { if(!command.isBlank()) { sql(command); } }
        }
    }
    static void safe(Connection c) throws SQLException { assertNotNull(c.getCatalog()); assertTrue(c.getCatalog().endsWith("_test"),"Isolated disposable test schema only"); }
    static void sql(String command) throws SQLException { try(var c=db.getConnection()) { safe(c); try(var s=c.createStatement()) { s.execute(command); } } }
    @BeforeEach void fixture() throws Exception {
        for(String table:List.of("phieu_thu","apartment_credit","invoice_lines","invoices","billing_periods","residents","households","apartments","fee_rates","fee_types")) { sql("DELETE FROM "+table); }
        sql("INSERT IGNORE INTO users(id,username,password_hash,ho_ten,role) VALUES(71,'debt-it','x','Debt IT','ACCOUNTANT')");
        for(int floor:List.of(12,20)) {
            String apartment="P"+floor+"04", household="HK-"+floor+"04";
            sql("INSERT INTO apartments(so_can_ho,tang,dien_tich,trang_thai) VALUES('"+apartment+"',"+floor+",80,'DANG_O')");
            sql("INSERT INTO households(ma_ho,so_can_ho,ngay_nhan_nha) VALUES('"+household+"','"+apartment+"','2020-01-01')");
            sql("INSERT INTO residents(ma_ho,ho_ten,ngay_sinh,gioi_tinh,so_cccd,quan_he_chu_ho,ngay_chuyen_den) VALUES('"+household+"','Chu ho "+floor+"','1990-01-01','NAM','0000000000"+floor+"','CHU_HO','2020-01-01')");
        }
        sql("INSERT INTO fee_types(ma_loai_thu,ten_loai_thu,loai_thu,phuong_thuc_tinh,trang_thai) VALUES('US009_IT','Debt fixture','BAT_BUOC','THEO_CAN_HO','DANG_AP_DUNG')");
        new FeeRateDAO(db).insert(new FeeRate(null,"US009_IT",new BigDecimal("1384000"),LocalDate.of(2026,1,1),null));
        FeeCategorySessionFixture.use(Role.ACCOUNTANT); assertTrue(new BillingServiceImpl(new BillingDAO(db),CLOCK).generatePeriod("2026-11").isSuccess());
        service=DebtModule.createService(db); payments=payments(new ReceiptDAO(db));
    }
    @AfterEach void logout() { FeeCategorySessionFixture.use(null); }
    @AfterAll static void cleanup() throws Exception { for(String table:List.of("phieu_thu","apartment_credit","invoice_lines","invoices","billing_periods","fee_rates")) { sql("DROP TABLE IF EXISTS "+table); } }
    ReceiptService payments(ReceiptRepository repo) { return new ReceiptServiceImpl(repo,new RoleBasedAuthorizationService(null),CLOCK); }
    ReceiptForm form(String amount) { return new ReceiptForm(UUID.randomUUID().toString(),"HD-202611-P1204","Nguyễn A",amount,LocalDate.of(2026,11,8),PaymentMethod.TIEN_MAT,null); }
    DebtReport report() { return service.getDebtByPeriod(null).data().orElseThrow(); }
    @Test void partialAndFullPaymentsUpdateDebtAndWholePeriodKpis() {
        assertEquals(0,new BigDecimal("2768000").compareTo(report().tongNo()));
        assertTrue(payments.recordPayment(form("1000000")).isSuccess());
        var partial=report(); assertEquals(0,new BigDecimal("1768000").compareTo(partial.tongNo())); assertEquals(0,new BigDecimal("1000000").compareTo(partial.tongDaThu()));
        var first=partial.rows().stream().filter(row -> row.soCanHo().equals("P1204")).findFirst().orElseThrow(); assertEquals(ChargeStatus.DONG_THIEU,first.trangThai()); assertEquals(LocalDate.of(2026,11,8),first.lanNopCuoi()); assertEquals("Chu ho 12",first.tenChuHo());
        assertTrue(payments.recordPayment(form("384000")).isSuccess());
        var settled=report(); assertEquals(1,settled.hoHoanThanh()); assertEquals(new BigDecimal("50.00"),settled.tyLeThu());
        var outstanding=service.getDebtByPeriod(new DebtQuery("2026-11",Set.of(ChargeStatus.CHUA_DONG,ChargeStatus.DONG_THIEU),10,25)).data().orElseThrow();
        assertEquals(1,outstanding.rows().size()); assertEquals("P2004",outstanding.rows().get(0).soCanHo()); assertEquals(0,new BigDecimal("1384000").compareTo(outstanding.filteredDebt())); assertEquals(0,new BigDecimal("2768000").compareTo(outstanding.tongPhaiThu()));
    }
    @Test void floorAndStatusFilterEmptyPeriodAndHouseholdBalance() {
        assertTrue(payments.recordPayment(form("1000000")).isSuccess());
        var only=service.getDebtByPeriod(new DebtQuery(null,Set.of(ChargeStatus.DONG_THIEU),10,15)).data().orElseThrow(); assertEquals(1,only.rows().size()); assertEquals("P1204",only.rows().get(0).soCanHo());
        var household=service.getHouseholdBalance("HK-1204").data().orElseThrow(); assertEquals(0,new BigDecimal("384000").compareTo(household.tongNo()));
        assertEquals(DebtResult.Status.NO_DATA,service.getDebtByPeriod(new DebtQuery("2026-10",null,null,null)).status());
        assertEquals(DebtResult.Status.NO_DATA,service.getHouseholdBalance("' OR 1=1").status());
    }
    @Test void surplusIsCreditAndCannotCancelOtherApartmentDebt() {
        assertTrue(payments.recordPayment(form("1400000")).isSuccess()); var result=report();
        assertEquals(0,new BigDecimal("1384000").compareTo(result.tongNo())); assertEquals(1,result.hoHoanThanh());
        assertEquals(0,new BigDecimal("16000").compareTo(service.getHouseholdBalance("HK-1204").data().orElseThrow().soDu()));
    }
    @Test void noUncommittedPaymentIsVisibleThenCommittedReceiptAndBalanceAppearTogether() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1); var pool=Executors.newSingleThreadExecutor();
        var gated=new ReceiptDAO(() -> {
            Connection real=db.getConnection();
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a) -> {
                if(m.getName().equals("commit")) { entered.countDown(); if(!release.await(10,TimeUnit.SECONDS)) { throw new SQLException("Gate timed out"); } }
                try { return m.invoke(real,a); } catch(InvocationTargetException failure) { throw failure.getCause(); }
            });
        });
        try {
            var pending=pool.submit(() -> payments(gated).recordPayment(form("1000"))); assertTrue(entered.await(5,TimeUnit.SECONDS));
            var before=report(); assertEquals(0,before.tongDaThu().signum()); assertTrue(before.rows().stream().allMatch(row -> row.lanNopCuoi()==null));
            release.countDown(); assertTrue(pending.get(10,TimeUnit.SECONDS).isSuccess());
            var after=report(); assertEquals(0,new BigDecimal("1000").compareTo(after.tongDaThu())); assertNotNull(after.rows().stream().filter(row -> row.soCanHo().equals("P1204")).findFirst().orElseThrow().lanNopCuoi());
        } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }
    @Test void reportUsesBilledAreaAndPeopleButCurrentOwnerAndEnforcesRbac() throws Exception {
        sql("UPDATE apartments SET dien_tich=90 WHERE so_can_ho='P1204'"); sql("UPDATE residents SET ho_ten='Owner updated' WHERE ma_ho='HK-1204'");
        var row=report().rows().stream().filter(r -> r.soCanHo().equals("P1204")).findFirst().orElseThrow(); assertEquals(0,new BigDecimal("80").compareTo(row.dienTich())); assertEquals(1,row.soNguoi()); assertEquals("Owner updated",row.tenChuHo());
        FeeCategorySessionFixture.use(Role.ADMIN); assertTrue(service.getDebtByPeriod(null).isSuccess());
        for(Role role:new Role[]{Role.RESIDENT,null}) { FeeCategorySessionFixture.use(role); assertThrows(AccessDeniedException.class,() -> service.getDebtByPeriod(null)); assertThrows(AccessDeniedException.class,() -> service.getHouseholdBalance("HK-1204")); }
    }
}
