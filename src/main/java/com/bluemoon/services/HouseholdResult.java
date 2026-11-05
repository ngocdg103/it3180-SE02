package com.bluemoon.services;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of a US-001 operation. UI shows message(); fieldErrors() maps SRS field names
 * (so_can_ho, tang, dien_tich, ma_ho, ngay_nhan_nha, trang_thai) to the message for that input.
 */
public final class HouseholdResult<T> {
    public enum Status {
        SUCCESS,
        /** Missing/invalid input. Nothing saved. See fieldErrors(). */
        VALIDATION_ERROR,
        /** so_can_ho or ma_ho already exists. Nothing saved. See fieldErrors(). */
        DUPLICATE,
        NOT_FOUND,
        /** BR-02: area change on a billed apartment. Call again with confirmAreaChange = true to proceed. */
        CONFIRMATION_REQUIRED,
        /** Database failure. Message is generic; details are never exposed. */
        SYSTEM_ERROR
    }

    private final Status status;
    private final String message;
    private final T data;
    private final Map<String, String> fieldErrors;

    private HouseholdResult(Status status, String message, T data, Map<String, String> fieldErrors) {
        this.status = Objects.requireNonNull(status);
        this.message = Objects.requireNonNull(message);
        this.data = data;
        this.fieldErrors = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fieldErrors));
    }

    public static <T> HouseholdResult<T> success(String message, T data) {
        return new HouseholdResult<>(Status.SUCCESS, message, Objects.requireNonNull(data), Map.of());
    }

    public static <T> HouseholdResult<T> failure(Status status, String message, Map<String, String> fieldErrors) {
        if (status == Status.SUCCESS) { throw new IllegalArgumentException("Use success(...)"); }
        return new HouseholdResult<>(status, message, null, fieldErrors);
    }

    public Status status() { return status; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public String message() { return message; }
    public Optional<T> data() { return Optional.ofNullable(data); }
    public Map<String, String> fieldErrors() { return fieldErrors; }

    @Override public String toString() { return "HouseholdResult[" + status + ", " + message + "]"; }
}
