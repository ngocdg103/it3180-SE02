package com.bluemoon.dao;

import com.bluemoon.models.FeeRate;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeeRateDAOTest {
    private final List<String> events = new ArrayList<>();
    private final List<String> sql = new ArrayList<>();
    private final Map<Integer,Object> binds = new HashMap<>();
    private SQLException failure;
    private boolean rollbackFailure, commitFailure, missingKey, missingParent, inactive, voluntary, empty, invalidRow;
    private int affected = 1;
    private long key = 9;
    private final LocalDate day = LocalDate.of(2026,11,1);
    private FeeRate rate() { return new FeeRate(null,"PHI",new BigDecimal("7000.50"),day,null); }
    @FunctionalInterface interface Call { Object run(String method, Object[] args) throws Throwable; }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},
                (p,m,a) -> call.run(m.getName(),a)));
    }
    private ResultSet rows(String kind) {
        int[] index = {0};
        return proxy(ResultSet.class,(m,a) -> {
            if (m.equals("close")) { events.add(kind+".close"); return null; }
            if (m.equals("next")) { return ++index[0] == 1 && !(kind.equals("parent") && missingParent)
                    && !(kind.equals("keys") && missingKey) && !(kind.equals("read") && empty); }
            if (m.equals("wasNull")) { return false; }
            if (m.equals("getLong")) { return key; }
            if (m.equals("getBigDecimal")) { return invalidRow ? BigDecimal.ZERO : rate().donGia(); }
            if (m.equals("getDate")) { return java.sql.Date.valueOf(day); }
            if (m.equals("getString")) {
                return switch ((String)a[0]) {
                    case "loai_thu" -> voluntary ? "DONG_GOP" : "BAT_BUOC";
                    case "trang_thai" -> inactive ? "NGUNG_AP_DUNG" : "DANG_AP_DUNG";
                    case "ma_loai_thu" -> "PHI";
                    default -> null;
                };
            }
            return null;
        });
    }
    private Connection connection() {
        events.add("open");
        return proxy(Connection.class,(m,a) -> {
            events.add("connection."+m);
            if (m.equals("commit") && commitFailure) { throw new SQLException("commit uncertain"); }
            if (m.equals("rollback") && rollbackFailure) { throw new SQLException("rollback failed"); }
            if (m.equals("prepareStatement")) {
                String query = (String)a[0]; sql.add(query);
                if (query.startsWith("INSERT")) { assertEquals(Statement.RETURN_GENERATED_KEYS,a[1]); }
                return proxy(PreparedStatement.class,(s,b) -> {
                    events.add("statement."+s);
                    if (s.startsWith("set")) { binds.put((Integer)b[0],b[1]); }
                    if (s.equals("executeQuery")) { return rows(query.contains("FOR UPDATE") ? "parent" : "read"); }
                    if (s.equals("executeUpdate")) { if (failure != null) { throw failure; } return affected; }
                    if (s.equals("getGeneratedKeys")) { return rows("keys"); }
                    return null;
                });
            }
            return null;
        });
    }
    private FeeRateDAO dao() { return new FeeRateDAO(this::connection); }
    @Test void insertLocksParentBindsExactlyAndClosesBeforeCommit() throws Exception {
        var saved = dao().insert(rate());
        assertEquals(9L,saved.id()); assertEquals(rate().donGia(),saved.donGia());
        assertTrue(sql.get(0).endsWith("FOR UPDATE")); assertTrue(sql.get(1).startsWith("INSERT"));
        assertEquals("PHI",binds.get(1)); assertEquals(rate().donGia(),binds.get(2));
        assertEquals(java.sql.Date.valueOf(day),binds.get(3)); assertEquals(Types.VARCHAR,binds.get(4));
        assertTrue(events.indexOf("keys.close") < events.indexOf("connection.commit"));
        assertEquals(2,Collections.frequency(events,"statement.close"));
        assertTrue(events.contains("parent.close")); assertFalse(events.contains("connection.rollback"));
        assertEquals("connection.close",events.get(events.size()-1));
    }
    @Test void vendorFailuresRemainIdenticalAndRollback() {
        for (int code : new int[]{1062,1452,3819,1213,1205}) {
            events.clear(); failure = new SQLException("failure","23000",code);
            var actual = assertThrows(SQLException.class, () -> dao().insert(rate()));
            assertSame(failure,actual); assertEquals(code,actual.getErrorCode()); assertEquals("23000",actual.getSQLState());
            assertTrue(events.contains("connection.rollback")); assertFalse(events.contains("connection.commit"));
            assertEquals("connection.close",events.get(events.size()-1));
        }
    }
    @Test void rejectsIneligibleOrMissingParentBeforeInsert() {
        missingParent=true;
        assertEquals("02000",assertThrows(SQLException.class, () -> dao().insert(rate())).getSQLState());
        missingParent=false; inactive=true;
        assertEquals("45000",assertThrows(SQLException.class, () -> dao().insert(rate())).getSQLState());
        inactive=false; voluntary=true;
        assertEquals("45000",assertThrows(SQLException.class, () -> dao().insert(rate())).getSQLState());
        assertFalse(sql.stream().anyMatch(q -> q.startsWith("INSERT")));
    }
    @Test void generatedKeyAndAffectedRowFailuresRollback() {
        missingKey=true; assertThrows(SQLException.class, () -> dao().insert(rate()));
        missingKey=false; key=0; assertThrows(SQLException.class, () -> dao().insert(rate()));
        key=1; affected=0; assertThrows(SQLException.class, () -> dao().insert(rate()));
        assertEquals(3,Collections.frequency(events,"connection.rollback"));
    }
    @Test void commitFailureDoesNotRetryAndRollbackFailureIsSuppressed() {
        commitFailure=true; rollbackFailure=true;
        var e=assertThrows(SQLException.class, () -> dao().insert(rate()));
        assertEquals(1,e.getSuppressed().length); assertEquals(1,Collections.frequency(events,"open"));
        assertEquals("connection.close",events.get(events.size()-1));
    }
    @Test void historyAndEffectiveReadMappedRowsWithPreparedParameters() throws Exception {
        var history=dao().findHistory("PHI");
        assertEquals(1,history.size()); assertEquals(9L,history.get(0).id());
        assertThrows(UnsupportedOperationException.class, () -> history.clear());
        assertTrue(sql.get(0).endsWith("ORDER BY ngay_ap_dung DESC, id DESC"));
        assertEquals(history.get(0),dao().findEffectiveRate("PHI",day).orElseThrow());
        assertEquals(java.sql.Date.valueOf(day),binds.get(2));
        assertTrue(sql.get(1).contains("ngay_ap_dung <= ? ORDER BY ngay_ap_dung DESC LIMIT 1"));
        assertEquals(2,Collections.frequency(events,"read.close"));
        assertFalse(events.contains("connection.commit"));
        empty=true; assertTrue(dao().findHistory("NONE").isEmpty());
        assertTrue(dao().findEffectiveRate("NONE",day).isEmpty());
    }
    @Test void invalidStoredRowIsSqlFailureAndCloses() {
        invalidRow=true; assertThrows(SQLException.class, () -> dao().findHistory("PHI"));
        assertEquals("connection.close",events.get(events.size()-1));
    }
    @Test void invalidInputsNeverOpenConnectionAndConnectionFailurePropagates() {
        assertThrows(IllegalArgumentException.class, () -> dao().findHistory("' OR 1=1 --"));
        assertThrows(NullPointerException.class, () -> dao().findEffectiveRate("PHI",null));
        assertThrows(NullPointerException.class, () -> dao().insert(null));
        assertThrows(IllegalArgumentException.class, () -> dao().insert(new FeeRate(1L,"PHI",BigDecimal.ONE,day,null)));
        assertTrue(events.isEmpty());
        var e=new SQLException("offline"); var offline=new FeeRateDAO(() -> { throw e; });
        assertSame(e,assertThrows(SQLException.class, () -> offline.insert(rate())));
        assertSame(e,assertThrows(SQLException.class, () -> offline.findHistory("PHI")));
        assertSame(e,assertThrows(SQLException.class, () -> offline.findEffectiveRate("PHI",day)));
    }
}
