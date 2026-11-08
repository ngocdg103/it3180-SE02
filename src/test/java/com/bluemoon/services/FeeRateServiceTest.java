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
import static com.bluemoon.services.FeeRateResult.Status.*;

class FeeRateServiceTest {
    private final Repository rates = new Repository();
    private final Types types = new Types();
    private final AuthorizationService authorization = new RoleBasedAuthorizationService(null);
    private final FeeRateService service = new FeeRateServiceImpl(rates, types, authorization);
    private final LocalDate day = LocalDate.of(2026, 11, 1);
    private FeeRateForm form(String price) { return new FeeRateForm("PHI_QL", price, day, null); }
    @BeforeEach void login() { FeeCategorySessionFixture.use(Role.ADMIN); }
    @AfterEach void logout() { FeeCategorySessionFixture.use(null); }

    @ParameterizedTest @EnumSource(Role.class)
    void roleMatrixProtectsAllOperationsAndAdapter(Role role) {
        FeeCategorySessionFixture.use(role);
        var adapter = new RepositoryFeeRateLookup(rates, authorization);
        List<Runnable> operations = List.of(() -> service.listFeeTypes(), () -> service.listHistory("PHI_QL"),
                () -> service.getEffectiveRate("PHI_QL", day), () -> service.createRate(form("7000")),
                () -> adapter.getEffectiveRate("PHI_QL", day));
        for (var operation : operations) {
            if (role == Role.RESIDENT) { assertThrows(AccessDeniedException.class, operation::run); }
            else { assertDoesNotThrow(operation::run); }
        }
        if (role == Role.RESIDENT) { assertEquals(0, rates.calls + types.calls); }
    }
    @Test void noSessionDeniesBeforeInvalidInputAndSessionIsNeverCached() {
        assertTrue(service.listHistory("PHI_QL").isSuccess());
        int before = rates.calls;
        FeeCategorySessionFixture.use(null);
        assertThrows(AccessDeniedException.class, () -> service.createRate(null));
        assertThrows(AccessDeniedException.class, () -> service.listHistory(null));
        assertThrows(AccessDeniedException.class, () -> service.getEffectiveRate(null, null));
        assertThrows(AccessDeniedException.class, service::listFeeTypes);
        assertThrows(AccessDeniedException.class, () -> new RepositoryFeeRateLookup(rates, authorization).getEffectiveRate(null, null));
        assertEquals(before, rates.calls); assertEquals(0, types.calls);
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);
        assertTrue(service.createRate(form("1")).isSuccess());
        FeeCategorySessionFixture.use(Role.RESIDENT);
        assertThrows(AccessDeniedException.class, () -> service.createRate(form("1")));
    }
    @Test void creationIsAppendOnlyAndKeepsExactDecimalDateAndUnicode() {
        var result = service.createRate(new FeeRateForm("PHI_QL", "99999999999999999.99", day, "😀".repeat(255)));
        assertEquals(SUCCESS, result.status());
        assertNull(rates.input.id()); assertEquals(new BigDecimal("99999999999999999.99"), rates.input.donGia());
        assertEquals(day, rates.input.ngayApDung()); assertEquals("😀".repeat(255), rates.input.ghiChu());
        assertEquals(91L, result.data().orElseThrow().id()); assertEquals(1, rates.calls);
        assertTrue(service.createRate(form("0.01")).isSuccess());
        assertTrue(service.createRate(form(" 7000.50 ")).isSuccess());
        assertEquals(new BigDecimal("7000.50"), rates.input.donGia()); assertNull(rates.input.ghiChu());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"0", "-1", "1.001", "1.230", "1e3", "NaN", "1,000", "1,5", "100000000000000000", " ", ".5"})
    void invalidPricesAreFieldErrorsBeforeIo(String price) {
        var result = service.createRate(form(price));
        assertEquals(VALIDATION_ERROR, result.status()); assertTrue(result.fieldErrors().containsKey("don_gia"));
        assertEquals(0, rates.calls + types.calls);
    }
    @Test void nullFormInvalidCodeDateAndNoteAreReportedTogether() {
        var result = service.createRate(new FeeRateForm("x' OR 1=1", "0", LocalDate.of(999, 1, 1), "😀".repeat(256)));
        assertEquals(Set.of("ma_khoan_thu", "don_gia", "ngay_ap_dung", "ghi_chu"), result.fieldErrors().keySet());
        assertEquals(VALIDATION_ERROR, service.createRate(null).status());
        assertEquals(VALIDATION_ERROR, service.createRate(new FeeRateForm("PHI", "1", LocalDate.of(10000, 1, 1), null)).status());
        assertEquals(0, rates.calls + types.calls);
    }
    @Test void readsValidateAndReturnImmutableHistoryAndMissingRateAsSuccessEmpty() {
        assertEquals(VALIDATION_ERROR, service.listHistory(null).status());
        assertEquals(VALIDATION_ERROR, service.getEffectiveRate("PHI_QL", null).status());
        assertEquals(0, rates.calls);
        assertTrue(service.getEffectiveRate("PHI_QL", day).data().orElseThrow().isEmpty());
        assertEquals(day, rates.target);
        var history = service.listHistory("PHI_QL").data().orElseThrow();
        rates.history.add(new FeeRate(1L, "PHI_QL", BigDecimal.ONE, day, null));
        assertTrue(history.isEmpty()); assertThrows(UnsupportedOperationException.class, () -> history.clear());
    }
    @Test void selectorsIncludeStoppedMandatoryAndExcludeVoluntaryWithoutExtraCategoryPermission() {
        var list = service.listFeeTypes().data().orElseThrow();
        assertEquals(List.of("PHI_QL", "OLD"), list.stream().map(FeeType::maKhoanThu).toList());
        assertThrows(UnsupportedOperationException.class, list::clear);
    }
    @ParameterizedTest @CsvSource({"02000,0,NOT_FOUND", "45000,0,VALIDATION_ERROR", "23000,1062,DUPLICATE", "40001,1213,BUSY", "HY000,1205,BUSY", "23000,1452,NOT_FOUND", "23000,3819,SYSTEM_ERROR", "23000,1451,SYSTEM_ERROR", "08006,0,SYSTEM_ERROR"})
    void sqlFailuresMapPreciselyWithoutLeakingOrRetrying(String state, int code, FeeRateResult.Status expected) {
        rates.failure = new SQLException("secret SQL/password", state, code);
        var result = service.createRate(form("7000"));
        assertEquals(expected, result.status()); assertEquals(1, rates.calls); assertTrue(result.data().isEmpty());
        assertFalse(result.message().contains("secret")); assertFalse(result.fieldErrors().toString().contains("secret"));
        if (expected == DUPLICATE) { assertTrue(result.fieldErrors().containsKey("ngay_ap_dung")); }
        if (expected == VALIDATION_ERROR || expected == NOT_FOUND) { assertTrue(result.fieldErrors().containsKey("ma_khoan_thu")); }
    }
    @Test void readFailuresNeverBecomeEmptySuccessAndAdapterPreservesEffectiveDate() {
        var adapter = new RepositoryFeeRateLookup(rates, authorization);
        assertTrue(adapter.getEffectiveRate("PHI_QL", day).isEmpty());
        rates.effective = Optional.of(new FeeRate(2L, "PHI_QL", new BigDecimal("7000.50"), day.minusMonths(2), null));
        var result = adapter.getEffectiveRate("PHI_QL", day).orElseThrow();
        assertEquals("PHI_QL", result.maKhoanThu()); assertEquals(day.minusMonths(2), result.ngayApDung());
        assertEquals(new BigDecimal("7000.50"), result.donGia());
        rates.failure = new SQLException("private SQL");
        assertEquals(SYSTEM_ERROR, service.listHistory("PHI_QL").status());
        assertEquals(SYSTEM_ERROR, service.getEffectiveRate("PHI_QL", day).status());
        var failure = assertThrows(IllegalStateException.class, () -> adapter.getEffectiveRate("PHI_QL", day));
        assertSame(rates.failure, failure.getCause()); assertFalse(failure.getMessage().contains("private"));
        types.failure = rates.failure; assertEquals(SYSTEM_ERROR, service.listFeeTypes().status());
    }
    @Test void resultDefensivelyCopiesFieldErrors() {
        var errors = new HashMap<String, String>(); errors.put("don_gia", "bad");
        var result = FeeRateResult.failure(VALIDATION_ERROR, "bad", errors); errors.clear();
        assertEquals("bad", result.fieldErrors().get("don_gia"));
        assertThrows(UnsupportedOperationException.class, () -> result.fieldErrors().clear());
        assertThrows(IllegalArgumentException.class, () -> FeeRateResult.failure(SUCCESS, "bad", Map.of()));
    }
    @ParameterizedTest @ValueSource(ints = {1213, 1205})
    void readContentionIsBusyAndNotMissingRate(int code) {
        rates.failure = new SQLException("private", "HY000", code); types.failure = rates.failure;
        assertEquals(BUSY, service.listFeeTypes().status());
        assertEquals(BUSY, service.listHistory("PHI_QL").status());
        assertEquals(BUSY, service.getEffectiveRate("PHI_QL", day).status());
        assertEquals(2, rates.calls); assertEquals(1, types.calls);
    }
    @Test void adapterRejectsInvalidRequestBeforeRepository() {
        var adapter = new RepositoryFeeRateLookup(rates, authorization);
        assertThrows(IllegalArgumentException.class, () -> adapter.getEffectiveRate("' OR 1=1", day));
        assertThrows(NullPointerException.class, () -> adapter.getEffectiveRate("PHI_QL", null));
        assertEquals(0, rates.calls);
    }
    private static class Repository implements FeeRateRepository {
        int calls; SQLException failure; FeeRate input; LocalDate target;
        List<FeeRate> history = new ArrayList<>(); Optional<FeeRate> effective = Optional.empty();
        void check() throws SQLException { calls++; if (failure != null) { throw failure; } }
        public FeeRate insert(FeeRate rate) throws SQLException { check(); input = rate; return new FeeRate(91L, rate.maLoaiThu(), rate.donGia(), rate.ngayApDung(), rate.ghiChu()); }
        public List<FeeRate> findHistory(String code) throws SQLException { check(); return history; }
        public Optional<FeeRate> findEffectiveRate(String code, LocalDate date) throws SQLException { check(); target = date; return effective; }
    }
    private static class Types implements FeeTypeRepository {
        int calls; SQLException failure;
        public List<FeeType> findAll(FeeStatus filter) throws SQLException {
            calls++; if (failure != null) { throw failure; }
            return List.of(type("PHI_QL", FeeKind.BAT_BUOC, FeeStatus.DANG_AP_DUNG), type("OLD", FeeKind.BAT_BUOC, FeeStatus.NGUNG_AP_DUNG), type("QUY", FeeKind.DONG_GOP, FeeStatus.DANG_AP_DUNG));
        }
        static FeeType type(String code, FeeKind kind, FeeStatus status) { return new FeeType(code, code, kind, ChargeMethod.THEO_CAN_HO, BigDecimal.ZERO, status); }
        public Optional<FeeType> findByCode(String code) { throw new AssertionError("No eligibility precheck may replace DAO transaction"); }
        public void insert(FeeType fee) { throw new AssertionError(); }
        public Optional<FeeType> update(FeeType fee) { throw new AssertionError(); }
        public Optional<FeeType> deactivate(String code) { throw new AssertionError(); }
    }
}
