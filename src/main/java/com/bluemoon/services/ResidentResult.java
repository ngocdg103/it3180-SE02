package com.bluemoon.services;

import com.bluemoon.models.Resident;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of a US-002 operation. UI shows message(); fieldErrors() maps SRS field names
 * (ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, ngay_chuyen_den) to the message for that input.
 */
public final class ResidentResult<T> {
    public enum Status {
        SUCCESS,
        /** Missing/invalid input or a refused business rule. Nothing saved. See fieldErrors(). */
        VALIDATION_ERROR,
        /** CCCD already used, or the same person (no CCCD) already in the household. Nothing saved. */
        DUPLICATE,
        /** The household does not exist. Nothing saved. */
        NOT_FOUND,
        /** BR-04: ask the user. See confirmationReason() and currentHead(). Nothing saved. */
        CONFIRMATION_REQUIRED,
        /** Database failure. Message is generic; details are never exposed. */
        SYSTEM_ERROR
    }

    public enum ConfirmationReason {
        /** The household already has a head: ask, then call again with confirmReplaceHeadId = currentHead().maNhanKhau(). */
        HEAD_EXISTS,
        /** The confirmation is out of date (the head changed). Show currentHead() if present and ask again; do not reuse the old id. */
        CONFIRMATION_STALE
    }

    private final Status status;
    private final String message;
    private final T data;
    private final Map<String, String> fieldErrors;
    private final ConfirmationReason reason;
    private final Resident currentHead;

    private ResidentResult(Status status, String message, T data, Map<String, String> fieldErrors,
                           ConfirmationReason reason, Resident currentHead) {
        this.status = Objects.requireNonNull(status);
        this.message = Objects.requireNonNull(message);
        this.data = data;
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
        this.reason = reason;
        this.currentHead = currentHead;
    }

    public static <T> ResidentResult<T> success(String message, T data) {
        return new ResidentResult<>(Status.SUCCESS, message, Objects.requireNonNull(data), Map.of(), null, null);
    }

    public static <T> ResidentResult<T> failure(Status status, String message, Map<String, String> fieldErrors) {
        if (status == Status.SUCCESS || status == Status.CONFIRMATION_REQUIRED) {
            throw new IllegalArgumentException("Use success(...) / confirmation(...)");
        }
        return new ResidentResult<>(status, message, null, fieldErrors, null, null);
    }

    public static <T> ResidentResult<T> confirmation(String message, ConfirmationReason reason, Resident currentHead) {
        return new ResidentResult<>(Status.CONFIRMATION_REQUIRED, message, null, Map.of(),
                Objects.requireNonNull(reason), currentHead);
    }

    public Status status() { return status; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public String message() { return message; }
    public Optional<T> data() { return Optional.ofNullable(data); }
    public Map<String, String> fieldErrors() { return fieldErrors; }
    /** Only for CONFIRMATION_REQUIRED. */
    public Optional<ConfirmationReason> confirmationReason() { return Optional.ofNullable(reason); }
    /** Only for CONFIRMATION_REQUIRED; empty when the household no longer has a head. */
    public Optional<Resident> currentHead() { return Optional.ofNullable(currentHead); }

    @Override public String toString() { return "ResidentResult[" + status + ", " + message + "]"; }
}
