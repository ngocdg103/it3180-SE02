package com.bluemoon.services;

import com.bluemoon.models.Account;
import com.bluemoon.models.Permission;
import com.bluemoon.models.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.bluemoon.models.Permission.*;
import static com.bluemoon.models.Role.*;
import static org.junit.jupiter.api.Assertions.*;

class RoleBasedAuthorizationServiceTest {
    // Expected values are independently transcribed from the approved SRS matrix.
    // Columns: permission, ADMIN, ACCOUNTANT, RESIDENT (role-only access).
    private static final Object[][] MATRIX = {
            {ACCOUNT_VIEW, true, false, false},
            {ACCOUNT_CREATE, true, false, false},
            {ACCOUNT_LOCK, true, false, false},
            {ACCOUNT_UNLOCK, true, false, false},
            {ACCOUNT_ASSIGN_ROLE, true, false, false},
            {HOUSEHOLD_VIEW, true, true, false},
            {HOUSEHOLD_CREATE, true, false, false},
            {HOUSEHOLD_UPDATE, true, false, false},
            {RESIDENT_PROFILE_VIEW, true, true, false},
            {RESIDENT_PROFILE_CREATE, true, false, false},
            {RESIDENT_PROFILE_UPDATE, true, false, false},
            {FEE_CATEGORY_VIEW, true, true, false},
            {FEE_CATEGORY_CREATE, true, true, false},
            {FEE_CATEGORY_UPDATE, true, true, false},
            {FEE_RATE_VIEW, true, true, false},
            {FEE_RATE_CREATE, true, true, false},
            {FEE_RATE_UPDATE, true, true, false},
            {BILLING_PERIOD_CREATE, true, true, false},
            {PAYMENT_RECORD, false, true, false},
            {DEBT_REPORT_VIEW, true, true, false},
            {PAYMENT_HISTORY_VIEW_ALL, true, true, false},
            {PAYMENT_HISTORY_VIEW_OWN, false, false, false}
    };

    @AfterEach void clearSession() { SessionContext.clear(); }

    static Stream<Arguments> matrixCases() {
        return Arrays.stream(MATRIX).flatMap(row -> Stream.of(
                Arguments.of(row[0], ADMIN, row[1]),
                Arguments.of(row[0], ACCOUNTANT, row[2]),
                Arguments.of(row[0], RESIDENT, row[3])));
    }

    @Test void expectedMatrixCoversExactlyAllTwentyTwoPermissions() {
        assertEquals(22, Permission.values().length);
        assertEquals(Permission.values().length, MATRIX.length);
        assertEquals(Permission.values().length, Arrays.stream(MATRIX).map(row -> row[0]).distinct().count());
    }

