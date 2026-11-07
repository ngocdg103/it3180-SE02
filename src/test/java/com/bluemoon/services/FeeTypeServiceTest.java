package com.bluemoon.services;

import com.bluemoon.dao.FeeTypeRepository;
import com.bluemoon.models.*;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.bluemoon.services.FeeTypeResult.Status.*;

class FeeTypeServiceTest {
    private final Memory repository = new Memory();
    private FeeTypeServiceImpl service(Role role) {
        var auth = new RoleBasedAuthorizationService(() -> role == null ? Optional.empty()
                : Optional.of(new Account(1, "staff", "Staff", role)), null);
        return new FeeTypeServiceImpl(repository, auth);
    }
    private static FeeTypeForm form(String code, String amount) {
        return new FeeTypeForm(code, " Phí quản lý chung cư ", FeeKind.BAT_BUOC,
                ChargeMethod.THEO_DIEN_TICH, amount, FeeStatus.DANG_AP_DUNG);
    }

    @Test void lifecycleAndBothAcceptanceCriteria() {
        for (Role role : List.of(Role.ADMIN, Role.ACCOUNTANT)) {
            repository.rows.clear();
            var s = service(role);
            assertTrue(s.listFeeTypes(null).data().orElseThrow().isEmpty());
            var created = s.createFeeType(form("PHI_QL", ""));
            assertEquals(SUCCESS, created.status());
            assertEquals("Tạo khoản thu PHI_QL thành công", created.message());
            assertEquals("Phí quản lý chung cư", created.data().orElseThrow().tenKhoanThu());
            assertEquals(0, created.data().orElseThrow().soTienMacDinh().signum());
            assertTrue(s.getFeeType("PHI_QL").isSuccess());
            assertTrue(s.updateFeeType("PHI_QL", form("PHI_QL", "7000.50")).isSuccess());
            assertEquals("7000.50", s.getFeeType("PHI_QL").data().orElseThrow().soTienMacDinh().toPlainString());
            var donation = new FeeTypeForm("QUY_NGHEO_2026", "Quỹ vì người nghèo năm 2026", FeeKind.DONG_GOP,
                    ChargeMethod.TU_NGUYEN, "0", FeeStatus.DANG_AP_DUNG);
            assertTrue(s.createFeeType(donation).isSuccess());
            assertTrue(s.deactivateFeeType("PHI_QL").isSuccess());
            assertTrue(s.deactivateFeeType("PHI_QL").isSuccess());
            assertEquals(2, s.listFeeTypes(null).data().orElseThrow().size(), "deactivation preserves row");
            assertEquals(1, s.listFeeTypes(FeeStatus.NGUNG_AP_DUNG).data().orElseThrow().size());
            assertEquals(FeeStatus.NGUNG_AP_DUNG, s.getFeeType("PHI_QL").data().orElseThrow().trangThai());
            assertEquals(NOT_FOUND, s.getFeeType("MISSING").status());
            assertEquals(NOT_FOUND, s.updateFeeType("MISSING", form("MISSING", "0")).status());
            assertEquals(NOT_FOUND, s.deactivateFeeType("MISSING").status());
        }
    }

    @Test void unauthorizedOperationsFailBeforeRepositoryOrValidation() {
        for (Role role : new Role[]{null, Role.RESIDENT}) {
            var s = service(role);
            assertThrows(AccessDeniedException.class, () -> s.listFeeTypes(null));
            assertThrows(AccessDeniedException.class, () -> s.getFeeType(null));
            assertThrows(AccessDeniedException.class, () -> s.createFeeType(null));
            assertThrows(AccessDeniedException.class, () -> s.updateFeeType(null, null));
            assertThrows(AccessDeniedException.class, () -> s.deactivateFeeType(null));
        }
        assertEquals(0, repository.calls);
    }

