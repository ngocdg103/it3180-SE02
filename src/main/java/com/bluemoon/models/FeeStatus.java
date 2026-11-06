package com.bluemoon.models;

/** US-005 trang_thai. toString() is the display label (ComboBox). */
public enum FeeStatus {
    DANG_AP_DUNG("Đang áp dụng"),
    NGUNG_AP_DUNG("Ngừng áp dụng");

    private final String label;
    FeeStatus(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }
}
