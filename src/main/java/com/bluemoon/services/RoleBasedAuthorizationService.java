package com.bluemoon.services;

import com.bluemoon.models.Account;
import com.bluemoon.models.Permission;
import com.bluemoon.models.Role;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.bluemoon.models.Permission.*;
import static com.bluemoon.models.Role.*;

/** SRS EP-06 matrix, with no ADMIN wildcard and no cached session identity. */
public final class RoleBasedAuthorizationService implements AuthorizationService {
    private static final String DENIED = "Bạn không có quyền thực hiện chức năng này.";
    private static final Map<Permission, Set<Role>> ROLE_PERMISSIONS = permissions();
    private final Supplier<Optional<Account>> currentAccount;
    private final ApartmentOwnershipResolver ownership;

    /** A null resolver leaves all OWN access denied, while role-only checks still work. */
    public RoleBasedAuthorizationService(ApartmentOwnershipResolver ownership) {
        this(SessionContext::getCurrentUser, ownership);
    }

    // Test seam only; production always reads the existing global SessionContext.
    RoleBasedAuthorizationService(Supplier<Optional<Account>> currentAccount,
                                  ApartmentOwnershipResolver ownership) {
        this.currentAccount = Objects.requireNonNull(currentAccount);
        this.ownership = ownership;
    }

    @Override
    public boolean canAccess(Permission permission) {
        var account = currentAccount.get();
        if (account.isEmpty() || permission == null) { return false; }
        return ROLE_PERMISSIONS.getOrDefault(permission, Set.of()).contains(account.get().role());
    }

    @Override
    public void requirePermission(Permission permission) {
        if (!canAccess(permission)) { throw new AccessDeniedException(DENIED); }
    }

    @Override
    public boolean canAccessApartment(String soCanHo, Permission permission) {
        var account = currentAccount.get();
        if (account.isEmpty() || permission != PAYMENT_HISTORY_VIEW_OWN
                || soCanHo == null || soCanHo.isBlank() || ownership == null) {
            return false;
        }
        var principal = account.get();
        if (principal.role() != RESIDENT) { return false; }
        try {
            return ownership.isLinked(principal.userId(), soCanHo);
        } catch (RuntimeException unavailable) {
            // Never turn a resolver failure into permission, or expose its message/cause.
            return false;
        }
    }

    @Override
    public void requireApartmentPermission(String soCanHo, Permission permission) {
        if (!canAccessApartment(soCanHo, permission)) { throw new AccessDeniedException(DENIED); }
    }

    private static Map<Permission, Set<Role>> permissions() {
        var matrix = new EnumMap<Permission, Set<Role>>(Permission.class);
        var admin = Set.of(ADMIN);
        var staff = Set.of(ADMIN, ACCOUNTANT);
        matrix.put(ACCOUNT_VIEW, admin);
        matrix.put(ACCOUNT_CREATE, admin);
        matrix.put(ACCOUNT_LOCK, admin);
        matrix.put(ACCOUNT_UNLOCK, admin);
        matrix.put(ACCOUNT_ASSIGN_ROLE, admin);
        matrix.put(HOUSEHOLD_VIEW, staff);
        matrix.put(HOUSEHOLD_CREATE, admin);
        matrix.put(HOUSEHOLD_UPDATE, admin);
        matrix.put(RESIDENT_PROFILE_VIEW, staff);
        matrix.put(RESIDENT_PROFILE_CREATE, admin);
        matrix.put(RESIDENT_PROFILE_UPDATE, admin);
        matrix.put(FEE_CATEGORY_VIEW, staff);
        matrix.put(FEE_CATEGORY_CREATE, staff);
        matrix.put(FEE_CATEGORY_UPDATE, staff);
        matrix.put(FEE_RATE_VIEW, staff);
        matrix.put(FEE_RATE_CREATE, staff);
        matrix.put(FEE_RATE_UPDATE, staff);
        matrix.put(BILLING_PERIOD_CREATE, staff);
        matrix.put(PAYMENT_RECORD, Set.of(ACCOUNTANT));
        matrix.put(DEBT_REPORT_VIEW, staff);
        matrix.put(PAYMENT_HISTORY_VIEW_ALL, staff);
        // OWN is deliberately absent: it can never pass a role-only check.
        return Map.copyOf(matrix);
    }
}
