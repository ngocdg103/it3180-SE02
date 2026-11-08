package com.bluemoon.dao;

/** Q17: a vacant status was refused inside the household update transaction. */
public final class HouseholdHasResidentsException extends RuntimeException {
    public HouseholdHasResidentsException() {
        super("Household still has residents");
    }
}
