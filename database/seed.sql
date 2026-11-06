-- ============================================================
-- BlueMoon Database Seed Data
-- Description: DML script for inserting initial / mock data
-- ============================================================

-- USE bluemoon_db;

-- LOCAL/QA ONLY: public example identity from EP-06 AC-01.
-- Password is documented in SRS; only a BCrypt cost-12 hash is stored here.
-- Existing users are never overwritten/reset when the script is rerun.
INSERT INTO users (username, password_hash, ho_ten, role)
SELECT 'admin', '$2a$12$m9dwO.N73AR/7uG1vpgYP.hNS4KCSvmJWuW/jkqYMccqlugrXwI9C', 'Admin', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

