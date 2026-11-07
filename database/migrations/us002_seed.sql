-- ============================================================
-- US-002 sample data for local/QA/UI demo. Idempotent (existing rows untouched).
-- Needs us001_seed.sql. Fake CCCD numbers only.
--   HK-1002 (P1002, Dang o): head + spouse + child under 14 without CCCD  -> AC-01/AC-03 demo (already has a head)
--   HK-0101 (P0101, Dang o): head only
--   HK-0508 (P0508, Cho thue): tenant head
--   HK-1502 (P1502, Dang o): NO residents -> first member must be Chu ho
--   HK-1204 / HK-3015: Can ho trong -> adding residents is refused
-- ============================================================

INSERT INTO residents (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, ngay_chuyen_den) VALUES
    ('HK-1002', 'Nguyễn Văn A', '1990-05-12', 'NAM', '001090000001', 'CHU_HO', '2024-01-20'),
    ('HK-1002', 'Trần Thị B', '1992-08-30', 'NU', '001092000002', 'VO', '2024-01-20'),
    ('HK-1002', 'Nguyễn Minh C', '2018-03-01', 'NAM', NULL, 'CON', '2024-01-20'),
    ('HK-0101', 'Lê Văn D', '1985-11-02', 'NAM', '001085000003', 'CHU_HO', '2023-06-01'),
    ('HK-0508', 'Phạm Thị E', '1995-02-14', 'NU', '001095000004', 'CHU_HO', '2023-09-10')
ON DUPLICATE KEY UPDATE ma_nhan_khau = ma_nhan_khau;
