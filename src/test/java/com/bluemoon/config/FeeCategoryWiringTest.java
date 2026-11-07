package com.bluemoon.config;

import com.bluemoon.controllers.FeeCategoryModule;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeeCategoryWiringTest {
    @Test void productionGraphUsesJdbcAndRealSessionAuthorizationForAllFiveMethods() throws Exception {
        var attempts = new AtomicInteger();
        String url = "jdbc:mysql:us005-composition-test";
        Driver driver = new Driver() {
            public boolean acceptsURL(String u) { return url.equals(u); }
            public Connection connect(String u, Properties p) throws SQLException {
                if (!acceptsURL(u)) { return null; }
                attempts.incrementAndGet();
                throw new SQLException("test database unavailable");
            }
            public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) { return new DriverPropertyInfo[0]; }
            public int getMajorVersion() { return 1; }
            public int getMinorVersion() { return 0; }
            public boolean jdbcCompliant() { return false; }
            public Logger getParentLogger() { return Logger.getGlobal(); }
        };
        DriverManager.registerDriver(driver);
        try {
            var config = DatabaseConfig.load(java.nio.file.Path.of("absent-us005-test.properties"),
                    Map.of("BLUEMOON_DB_URL", url, "BLUEMOON_DB_USERNAME", "test", "BLUEMOON_DB_PASSWORD", "test"));
            var service = FeeCategoryModule.createService(new DatabaseConnection(config));
            assertInstanceOf(FeeTypeServiceImpl.class, service);
            assertEquals(0, attempts.get(), "composition must not connect");
            var form = new FeeTypeForm("PHI", "Phí thu", FeeKind.BAT_BUOC, ChargeMethod.THEO_CAN_HO, "0", FeeStatus.DANG_AP_DUNG);
            for (Role role : new Role[]{null, Role.RESIDENT}) {
                FeeCategorySessionFixture.use(role);
                assertThrows(AccessDeniedException.class, () -> service.listFeeTypes(null));
                assertThrows(AccessDeniedException.class, () -> service.getFeeType("PHI"));
                assertThrows(AccessDeniedException.class, () -> service.createFeeType(form));
                assertThrows(AccessDeniedException.class, () -> service.updateFeeType("PHI", form));
                assertThrows(AccessDeniedException.class, () -> service.deactivateFeeType("PHI"));
            }
            assertEquals(0, attempts.get(), "denied requests never reach JDBC");
            for (Role role : List.of(Role.ADMIN, Role.ACCOUNTANT)) {
                FeeCategorySessionFixture.use(role);
                for (var result : List.of(service.listFeeTypes(null), service.getFeeType("PHI"), service.createFeeType(form),
                        service.updateFeeType("PHI", form), service.deactivateFeeType("PHI"))) {
                    assertEquals(FeeTypeResult.Status.SYSTEM_ERROR, result.status());
                    assertFalse(result.message().contains("test database"));
                }
            }
            assertEquals(10, attempts.get(), "all five operations must use real JDBC DAO");
        } finally {
            FeeCategorySessionFixture.use(null);
            DriverManager.deregisterDriver(driver);
        }
    }
}
