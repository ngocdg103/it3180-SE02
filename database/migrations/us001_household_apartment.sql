-- ============================================================
-- US-001 (FE-011) - Apartment / Household schema  [Dev-3 proposal]
-- Standalone migration: NOT merged into database/schema.sql (owned by Dev-1).
-- Select the database first. Safe to re-run. Requires MySQL 8.0.16+ (enforced CHECK).
-- Source: docs/srs/EP01_QuanLyCuDan.md section 1.1 and BR-01..BR-03.
-- ============================================================

CREATE TABLE IF NOT EXISTS apartments (
    so_can_ho VARCHAR(10) NOT NULL,
    tang INT NOT NULL,
    dien_tich DECIMAL(8,2) NOT NULL,
    trang_thai ENUM('DANG_O', 'CAN_HO_TRONG', 'CHO_THUE') NOT NULL,
    PRIMARY KEY (so_can_ho),
    CONSTRAINT chk_apartments_so_can_ho CHECK (REGEXP_LIKE(so_can_ho, '^P[0-9]{4}$')),
    CONSTRAINT chk_apartments_tang CHECK (tang BETWEEN 1 AND 30),
    CONSTRAINT chk_apartments_dien_tich CHECK (dien_tich > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One household record per apartment (BR-01). Household cannot exist without its apartment.
-- residents.ma_ho (US-002, Dev-1) should reference households(ma_ho).
CREATE TABLE IF NOT EXISTS households (
    ma_ho VARCHAR(20) NOT NULL,
    so_can_ho VARCHAR(10) NOT NULL,
    ngay_nhan_nha DATE NOT NULL,
    PRIMARY KEY (ma_ho),
    CONSTRAINT uq_households_apartment UNIQUE (so_can_ho),
    CONSTRAINT fk_households_apartment FOREIGN KEY (so_can_ho)
        REFERENCES apartments (so_can_ho) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
