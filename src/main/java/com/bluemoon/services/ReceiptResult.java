package com.bluemoon.services;

import java.util.*;

public final class ReceiptResult<T> {
    public enum Status { SUCCESS, DUPLICATE, VALIDATION_ERROR, NOT_FOUND, ALREADY_SETTLED, CONFLICT, BUSY, SYSTEM_ERROR }
    private final Status status;
    private final String message;
    private final T data;
    private final Map<String,String> errors;
    private ReceiptResult(Status status, String message, T data, Map<String,String> errors) {
        this.status = Objects.requireNonNull(status); this.message = Objects.requireNonNull(message); this.data = data; this.errors = Map.copyOf(errors);
    }
    public static <T> ReceiptResult<T> success(T data) { return new ReceiptResult<>(Status.SUCCESS, "Ghi nhận thanh toán thành công.", Objects.requireNonNull(data), Map.of()); }
    public static <T> ReceiptResult<T> failure(Status status, String message, Map<String,String> errors) {
        if (status == Status.SUCCESS) { throw new IllegalArgumentException("Use success"); } return new ReceiptResult<>(status, message, null, errors);
    }
    public Status status() { return status; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public String message() { return message; }
    public Optional<T> data() { return Optional.ofNullable(data); }
    public Map<String,String> fieldErrors() { return errors; }
}
