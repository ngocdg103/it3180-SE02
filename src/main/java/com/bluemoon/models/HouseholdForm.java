package com.bluemoon.models;

/**
 * Create input = raw form text, so the Service owns all parsing/validation messages.
 * Dates are YYYY-MM-DD, tang is an integer string, dienTich a plain decimal ("85.5"). Null is treated as blank.
 */
public record HouseholdForm(String soCanHo, String tang, String dienTich, String maHo,
                            String ngayNhanNha, ApartmentStatus trangThai) { }
