package com.bluemoon.models;

/** Update input. so_can_ho and ma_ho are identifiers and cannot be edited (proposal), so they are absent. */
public record HouseholdUpdateForm(String tang, String dienTich, String ngayNhanNha, ApartmentStatus trangThai) { }
