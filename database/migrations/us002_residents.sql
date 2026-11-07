-- ============================================================
-- US-002 (FE-012) - Residents schema  [Dev-3]
-- Standalone migration: NOT merged into database/schema.sql (owned by Dev-1).
-- Apply AFTER us001_household_apartment.sql. Safe to re-run. Requires MySQL 8.0.16+ (enforced CHECK).
-- Source: docs/srs/EP01_QuanLyCuDan.md section 2 and BR-04; docs/design/us-002-contract-review.md.
-- No hard delete: FK is RESTRICT and no delete path exists in Sprint 1.
-- ============================================================

CREATE TABLE IF NOT EXISTS residents (
    ma_nhan_khau    BIGINT NOT NULL AUTO_INCREMENT,
    ma_ho           VARCHAR(20) NOT NULL,
    ho_ten          VARCHAR(50) NOT NULL,
    ngay_sinh       DATE NOT NULL,
    gioi_tinh       ENUM('NAM', 'NU') NOT NULL,
    so_cccd         VARCHAR(12) NULL,
    quan_he_chu_ho  ENUM('CHU_HO', 'VO', 'CHONG', 'CON', 'BO_ME', 'NGUOI_THUE', 'KHAC') NOT NULL,
    ngay_chuyen_den DATE NOT NULL,
    -- BR-04: ma_ho only for the head, NULL otherwise. UNIQUE ignores NULLs => at most one head per household.
    head_ma_ho      VARCHAR(20) GENERATED ALWAYS AS (IF(quan_he_chu_ho = 'CHU_HO', ma_ho, NULL)) STORED,
    -- Same person without a CCCD in the same household (case-insensitive, accent-sensitive). NULL when a CCCD exists.
    no_cccd_key     VARCHAR(100) COLLATE utf8mb4_0900_as_ci GENERATED ALWAYS AS (
                        IF(so_cccd IS NULL, CONCAT_WS('|', ma_ho, ho_ten, ngay_sinh, gioi_tinh), NULL)) STORED,
    PRIMARY KEY (ma_nhan_khau),
    CONSTRAINT uq_residents_cccd UNIQUE (so_cccd),
    CONSTRAINT uq_residents_one_head UNIQUE (head_ma_ho),
    CONSTRAINT uq_residents_no_cccd_person UNIQUE (no_cccd_key),
    CONSTRAINT fk_residents_household FOREIGN KEY (ma_ho)
        REFERENCES households (ma_ho) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_residents_cccd CHECK (so_cccd IS NULL OR REGEXP_LIKE(so_cccd, '^([0-9]{9}|[0-9]{12})$')),
    INDEX idx_residents_household (ma_ho)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
