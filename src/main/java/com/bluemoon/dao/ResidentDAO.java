package com.bluemoon.dao;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.Gender;
import com.bluemoon.models.RelationToHead;
import com.bluemoon.models.Resident;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation on Dev-1's DatabaseConnection. Prepared statements only.
 * Lock order (docs/design/us-002-contract-review.md B.4a): households row FOR UPDATE, then residents.
 * This class never locks apartments; the apartment status is a plain read after the household lock is held.
 */
public final class ResidentDAO implements ResidentRepository {
    static final int DUPLICATE_ENTRY = 1062;
    static final int NO_REFERENCED_ROW = 1452;
    static final int DEADLOCK = 1213;
    private static final String COLUMNS = "ma_nhan_khau, ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, "
            + "quan_he_chu_ho, ngay_chuyen_den";

    private final DatabaseConnection connections;

    public ResidentDAO(DatabaseConnection connections) { this.connections = Objects.requireNonNull(connections); }

    @Override
    public InsertOutcome insert(Resident resident, HeadPolicy policy, Long expectedHeadId)
            throws SQLException, ResidentDuplicateException {
        Objects.requireNonNull(resident);
        Objects.requireNonNull(policy);
        try {
            return insertOnce(resident, policy, expectedHeadId);
        } catch (SQLException failure) {
            // A deadlock victim is rolled back completely and every check re-runs under the locks: retry once.
            if (failure.getErrorCode() != DEADLOCK) { throw failure; }
            return insertOnce(resident, policy, expectedHeadId);
        }
    }

