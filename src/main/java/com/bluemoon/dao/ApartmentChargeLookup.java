package com.bluemoon.dao;

import java.sql.SQLException;

/**
 * BR-02 hook: has this apartment ever been billed? The charge tables belong to US-007 and do not exist yet,
 * so the default answers "no". Wire a real implementation when US-007 lands.
 */
@FunctionalInterface
public interface ApartmentChargeLookup {
    ApartmentChargeLookup NONE = soCanHo -> false;

    boolean hasCharges(String soCanHo) throws SQLException;
}
