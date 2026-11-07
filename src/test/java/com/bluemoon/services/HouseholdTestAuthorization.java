package com.bluemoon.services;

import com.bluemoon.models.Account;
import com.bluemoon.models.Role;
import java.util.Optional;

/** Test-only bridge for DAO tests in another package; always uses the real RBAC policy. */
public final class HouseholdTestAuthorization {
    private HouseholdTestAuthorization() { }

    public static AuthorizationService admin() {
        return new RoleBasedAuthorizationService(
                () -> Optional.of(new Account(1, "testadmin", "Test Admin", Role.ADMIN)), null);
    }
}
