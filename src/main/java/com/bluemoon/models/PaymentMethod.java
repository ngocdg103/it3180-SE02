package com.bluemoon.models;

/** EP-03 US-008; manual records, not a banking integration. */
public enum PaymentMethod {
    TIEN_MAT("Tiền mặt"), CHUYEN_KHOAN("Chuyển khoản");
    private final String label;
    PaymentMethod(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
