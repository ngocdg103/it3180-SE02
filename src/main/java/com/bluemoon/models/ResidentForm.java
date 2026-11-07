package com.bluemoon.models;

/**
 * Create input = raw form text, so the Service owns all parsing/validation messages (same convention as
 * HouseholdForm). Dates are YYYY-MM-DD. soCccd blank = none. Null is treated as blank / not chosen.
 */
public record ResidentForm(String maHo, String hoTen, String ngaySinh, Gender gioiTinh, String soCccd,
                           RelationToHead quanHeChuHo, String ngayChuyenDen) { }
