package com.bluemoon.models;

import java.util.Optional;

/** EP-01 `gioi_tinh`: Nam or Nữ. Stored by name, shown by label. */
public enum Gender {
    NAM("Nam"), NU("Nữ");

    private final String label;

    Gender(String label) { this.label = label; }

    public String label() { return label; }

    @Override public String toString() { return label; }

    /** Accepts the enum name or the Vietnamese label (case-insensitive). */
    public static Optional<Gender> parse(String text) {
        if (text == null) { return Optional.empty(); }
        var value = text.trim();
        for (var gender : values()) {
            if (gender.name().equalsIgnoreCase(value) || gender.label.equalsIgnoreCase(value)) {
                return Optional.of(gender);
            }
        }
        return Optional.empty();
    }
}
