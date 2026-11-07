package com.bluemoon.models;

/** US-005 loai_khoan_thu. toString() is the display label (ComboBox). */
public enum FeeKind {
    BAT_BUOC("Bắt buộc"),
    DONG_GOP("Tự nguyện");

    private final String label;
    FeeKind(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }
}
