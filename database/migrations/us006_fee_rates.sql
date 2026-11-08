-- MySQL 8.0.16+; apply us005_fee_types.sql first. No implicit seed dates.
CREATE TABLE IF NOT EXISTS fee_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ma_loai_thu VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    don_gia DECIMAL(19,2) NOT NULL,
    ngay_ap_dung DATE NOT NULL,
    ghi_chu VARCHAR(255) NULL,
    CONSTRAINT ck_fee_rates_amount CHECK (don_gia > 0),
    CONSTRAINT uq_fee_rates_type_date UNIQUE (ma_loai_thu, ngay_ap_dung),
    CONSTRAINT fk_fee_rates_fee_type FOREIGN KEY (ma_loai_thu)
        REFERENCES fee_types (ma_loai_thu)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
