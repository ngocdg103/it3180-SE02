package com.bluemoon.services;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** UI-safe result. SQL/connection details must never be put in message or fieldErrors. */
public final class FeeRateResult<T> {
    public enum Status { SUCCESS, VALIDATION_ERROR, NOT_FOUND, DUPLICATE, BUSY, SYSTEM_ERROR }
    private final Status status;
    private final String message;
    private final T data;
    private final Map<String, String> fieldErrors;

    private FeeRateResult(Status status, String message, T data, Map<String, String> errors) {
        this.status = Objects.requireNonNull(status);
        this.message = Objects.requireNonNull(message);
        this.data = data;
        this.fieldErrors = Map.copyOf(errors);
    }
    public static <T> FeeRateResult<T> success(String message, T data) {
        return new FeeRateResult<>(Status.SUCCESS, message, Objects.requireNonNull(data), Map.of());
    }
    public static <T> FeeRateResult<T> failure(Status status, String message, Map<String, String> errors) {
        if (status == Status.SUCCESS) { throw new IllegalArgumentException("Use success"); }
        return new FeeRateResult<>(status, message, null, errors);
    }
    public Status status() { return status; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public String message() { return message; }
    public Optional<T> data() { return Optional.ofNullable(data); }
    public Map<String, String> fieldErrors() { return fieldErrors; }
}
