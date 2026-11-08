package com.bluemoon.config;

import com.bluemoon.controllers.FeeRateModule;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.sql.*;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeeRateWiringTest {
    @Test void registrationIsLazyUsesViewPermissionAndRequiresFxOnlyWhenOpened() throws Exception {
        var config = DatabaseConfig.load(Path.of("missing-feerate-registration.properties"), Map.of(
                "BLUEMOON_DB_URL", "jdbc:mysql://127.0.0.1:1/unavailable_test",
                "BLUEMOON_DB_USERNAME", "test-only", "BLUEMOON_DB_PASSWORD", "test-only"));
        var registration = FeeRateModule.registration(new DatabaseConnection(config));
        assertEquals("feeRates", registration.id());
        assertEquals(Permission.FEE_RATE_VIEW, registration.permission());
        assertFalse(registration.title().isBlank());
        assertThrows(IllegalStateException.class, () -> registration.open().get());
        assertThrows(NullPointerException.class, () -> FeeRateModule.registration(null));
    }
    @Test void productionGraphReachesJdbcOnlyAfterSessionAuthorization() throws Exception {
        var attempts = new AtomicInteger();
        String url = "jdbc:mysql:us006-composition-test";
        Driver driver = new Driver() {
            public boolean acceptsURL(String candidate) { return url.equals(candidate); }
            public Connection connect(String candidate, Properties properties) throws SQLException {
                if (!acceptsURL(candidate)) { return null; }
                attempts.incrementAndGet(); throw new SQLException("private connection details");
            }
            public DriverPropertyInfo[] getPropertyInfo(String candidate, Properties properties) { return new DriverPropertyInfo[0]; }
            public int getMajorVersion() { return 1; }
            public int getMinorVersion() { return 0; }
            public boolean jdbcCompliant() { return false; }
            public Logger getParentLogger() { return Logger.getGlobal(); }
        };
        DriverManager.registerDriver(driver);
        try {
            var config = DatabaseConfig.load(Path.of("missing-us006.properties"), Map.of(
                    "BLUEMOON_DB_URL", url, "BLUEMOON_DB_USERNAME", "test-only", "BLUEMOON_DB_PASSWORD", "test-only"));
            var connections = new DatabaseConnection(config);
            var authorization = new RoleBasedAuthorizationService(null);
            var service = FeeRateModule.createService(connections, authorization);
            var adapter = FeeRateModule.createLookup(connections, authorization);
            assertEquals(0, attempts.get(), "composition must not connect");
            var date = LocalDate.of(2026, 11, 1);
            var form = new FeeRateForm("PHI", "7000", date, null);
            for (Role role : new Role[]{null, Role.RESIDENT}) {
                FeeCategorySessionFixture.use(role);
                assertThrows(AccessDeniedException.class, service::listFeeTypes);
                assertThrows(AccessDeniedException.class, () -> service.listHistory("PHI"));
                assertThrows(AccessDeniedException.class, () -> service.getEffectiveRate("PHI", date));
                assertThrows(AccessDeniedException.class, () -> service.createRate(form));
                assertThrows(AccessDeniedException.class, () -> adapter.getEffectiveRate("PHI", date));
            }
            assertEquals(0, attempts.get(), "denied operations must not reach JDBC");
            for (Role role : List.of(Role.ADMIN, Role.ACCOUNTANT)) {
                FeeCategorySessionFixture.use(role);
                for (var result : List.of(service.listFeeTypes(), service.listHistory("PHI"), service.getEffectiveRate("PHI", date), service.createRate(form))) {
                    assertEquals(FeeRateResult.Status.SYSTEM_ERROR, result.status());
                    assertFalse(result.message().contains("private"));
                }
                assertThrows(IllegalStateException.class, () -> adapter.getEffectiveRate("PHI", date));
            }
            assertEquals(10, attempts.get(), "all operations use the production JDBC composition");
        } finally { FeeCategorySessionFixture.use(null); DriverManager.deregisterDriver(driver); }
    }
}