    @Test void nullFormReportsAllFieldsTogether() {
        var result = service(Role.ADMIN).createFeeType(null);
        assertEquals(VALIDATION_ERROR, result.status());
        assertEquals(Set.of("ma_khoan_thu", "ten_khoan_thu", "loai_khoan_thu", "hinh_thuc_thu", "trang_thai"), result.fieldErrors().keySet());
        assertEquals(0, repository.calls);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "phi", " PHI", "PHI ", "PHÍ", "PHI-A", "A12345678901234567890", "X' OR 1=1"})
    void invalidCodes(String code) {
        var s = service(Role.ADMIN);
        assertEquals(VALIDATION_ERROR, s.createFeeType(form(code, "0")).status());
        assertEquals(VALIDATION_ERROR, s.getFeeType(code).status());
        assertEquals(VALIDATION_ERROR, s.updateFeeType(code, form(code, "0")).status());
        assertEquals(VALIDATION_ERROR, s.deactivateFeeType(code).status());
        assertEquals(0, repository.calls);
    }

    @ParameterizedTest @ValueSource(strings={"-1", "1.001", "0.000", "100000000000000000", "1e2", "NaN", "1,00", "+1", ".5", "1."})
    void rejectsAmountsWithoutRounding(String amount) {
        var result = service(Role.ADMIN).createFeeType(form("PHI", amount));
        assertEquals(VALIDATION_ERROR, result.status());
        assertTrue(result.fieldErrors().containsKey("so_tien_mac_dinh"));
        assertEquals(0, repository.calls);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"0", "0.00", "99999999999999999.99", "0.01"})
    void decimalBoundaries(String amount) { assertTrue(service(Role.ADMIN).createFeeType(form("PHI", amount)).isSuccess()); }

    @Test void validatesNameBoundariesAndEnums() {
        var s = service(Role.ADMIN);
        for (String name : new String[]{null, " ", "ab", "a".repeat(101)}) {
            assertTrue(s.createFeeType(new FeeTypeForm("PHI", name, FeeKind.BAT_BUOC,
                    ChargeMethod.THEO_CAN_HO, "0", FeeStatus.DANG_AP_DUNG)).fieldErrors().containsKey("ten_khoan_thu"));
        }
        for (String name : List.of("abc", "a".repeat(100))) {
            repository.rows.clear();
            assertTrue(s.createFeeType(new FeeTypeForm("PHI", name, FeeKind.BAT_BUOC,
                    ChargeMethod.THEO_CAN_HO, "0", FeeStatus.DANG_AP_DUNG)).isSuccess());
        }
        assertTrue(s.createFeeType(new FeeTypeForm("PHI", "Valid", FeeKind.BAT_BUOC,
                ChargeMethod.TU_NGUYEN, "0", null)).fieldErrors().keySet().containsAll(Set.of("hinh_thuc_thu", "trang_thai")));
        var invalid = s.createFeeType(new FeeTypeForm("QUY", "Quỹ thu", FeeKind.DONG_GOP,
                ChargeMethod.THEO_CAN_HO, "1", FeeStatus.DANG_AP_DUNG));
        assertTrue(invalid.fieldErrors().keySet().containsAll(Set.of("hinh_thuc_thu", "so_tien_mac_dinh")));
    }

    @Test void duplicateAndImmutableCode() {
        var s = service(Role.ADMIN);
        s.createFeeType(form("PHI", "1"));
        var duplicate = s.createFeeType(form("PHI", "2"));
        assertEquals(DUPLICATE, duplicate.status());
        assertTrue(duplicate.fieldErrors().containsKey("ma_khoan_thu"));
        assertEquals(VALIDATION_ERROR, s.updateFeeType("PHI", form("OTHER", "2")).status());
        assertEquals("1", s.getFeeType("PHI").data().orElseThrow().soTienMacDinh().toPlainString());
        assertFalse(repository.rows.containsKey("OTHER"));
    }

    @Test void databaseFailuresAreGenericForAllOperations() {
        repository.failure = true;
        var s = service(Role.ADMIN);
        for (var result : List.of(s.listFeeTypes(null), s.getFeeType("PHI"), s.createFeeType(form("PHI", "0")),
                s.updateFeeType("PHI", form("PHI", "0")), s.deactivateFeeType("PHI"))) {
            assertEquals(SYSTEM_ERROR, result.status());
            assertFalse(result.message().contains("secret"));
            assertTrue(result.fieldErrors().isEmpty());
        }
    }

    private static final class Memory implements FeeTypeRepository {
        final Map<String, FeeType> rows = new TreeMap<>();
        boolean failure;
        int calls;
        void check() throws SQLException { calls++; if (failure) { throw new SQLException("secret connection details"); } }
        public List<FeeType> findAll(FeeStatus filter) throws SQLException {
            check(); return rows.values().stream().filter(f -> filter == null || f.trangThai() == filter).toList();
        }
        public Optional<FeeType> findByCode(String code) throws SQLException { check(); return Optional.ofNullable(rows.get(code)); }
        public void insert(FeeType fee) throws SQLException {
            check(); if (rows.containsKey(fee.maKhoanThu())) { throw new SQLException("duplicate", "23000", 1062); }
            rows.put(fee.maKhoanThu(), fee);
        }
        public Optional<FeeType> update(FeeType fee) throws SQLException {
            check(); return rows.replace(fee.maKhoanThu(), fee) == null ? Optional.empty() : Optional.of(fee);
        }
        public Optional<FeeType> deactivate(String code) throws SQLException {
            check(); var old = rows.get(code);
            return old == null ? Optional.empty() : update(new FeeType(code, old.tenKhoanThu(), old.loai(),
                    old.hinhThuc(), old.soTienMacDinh(), FeeStatus.NGUNG_AP_DUNG));
        }
    }
}
