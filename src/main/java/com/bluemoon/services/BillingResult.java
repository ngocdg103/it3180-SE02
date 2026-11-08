package com.bluemoon.services;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of a US-007 operation. message() is shown as is. fieldErrors() maps "ky_thu" to its message;
 * for MISSING_RATE it maps each fee code lacking an effective rate to a message.
 */
public final class BillingResult<T> {
    public enum Status {
        SUCCESS,
        /** ky_thu missing/malformed or not the current month (Q11). Nothing saved. */
        VALIDATION_ERROR,
        /** The period already exists (AC-02). Nothing saved. fieldErrors()["ky_thu"] is set. */
        DUPLICATE,
        /** An active mandatory fee has no effective rate. Nothing saved. */
        MISSING_RATE,
        /** No active mandatory fee or no household to bill. Nothing saved. */
        NOTHING_TO_BILL,
        NOT_FOUND,
        /** Lock wait timeout or deadlock; safe to retry. Nothing saved. */
        BUSY,
        /** Database failure. Message is generic; details are never exposed. */
        SYSTEM_ERROR
    }

    private final Status status;
    private final String message;
    private final T data;
    private final Map<String, String> fieldErrors;

    private BillingResult(Status status, String message, T data, Map<String, String> fieldErrors) {
        this.status = Objects.requireNonNull(status);
        this.message = Objects.requireNonNull(message);
        this.data = data;
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public static <T> BillingResult<T> success(String message, T data) {
        return new BillingResult<>(Status.SUCCESS, message, Objects.requireNonNull(data), Map.of());
    }

    public static <T> BillingResult<T> failure(Status status, String message, Map<String, String> fieldErrors) {
        if (status == Status.SUCCESS) { throw new IllegalArgumentException("Use success(...)"); }
        return new BillingResult<>(status, message, null, fieldErrors);
    }

    public Status status() { return status; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public String message() { return message; }
    public Optional<T> data() { return Optional.ofNullable(data); }
    public Map<String, String> fieldErrors() { return fieldErrors; }

    @Override public String toString() { return "BillingResult[" + status + ", " + message + "]"; }
}
