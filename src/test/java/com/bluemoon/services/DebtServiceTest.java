package com.bluemoon.services;

import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.bluemoon.services.DebtResult.Status.*;

class DebtServiceTest {
    private final Repository repository=new Repository();
    private final DebtService service=new DebtServiceImpl(repository,new RoleBasedAuthorizationService(null));
    @BeforeEach void login() { FeeCategorySessionFixture.use(Role.ADMIN); }
    @AfterEach void logout() { FeeCategorySessionFixture.use(null); }
    @ParameterizedTest @EnumSource(Role.class)
    void roleMatrixGuardsBothMethods(Role role) {
        FeeCategorySessionFixture.use(role);
        if(role==Role.RESIDENT) { assertThrows(AccessDeniedException.class,() -> service.getDebtByPeriod(null)); assertThrows(AccessDeniedException.class,() -> service.getHouseholdBalance(null)); assertEquals(0,repository.calls); }
        else { assertTrue(service.getDebtByPeriod(null).isSuccess()); assertTrue(service.getHouseholdBalance("HK-1204").isSuccess()); }
    }
    @Test void noSessionAndLogoutDenyBeforeIo() {
        assertTrue(service.getDebtByPeriod(null).isSuccess()); int before=repository.calls; FeeCategorySessionFixture.use(null);
        assertThrows(AccessDeniedException.class,() -> service.getDebtByPeriod(new DebtQuery("bad",null,-1,-2))); assertThrows(AccessDeniedException.class,() -> service.getHouseholdBalance(null)); assertEquals(before,repository.calls);
    }
    @Test void outstandingFilterAndFloorRangeKeepWholePeriodKpisAndCorrectFooter() {
        var report=service.getDebtByPeriod(new DebtQuery(null,Set.of(ChargeStatus.CHUA_DONG,ChargeStatus.DONG_THIEU),10,15)).data().orElseThrow();
        assertNull(repository.period); assertEquals("2026-11",report.kyThu()); assertEquals(2,report.rows().size());
        assertEquals(new BigDecimal("400.00"),report.tongPhaiThu()); assertEquals(new BigDecimal("250.00"),report.tongDaThu()); assertEquals(new BigDecimal("170.00"),report.tongNo());
        assertEquals(new BigDecimal("62.50"),report.tyLeThu()); assertEquals(2,report.hoHoanThanh()); assertEquals(4,report.tongSoHo()); assertEquals(new BigDecimal("170.00"),report.filteredDebt());
        var only12=service.getDebtByPeriod(new DebtQuery("2026-11",Set.of(),12,12)).data().orElseThrow();
        assertEquals(1,only12.rows().size()); assertEquals(new BigDecimal("70.00"),only12.filteredDebt()); assertEquals(report.tongNo(),only12.tongNo());
        assertThrows(UnsupportedOperationException.class,() -> report.rows().clear());
    }
    @ParameterizedTest @EnumSource(ChargeStatus.class)
    void exactBillingStatusesAreReusableWithoutNewFinancialEnum(ChargeStatus status) {
        var report=service.getDebtByPeriod(new DebtQuery("2026-11",Set.of(status),null,null)).data().orElseThrow();
        assertEquals(1,report.rows().size()); assertEquals(status,report.rows().get(0).trangThai());
    }
    @Test void completedFilterIncludesSurplusButNeverNetsItAgainstOtherDebt() {
        var report=service.getDebtByPeriod(new DebtQuery(null,Set.of(ChargeStatus.DA_DONG_DU,ChargeStatus.DONG_THUA),null,null)).data().orElseThrow();
        assertEquals(2,report.rows().size()); assertEquals(new BigDecimal("0.00"),report.filteredDebt()); assertEquals(new BigDecimal("170.00"),report.tongNo());
    }
    @ParameterizedTest @ValueSource(strings={"2026-13","2026-1"," 2026-11","2026-11' OR 1=1","abcd"})
    void invalidPeriodNeverQueries(String period) { assertEquals(VALIDATION_ERROR,service.getDebtByPeriod(new DebtQuery(period,null,null,null)).status()); assertEquals(0,repository.calls); }
    @Test void invalidFloorAndNullStatusGiveFieldErrors() {
        var statuses=new HashSet<ChargeStatus>(); statuses.add(null);
        var result=service.getDebtByPeriod(new DebtQuery(null,statuses,0,-1)); assertEquals(VALIDATION_ERROR,result.status()); assertEquals(Set.of("trang_thai","tang_tu","tang_den"),result.fieldErrors().keySet());
        assertEquals(VALIDATION_ERROR,service.getDebtByPeriod(new DebtQuery(null,null,15,10)).status()); assertEquals(0,repository.calls);
    }
    @Test void emptyPeriodAndEmptyFilterAreDifferentAndHouseholdCreditIsSeparate() {
        var noRows=service.getDebtByPeriod(new DebtQuery(null,null,99,100)); assertTrue(noRows.isSuccess()); assertTrue(noRows.data().orElseThrow().rows().isEmpty()); assertTrue(noRows.message().contains("Không có dữ liệu"));
        repository.empty=true; assertEquals(NO_DATA,service.getDebtByPeriod(null).status()); assertEquals(NO_DATA,service.getHouseholdBalance("HK-NONE").status());
        assertEquals(VALIDATION_ERROR,service.getHouseholdBalance("").status());
        repository.empty=false; assertEquals(new BigDecimal("20"),service.getHouseholdBalance("HK-1204").data().orElseThrow().soDu());
    }
    @ParameterizedTest @CsvSource({"0,SYSTEM_ERROR","1205,BUSY","1213,BUSY"})
    void sqlFailureDoesNotBecomeEmptyReportOrLeak(int code,DebtResult.Status expected) {
        repository.failure=new SQLException("secret connection password","HY000",code); var result=service.getDebtByPeriod(null);
        assertEquals(expected,result.status()); assertFalse(result.message().contains("secret")); assertEquals(expected,service.getHouseholdBalance("HK-1204").status()); assertEquals(2,repository.calls);
    }
    @Test void percentageUsesDecimalHalfUp() {
        repository.rows=List.of(row("P1204",12,"3","1",ChargeStatus.DONG_THIEU));
        assertEquals(new BigDecimal("33.33"),service.getDebtByPeriod(null).data().orElseThrow().tyLeThu());
    }
    private static DebtRow row(String apartment,int floor,String total,String paid,ChargeStatus status) {
        var billed=new BigDecimal(total); var received=new BigDecimal(paid);
        return new DebtRow("HD-202611-"+apartment,"HK-"+apartment,apartment,floor,"Chủ hộ",new BigDecimal("80"),4,billed,received,billed.subtract(received),status,received.signum()==0?null:LocalDate.of(2026,11,8));
    }
    private static class Repository implements DebtRepository {
        int calls; String period; SQLException failure; boolean empty;
        List<DebtRow> rows=List.of(row("P1001",10,"100","0",ChargeStatus.CHUA_DONG),row("P1201",12,"100","30",ChargeStatus.DONG_THIEU),row("P1501",15,"100","100",ChargeStatus.DA_DONG_DU),row("P2001",20,"100","120",ChargeStatus.DONG_THUA));
        void check() throws SQLException { calls++; if(failure!=null) { throw failure; } }
        public Optional<PeriodData> loadPeriod(String period) throws SQLException { check(); this.period=period; return empty?Optional.empty():Optional.of(new PeriodData("2026-11",rows)); }
        public Optional<HouseholdDebt> getHouseholdBalance(String code) throws SQLException { check(); return empty?Optional.empty():Optional.of(new HouseholdDebt(code,new BigDecimal("100"),new BigDecimal("120"),BigDecimal.ZERO,new BigDecimal("20"))); }
    }
}
