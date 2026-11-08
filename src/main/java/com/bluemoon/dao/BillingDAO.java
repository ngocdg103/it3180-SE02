package com.bluemoon.dao;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.AppliedRate;
import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.ChargeLine;
import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.ChargeStatus;
import com.bluemoon.models.MonthlyCharge;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Prepared JDBC for US-007. Only inserts, plus finalizing the period row inside the creating transaction;
 * there is no update or delete of lines or periods. Timestamps are stored as UTC.
 */
public final class BillingDAO implements BillingRepository {
    private static final int DUPLICATE_KEY = 1062;
    private static final String PERIOD_COLUMNS = "ky_thu, han_nop, so_hoa_don, so_ho_bo_qua, tong_tien, created_at";
    private static final String INVOICE_COLUMNS = "ma_hoa_don, ky_thu, ma_ho, so_can_ho, dien_tich, so_nhan_khau, "
            + "tong_tien, da_thanh_toan, con_thieu, trang_thai, han_nop";

    @FunctionalInterface interface Connections { Connection open() throws SQLException; }
    private final Connections connections;

    public BillingDAO(DatabaseConnection connections) { this(Objects.requireNonNull(connections)::getConnection); }
    BillingDAO(Connections connections) { this.connections = Objects.requireNonNull(connections); }

