package com.bluemoon.dao;

import com.bluemoon.models.ApartmentStatus;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HouseholdQ17DAOTest {
    private final List<String> events = new ArrayList<>();
    private boolean household = true;
    private boolean occupied;
    private boolean failRead;
    private boolean failUpdate;
    private boolean failCommit;
    private boolean failRollback;
    private int connections;

    @Test void occupiedRollsBackWithoutAnyUpdate() {
        occupied = true;
        assertThrows(HouseholdHasResidentsException.class, () -> update(ApartmentStatus.CAN_HO_TRONG));
        assertTrue(events.contains("rollback"));
        assertFalse(events.contains("write"));
        assertFalse(events.contains("commit"));
        assertEquals(1, connections);
        assertEquals("close", events.get(events.size() - 1));
    }

    @Test void emptyHouseholdUsesCurrentReadAfterHouseholdLockOnSameConnectionThenCommits() throws Exception {
        assertTrue(update(ApartmentStatus.CAN_HO_TRONG));
        int lock = index("SELECT ma_ho FROM households");
        int read = index("SELECT ma_nhan_khau FROM residents");
        assertTrue(events.indexOf("begin") < lock && lock < read && read < events.indexOf("write"));
        assertTrue(events.indexOf("write") < events.indexOf("commit"));
        assertTrue(events.get(lock).endsWith("FOR UPDATE"));
        assertTrue(events.get(read).endsWith("FOR UPDATE"));
        assertEquals(1, connections);
        assertFalse(events.contains("rollback"));
    }

    @Test void otherStatusesRetainUpdateBehaviourWithoutResidentRead() throws Exception {
        occupied = true;
        for (var status : new ApartmentStatus[] {ApartmentStatus.DANG_O, ApartmentStatus.CHO_THUE}) {
            events.clear();
            assertTrue(update(status));
            assertEquals(-1, index("SELECT ma_nhan_khau FROM residents"));
            assertTrue(index("SELECT ma_ho FROM households") < events.indexOf("write"));
        }
    }

    @Test void missingHouseholdReturnsFalseAndRollsBack() throws Exception {
        household = false;
        assertFalse(update(ApartmentStatus.CAN_HO_TRONG));
        assertTrue(events.contains("rollback"));
        assertFalse(events.contains("write"));
    }

    @Test void residentReadFailureCannotBeTreatedAsZero() {
        failRead = true;
        assertThrows(SQLException.class, () -> update(ApartmentStatus.CAN_HO_TRONG));
        assertTrue(events.contains("rollback"));
        assertFalse(events.contains("write"));
    }

    @Test void writeAndCommitFailuresRollback() {
        failUpdate = true;
        assertThrows(SQLException.class, () -> update(ApartmentStatus.CAN_HO_TRONG));
        assertTrue(events.contains("rollback"));
        events.clear(); failUpdate = false; failCommit = true;
        assertThrows(SQLException.class, () -> update(ApartmentStatus.CAN_HO_TRONG));
        assertTrue(events.contains("rollback"));
    }

    @Test void rollbackFailureIsSuppressedOnDomainFailure() {
        occupied = true; failRollback = true;
        var failure = assertThrows(HouseholdHasResidentsException.class, () -> update(ApartmentStatus.CAN_HO_TRONG));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("close", events.get(events.size() - 1));
    }

    private int index(String prefix) {
        for (int i = 0; i < events.size(); i++) { if (events.get(i).startsWith(prefix)) { return i; } }
        return -1;
    }

    private boolean update(ApartmentStatus status) throws SQLException {
        return new HouseholdDAO(this::connection).update("HK-1701", 17, new BigDecimal("80.00"), LocalDate.of(2024, 1, 1), status);
    }

    private Connection connection() {
        connections++;
        return proxy(Connection.class, (method, args) -> {
            if (method.equals("setAutoCommit")) { assertEquals(false, args[0]); events.add("begin"); }
            else { events.add(method); }
            if (method.equals("commit") && failCommit) { throw new SQLException("commit failed"); }
            if (method.equals("rollback") && failRollback) { throw new SQLException("rollback failed"); }
            if (method.equals("prepareStatement")) {
                String sql = (String) args[0]; events.add(sql);
                return proxy(PreparedStatement.class, (call, parameters) -> {
                    if (call.equals("executeUpdate")) {
                        events.add("write");
                        if (failUpdate) { throw new SQLException("write failed"); }
                        return 1;
                    }
                    if (call.equals("executeQuery")) {
                        boolean resident = sql.contains("FROM residents");
                        if (resident && failRead) { throw new SQLException("read failed"); }
                        boolean[] first = {true};
                        return proxy(ResultSet.class, (read, values) -> {
                            if (!read.equals("next")) { return null; }
                            boolean next = first[0] && (resident ? occupied : household);
                            first[0] = false;
                            return next;
                        });
                    }
                    return null;
                });
            }
            return null;
        });
    }

    @FunctionalInterface private interface Call { Object run(String method, Object[] args) throws Throwable; }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (instance, method, args) -> call.run(method.getName(), args)));
    }
}
