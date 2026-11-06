-- ============================================================
-- BlueMoon Database Schema
-- Description: DDL script for creating tables, views, and indexes
-- ============================================================

-- Create database if not exists
-- CREATE DATABASE IF NOT EXISTS bluemoon_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE bluemoon_db;

-- US-012 / docs/srs/EP06_VanHanhHeThong.md. Select the database before sourcing.
-- Additive foundation only; household/apartment tables remain owned by Dev-3.
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    ho_ten VARCHAR(50) NOT NULL,
    role ENUM('ADMIN', 'ACCOUNTANT', 'RESIDENT') NOT NULL,
    trang_thai ENUM('ACTIVE', 'LOCKED') NOT NULL DEFAULT 'ACTIVE',
    so_lan_sai INT NOT NULL DEFAULT 0,
    -- UTC deadline for a timed lock. LOCKED + NULL means administrative lock.
    locked_until DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT chk_users_failures CHECK (so_lan_sai >= 0),
    CONSTRAINT chk_users_lock_state CHECK (trang_thai = 'LOCKED' OR locked_until IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

