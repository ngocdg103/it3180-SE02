-- US-008; apply official users + US-001 + US-007 first. Additive, no billing snapshot changes.
-- Receipt code keeps full household code and timestamp; 64 chars (SRS's 30-char limit needs PO reconciliation).
CREATE TABLE IF NOT EXISTS phieu_thu (
    ma_phieu_thu VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ma_hoa_don VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nguoi_nop VARCHAR(50) NOT NULL,
    so_tien_nop DECIMAL(19,2) NOT NULL,
    ngay_nop DATE NOT NULL,
    hinh_thuc ENUM('TIEN_MAT','CHUYEN_KHOAN') NOT NULL,
    ghi_chu VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_receipt_request UNIQUE(request_id),
    CONSTRAINT ck_receipt_money CHECK(so_tien_nop > 0 AND so_tien_nop = ROUND(so_tien_nop,0)),
    CONSTRAINT ck_receipt_name CHECK(CHAR_LENGTH(TRIM(nguoi_nop)) BETWEEN 2 AND 50),
    CONSTRAINT fk_receipt_invoice FOREIGN KEY(ma_hoa_don) REFERENCES invoices(ma_hoa_don) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_receipt_user FOREIGN KEY(created_by) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX ix_receipt_invoice(ma_hoa_don,ngay_nop,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Ledger balance for BR-13. Future-period consumption requires coordinated Billing integration, not snapshot edits.
CREATE TABLE IF NOT EXISTS apartment_credit (
    so_can_ho VARCHAR(10) PRIMARY KEY,
    so_du DECIMAL(19,2) NOT NULL DEFAULT 0,
    CONSTRAINT ck_apartment_credit CHECK(so_du >= 0 AND so_du = ROUND(so_du,0)),
    CONSTRAINT fk_credit_apartment FOREIGN KEY(so_can_ho) REFERENCES apartments(so_can_ho) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
