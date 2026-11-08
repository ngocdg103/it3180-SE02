package com.bluemoon.models;

/** replay=true means this exact intent had already committed; no second receipt was inserted. */
public record ReceiptOutcome(Receipt receipt, PaymentInvoice invoice, boolean replay) { }
