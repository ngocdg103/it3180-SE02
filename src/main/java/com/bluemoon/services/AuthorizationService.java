package com.bluemoon.services;

import com.bluemoon.models.Permission;

/** UI checks are advisory; protected business operations must also call require*. */
public interface AuthorizationService {
    /** Role-only permissions. OWN and missing session/permission always deny. */
    boolean canAccess(Permission permission);

    /** Throws AccessDeniedException before protected work if the role check denies. */
    void requirePermission(Permission permission);

    /** Only OWN, for a RESIDENT with a trusted link to the exact apartment. */
    boolean canAccessApartment(String soCanHo, Permission permission);

    /** Throws AccessDeniedException if ownership cannot be verified. */
    void requireApartmentPermission(String soCanHo, Permission permission);
}
