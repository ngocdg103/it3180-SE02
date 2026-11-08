package com.bluemoon.services;

import com.bluemoon.config.*;
import com.bluemoon.controllers.FeeRateModule;
import com.bluemoon.dao.FeeRateLookup;
import com.bluemoon.models.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.bluemoon.services.FeeRateResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production Service/DAO/lookup on an exclusively assigned *_test schema; no App Shell/UI E2E claim. */
@EnabledIfEnvironmentVariable(named = "BLUEMOON_MYSQL_TEST", matches = "1")
@Timeout(30)
class FeeRateServiceMySqlIT {
    private static final String ACTIVE = "SVC_RATE_IT", STOPPED = "SVC_OLD_IT", GIFT = "SVC_GIFT_IT";
    private static final LocalDate OLD_DATE = LocalDate.of(2026, 1, 1), NEW_DATE = LocalDate.of(2027, 1, 1);
    private static DatabaseConnection connections;
    private FeeRateService service;
    private FeeRateLookup lookup;

    @BeforeAll static void database() throws Exception {
        connections = new DatabaseConnection(DatabaseConfig.load());
        try (var c = connections.getConnection()) {
            safe(c); assertEquals("MySQL", c.getMetaData().getDatabaseProductName());
            assertTrue(c.getMetaData().getDatabaseMajorVersion() >= 8);
            try (var s = c.prepareStatement("SELECT @@sql_mode, @@default_storage_engine"); var rows = s.executeQuery()) {
                assertTrue(rows.next()); assertTrue(rows.getString(1).contains("STRICT_")); assertEquals("InnoDB", rows.getString(2));
            }
        }
        for (String file : List.of("us005_fee_types.sql", "us006_fee_rates.sql")) {
            String script = Files.readString(Path.of("database/migrations", file)).replaceAll("(?m)^\\s*--.*$", "");
            for (String command : script.split(";")) { if (!command.isBlank()) { execute(command); } }
        }
    }
    private static void safe(Connection c) throws SQLException {
        assertNotNull(c.getCatalog()); assertTrue(c.getCatalog().endsWith("_test"), "Disposable *_test schema required before writes");
    }
    private static void execute(String sql, Object... args) throws SQLException {
        try (var c = connections.getConnection()) {
            safe(c);
            try (var s = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) { s.setObject(i + 1, args[i]); }
                s.executeUpdate();
            }
        }
    }
    private static int rateCount() throws SQLException {
        try (var c = connections.getConnection()) {
            safe(c);
            try (var s = c.prepareStatement("SELECT COUNT(*) FROM fee_rates WHERE ma_loai_thu IN (?, ?, ?)")) {
                s.setString(1, ACTIVE); s.setString(2, STOPPED); s.setString(3, GIFT);
                try (var rows = s.executeQuery()) { assertTrue(rows.next()); return rows.getInt(1); }
            }
        }
    }
    private static void removeFixture() throws SQLException {
        execute("DELETE FROM fee_rates WHERE ma_loai_thu IN (?, ?, ?)", ACTIVE, STOPPED, GIFT);
        execute("DELETE FROM fee_types WHERE ma_loai_thu IN (?, ?, ?)", ACTIVE, STOPPED, GIFT);
    }
    @BeforeEach void fixture() throws Exception {
        removeFixture();
        execute("INSERT INTO fee_types(ma_loai_thu, ten_loai_thu, loai_thu, phuong_thuc_tinh, trang_thai) VALUES (?, 'Service integration', 'BAT_BUOC', 'THEO_DIEN_TICH', 'DANG_AP_DUNG')", ACTIVE);
        execute("INSERT INTO fee_types(ma_loai_thu, ten_loai_thu, loai_thu, phuong_thuc_tinh, trang_thai) VALUES (?, 'Stopped integration', 'BAT_BUOC', 'THEO_CAN_HO', 'NGUNG_AP_DUNG')", STOPPED);
        execute("INSERT INTO fee_types(ma_loai_thu, ten_loai_thu, loai_thu, phuong_thuc_tinh, trang_thai) VALUES (?, 'Gift integration', 'DONG_GOP', 'TU_NGUYEN', 'DANG_AP_DUNG')", GIFT);
        var authorization = new RoleBasedAuthorizationService(null);
        service = FeeRateModule.createService(connections, authorization);
        lookup = FeeRateModule.createLookup(connections, authorization);
        FeeCategorySessionFixture.use(Role.ADMIN);
    }
    @AfterEach void cleanup() throws Exception {
        try { removeFixture(); } finally { FeeCategorySessionFixture.use(null); }
    }
    private FeeRateForm form(String code, String price, LocalDate date) { return new FeeRateForm(code, price, date, "Quyết định 😀"); }

    @Test void fourServiceMethodsAndAdapterUseRealDaoWithHistoricalPrices() throws Exception {
        var types = service.listFeeTypes().data().orElseThrow();
        assertTrue(types.stream().anyMatch(type -> type.maKhoanThu().equals(ACTIVE)));
        assertTrue(types.stream().anyMatch(type -> type.maKhoanThu().equals(STOPPED)));
        assertTrue(types.stream().noneMatch(type -> type.maKhoanThu().equals(GIFT)));
        assertTrue(service.listHistory(ACTIVE).data().orElseThrow().isEmpty());
        assertTrue(service.getEffectiveRate(ACTIVE, OLD_DATE).data().orElseThrow().isEmpty());
        assertTrue(lookup.getEffectiveRate(ACTIVE, OLD_DATE).isEmpty());

        var old = service.createRate(form(ACTIVE, "7000.50", OLD_DATE)).data().orElseThrow();
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);
        var next = service.createRate(form(ACTIVE, "8000.75", NEW_DATE)).data().orElseThrow();
        assertTrue(old.id() > 0 && next.id() > old.id());
        assertEquals(List.of(next, old), service.listHistory(ACTIVE).data().orElseThrow());
        var at = LocalDate.of(2026, 11, 1);
        assertEquals(old, service.getEffectiveRate(ACTIVE, at).data().orElseThrow().orElseThrow());
        assertEquals(next, service.getEffectiveRate(ACTIVE, NEW_DATE).data().orElseThrow().orElseThrow());
        var applied = lookup.getEffectiveRate(ACTIVE, at).orElseThrow();
        assertEquals(new AppliedRate(ACTIVE, new BigDecimal("7000.50"), OLD_DATE), applied);
        assertEquals("Quyết định 😀", old.ghiChu()); assertEquals(2, rateCount());
    }
    @Test void realDuplicateAndParentPrechecksMapToFieldErrorsWithoutExtraRows() throws Exception {
        assertTrue(service.createRate(form(ACTIVE, "7000", OLD_DATE)).isSuccess());
        var duplicate = service.createRate(form(ACTIVE, "9000", OLD_DATE));
        assertEquals(DUPLICATE, duplicate.status()); assertTrue(duplicate.fieldErrors().containsKey("ngay_ap_dung"));
        var missing = service.createRate(form("SVC_MISSING_IT", "1", OLD_DATE));
        assertEquals(NOT_FOUND, missing.status()); assertTrue(missing.fieldErrors().containsKey("ma_khoan_thu"));
        for (String code : List.of(STOPPED, GIFT)) {
            var ineligible = service.createRate(form(code, "1", OLD_DATE));
            assertEquals(VALIDATION_ERROR, ineligible.status()); assertTrue(ineligible.fieldErrors().containsKey("ma_khoan_thu"));
        }
        assertEquals(1, rateCount());
        assertEquals(new BigDecimal("7000.00"), lookup.getEffectiveRate(ACTIVE, NEW_DATE).orElseThrow().donGia());
    }
    @Test void validationAndSessionSecurityDoNotMutateRealDatabase() throws Exception {
        assertEquals(VALIDATION_ERROR, service.createRate(form(ACTIVE, "0", OLD_DATE)).status());
        assertEquals(VALIDATION_ERROR, service.createRate(form(ACTIVE, "1.001", OLD_DATE)).status());
        assertEquals(VALIDATION_ERROR, service.createRate(form(ACTIVE, "1", null)).status());
        for (Role role : new Role[]{Role.RESIDENT, null}) {
            FeeCategorySessionFixture.use(role);
            assertThrows(AccessDeniedException.class, service::listFeeTypes);
            assertThrows(AccessDeniedException.class, () -> service.listHistory(ACTIVE));
            assertThrows(AccessDeniedException.class, () -> service.getEffectiveRate(ACTIVE, OLD_DATE));
            assertThrows(AccessDeniedException.class, () -> service.createRate(form(ACTIVE, "1", OLD_DATE)));
            assertThrows(AccessDeniedException.class, () -> lookup.getEffectiveRate(ACTIVE, OLD_DATE));
        }
        assertEquals(0, rateCount());
        FeeCategorySessionFixture.use(Role.ACCOUNTANT);
        assertTrue(service.createRate(form(ACTIVE, "1", OLD_DATE)).isSuccess());
    }
    @Test void parentChangedAfterSelectionIsRejectedAndExistingHistoryIsPreserved() throws Exception {
        assertTrue(service.listFeeTypes().data().orElseThrow().stream().anyMatch(type -> type.maKhoanThu().equals(ACTIVE)));
        assertTrue(service.createRate(form(ACTIVE, "7000", OLD_DATE)).isSuccess());
        execute("UPDATE fee_types SET trang_thai='NGUNG_AP_DUNG' WHERE ma_loai_thu=?", ACTIVE);
        var result = service.createRate(form(ACTIVE, "8000", NEW_DATE));
        assertEquals(VALIDATION_ERROR, result.status()); assertTrue(result.fieldErrors().containsKey("ma_khoan_thu"));
        assertEquals(1, service.listHistory(ACTIVE).data().orElseThrow().size());
        assertEquals(new BigDecimal("7000.00"), lookup.getEffectiveRate(ACTIVE, NEW_DATE).orElseThrow().donGia());
        assertEquals(1, rateCount());
    }
}
