package com.bluemoon.dao;

/** A US-002 unique constraint was violated. Distinct from the US-001 DuplicateRecordException. */
public final class ResidentDuplicateException extends Exception {
    /**
     * SO_CCCD: CCCD already used. CHU_HO: a second head. PERSON: same name + birth date + gender without CCCD
     * in the same household.
     */
    public enum Field { SO_CCCD, CHU_HO, PERSON }

    private final Field field;

    public ResidentDuplicateException(Field field, Throwable cause) {
        super("Duplicate " + field, cause);
        this.field = field;
    }

    public Field field() { return field; }
}
