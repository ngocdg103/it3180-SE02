-- LOCAL/QA ONLY. Public demo credentials are documented in README.md.
-- Insert missing users only; preserve every field of existing accounts.
SET NAMES utf8mb4;

INSERT INTO users (username, password_hash, ho_ten, role, trang_thai)
SELECT 'admin', '$2a$12$m9dwO.N73AR/7uG1vpgYP.hNS4KCSvmJWuW/jkqYMccqlugrXwI9C', 'Ban Quản Trị Hệ Thống', 'ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO users (username, password_hash, ho_ten, role, trang_thai)
SELECT 'accountant', '$2a$12$ydOL.6Y0iN.E1h8T.YnML.sZ02RyNT48IlPmHrSJDvbAHGVDqEX4C', 'Kế Toán Viên BlueMoon', 'ACCOUNTANT', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'accountant');

INSERT INTO users (username, password_hash, ho_ten, role, trang_thai)
SELECT 'resident', '$2a$12$bMao/t8dfBuUVVvBTqaaO.AYZKug4oDtrXlHaiSUXoncWxgu1oRCS', 'Cư Dân BlueMoon', 'RESIDENT', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'resident');
