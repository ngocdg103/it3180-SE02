package com.bluemoon.services;

import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static com.bluemoon.services.ReceiptResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptServiceTest {
    private final Repository repository=new Repository();
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"),ZoneOffset.UTC);
    private final ReceiptService service=new ReceiptServiceImpl(repository,new RoleBasedAuthorizationService(null),clock);
    private final String id=UUID.randomUUID().toString();
    private ReceiptForm form(String amount) { return new ReceiptForm(id,"HD-202611-P1204"," Nguyễn A ",amount,LocalDate.of(2026,11,8),PaymentMethod.TIEN_MAT,null); }
    @BeforeEach void login() { FeeCategorySessionFixture.use(Role.ACCOUNTANT); }
    @AfterEach void logout() { FeeCategorySessionFixture.use(null); }
    @ParameterizedTest @EnumSource(Role.class)
    void onlyAccountantMayRecordButStaffCanRead(Role role) {
        FeeCategorySessionFixture.use(role);
        if(role==Role.ACCOUNTANT) { assertTrue(service.recordPayment(form("1000000")).isSuccess()); assertEquals(71,repository.input.createdBy()); }
        else { assertThrows(AccessDeniedException.class,() -> service.recordPayment(null)); assertEquals(0,repository.calls); }
        if(role==Role.RESIDENT) {
            assertThrows(AccessDeniedException.class,service::listOutstanding);
            assertThrows(AccessDeniedException.class,() -> service.listByHousehold(null));
            assertThrows(AccessDeniedException.class,() -> service.findByRequestId(null)); assertEquals(0,repository.calls);
        } else { assertTrue(service.listOutstanding().isSuccess()); assertTrue(service.listByHousehold("HK-1204").isSuccess()); assertTrue(service.findByRequestId(id).isSuccess()); }
    }
    @Test void logoutIsNotCachedAndDeniesBeforeValidation() {
        assertTrue(service.recordPayment(form("1000")).isSuccess()); int calls=repository.calls;
        FeeCategorySessionFixture.use(null);
        assertThrows(AccessDeniedException.class,() -> service.recordPayment(null)); assertThrows(AccessDeniedException.class,service::listOutstanding);
        assertEquals(calls,repository.calls);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"0","-1","0.5","1.5","1e3","NaN","1,000","100000000000000000","1.001"})
    void invalidAmountNeverCallsRepository(String value) {
        var result=service.recordPayment(form(value)); assertEquals(VALIDATION_ERROR,result.status()); assertTrue(result.fieldErrors().containsKey("so_tien_nop")); assertEquals(0,repository.calls);
    }
    @Test void invalidFieldsAndUnicodeAreValidatedTogether() {
        var result=service.recordPayment(new ReceiptForm("bad","","A","1",LocalDate.of(2026,11,9),null,"😀".repeat(256)));
        assertEquals(Set.of("request_id","ma_hoa_don","nguoi_nop","ngay_nop","hinh_thuc","ghi_chu"),result.fieldErrors().keySet());
        assertEquals(0,repository.calls);
        var valid=new ReceiptForm(id,"HD-202611-P1204","😀".repeat(50),"1.00",LocalDate.of(2026,11,8),PaymentMethod.CHUYEN_KHOAN,"😀".repeat(255));
        assertTrue(service.recordPayment(valid).isSuccess()); assertEquals(new BigDecimal("1.00"),repository.input.soTienNop());
    }
    @Test void paymentDateUsesVietnamDateRatherThanClockZone() {
        var nearMidnight=new ReceiptServiceImpl(repository,new RoleBasedAuthorizationService(null),Clock.fixed(Instant.parse("2026-11-07T18:00:00Z"),ZoneOffset.UTC));
        assertTrue(nearMidnight.recordPayment(form("1")).isSuccess());
    }
    @ParameterizedTest @CsvSource({"NOT_FOUND,NOT_FOUND","ALREADY_SETTLED,ALREADY_SETTLED","REQUEST_CONFLICT,CONFLICT","AMOUNT_OVERFLOW,VALIDATION_ERROR"})
    void businessRefusalsAreNotSystemErrors(ReceiptRuleException.Reason reason,ReceiptResult.Status status) {
        repository.rule=new ReceiptRuleException(reason); assertEquals(status,service.recordPayment(form("1000")).status()); assertEquals(1,repository.calls);
    }
    @ParameterizedTest @CsvSource({"1062,CONFLICT","1205,BUSY","1213,BUSY","1452,SYSTEM_ERROR","0,SYSTEM_ERROR"})
    void sqlMappingNeverRetriesOrLeaks(int code,ReceiptResult.Status status) {
        repository.failure=new SQLException("secret SQL password","08006",code);
        var result=service.recordPayment(form("1000")); assertEquals(status,result.status()); assertEquals(1,repository.calls); assertFalse(result.message().contains("secret"));
    }
    @Test void exactReplayIsDuplicateNotSecondSuccessAndErrorsAreImmutable() {
        repository.replay=true; assertEquals(DUPLICATE,service.recordPayment(form("1")).status()); assertEquals(1,repository.calls);
        var errors=new HashMap<String,String>(); errors.put("a","b"); var result=ReceiptResult.failure(VALIDATION_ERROR,"bad",errors); errors.clear();
        assertEquals("b",result.fieldErrors().get("a")); assertThrows(UnsupportedOperationException.class,() -> result.fieldErrors().clear());
    }
    @Test void readsRejectInvalidRequestsAndDatabaseFailureIsNotEmptySuccess() {
        assertEquals(VALIDATION_ERROR,service.listByHousehold("").status()); assertEquals(VALIDATION_ERROR,service.findByRequestId("bad").status()); assertEquals(0,repository.calls);
        repository.failure=new SQLException("secret"); assertEquals(SYSTEM_ERROR,service.listOutstanding().status()); assertEquals(SYSTEM_ERROR,service.listByHousehold("HK-1204").status()); assertEquals(SYSTEM_ERROR,service.findByRequestId(id).status());
    }
    private static class Repository implements ReceiptRepository {
        int calls; Receipt input; SQLException failure; ReceiptRuleException rule; boolean replay;
        void check() throws SQLException { calls++; if(failure!=null) { throw failure; } if(rule!=null) { throw rule; } }
        public ReceiptOutcome record(Receipt r) throws SQLException { check(); input=r; return new ReceiptOutcome(r,new PaymentInvoice(r.maHoaDon(),"2026-11","HK-1204","P1204",new BigDecimal("1384000"),r.soTienNop(),new BigDecimal("1384000").subtract(r.soTienNop()),ChargeStatus.DONG_THIEU),replay); }
        public List<PaymentInvoice> listOutstanding() throws SQLException { check(); return List.of(); }
        public List<Receipt> listByHousehold(String code) throws SQLException { check(); return List.of(); }
        public Optional<Receipt> findByRequestId(String id) throws SQLException { check(); return Optional.empty(); }
    }
}
