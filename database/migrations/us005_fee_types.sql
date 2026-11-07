-- US-005 only. MySQL 8.0.16+ (enforced CHECK constraints).
-- DTO maKhoanThu/tenKhoanThu/loai/hinhThuc map to the columns below.
CREATE TABLE IF NOT EXISTS fee_types (
    ma_loai_thu VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    ten_loai_thu VARCHAR(100) NOT NULL,
    loai_thu ENUM('BAT_BUOC', 'DONG_GOP') NOT NULL,
    phuong_thuc_tinh ENUM('THEO_DIEN_TICH', 'THEO_NHAN_KHAU', 'THEO_CAN_HO', 'TU_NGUYEN') NOT NULL,
    so_tien_mac_dinh DECIMAL(19,2) NOT NULL DEFAULT 0,
    trang_thai ENUM('DANG_AP_DUNG', 'NGUNG_AP_DUNG') NOT NULL DEFAULT 'DANG_AP_DUNG',
    CONSTRAINT ck_fee_code CHECK (REGEXP_LIKE(ma_loai_thu, '^[A-Z0-9_]+$', 'c')),
    CONSTRAINT ck_fee_name CHECK (CHAR_LENGTH(TRIM(ten_loai_thu)) BETWEEN 3 AND 100),
    CONSTRAINT ck_fee_amount CHECK (so_tien_mac_dinh >= 0),
    CONSTRAINT ck_fee_kind CHECK (
        (loai_thu = 'DONG_GOP' AND phuong_thuc_tinh = 'TU_NGUYEN' AND so_tien_mac_dinh = 0)
        OR (loai_thu = 'BAT_BUOC' AND phuong_thuc_tinh IN ('THEO_DIEN_TICH', 'THEO_NHAN_KHAU', 'THEO_CAN_HO'))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
