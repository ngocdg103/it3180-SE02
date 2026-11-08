-- ============================================================
-- US-007 - Monthly billing schema  [Dev-3]
-- Standalone migration: NOT merged into database/schema.sql. Safe to re-run. Requires MySQL 8.0.16+ (enforced CHECK).
-- Apply AFTER: schema.sql (users), us001_household_apartment.sql, us002_residents.sql, us005_fee_types.sql.
-- Does NOT create fee_rates (US-006 owns it) and seeds nothing.
-- Source: docs/design/us-007-backend-contract-review.md section E.1.
-- Money is DECIMAL(19,2) but always a whole number of VND (HALF_UP before saving, enforced by CHECK).
-- No hard delete: every FK is RESTRICT and no delete path exists. invoice_lines/billing_periods are never
-- updated by the application (snapshot immutability is application-level; the DB does not block direct UPDATE).
-- ============================================================

CREATE TABLE IF NOT EXISTS billing_periods (
    ky_thu       CHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    han_nop      DATE NOT NULL,
    trang_thai   ENUM('DA_PHAT_HANH') NOT NULL DEFAULT 'DA_PHAT_HANH',
    so_hoa_don   INT NOT NULL DEFAULT 0,
    so_ho_bo_qua INT NOT NULL DEFAULT 0,
    tong_tien    DECIMAL(19,2) NOT NULL DEFAULT 0,
    created_by   BIGINT NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (ky_thu),
    CONSTRAINT ck_bp_ky CHECK (REGEXP_LIKE(ky_thu, '^[0-9]{4}-(0[1-9]|1[0-2])$', 'c')),
    CONSTRAINT ck_bp_counts CHECK (so_hoa_don >= 0 AND so_ho_bo_qua >= 0 AND tong_tien >= 0
        AND tong_tien = ROUND(tong_tien, 0)),
    CONSTRAINT fk_bp_user FOREIGN KEY (created_by) REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invoices (
    ma_hoa_don    VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ky_thu        CHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ma_ho         VARCHAR(20) NOT NULL,
    so_can_ho     VARCHAR(10) NOT NULL,
    dien_tich     DECIMAL(8,2) NOT NULL,
    so_nhan_khau  INT NOT NULL,
    tong_tien     DECIMAL(19,2) NOT NULL,
    da_thanh_toan DECIMAL(19,2) NOT NULL DEFAULT 0,
    con_thieu     DECIMAL(19,2) GENERATED ALWAYS AS (tong_tien - da_thanh_toan) STORED,
    trang_thai    ENUM('CHUA_DONG', 'DONG_THIEU', 'DA_DONG_DU', 'DONG_THUA') NOT NULL DEFAULT 'CHUA_DONG',
    han_nop       DATE NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (ma_hoa_don),
    CONSTRAINT uq_invoice_period_apartment UNIQUE (ky_thu, so_can_ho),
    CONSTRAINT ck_inv_amounts CHECK (tong_tien >= 0 AND da_thanh_toan >= 0 AND dien_tich > 0 AND so_nhan_khau >= 0
        AND tong_tien = ROUND(tong_tien, 0)),
    CONSTRAINT fk_inv_period FOREIGN KEY (ky_thu) REFERENCES billing_periods (ky_thu)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_inv_household FOREIGN KEY (ma_ho) REFERENCES households (ma_ho)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_inv_apartment FOREIGN KEY (so_can_ho) REFERENCES apartments (so_can_ho)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_inv_period_status (ky_thu, trang_thai),
    INDEX idx_inv_apartment (so_can_ho)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invoice_lines (
    ma_hoa_don       VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ma_loai_thu      VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ten_loai_thu     VARCHAR(100) NOT NULL,
    phuong_thuc_tinh ENUM('THEO_DIEN_TICH', 'THEO_NHAN_KHAU', 'THEO_CAN_HO') NOT NULL,
    so_luong         DECIMAL(10,2) NOT NULL,
    don_gia          DECIMAL(19,2) NOT NULL,
    ngay_ap_dung_gia DATE NOT NULL,
    thanh_tien       DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (ma_hoa_don, ma_loai_thu),
    CONSTRAINT ck_line_amounts CHECK (so_luong >= 0 AND don_gia > 0 AND thanh_tien >= 0
        AND thanh_tien = ROUND(thanh_tien, 0)),
    CONSTRAINT fk_line_invoice FOREIGN KEY (ma_hoa_don) REFERENCES invoices (ma_hoa_don)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_line_fee FOREIGN KEY (ma_loai_thu) REFERENCES fee_types (ma_loai_thu)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
