package com.bluemoon.services;

/** BR-22: access was denied; the protected operation must not proceed. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) { super(message); }
}
