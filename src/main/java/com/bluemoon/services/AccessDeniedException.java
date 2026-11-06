package com.bluemoon.services;

/**
 * EP-06 BR-20: thrown when the current role may not run a function. Nothing was changed.
 * NOTE for Dev-1 (US-018): if you define this class too, keep one and delete the other.
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) { super(message); }
}
