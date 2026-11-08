package com.bluemoon.dao;

/** MySQL error 1062 on billing_periods.ky_thu: the period was already generated. */
public final class BillingPeriodExistsException extends Exception {
    public BillingPeriodExistsException(Throwable cause) {
        super("Billing period already exists", cause);
    }
}