    @ParameterizedTest(name = "{0}, {1}: role-only={2}")
    @MethodSource("matrixCases")
    void allPermissionsAcrossAllRoles(Permission permission, Role role, boolean expected) {
        var auth = forRole(role, (userId, apartment) -> true);
        assertEquals(expected, auth.canAccess(permission));
        if (expected) { assertDoesNotThrow(() -> auth.requirePermission(permission)); }
        else { assertThrows(AccessDeniedException.class, () -> auth.requirePermission(permission)); }
        boolean scoped = permission == PAYMENT_HISTORY_VIEW_OWN && role == RESIDENT;
        assertEquals(scoped, auth.canAccessApartment("P1002", permission));
        if (scoped) { assertDoesNotThrow(() -> auth.requireApartmentPermission("P1002", permission)); }
        else { assertThrows(AccessDeniedException.class, () -> auth.requireApartmentPermission("P1002", permission)); }
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    void missingSessionDeniesEveryPermissionWithoutCallingResolver(Permission permission) {
        var auth = new RoleBasedAuthorizationService(Optional::empty, (id, apartment) -> {
            fail("No resolver call is allowed without a session");
            return true;
        });
        assertFalse(auth.canAccess(permission));
        assertFalse(auth.canAccessApartment("P1002", permission));
        assertThrows(AccessDeniedException.class, () -> auth.requirePermission(permission));
        assertThrows(AccessDeniedException.class, () -> auth.requireApartmentPermission("P1002", permission));
    }

    @Test void residentScopeUsesTrustedUserIdAndExactApartmentAndCannotBypassOwnership() {
        var calls = new AtomicInteger();
        var principal = new AtomicReference<>(Optional.of(account(7, RESIDENT)));
        var auth = new RoleBasedAuthorizationService(principal::get, (id, apartment) -> {
            calls.incrementAndGet();
            return id == 7 && apartment.equals("P1002");
        });
        assertFalse(auth.canAccess(PAYMENT_HISTORY_VIEW_OWN));
        assertFalse(auth.canAccess(PAYMENT_HISTORY_VIEW_ALL));
        assertEquals(0, calls.get());
        assertTrue(auth.canAccessApartment("P1002", PAYMENT_HISTORY_VIEW_OWN));
        assertFalse(auth.canAccessApartment("P1003", PAYMENT_HISTORY_VIEW_OWN));
        assertFalse(auth.canAccessApartment(" P1002 ", PAYMENT_HISTORY_VIEW_OWN));
        principal.set(Optional.of(account(8, RESIDENT)));
        assertFalse(auth.canAccessApartment("P1002", PAYMENT_HISTORY_VIEW_OWN));
    }

    @Test void absentResolverAndMissingLinkDenyWithMatchingGuard() {
        for (ApartmentOwnershipResolver resolver : new ApartmentOwnershipResolver[] {null, (id, apartment) -> false}) {
            var auth = forRole(RESIDENT, resolver);
            assertFalse(auth.canAccessApartment("P1002", PAYMENT_HISTORY_VIEW_OWN));
            assertThrows(AccessDeniedException.class,
                    () -> auth.requireApartmentPermission("P1002", PAYMENT_HISTORY_VIEW_OWN));
        }
        assertTrue(forRole(ADMIN, null).canAccess(ACCOUNT_VIEW));
    }

    @Test void resolverFailureDeniesAndDoesNotLeakSensitiveDetailsOrCause() {
        var auth = forRole(RESIDENT, (id, apartment) -> {
            throw new IllegalStateException("private-sql-credential-detail");
        });
        assertFalse(auth.canAccessApartment("P1002", PAYMENT_HISTORY_VIEW_OWN));
        var denied = assertThrows(AccessDeniedException.class,
                () -> auth.requireApartmentPermission("P1002", PAYMENT_HISTORY_VIEW_OWN));
        assertEquals("Bạn không có quyền thực hiện chức năng này.", denied.getMessage());
        assertNull(denied.getCause());
    }

    @Test void nullPermissionAndNullOrBlankApartmentAlwaysDeny() {
        var auth = forRole(RESIDENT, (id, apartment) -> {
            fail("Invalid arguments must not reach the resolver");
            return true;
        });
        assertFalse(auth.canAccess(null));
        assertThrows(AccessDeniedException.class, () -> auth.requirePermission(null));
        assertFalse(auth.canAccessApartment("P1002", null));
        assertThrows(AccessDeniedException.class, () -> auth.requireApartmentPermission("P1002", null));
        for (String apartment : new String[] {null, "", " ", "\t\n"}) {
            assertFalse(auth.canAccessApartment(apartment, PAYMENT_HISTORY_VIEW_OWN));
            assertThrows(AccessDeniedException.class,
                    () -> auth.requireApartmentPermission(apartment, PAYMENT_HISTORY_VIEW_OWN));
        }
    }

    @Test void roleOnlyPermissionsNeverConsultOwnership() {
        var auth = forRole(ADMIN, (id, apartment) -> {
            fail("Role-only permissions must not consult ownership");
            return true;
        });
        for (Permission permission : Permission.values()) {
            assertDoesNotThrow(() -> auth.canAccess(permission));
            assertFalse(auth.canAccessApartment("P1002", permission));
        }
    }

    @Test void productionConstructorReadsRealSessionAfterEveryRoleChangeAndLogout() {
        SessionContext.clear();
        var auth = new RoleBasedAuthorizationService((id, apartment) -> id == 7 && apartment.equals("P1002"));
        assertFalse(auth.canAccess(ACCOUNT_VIEW));
        SessionContext.setCurrentUser(account(1, ADMIN));
        assertTrue(auth.canAccess(ACCOUNT_VIEW));
        assertFalse(auth.canAccess(PAYMENT_RECORD));
        SessionContext.setCurrentUser(account(2, ACCOUNTANT));
        assertFalse(auth.canAccess(ACCOUNT_VIEW));
        assertTrue(auth.canAccess(PAYMENT_RECORD));
        SessionContext.setCurrentUser(account(7, RESIDENT));
        assertTrue(auth.canAccessApartment("P1002", PAYMENT_HISTORY_VIEW_OWN));
        SessionContext.clear();
        assertFalse(auth.canAccess(PAYMENT_RECORD));
        assertFalse(auth.canAccessApartment("P1002", PAYMENT_HISTORY_VIEW_OWN));
        assertThrows(AccessDeniedException.class,
                () -> auth.requireApartmentPermission("P1002", PAYMENT_HISTORY_VIEW_OWN));
    }

    @Test void roleGuardStopsFakeDaoOperationEvenIfUiAllowsButton() {
        var writes = new AtomicInteger();
        Runnable recordAsAdmin = () -> {
            forRole(ADMIN, null).requirePermission(PAYMENT_RECORD);
            writes.incrementAndGet();
        };
        assertThrows(AccessDeniedException.class, recordAsAdmin::run);
        assertEquals(0, writes.get());
        forRole(ACCOUNTANT, null).requirePermission(PAYMENT_RECORD);
        writes.incrementAndGet();
        assertEquals(1, writes.get());
    }

    @Test void ownershipGuardStopsFakeDaoReadOnForeignApartmentOrResolverFailure() {
        var reads = new AtomicInteger();
        var auth = forRole(RESIDENT, (id, apartment) -> apartment.equals("P1002"));
        Runnable foreignRead = () -> {
            auth.requireApartmentPermission("P1003", PAYMENT_HISTORY_VIEW_OWN);
            reads.incrementAndGet();
        };
        assertThrows(AccessDeniedException.class, foreignRead::run);
        assertEquals(0, reads.get());
        auth.requireApartmentPermission("P1002", PAYMENT_HISTORY_VIEW_OWN);
        reads.incrementAndGet();
        assertEquals(1, reads.get());
    }

    private static RoleBasedAuthorizationService forRole(Role role, ApartmentOwnershipResolver resolver) {
        return new RoleBasedAuthorizationService(() -> Optional.of(account(7, role)), resolver);
    }

    private static Account account(long id, Role role) {
        return new Account(id, "testuser", "Test User", role);
    }
}
