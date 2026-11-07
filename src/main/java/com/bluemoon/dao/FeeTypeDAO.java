package com.bluemoon.dao;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.*;
import java.sql.*;
import java.util.*;

/** Prepared JDBC operations. Mutations and their returned snapshot share one transaction. */
public final class FeeTypeDAO implements FeeTypeRepository {
    @FunctionalInterface interface Connections { Connection open() throws SQLException; }
    private static final String SELECT = "SELECT ma_loai_thu, ten_loai_thu, loai_thu, phuong_thuc_tinh, so_tien_mac_dinh, trang_thai FROM fee_types";
    private final Connections connections;

    public FeeTypeDAO(DatabaseConnection connections) { this(Objects.requireNonNull(connections)::getConnection); }
    FeeTypeDAO(Connections connections) { this.connections = Objects.requireNonNull(connections); }

    @Override public List<FeeType> findAll(FeeStatus filter) throws SQLException {
        String sql = SELECT + (filter == null ? "" : " WHERE trang_thai = ?") + " ORDER BY ma_loai_thu";
        try (var c = connections.open(); var s = c.prepareStatement(sql)) {
            if (filter != null) { s.setString(1, filter.name()); }
            try (var rows = s.executeQuery()) {
                var result = new ArrayList<FeeType>();
                while (rows.next()) { result.add(map(rows)); }
                return List.copyOf(result);
            }
        }
    }

    @Override public Optional<FeeType> findByCode(String code) throws SQLException {
        try (var c = connections.open()) { return find(c, code); }
    }

    @Override public void insert(FeeType fee) throws SQLException {
        transaction(c -> {
            try (var s = c.prepareStatement("INSERT INTO fee_types (ten_loai_thu, loai_thu, phuong_thuc_tinh, so_tien_mac_dinh, trang_thai, ma_loai_thu) VALUES (?, ?, ?, ?, ?, ?)")) {
                bind(s, fee);
                s.executeUpdate();
            }
            return Optional.empty();
        });
    }

    @Override public Optional<FeeType> update(FeeType fee) throws SQLException {
        return transaction(c -> {
            try (var s = c.prepareStatement("UPDATE fee_types SET ten_loai_thu = ?, loai_thu = ?, phuong_thuc_tinh = ?, so_tien_mac_dinh = ?, trang_thai = ? WHERE ma_loai_thu = ?")) {
                bind(s, fee);
                s.executeUpdate();
            }
            return find(c, fee.maKhoanThu());
        });
    }

    @Override public Optional<FeeType> deactivate(String code) throws SQLException {
        return transaction(c -> {
            try (var s = c.prepareStatement("UPDATE fee_types SET trang_thai = ? WHERE ma_loai_thu = ?")) {
                s.setString(1, FeeStatus.NGUNG_AP_DUNG.name());
                s.setString(2, code);
                s.executeUpdate();
            }
            return find(c, code);
        });
    }

    private static void bind(PreparedStatement s, FeeType fee) throws SQLException {
        s.setString(1, fee.tenKhoanThu());
        s.setString(2, fee.loai().name());
        s.setString(3, fee.hinhThuc().name());
        s.setBigDecimal(4, fee.soTienMacDinh());
        s.setString(5, fee.trangThai().name());
        s.setString(6, fee.maKhoanThu());
    }

    private static Optional<FeeType> find(Connection c, String code) throws SQLException {
        try (var s = c.prepareStatement(SELECT + " WHERE ma_loai_thu = ?")) {
            s.setString(1, code);
            try (var rows = s.executeQuery()) { return rows.next() ? Optional.of(map(rows)) : Optional.empty(); }
        }
    }

    private static FeeType map(ResultSet rows) throws SQLException {
        try {
            return new FeeType(rows.getString("ma_loai_thu"), rows.getString("ten_loai_thu"),
                    FeeKind.valueOf(rows.getString("loai_thu")), ChargeMethod.valueOf(rows.getString("phuong_thuc_tinh")),
                    rows.getBigDecimal("so_tien_mac_dinh"), FeeStatus.valueOf(rows.getString("trang_thai")));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new SQLException("Invalid fee category stored value", invalid);
        }
    }

    @FunctionalInterface private interface Mutation { Optional<FeeType> run(Connection c) throws SQLException; }
    private Optional<FeeType> transaction(Mutation mutation) throws SQLException {
        try (var c = connections.open()) {
            c.setAutoCommit(false);
            try {
                var result = mutation.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }
}
