package com.bluemoon.models;

import java.util.Optional;

/** EP-01 `trang_thai`: exactly three allowed values. Stored by name, shown by label. */
public enum ApartmentStatus {
    DANG_O("Đang ở"), CAN_HO_TRONG("Căn hộ trống"), CHO_THUE("Cho thuê");

    private final String label;

    ApartmentStatus(String label) { this.label = label; }

    public String label() { return label; }

    @Override public String toString() { return label; }

    /** Accepts the enum name or the Vietnamese label (case-insensitive). */
    public static Optional<ApartmentStatus> parse(String text) {
        if (text == null) { return Optional.empty(); }
        var value = text.trim();
        for (var status : values()) {
            if (status.name().equalsIgnoreCase(value) || status.label.equalsIgnoreCase(value)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
