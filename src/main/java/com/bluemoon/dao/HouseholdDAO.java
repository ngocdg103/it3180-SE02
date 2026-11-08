package com.bluemoon.dao;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.Apartment;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.Household;
import com.bluemoon.models.HouseholdProfile;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** JDBC implementation on Dev-1's DatabaseConnection. Prepared statements only. */
public final class HouseholdDAO implements HouseholdRepository {
    private static final int DUPLICATE_ENTRY = 1062;
    private static final String SELECT = "SELECT a.so_can_ho, a.tang, a.dien_tich, a.trang_thai, "
            + "h.ma_ho, h.ngay_nhan_nha FROM apartments a JOIN households h ON h.so_can_ho = a.so_can_ho ";

    @FunctionalInterface interface Connections { Connection open() throws SQLException; }
    private final Connections connections;

    public HouseholdDAO(DatabaseConnection connections) { this(Objects.requireNonNull(connections)::getConnection); }
    HouseholdDAO(Connections connections) { this.connections = Objects.requireNonNull(connections); }

    @Override
    public void insert(HouseholdProfile profile) throws SQLException, DuplicateRecordException {
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement(
                        "INSERT INTO apartments (so_can_ho, tang, dien_tich, trang_thai) VALUES (?, ?, ?, ?)")) {
                    statement.setString(1, profile.soCanHo());
                    statement.setInt(2, profile.tang());
                    statement.setBigDecimal(3, profile.dienTich());
                    statement.setString(4, profile.trangThai().name());
                    statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement(
                        "INSERT INTO households (ma_ho, so_can_ho, ngay_nhan_nha) VALUES (?, ?, ?)")) {
                    statement.setString(1, profile.maHo());
                    statement.setString(2, profile.soCanHo());
                    statement.setObject(3, profile.ngayNhanNha());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof SQLException sql && sql.getErrorCode() == DUPLICATE_ENTRY) {
                    throw new DuplicateRecordException(duplicateField(sql), sql);
                }
                throw failure;
            }
        }
    }

    @Override
    public Optional<HouseholdProfile> findByMaHo(String maHo) throws SQLException {
        return findOne("WHERE h.ma_ho = ?", maHo);
    }

    @Override
    public Optional<HouseholdProfile> findBySoCanHo(String soCanHo) throws SQLException {
        return findOne("WHERE a.so_can_ho = ?", soCanHo);
    }

    @Override
    public List<HouseholdProfile> findAll(ApartmentStatus status) throws SQLException {
        var sql = SELECT + (status == null ? "" : "WHERE a.trang_thai = ? ") + "ORDER BY a.so_can_ho";
        try (var connection = connections.open(); var statement = connection.prepareStatement(sql)) {
            if (status != null) { statement.setString(1, status.name()); }
            try (var rows = statement.executeQuery()) {
                var result = new ArrayList<HouseholdProfile>();
                while (rows.next()) { result.add(map(rows)); }
                return result;
            }
        }
    }

    @Override
    public boolean update(String maHo, int tang, BigDecimal dienTich, LocalDate ngayNhanNha,
                          ApartmentStatus trangThai) throws SQLException {
        // Shared serialization point with ResidentDAO.insert: household first, never apartment first.
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                try (var lock = connection.prepareStatement("SELECT ma_ho FROM households WHERE ma_ho = ? FOR UPDATE")) {
                    lock.setString(1, maHo);
                    try (var rows = lock.executeQuery()) {
                        if (!rows.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                if (trangThai == ApartmentStatus.CAN_HO_TRONG && ResidentDAO.hasAnyForUpdate(connection, maHo)) {
                    throw new HouseholdHasResidentsException();
                }
                boolean updated;
                try (var statement = connection.prepareStatement(
                        "UPDATE apartments a JOIN households h ON h.so_can_ho = a.so_can_ho "
                                + "SET a.tang = ?, a.dien_tich = ?, a.trang_thai = ?, h.ngay_nhan_nha = ? "
                                + "WHERE h.ma_ho = ?")) {
                    statement.setInt(1, tang);
                    statement.setBigDecimal(2, dienTich);
                    statement.setString(3, trangThai.name());
                    statement.setObject(4, ngayNhanNha);
                    statement.setString(5, maHo);
                    updated = statement.executeUpdate() > 0;
                }
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    private Optional<HouseholdProfile> findOne(String where, String key) throws SQLException {
        try (var connection = connections.open();
             var statement = connection.prepareStatement(SELECT + where)) {
            statement.setString(1, key);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    private static HouseholdProfile map(ResultSet rows) throws SQLException {
        var soCanHo = rows.getString("so_can_ho");
        return new HouseholdProfile(
                new Apartment(soCanHo, rows.getInt("tang"), rows.getBigDecimal("dien_tich"),
                        ApartmentStatus.valueOf(rows.getString("trang_thai"))),
                new Household(rows.getString("ma_ho"), soCanHo, rows.getObject("ngay_nhan_nha", LocalDate.class)));
    }

    /** MySQL names the key in its message: households.PRIMARY = ma_ho, anything else = so_can_ho. */
    static DuplicateRecordException.Field duplicateField(SQLException failure) {
        var message = String.valueOf(failure.getMessage());
        return message.contains("households.PRIMARY") ? DuplicateRecordException.Field.MA_HO
                : DuplicateRecordException.Field.SO_CAN_HO;
    }

    private static void rollback(Connection connection, Exception failure) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
    }
}
