package com.bluemoon.dao;

/** A unique constraint was violated; field() tells which SRS field collided. */
public final class DuplicateRecordException extends Exception {
    public enum Field { SO_CAN_HO, MA_HO }

    private final Field field;

    public DuplicateRecordException(Field field, Throwable cause) {
        super("Duplicate " + field, cause);
        this.field = field;
    }

    public Field field() { return field; }
}
