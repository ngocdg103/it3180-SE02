package com.bluemoon.models;

/** Validated by ResidentSearchService; repository binds value as data, never SQL. */
public record ResidentSearchCriteria(Field field,String value) {
    public enum Field {
        APARTMENT("Số căn hộ"), NAME("Tên cư dân"), CCCD("CCCD/CMND"), FLOOR("Tầng");
        private final String label;
        Field(String label){this.label=label;}
        @Override public String toString(){return label;}
    }
}
