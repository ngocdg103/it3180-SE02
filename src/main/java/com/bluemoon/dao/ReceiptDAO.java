package com.bluemoon.dao;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.models.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.bluemoon.dao.ReceiptRuleException.Reason.*;

/** Lock invoice -> receipt intent -> credit row. A single transaction owns receipt + payment fields + surplus. */
public final class ReceiptDAO implements ReceiptRepository {
    @FunctionalInterface interface Connections { Connection open() throws SQLException; }
    private static final String INVOICE = "SELECT ma_hoa_don,ky_thu,ma_ho,so_can_ho,tong_tien,da_thanh_toan,con_thieu,trang_thai FROM invoices";
    private static final String RECEIPT = "SELECT p.ma_phieu_thu,p.request_id,p.ma_hoa_don,p.nguoi_nop,p.so_tien_nop,p.ngay_nop,p.hinh_thuc,p.ghi_chu,p.created_by,p.created_at FROM phieu_thu p";
    private final Connections connections;
    public ReceiptDAO(DatabaseConnection connections) { this(Objects.requireNonNull(connections)::getConnection); }
    ReceiptDAO(Connections connections) { this.connections = Objects.requireNonNull(connections); }
    @Override public ReceiptOutcome record(Receipt input) throws SQLException {
        Objects.requireNonNull(input);
        try (var c = connections.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            // Server refusal must precede the Foundation's 10s socket timeout. Scoped to this connection only.
            try (var s = c.prepareStatement("SET SESSION innodb_lock_wait_timeout=5")) { s.execute(); }
            c.setAutoCommit(false);
            try {
                PaymentInvoice invoice;
                try (var s = c.prepareStatement(INVOICE + " WHERE ma_hoa_don=? FOR UPDATE")) {
                    s.setString(1, input.maHoaDon());
                    try (var r = s.executeQuery()) { if (!r.next()) { throw new ReceiptRuleException(NOT_FOUND); } invoice = invoice(r); }
                }
                try (var s = c.prepareStatement(RECEIPT + " WHERE p.request_id=? FOR UPDATE")) {
                    s.setString(1, input.requestId());
                    try (var r = s.executeQuery()) {
                        if (r.next()) {
                            Receipt old = receipt(r);
                            if (!sameIntent(old, input)) { throw new ReceiptRuleException(REQUEST_CONFLICT); }
                            c.rollback(); return new ReceiptOutcome(old, invoice, true);
                        }
                    }
                }
                if (invoice.conThieu().signum() <= 0) { throw new ReceiptRuleException(ALREADY_SETTLED); }
                BigDecimal paid;
                try { paid = Receipt.wholeMoney(invoice.daThanhToan().add(input.soTienNop())); }
                catch (IllegalArgumentException invalid) { throw new ReceiptRuleException(AMOUNT_OVERFLOW); }
                BigDecimal remaining = invoice.tongTien().subtract(paid);
                ChargeStatus status = remaining.signum() < 0 ? ChargeStatus.DONG_THUA
                        : remaining.signum() == 0 ? ChargeStatus.DA_DONG_DU : ChargeStatus.DONG_THIEU;
                // Preserve the full household code and timestamp; unique request tag handles same-millisecond submissions.
                String code = "PT-" + input.createdAt().toEpochMilli() + "-" + input.requestId().substring(0, 8) + "-" + invoice.maHo();
                Receipt saved = new Receipt(code, input.requestId(), input.maHoaDon(), input.nguoiNop(), input.soTienNop(),
                        input.ngayNop(), input.hinhThuc(), input.ghiChu(), input.createdBy(), input.createdAt());
                try (var s = c.prepareStatement("INSERT INTO phieu_thu(ma_phieu_thu,request_id,ma_hoa_don,nguoi_nop,so_tien_nop,ngay_nop,hinh_thuc,ghi_chu,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                    s.setString(1, code); s.setString(2, saved.requestId()); s.setString(3, saved.maHoaDon()); s.setString(4, saved.nguoiNop());
                    s.setBigDecimal(5, saved.soTienNop()); s.setObject(6, saved.ngayNop()); s.setString(7, saved.hinhThuc().name());
                    s.setString(8, saved.ghiChu()); s.setLong(9, saved.createdBy()); s.setObject(10, LocalDateTime.ofInstant(saved.createdAt(), ZoneOffset.UTC));
                    if (s.executeUpdate() != 1) { throw new SQLException("Receipt insert did not affect one row"); }
                }
                try (var s = c.prepareStatement("UPDATE invoices SET da_thanh_toan=?,trang_thai=? WHERE ma_hoa_don=?")) {
                    s.setBigDecimal(1, paid); s.setString(2, status.name()); s.setString(3, saved.maHoaDon());
                    if (s.executeUpdate() != 1) { throw new SQLException("Invoice update did not affect one row"); }
                }
                if (remaining.signum() < 0) {
                    try (var s = c.prepareStatement("INSERT INTO apartment_credit(so_can_ho,so_du) VALUES(?,?) ON DUPLICATE KEY UPDATE so_du=so_du+?")) {
                        s.setString(1, invoice.soCanHo()); s.setBigDecimal(2, remaining.negate()); s.setBigDecimal(3, remaining.negate()); s.executeUpdate();
                    }
                }
                c.commit();
                return new ReceiptOutcome(saved, new PaymentInvoice(invoice.maHoaDon(), invoice.kyThu(), invoice.maHo(), invoice.soCanHo(),
                        invoice.tongTien(), paid, remaining, status), false);
            } catch (SQLException | RuntimeException failure) {
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure; // includes uncertain commit: never retry automatically
            }
        }
    }
    private static boolean sameIntent(Receipt a, Receipt b) {
        return a.maHoaDon().equals(b.maHoaDon()) && a.nguoiNop().equals(b.nguoiNop()) && a.soTienNop().compareTo(b.soTienNop()) == 0
                && a.ngayNop().equals(b.ngayNop()) && a.hinhThuc() == b.hinhThuc() && Objects.equals(a.ghiChu(), b.ghiChu())
                && a.createdBy() == b.createdBy();
    }
    @Override public List<PaymentInvoice> listOutstanding() throws SQLException {
        try (var c = connections.open(); var s = c.prepareStatement(INVOICE + " WHERE con_thieu>0 ORDER BY ky_thu,so_can_ho"); var r = s.executeQuery()) {
            var rows = new ArrayList<PaymentInvoice>(); while (r.next()) { rows.add(invoice(r)); } return List.copyOf(rows);
        }
    }
    @Override public List<Receipt> listByHousehold(String code) throws SQLException {
        try (var c = connections.open(); var s = c.prepareStatement(RECEIPT + " JOIN invoices i ON i.ma_hoa_don=p.ma_hoa_don WHERE i.ma_ho=? ORDER BY p.created_at DESC,p.ma_phieu_thu DESC")) {
            s.setString(1, code); try (var r = s.executeQuery()) { var rows = new ArrayList<Receipt>(); while (r.next()) { rows.add(receipt(r)); } return List.copyOf(rows); }
        }
    }
    @Override public Optional<Receipt> findByRequestId(String id) throws SQLException {
        try (var c = connections.open(); var s = c.prepareStatement(RECEIPT + " WHERE p.request_id=?")) {
            s.setString(1, id); try (var r = s.executeQuery()) { return r.next() ? Optional.of(receipt(r)) : Optional.empty(); }
        }
    }
    private static PaymentInvoice invoice(ResultSet r) throws SQLException {
        return new PaymentInvoice(r.getString("ma_hoa_don"), r.getString("ky_thu"), r.getString("ma_ho"), r.getString("so_can_ho"),
                r.getBigDecimal("tong_tien"), r.getBigDecimal("da_thanh_toan"), r.getBigDecimal("con_thieu"), ChargeStatus.valueOf(r.getString("trang_thai")));
    }
    private static Receipt receipt(ResultSet r) throws SQLException {
        return new Receipt(r.getString("ma_phieu_thu"), r.getString("request_id"), r.getString("ma_hoa_don"), r.getString("nguoi_nop"),
                r.getBigDecimal("so_tien_nop"), r.getObject("ngay_nop", LocalDate.class), PaymentMethod.valueOf(r.getString("hinh_thuc")),
                r.getString("ghi_chu"), r.getLong("created_by"), r.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }
}
