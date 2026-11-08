package com.bluemoon.dao;

/** Business refusal after the invoice is locked. Propagated only after rollback. */
public final class ReceiptRuleException extends RuntimeException {
    public enum Reason { NOT_FOUND, ALREADY_SETTLED, REQUEST_CONFLICT, AMOUNT_OVERFLOW }
    private final Reason reason;
    public ReceiptRuleException(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
