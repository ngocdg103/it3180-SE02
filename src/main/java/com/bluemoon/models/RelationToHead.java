package com.bluemoon.models;

import java.util.Optional;

/** EP-01 `quan_he_chu_ho`: exactly the seven SRS values. Stored by name, shown by label. */
public enum RelationToHead {
    CHU_HO("Chủ hộ"), VO("Vợ"), CHONG("Chồng"), CON("Con"), BO_ME("Bố/Mẹ"), NGUOI_THUE("Người thuê"), KHAC("Khác");

    private final String label;

    RelationToHead(String label) { this.label = label; }

    public String label() { return label; }

    @Override public String toString() { return label; }

    /** Accepts the enum name or the Vietnamese label (case-insensitive). */
    public static Optional<RelationToHead> parse(String text) {
        if (text == null) { return Optional.empty(); }
        var value = text.trim();
        for (var relation : values()) {
            if (relation.name().equalsIgnoreCase(value) || relation.label.equalsIgnoreCase(value)) {
                return Optional.of(relation);
            }
        }
        return Optional.empty();
    }
}
