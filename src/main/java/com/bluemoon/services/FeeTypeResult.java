package com.bluemoon.services;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of a US-005 operation. message() is shown as is; fieldErrors() maps SRS field names
 * (ma_khoan_thu, ten_khoan_thu, loai_khoan_thu, hinh_thuc_thu, so_tien_mac_dinh, trang_thai) to the
 * message for that input.
 */
public final class FeeTypeResult<T> {
    public enum Status {
        SUCCESS,
        /** Missing/invalid input. Nothing saved. See fieldErrors(). */
        VALIDATION_ERROR,
        /** ma_khoan_thu already exists. Nothing saved. fieldErrors()["ma_khoan_thu"] is set. */
        DUPLICATE,
        NOT_FOUND,
        /** Database failure. Message is generic; details are never exposed. */
        SYSTEM_ERROR
    }

    private final Status status;
    private final String message;
    private final T data;
    private final Map<String, String> fieldErrors;

    private FeeTypeResult(Status status, String message, T data, Map<String, String> fieldErrors) {
        this.status = Objects.requireNonNull(status);
        this.message = Objects.requireNonNull(message);
        this.data = data;
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public static <T> FeeTypeResult<T> success(String message, T data) {
        return new FeeTypeResult<>(Status.SUCCESS, message, Objects.requireNonNull(data), Map.of());
    }

    public static <T> FeeTypeResult<T> failure(Status status, String message, Map<String, String> fieldErrors) {
        if (status == Status.SUCCESS) { throw new IllegalArgumentException("Use success(...)"); }
        return new FeeTypeResult<>(status, message, null, fieldErrors);
    }

    public Status status() { return status; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public String message() { return message; }
    public Optional<T> data() { return Optional.ofNullable(data); }
    public Map<String, String> fieldErrors() { return fieldErrors; }

    @Override public String toString() { return "FeeTypeResult[" + status + ", " + message + "]"; }
}
