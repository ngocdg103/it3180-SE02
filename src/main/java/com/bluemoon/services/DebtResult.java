package com.bluemoon.services;

import java.util.*;

public final class DebtResult<T> {
    public enum Status { SUCCESS, VALIDATION_ERROR, NO_DATA, BUSY, SYSTEM_ERROR }
    private final Status status;
    private final String message;
    private final T data;
    private final Map<String,String> errors;
    private DebtResult(Status status,String message,T data,Map<String,String> errors) { this.status=Objects.requireNonNull(status); this.message=Objects.requireNonNull(message); this.data=data; this.errors=Map.copyOf(errors); }
    public static <T> DebtResult<T> success(T data,String message) { return new DebtResult<>(Status.SUCCESS,message,Objects.requireNonNull(data),Map.of()); }
    public static <T> DebtResult<T> failure(Status status,String message,Map<String,String> errors) { if(status==Status.SUCCESS) { throw new IllegalArgumentException(); } return new DebtResult<>(status,message,null,errors); }
    public Status status() { return status; }
    public boolean isSuccess() { return status==Status.SUCCESS; }
    public String message() { return message; }
    public Optional<T> data() { return Optional.ofNullable(data); }
    public Map<String,String> fieldErrors() { return errors; }
}
