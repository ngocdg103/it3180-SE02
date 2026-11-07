package com.bluemoon.services;

import com.bluemoon.dao.ApartmentChargeLookup;
import com.bluemoon.dao.DuplicateRecordException;
import com.bluemoon.dao.HouseholdRepository;
import com.bluemoon.dao.InMemoryHouseholdRepository;
import com.bluemoon.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Actual HouseholdService + actual RBAC, with observable in-memory persistence. */
class HouseholdAuthorizationIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC);
    private final CountingRepository repository = new CountingRepository();
    private final AtomicInteger chargeChecks = new AtomicInteger();

    @BeforeEach void setup() throws Exception {
        SessionContext.clear();
        repository.delegate.insert(new HouseholdProfile(
                new Apartment("P1204", 12, new BigDecimal("85.0"), ApartmentStatus.DANG_O),
                new Household("HK-1204", "P1204", LocalDate.parse("2024-03-15"))));
    }

    @AfterEach void logout() { SessionContext.clear(); }

    @ParameterizedTest
    @CsvSource({
            "CREATE, ADMIN, true", "CREATE, ACCOUNTANT, false", "CREATE, RESIDENT, false", "CREATE, NONE, false",
            "UPDATE, ADMIN, true", "UPDATE, ACCOUNTANT, false", "UPDATE, RESIDENT, false", "UPDATE, NONE, false",
            "GET_BY_ID, ADMIN, true", "GET_BY_ID, ACCOUNTANT, true", "GET_BY_ID, RESIDENT, false", "GET_BY_ID, NONE, false",
            "GET_BY_APARTMENT, ADMIN, true", "GET_BY_APARTMENT, ACCOUNTANT, true", "GET_BY_APARTMENT, RESIDENT, false", "GET_BY_APARTMENT, NONE, false",
            "LIST, ADMIN, true", "LIST, ACCOUNTANT, true", "LIST, RESIDENT, false", "LIST, NONE, false"
    })
    void everyEntryPointUsesApprovedPermissionAndStopsDaoWhenDenied(Operation operation, String role, boolean allowed) {
        if (!role.equals("NONE")) { login(Role.valueOf(role)); }
        var guard = new RecordingAuthorization(new RoleBasedAuthorizationService(null));
        var service = service(guard);
        if (allowed) {
            assertTrue(operation.invoke(service).isSuccess());
            assertTrue(repository.calls > 0);
        } else {
            assertThrows(AccessDeniedException.class, () -> operation.invoke(service));
            assertEquals(0, repository.calls);
            assertEquals(0, chargeChecks.get());
        }
        assertEquals(1, guard.checks);
        assertEquals(operation.permission, guard.last);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void guardPrecedesValidationEvenWithNullInput(Operation operation) {
        login(Role.RESIDENT);
        var service = service(new RoleBasedAuthorizationService(null));
        assertThrows(AccessDeniedException.class, () -> operation.invokeInvalid(service));
        assertEquals(0, repository.calls);
        assertEquals(0, chargeChecks.get());
    }

    @Test void defaultConstructorUsesCurrentSessionWithoutCachingOrStaleUiPermission() {
        HouseholdService service = new HouseholdServiceImpl(repository, ApartmentChargeLookup.NONE);
        assertThrows(AccessDeniedException.class, () -> service.listHouseholds(null));
        login(Role.ADMIN);
        assertTrue(service.createHousehold(createForm()).isSuccess());
        login(Role.ACCOUNTANT);
        assertTrue(service.getHouseholdById("HK-1204").isSuccess());
        repository.calls = 0;
        assertThrows(AccessDeniedException.class, () -> service.updateHousehold("HK-1204", updateForm(), false));
        assertEquals(0, repository.calls);
        var uiAuthorization = new RoleBasedAuthorizationService(null);
        assertTrue(uiAuthorization.canAccess(Permission.HOUSEHOLD_VIEW));
        SessionContext.clear(); // Same transition that AuthService.logout performs.
        assertThrows(AccessDeniedException.class, () -> service.listHouseholds(null));
        assertEquals(0, repository.calls);
        login(Role.RESIDENT);
        assertThrows(AccessDeniedException.class, () -> service.getHouseholdByApartment("P1204"));
        assertEquals(0, repository.calls);
        login(Role.ADMIN);
        assertTrue(service.updateHousehold("HK-1204", updateForm(), false).isSuccess());
    }

    @Test void residentOwnershipNeverGrantsHouseholdProfileAccess() {
        login(Role.RESIDENT);
        var resolutions = new AtomicInteger();
        var authorization = new RoleBasedAuthorizationService((id, apartment) -> {
            resolutions.incrementAndGet();
            return true;
        });
        assertTrue(authorization.canAccessApartment("P1204", Permission.PAYMENT_HISTORY_VIEW_OWN));
        resolutions.set(0);
        HouseholdService service = service(authorization);
        assertThrows(AccessDeniedException.class, () -> service.getHouseholdByApartment("P1204"));
        assertEquals(0, repository.calls);
        assertEquals(0, resolutions.get());
    }

    @Test void deniedUpdateDoesNotAlterExistingData() throws Exception {
        login(Role.ACCOUNTANT);
        var before = repository.delegate.findByMaHo("HK-1204");
        assertThrows(AccessDeniedException.class,
                () -> service(new RoleBasedAuthorizationService(null)).updateHousehold("HK-1204", updateForm(), true));
        assertEquals(before, repository.delegate.findByMaHo("HK-1204"));
        assertEquals(0, repository.calls);
        assertEquals(0, chargeChecks.get());
    }

    @Test void missingAuthorizationDependencyCannotConstructService() {
        assertThrows(NullPointerException.class,
                () -> new HouseholdServiceImpl(repository, ApartmentChargeLookup.NONE, null, CLOCK));
    }

    private HouseholdServiceImpl service(AuthorizationService authorization) {
        return new HouseholdServiceImpl(repository, apartment -> {
            chargeChecks.incrementAndGet();
            return false;
        }, authorization, CLOCK);
    }

    private static void login(Role role) {
        SessionContext.setCurrentUser(new Account(7, "testuser", "Test User", role));
    }

    private static HouseholdForm createForm() {
        return new HouseholdForm("P1205", "12", "85.0", "HK-1205", "2024-03-15", ApartmentStatus.DANG_O);
    }

    private static HouseholdUpdateForm updateForm() {
        return new HouseholdUpdateForm("12", "90.0", "2024-03-15", ApartmentStatus.CHO_THUE);
    }

    enum Operation {
        CREATE(Permission.HOUSEHOLD_CREATE), UPDATE(Permission.HOUSEHOLD_UPDATE),
        GET_BY_ID(Permission.HOUSEHOLD_VIEW), GET_BY_APARTMENT(Permission.HOUSEHOLD_VIEW), LIST(Permission.HOUSEHOLD_VIEW);
        final Permission permission;
        Operation(Permission permission) { this.permission = permission; }
        HouseholdResult<?> invoke(HouseholdService service) {
            return switch (this) {
                case CREATE -> service.createHousehold(createForm());
                case UPDATE -> service.updateHousehold("HK-1204", updateForm(), false);
                case GET_BY_ID -> service.getHouseholdById("HK-1204");
                case GET_BY_APARTMENT -> service.getHouseholdByApartment("P1204");
                case LIST -> service.listHouseholds(null);
            };
        }
        HouseholdResult<?> invokeInvalid(HouseholdService service) {
            return switch (this) {
                case CREATE -> service.createHousehold(null);
                case UPDATE -> service.updateHousehold(null, null, false);
                case GET_BY_ID -> service.getHouseholdById(null);
                case GET_BY_APARTMENT -> service.getHouseholdByApartment(null);
                case LIST -> service.listHouseholds(null);
            };
        }
    }

    private static final class RecordingAuthorization implements AuthorizationService {
        private final AuthorizationService delegate;
        private int checks;
        private Permission last;
        RecordingAuthorization(AuthorizationService delegate) { this.delegate = delegate; }
        public boolean canAccess(Permission permission) { return delegate.canAccess(permission); }
        public void requirePermission(Permission permission) {
            checks++;
            last = permission;
            delegate.requirePermission(permission);
        }
        public boolean canAccessApartment(String id, Permission permission) { return delegate.canAccessApartment(id, permission); }
        public void requireApartmentPermission(String id, Permission permission) { delegate.requireApartmentPermission(id, permission); }
    }

    private static final class CountingRepository implements HouseholdRepository {
        final InMemoryHouseholdRepository delegate = new InMemoryHouseholdRepository();
        int calls;
        public void insert(HouseholdProfile profile) throws SQLException, DuplicateRecordException {
            calls++; delegate.insert(profile);
        }
        public Optional<HouseholdProfile> findByMaHo(String id) throws SQLException {
            calls++; return delegate.findByMaHo(id);
        }
        public Optional<HouseholdProfile> findBySoCanHo(String apartment) throws SQLException {
            calls++; return delegate.findBySoCanHo(apartment);
        }
        public List<HouseholdProfile> findAll(ApartmentStatus status) throws SQLException {
            calls++; return delegate.findAll(status);
        }
        public boolean update(String id, int floor, BigDecimal area, LocalDate date, ApartmentStatus status) throws SQLException {
            calls++; return delegate.update(id, floor, area, date, status);
        }
    }
}
