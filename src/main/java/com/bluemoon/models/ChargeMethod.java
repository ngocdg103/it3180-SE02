package com.bluemoon.models;

import java.util.List;

/** US-005 hinh_thuc_thu. toString() is the display label (ComboBox). */
public enum ChargeMethod {
    THEO_DIEN_TICH("Theo diện tích (m²)"),
    THEO_NHAN_KHAU("Theo nhân khẩu (người)"),
    THEO_CAN_HO("Theo căn hộ (cố định)"),
    TU_NGUYEN("Tự nguyện");

    private final String label;
    ChargeMethod(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }

    /** BR-06: BAT_BUOC needs a concrete formula; DONG_GOP is always TU_NGUYEN. Shared by UI and Service. */
    public static List<ChargeMethod> forKind(FeeKind kind) {
        if (kind == null) { return List.of(); }
        return switch (kind) {
            case BAT_BUOC -> List.of(THEO_DIEN_TICH, THEO_NHAN_KHAU, THEO_CAN_HO);
            case DONG_GOP -> List.of(TU_NGUYEN);
        };
    }
}