    private InsertOutcome insertOnce(Resident resident, HeadPolicy policy, Long expectedHeadId)
            throws SQLException, ResidentDuplicateException {
        try (var connection = connections.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var outcome = insertLocked(connection, resident, policy, expectedHeadId);
                if (outcome instanceof Added) { connection.commit(); } else { connection.rollback(); }
                return outcome;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof SQLException sql) {
                    var duplicate = duplicateField(sql);
                    if (duplicate.isPresent()) { throw new ResidentDuplicateException(duplicate.get(), sql); }
                    if (sql.getErrorCode() == NO_REFERENCED_ROW) { return new HouseholdMissing(); }
                }
                throw failure;
            }
        }
    }

    private InsertOutcome insertLocked(Connection connection, Resident resident, HeadPolicy policy,
                                       Long expectedHeadId) throws SQLException {
        // 1. Lock the household: the single serialization point for every resident write of this household.
        try (var statement = connection.prepareStatement("SELECT ma_ho FROM households WHERE ma_ho = ? FOR UPDATE")) {
            statement.setString(1, resident.maHo());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) { return new HouseholdMissing(); }
            }
        }
        // 2. Apartment status: plain (non-locking) read after the household lock. Never lock apartments here.
        try (var statement = connection.prepareStatement("SELECT a.trang_thai FROM households h "
                + "JOIN apartments a ON a.so_can_ho = h.so_can_ho WHERE h.ma_ho = ?")) {
            statement.setString(1, resident.maHo());
            try (var rows = statement.executeQuery()) {
                if (rows.next() && "CAN_HO_TRONG".equals(rows.getString(1))) { return new ApartmentVacant(); }
            }
        }
        // 3. The first member of an empty household must be the head.
        if (resident.quanHeChuHo() != RelationToHead.CHU_HO && count(connection, resident.maHo()) == 0) {
            return new FirstMemberMustBeHead();
        }
        // 4. Head handling (BR-04).
        if (resident.quanHeChuHo() == RelationToHead.CHU_HO) {
            var head = lockHead(connection, resident.maHo());
            if (head.isPresent()) {
                var current = head.get();
                if (expectedHeadId == null || policy == HeadPolicy.REJECT_IF_HEAD_EXISTS) {
                    return new HeadExists(current);
                }
                if (expectedHeadId != current.maNhanKhau()) { return new HeadChanged(head); }
                try (var statement = connection.prepareStatement(
                        "UPDATE residents SET quan_he_chu_ho = 'KHAC' WHERE ma_nhan_khau = ?")) {
                    statement.setLong(1, current.maNhanKhau());
                    statement.executeUpdate();
                }
            } else if (expectedHeadId != null && policy == HeadPolicy.REPLACE_HEAD_IF_UNCHANGED) {
                return new HeadChanged(Optional.empty());
            }
        }
        // 5. Insert. Unique keys (CCCD, one head, same person without CCCD) are the last line of defence.
        try (var statement = connection.prepareStatement(
                "INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, ngay_chuyen_den) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, resident.maHo());
            statement.setString(2, resident.hoTen());
            statement.setObject(3, resident.ngaySinh());
            statement.setString(4, resident.gioiTinh().name());
            statement.setString(5, resident.soCccd());
            statement.setString(6, resident.quanHeChuHo().name());
            statement.setObject(7, resident.ngayChuyenDen());
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                keys.next();
                return new Added(new Resident(keys.getLong(1), resident.maHo(), resident.hoTen(),
                        resident.ngaySinh(), resident.gioiTinh(), resident.soCccd(), resident.quanHeChuHo(),
                        resident.ngayChuyenDen()));
            }
        }
    }

    private static Optional<Resident> lockHead(Connection connection, String maHo) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT " + COLUMNS
                + " FROM residents WHERE ma_ho = ? AND quan_he_chu_ho = 'CHU_HO' FOR UPDATE")) {
            statement.setString(1, maHo);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    private static int count(Connection connection, String maHo) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM residents WHERE ma_ho = ?")) {
            statement.setString(1, maHo);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    @Override
    public List<Resident> findByMaHo(String maHo) throws SQLException {
        try (var connection = connections.getConnection(); var statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM residents WHERE ma_ho = ? "
                        + "ORDER BY (quan_he_chu_ho = 'CHU_HO') DESC, ma_nhan_khau")) {
            statement.setString(1, maHo);
            try (var rows = statement.executeQuery()) {
                var result = new ArrayList<Resident>();
                while (rows.next()) { result.add(map(rows)); }
                return result;
            }
        }
    }

    @Override
    public Optional<Resident> findHead(String maHo) throws SQLException {
        try (var connection = connections.getConnection(); var statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM residents WHERE ma_ho = ? AND quan_he_chu_ho = 'CHU_HO'")) {
            statement.setString(1, maHo);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    @Override
    public int countByMaHo(String maHo) throws SQLException {
        try (var connection = connections.getConnection()) { return count(connection, maHo); }
    }

    private static Resident map(ResultSet rows) throws SQLException {
        return new Resident(rows.getLong("ma_nhan_khau"), rows.getString("ma_ho"), rows.getString("ho_ten"),
                rows.getObject("ngay_sinh", LocalDate.class), Gender.valueOf(rows.getString("gioi_tinh")),
                rows.getString("so_cccd"), RelationToHead.valueOf(rows.getString("quan_he_chu_ho")),
                rows.getObject("ngay_chuyen_den", LocalDate.class));
    }

    /** MySQL names the key in its message ('residents.uq_...'). An unrecognised key is NOT guessed: empty. */
    static Optional<ResidentDuplicateException.Field> duplicateField(SQLException failure) {
        if (failure.getErrorCode() != DUPLICATE_ENTRY) { return Optional.empty(); }
        var message = String.valueOf(failure.getMessage());
        if (message.contains("residents.uq_residents_cccd")) {
            return Optional.of(ResidentDuplicateException.Field.SO_CCCD);
        }
        if (message.contains("residents.uq_residents_one_head")) {
            return Optional.of(ResidentDuplicateException.Field.CHU_HO);
        }
        if (message.contains("residents.uq_residents_no_cccd_person")) {
            return Optional.of(ResidentDuplicateException.Field.PERSON);
        }
        return Optional.empty();
    }

    private static void rollback(Connection connection, Exception failure) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
    }
}
