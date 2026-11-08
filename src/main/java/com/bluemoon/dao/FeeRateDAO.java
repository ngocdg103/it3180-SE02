package com.bluemoon.dao;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.FeeRate;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Parent-first row locking serializes eligibility checks with FeeTypeDAO mutations. */
public final class FeeRateDAO implements FeeRateRepository {
    @FunctionalInterface interface Connections { Connection open() throws SQLException; }
    private static final String SELECT = "SELECT id, ma_loai_thu, don_gia, ngay_ap_dung, ghi_chu FROM fee_rates";
    private final Connections connections;

    public FeeRateDAO(DatabaseConnection connections) { this(Objects.requireNonNull(connections)::getConnection); }
    FeeRateDAO(Connections connections) { this.connections = Objects.requireNonNull(connections); }

    @Override public FeeRate insert(FeeRate rate) throws SQLException {
        Objects.requireNonNull(rate, "newRate");
        if (rate.id() != null) { throw new IllegalArgumentException("New rate must have null id"); }
        try (var c = connections.open()) {
            c.setAutoCommit(false);
            try {
                // US-005 update/deactivate acquire this same PK row lock first.
                try (var s = c.prepareStatement("SELECT loai_thu, trang_thai FROM fee_types WHERE ma_loai_thu = ? FOR UPDATE")) {
                    s.setString(1, rate.maLoaiThu());
                    try (var rows = s.executeQuery()) {
                        if (!rows.next()) { throw new SQLException("Fee type not found", "02000"); }
                        if (!"BAT_BUOC".equals(rows.getString("loai_thu"))
                                || !"DANG_AP_DUNG".equals(rows.getString("trang_thai"))) {
                            throw new SQLException("Fee type is not eligible for a unit price", "45000");
                        }
                    }
                }
                FeeRate stored;
                try (var s = c.prepareStatement("INSERT INTO fee_rates (ma_loai_thu, don_gia, ngay_ap_dung, ghi_chu) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1, rate.maLoaiThu());
                    s.setBigDecimal(2, rate.donGia());
                    s.setDate(3, java.sql.Date.valueOf(rate.ngayApDung()));
                    if (rate.ghiChu() == null) { s.setNull(4, Types.VARCHAR); }
                    else { s.setString(4, rate.ghiChu()); }
                    if (s.executeUpdate() != 1) { throw new SQLException("Expected one inserted rate"); }
                    try (var keys = s.getGeneratedKeys()) {
                        if (!keys.next()) { throw new SQLException("Missing generated rate id"); }
                        long id = keys.getLong(1);
                        if (keys.wasNull() || id <= 0) { throw new SQLException("Invalid generated rate id"); }
                        stored = new FeeRate(id, rate.maLoaiThu(), rate.donGia(), rate.ngayApDung(), rate.ghiChu());
                    }
                }
                c.commit();
                return stored;
            } catch (SQLException | RuntimeException failure) {
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    @Override public List<FeeRate> findHistory(String code) throws SQLException {
        FeeRate.requireCode(code);
        try (var c = connections.open(); var s = c.prepareStatement(SELECT + " WHERE ma_loai_thu = ? ORDER BY ngay_ap_dung DESC, id DESC")) {
            s.setString(1, code);
            try (var rows = s.executeQuery()) {
                var history = new ArrayList<FeeRate>();
                while (rows.next()) { history.add(map(rows)); }
                return List.copyOf(history);
            }
        }
    }

    @Override public Optional<FeeRate> findEffectiveRate(String code, LocalDate date) throws SQLException {
        FeeRate.requireCode(code); FeeRate.requireDate(date);
        try (var c = connections.open(); var s = c.prepareStatement(SELECT + " WHERE ma_loai_thu = ? AND ngay_ap_dung <= ? ORDER BY ngay_ap_dung DESC LIMIT 1")) {
            s.setString(1, code);
            s.setDate(2, java.sql.Date.valueOf(date));
            try (var rows = s.executeQuery()) { return rows.next() ? Optional.of(map(rows)) : Optional.empty(); }
        }
    }

    private static FeeRate map(ResultSet rows) throws SQLException {
        try {
            long id = rows.getLong("id");
            if (rows.wasNull()) { throw new SQLException("Null stored rate id"); }
            return new FeeRate(id, rows.getString("ma_loai_thu"), rows.getBigDecimal("don_gia"),
                    rows.getDate("ngay_ap_dung").toLocalDate(), rows.getString("ghi_chu"));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new SQLException("Invalid stored fee rate", invalid);
        }
    }
}
