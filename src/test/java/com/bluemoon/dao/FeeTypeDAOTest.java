package com.bluemoon.dao;

import com.bluemoon.models.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeeTypeDAOTest {
    private final List<String> events = new ArrayList<>();
    private SQLException executeFailure;
    private boolean commitFailure;
    private boolean rollbackFailure;
    private final FeeType fee = new FeeType("PHI", "Phí thu", FeeKind.BAT_BUOC,
            ChargeMethod.THEO_CAN_HO, new BigDecimal("1.25"), FeeStatus.DANG_AP_DUNG);

    private Connection connection() {
        return proxy(Connection.class, (method, args) -> {
            events.add(method);
            if (method.equals("prepareStatement")) {
                String sql = (String) args[0]; events.add(sql);
                return proxy(PreparedStatement.class, (m, a) -> {
                    events.add(m);
                    if (m.equals("executeUpdate")) { if (executeFailure != null) { throw executeFailure; } return 1; }
                    if (m.equals("executeQuery")) { return proxy(ResultSet.class, (r, v) -> r.equals("next") ? false : null); }
                    return null;
                });
            }
            if (method.equals("commit") && commitFailure) { throw new SQLException("commit failed"); }
            if (method.equals("rollback") && rollbackFailure) { throw new SQLException("rollback failed"); }
            return null;
        });
    }
    @FunctionalInterface interface Call { Object run(String method, Object[] args) throws Throwable; }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, m, args) -> call.run(m.getName(), args)));
    }

    @Test void insertCommitsPreparedStatementAndClosesResources() throws Exception {
        new FeeTypeDAO(this::connection).insert(fee);
        assertTrue(events.contains("setBigDecimal"));
        assertTrue(events.indexOf("executeUpdate") < events.indexOf("commit"));
        assertEquals("close", events.get(events.size() - 1));
        assertFalse(events.contains("rollback"));
    }
    @Test void duplicatePreservesMysqlCodeAndRollsBack() {
        executeFailure = new SQLException("duplicate", "23000", 1062);
        assertSame(executeFailure, assertThrows(SQLException.class, () -> new FeeTypeDAO(this::connection).insert(fee)));
        assertTrue(events.contains("rollback"));
        assertFalse(events.contains("commit"));
    }
    @Test void rollbackFailureIsSuppressedAndConnectionClosed() {
        executeFailure = new SQLException("unavailable"); rollbackFailure = true;
        var error = assertThrows(SQLException.class, () -> new FeeTypeDAO(this::connection).update(fee));
        assertEquals(1, error.getSuppressed().length);
        assertEquals("close", events.get(events.size() - 1));
    }
    @Test void commitFailureRollsBack() {
        commitFailure = true;
        assertThrows(SQLException.class, () -> new FeeTypeDAO(this::connection).deactivate("PHI"));
        assertTrue(events.contains("rollback"));
    }
    @Test void unavailableConnectionPropagates() {
        var dao = new FeeTypeDAO(() -> { throw new SQLException("offline"); });
        assertThrows(SQLException.class, () -> dao.findAll(null));
        assertThrows(SQLException.class, () -> dao.findByCode("PHI"));
        assertThrows(SQLException.class, () -> dao.insert(fee));
        assertThrows(SQLException.class, () -> dao.update(fee));
        assertThrows(SQLException.class, () -> dao.deactivate("PHI"));
    }
    @Test void deactivateOnlyUpdatesStatusAndReadsBeforeCommit() throws Exception {
        new FeeTypeDAO(this::connection).deactivate("PHI");
        assertTrue(events.contains("UPDATE fee_types SET trang_thai = ? WHERE ma_loai_thu = ?"));
        assertTrue(events.indexOf("executeQuery") < events.indexOf("commit"));
        assertFalse(events.stream().anyMatch(s -> s.contains("DELETE")));
    }
}