    @Override
    public <T> T inTransaction(TxWork<T> work) throws SQLException, BillingPeriodExistsException {
        try (var connection = connections.open()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            var tx = new Tx(connection);
            try {
                T result = work.run(tx);
                if (tx.rollbackOnly) { connection.rollback(); } else { connection.commit(); }
                return result;
            } catch (SQLException | BillingPeriodExistsException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    @Override
    public <T> T inReadOnlyTransaction(TxWork<T> work) throws SQLException {
        try (var connection = connections.open()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                T result = work.run(new Tx(connection));
                connection.rollback();
                return result;
            } catch (BillingPeriodExistsException unexpected) {
                rollback(connection, unexpected);
                throw new IllegalStateException(unexpected);
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    @Override
    public Optional<BillingPeriod> findPeriod(String kyThu) throws SQLException {
        try (var c = connections.open(); var s = c.prepareStatement(
                "SELECT " + PERIOD_COLUMNS + " FROM billing_periods WHERE ky_thu = ?")) {
            s.setString(1, kyThu);
            try (var rows = s.executeQuery()) { return rows.next() ? Optional.of(period(rows)) : Optional.empty(); }
        }
    }

    @Override
    public List<BillingPeriod> findAllPeriods() throws SQLException {
        try (var c = connections.open(); var s = c.prepareStatement(
                "SELECT " + PERIOD_COLUMNS + " FROM billing_periods ORDER BY ky_thu DESC");
             var rows = s.executeQuery()) {
            var result = new ArrayList<BillingPeriod>();
            while (rows.next()) { result.add(period(rows)); }
            return List.copyOf(result);
        }
    }

    @Override
    public List<MonthlyCharge> findCharges(String kyThu, boolean withLines) throws SQLException {
        try (var c = connections.open()) {
            var result = new ArrayList<MonthlyCharge>();
            try (var s = c.prepareStatement(
                    "SELECT " + INVOICE_COLUMNS + " FROM invoices WHERE ky_thu = ? ORDER BY so_can_ho")) {
                s.setString(1, kyThu);
                try (var rows = s.executeQuery()) {
                    while (rows.next()) { result.add(charge(rows, List.of())); }
                }
            }
            if (!withLines) { return List.copyOf(result); }
            var lines = linesByInvoice(c, kyThu);
            var full = new ArrayList<MonthlyCharge>();
            for (var header : result) {
                full.add(withLines(header, lines.getOrDefault(header.maHoaDon(), List.of())));
            }
            return List.copyOf(full);
        }
    }

    @Override
    public Optional<MonthlyCharge> findInvoice(String maHoaDon) throws SQLException {
        try (var c = connections.open()) {
            MonthlyCharge header;
            try (var s = c.prepareStatement("SELECT " + INVOICE_COLUMNS + " FROM invoices WHERE ma_hoa_don = ?")) {
                s.setString(1, maHoaDon);
                try (var rows = s.executeQuery()) {
                    if (!rows.next()) { return Optional.empty(); }
                    header = charge(rows, List.of());
                }
            }
            try (var s = c.prepareStatement(lineSelect("WHERE l.ma_hoa_don = ?"))) {
                s.setString(1, maHoaDon);
                try (var rows = s.executeQuery()) {
                    var lines = new ArrayList<ChargeLine>();
                    while (rows.next()) { lines.add(line(rows)); }
                    return Optional.of(withLines(header, lines));
                }
            }
        }
    }

    @Override
    public boolean hasCharges(String soCanHo) throws SQLException {
        try (var c = connections.open(); var s = c.prepareStatement(
                "SELECT 1 FROM invoices WHERE so_can_ho = ? LIMIT 1")) {
            s.setString(1, soCanHo);
            try (var rows = s.executeQuery()) { return rows.next(); }
        }
    }

    private static String lineSelect(String where) {
        return "SELECT l.ma_hoa_don, l.ma_loai_thu, l.ten_loai_thu, l.phuong_thuc_tinh, l.so_luong, l.don_gia, "
                + "l.ngay_ap_dung_gia, l.thanh_tien FROM invoice_lines l "
                + (where.isEmpty() ? "" : where + " ") + "ORDER BY l.ma_hoa_don, l.ma_loai_thu";
    }

    private static java.util.Map<String, List<ChargeLine>> linesByInvoice(Connection c, String kyThu)
            throws SQLException {
        var result = new java.util.HashMap<String, List<ChargeLine>>();
        try (var s = c.prepareStatement(lineSelect("JOIN invoices i ON i.ma_hoa_don = l.ma_hoa_don WHERE i.ky_thu = ?"))) {
            s.setString(1, kyThu);
            try (var rows = s.executeQuery()) {
                while (rows.next()) {
                    result.computeIfAbsent(rows.getString("ma_hoa_don"), key -> new ArrayList<>()).add(line(rows));
                }
            }
        }
        return result;
    }

    private static MonthlyCharge withLines(MonthlyCharge h, List<ChargeLine> lines) {
        return new MonthlyCharge(h.maHoaDon(), h.kyThu(), h.maHo(), h.soCanHo(), h.dienTich(), h.soNhanKhau(),
                h.tongTien(), h.daThanhToan(), h.conThieu(), h.trangThai(), h.hanNop(), lines);
    }

    private static BillingPeriod period(ResultSet rows) throws SQLException {
        return new BillingPeriod(rows.getString("ky_thu"), rows.getObject("han_nop", LocalDate.class),
                rows.getInt("so_hoa_don"), rows.getInt("so_ho_bo_qua"), rows.getBigDecimal("tong_tien"),
                rows.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

    private static MonthlyCharge charge(ResultSet rows, List<ChargeLine> lines) throws SQLException {
        return new MonthlyCharge(rows.getString("ma_hoa_don"), rows.getString("ky_thu"), rows.getString("ma_ho"),
                rows.getString("so_can_ho"), rows.getBigDecimal("dien_tich"), rows.getInt("so_nhan_khau"),
                rows.getBigDecimal("tong_tien"), rows.getBigDecimal("da_thanh_toan"), rows.getBigDecimal("con_thieu"),
                ChargeStatus.valueOf(rows.getString("trang_thai")), rows.getObject("han_nop", LocalDate.class), lines);
    }

    private static ChargeLine line(ResultSet rows) throws SQLException {
        return new ChargeLine(rows.getString("ma_loai_thu"), rows.getString("ten_loai_thu"),
                ChargeMethod.valueOf(rows.getString("phuong_thuc_tinh")), rows.getBigDecimal("so_luong"),
                rows.getBigDecimal("don_gia"), rows.getObject("ngay_ap_dung_gia", LocalDate.class),
                rows.getBigDecimal("thanh_tien"));
    }

    private static void rollback(Connection connection, Exception failure) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
    }

    private static final class Tx implements BillingTx {
        private final Connection c;
        private boolean rollbackOnly;

        Tx(Connection c) { this.c = c; }

        @Override
        public void insertPeriod(BillingPeriod period, long createdBy) throws SQLException, BillingPeriodExistsException {
            try (var s = c.prepareStatement("INSERT INTO billing_periods "
                    + "(ky_thu, han_nop, so_hoa_don, so_ho_bo_qua, tong_tien, created_by, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                s.setString(1, period.kyThu());
                s.setObject(2, period.hanNop());
                s.setInt(3, period.soHoaDon());
                s.setInt(4, period.soHoBoQua());
                s.setBigDecimal(5, period.tongTien());
                s.setLong(6, createdBy);
                s.setObject(7, utc(period.createdAt()));
                s.executeUpdate();
            } catch (SQLException failure) {
                if (failure.getErrorCode() == DUPLICATE_KEY) { throw new BillingPeriodExistsException(failure); }
                throw failure;
            }
        }

        @Override
        public BillingInputs loadInputs(YearMonth period) throws SQLException {
            var fees = new ArrayList<BillingInputs.FeeInput>();
            // fee_rates is read on THIS connection: latest rate with ngay_ap_dung <= first day of the period.
            try (var s = c.prepareStatement("SELECT f.ma_loai_thu, f.ten_loai_thu, f.phuong_thuc_tinh, "
                    + "r.don_gia, r.ngay_ap_dung FROM fee_types f LEFT JOIN fee_rates r "
                    + "ON r.ma_loai_thu = f.ma_loai_thu AND r.ngay_ap_dung = "
                    + "(SELECT MAX(r2.ngay_ap_dung) FROM fee_rates r2 WHERE r2.ma_loai_thu = f.ma_loai_thu "
                    + "AND r2.ngay_ap_dung <= ?) "
                    + "WHERE f.loai_thu = 'BAT_BUOC' AND f.trang_thai = 'DANG_AP_DUNG' ORDER BY f.ma_loai_thu")) {
                s.setObject(1, period.atDay(1));
                try (var rows = s.executeQuery()) {
                    while (rows.next()) {
                        String code = rows.getString("ma_loai_thu");
                        BigDecimal price = rows.getBigDecimal("don_gia");
                        Optional<AppliedRate> rate = price == null ? Optional.empty()
                                : Optional.of(new AppliedRate(code, price, rows.getObject("ngay_ap_dung", LocalDate.class)));
                        fees.add(new BillingInputs.FeeInput(code, rows.getString("ten_loai_thu"),
                                ChargeMethod.valueOf(rows.getString("phuong_thuc_tinh")), rate));
                    }
                }
            }
            var households = new ArrayList<BillingInputs.HouseholdInput>();
            try (var s = c.prepareStatement("SELECT h.ma_ho, h.so_can_ho, a.dien_tich, a.trang_thai, "
                    + "(SELECT COUNT(*) FROM residents r WHERE r.ma_ho = h.ma_ho AND r.ngay_chuyen_den <= ?) AS so_nhan_khau "
                    + "FROM households h JOIN apartments a ON a.so_can_ho = h.so_can_ho "
                    + "WHERE h.ngay_nhan_nha <= ? ORDER BY h.so_can_ho")) {
                s.setObject(1, period.atEndOfMonth());
                s.setObject(2, period.atEndOfMonth());
                try (var rows = s.executeQuery()) {
                    while (rows.next()) {
                        households.add(new BillingInputs.HouseholdInput(rows.getString("ma_ho"),
                                rows.getString("so_can_ho"), rows.getBigDecimal("dien_tich"),
                                ApartmentStatus.valueOf(rows.getString("trang_thai")), rows.getInt("so_nhan_khau")));
                    }
                }
            }
            return new BillingInputs(fees, households);
        }

        @Override
        public void insertInvoices(List<MonthlyCharge> charges, Instant createdAt) throws SQLException {
            if (charges.isEmpty()) { return; }
            try (var invoice = c.prepareStatement("INSERT INTO invoices (ma_hoa_don, ky_thu, ma_ho, so_can_ho, "
                    + "dien_tich, so_nhan_khau, tong_tien, da_thanh_toan, trang_thai, han_nop, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                 var line = c.prepareStatement("INSERT INTO invoice_lines (ma_hoa_don, ma_loai_thu, ten_loai_thu, "
                         + "phuong_thuc_tinh, so_luong, don_gia, ngay_ap_dung_gia, thanh_tien) "
                         + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (var charge : charges) {
                    invoice.setString(1, charge.maHoaDon());
                    invoice.setString(2, charge.kyThu());
                    invoice.setString(3, charge.maHo());
                    invoice.setString(4, charge.soCanHo());
                    invoice.setBigDecimal(5, charge.dienTich());
                    invoice.setInt(6, charge.soNhanKhau());
                    invoice.setBigDecimal(7, charge.tongTien());
                    invoice.setBigDecimal(8, charge.daThanhToan());
                    invoice.setString(9, charge.trangThai().name());
                    invoice.setObject(10, charge.hanNop());
                    invoice.setObject(11, utc(createdAt));
                    invoice.addBatch();
                }
                invoice.executeBatch();
                for (var charge : charges) {
                    for (var l : charge.lines()) {
                        line.setString(1, charge.maHoaDon());
                        line.setString(2, l.maKhoanThu());
                        line.setString(3, l.tenKhoanThu());
                        line.setString(4, l.hinhThuc().name());
                        line.setBigDecimal(5, l.soLuong());
                        line.setBigDecimal(6, l.donGia());
                        line.setObject(7, l.ngayApDungGia());
                        line.setBigDecimal(8, l.thanhTien());
                        line.addBatch();
                    }
                }
                line.executeBatch();
            }
        }

        @Override
        public void finalizePeriod(String kyThu, int soHoaDon, int soHoBoQua, BigDecimal tongTien) throws SQLException {
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE billing_periods SET so_hoa_don = ?, so_ho_bo_qua = ?, tong_tien = ? WHERE ky_thu = ?")) {
                s.setInt(1, soHoaDon);
                s.setInt(2, soHoBoQua);
                s.setBigDecimal(3, tongTien);
                s.setString(4, kyThu);
                s.executeUpdate();
            }
        }

        @Override public void rollbackOnly() { rollbackOnly = true; }

        private static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
    }
}
