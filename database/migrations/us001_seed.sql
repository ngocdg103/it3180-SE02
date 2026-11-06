-- ============================================================
-- US-001 sample data for local/QA/UI demo. Idempotent (existing rows untouched).
-- P1204 is CAN_HO_TRONG so AC-02 (duplicate) and AC-03 (empty -> occupied) can be demoed.
-- Use another number (e.g. P1205) to demo AC-01 (create).
-- ============================================================

INSERT INTO apartments (so_can_ho, tang, dien_tich, trang_thai) VALUES
    ('P0101', 1, 45.50, 'DANG_O'),
    ('P0508', 5, 62.00, 'CHO_THUE'),
    ('P1002', 10, 75.50, 'DANG_O'),
    ('P1204', 12, 80.00, 'CAN_HO_TRONG'),
    ('P1502', 15, 95.00, 'DANG_O'),
    ('P3015', 30, 120.00, 'CAN_HO_TRONG')
ON DUPLICATE KEY UPDATE so_can_ho = so_can_ho;

INSERT INTO households (ma_ho, so_can_ho, ngay_nhan_nha) VALUES
    ('HK-0101', 'P0101', '2023-06-01'),
    ('HK-0508', 'P0508', '2023-09-10'),
    ('HK-1002', 'P1002', '2024-01-20'),
    ('HK-1204', 'P1204', '2024-03-15'),
    ('HK-1502', 'P1502', '2024-05-05'),
    ('HK-3015', 'P3015', '2025-02-01')
ON DUPLICATE KEY UPDATE ma_ho = ma_ho;
