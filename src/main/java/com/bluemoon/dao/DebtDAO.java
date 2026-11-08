package com.bluemoon.dao;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Read-only report on committed invoice balances and receipts. No report totals table/cache is stored. */
public final class DebtDAO implements DebtRepository {
    private final DatabaseConnection connections;
    public DebtDAO(DatabaseConnection connections) { this.connections=Objects.requireNonNull(connections); }
    @FunctionalInterface private interface Read<T> { T run(Connection c) throws SQLException; }
    private <T> T read(Read<T> operation) throws SQLException {
        try(var c=connections.getConnection()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setReadOnly(true); c.setAutoCommit(false);
            try { T result=operation.run(c); c.rollback(); return result; }
            catch(SQLException|RuntimeException failure) { try { c.rollback(); } catch(SQLException rollback) { failure.addSuppressed(rollback); } throw failure; }
        }
    }
    @Override public Optional<PeriodData> loadPeriod(String requested) throws SQLException {
        return read(c -> {
            String period;
            String query=requested==null?"SELECT ky_thu FROM billing_periods ORDER BY ky_thu DESC LIMIT 1":"SELECT ky_thu FROM billing_periods WHERE ky_thu=?";
            try(var s=c.prepareStatement(query)) {
                if(requested!=null) { s.setString(1,requested); }
                try(var r=s.executeQuery()) { if(!r.next()) { return Optional.empty(); } period=r.getString(1); }
            }
            var rows=new ArrayList<DebtRow>();
            try(var s=c.prepareStatement("SELECT i.ma_hoa_don,i.ma_ho,i.so_can_ho,a.tang,i.dien_tich,i.so_nhan_khau,i.tong_tien,i.da_thanh_toan,i.con_thieu,i.trang_thai,"
                    +"(SELECT r.ho_ten FROM residents r WHERE r.ma_ho=i.ma_ho AND r.quan_he_chu_ho='CHU_HO' LIMIT 1) AS chu_ho,"
                    +"(SELECT MAX(p.ngay_nop) FROM phieu_thu p WHERE p.ma_hoa_don=i.ma_hoa_don) AS lan_nop_cuoi "
                    +"FROM invoices i JOIN apartments a ON a.so_can_ho=i.so_can_ho WHERE i.ky_thu=? ORDER BY a.tang,i.so_can_ho")) {
                s.setString(1,period);
                try(var r=s.executeQuery()) { while(r.next()) { rows.add(new DebtRow(r.getString("ma_hoa_don"),r.getString("ma_ho"),r.getString("so_can_ho"),r.getInt("tang"),r.getString("chu_ho"),
                        r.getBigDecimal("dien_tich"),r.getInt("so_nhan_khau"),r.getBigDecimal("tong_tien"),r.getBigDecimal("da_thanh_toan"),r.getBigDecimal("con_thieu"),
                        ChargeStatus.valueOf(r.getString("trang_thai")),r.getObject("lan_nop_cuoi",LocalDate.class))); } }
            }
            return Optional.of(new PeriodData(period,rows));
        });
    }
    @Override public Optional<HouseholdDebt> getHouseholdBalance(String code) throws SQLException {
        return read(c -> {
            try(var s=c.prepareStatement("SELECT COUNT(*) AS n,COALESCE(SUM(i.tong_tien),0) AS billed,COALESCE(SUM(i.da_thanh_toan),0) AS paid,"
                    +"COALESCE(SUM(GREATEST(i.con_thieu,0)),0) AS debt,"
                    +"COALESCE((SELECT ac.so_du FROM apartment_credit ac JOIN households h ON h.so_can_ho=ac.so_can_ho WHERE h.ma_ho=?),0) AS credit "
                    +"FROM invoices i WHERE i.ma_ho=?")) {
                s.setString(1,code); s.setString(2,code);
                try(var r=s.executeQuery()) { r.next(); return r.getInt("n")==0?Optional.empty():Optional.of(new HouseholdDebt(code,r.getBigDecimal("billed"),r.getBigDecimal("paid"),r.getBigDecimal("debt"),r.getBigDecimal("credit"))); }
            }
        });
    }
}
