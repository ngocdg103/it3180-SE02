package com.bluemoon.services;

/** Trusted backend port; no production persistence adapter is supplied by US-018. */
@FunctionalInterface
public interface ApartmentOwnershipResolver {
    /** Resolve an existing account/apartment link, never a UI-supplied ownership claim. */
    boolean isLinked(long userId, String soCanHo);
}
