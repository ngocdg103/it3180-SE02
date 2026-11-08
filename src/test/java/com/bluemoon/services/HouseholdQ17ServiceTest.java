package com.bluemoon.services;

import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class HouseholdQ17ServiceTest {
    private final Boundary repository = new Boundary();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC);
    private HouseholdService service;

    @BeforeEach void setup() throws Exception {
        repository.store.insert(new HouseholdProfile(new Apartment("P1701", 17, new BigDecimal("80"), ApartmentStatus.DANG_O),
                new Household("HK-1701", "P1701", LocalDate.of(2024, 1, 1))));
        service = new HouseholdServiceImpl(repository, ApartmentChargeLookup.NONE, HouseholdTestAuthorization.admin(), clock);
    }

    @Test void domainRefusalMapsToStatusFieldAndLeavesEveryFieldUnchanged() {
        var before = repository.store.findByMaHo("HK-1701");
        repository.occupied = true;
        var result = service.updateHousehold("HK-1701", form(ApartmentStatus.CAN_HO_TRONG), true);
        assertEquals(HouseholdResult.Status.VALIDATION_ERROR, result.status());
        assertTrue(result.fieldErrors().containsKey("trang_thai"));
        assertTrue(result.data().isEmpty());
        assertEquals(before, repository.store.findByMaHo("HK-1701"));
    }

    @Test void noResidentsAllowsVacantAndOtherStatusesRemainAllowed() {
        assertTrue(service.updateHousehold("HK-1701", form(ApartmentStatus.CAN_HO_TRONG), false).isSuccess());
        repository.occupied = true;
        for (var status : new ApartmentStatus[] {ApartmentStatus.DANG_O, ApartmentStatus.CHO_THUE}) {
            assertTrue(service.updateHousehold("HK-1701", form(status), false).isSuccess());
        }
    }

    @Test void areaConfirmationDoesNotBypassQ17() {
        repository.occupied = true;
        service = new HouseholdServiceImpl(repository, apartment -> true, HouseholdTestAuthorization.admin(), clock);
        assertEquals(HouseholdResult.Status.CONFIRMATION_REQUIRED,
                service.updateHousehold("HK-1701", form(ApartmentStatus.CAN_HO_TRONG), false).status());
        assertEquals(HouseholdResult.Status.VALIDATION_ERROR,
                service.updateHousehold("HK-1701", form(ApartmentStatus.CAN_HO_TRONG), true).status());
    }

    @Test void sqlFailureStillMapsToGenericSystemError() {
        repository.fail = true;
        var result = service.updateHousehold("HK-1701", form(ApartmentStatus.CAN_HO_TRONG), true);
        assertEquals(HouseholdResult.Status.SYSTEM_ERROR, result.status());
        assertFalse(result.message().contains("private"));
    }

    private HouseholdUpdateForm form(ApartmentStatus status) {
        return new HouseholdUpdateForm("18", "90", "2024-02-01", status);
    }

    /** Simulates the DAO's atomic outcome only; concurrency is verified against real MySQL separately. */
    private static final class Boundary implements HouseholdRepository {
        final InMemoryHouseholdRepository store = new InMemoryHouseholdRepository();
        boolean occupied;
        boolean fail;
        public void insert(HouseholdProfile profile) throws DuplicateRecordException { store.insert(profile); }
        public Optional<HouseholdProfile> findByMaHo(String id) { return store.findByMaHo(id); }
        public Optional<HouseholdProfile> findBySoCanHo(String id) { return store.findBySoCanHo(id); }
        public List<HouseholdProfile> findAll(ApartmentStatus status) { return store.findAll(status); }
        public boolean update(String id, int floor, BigDecimal area, LocalDate date, ApartmentStatus status) throws SQLException {
            if (fail) { throw new SQLException("private database detail"); }
            if (occupied && status == ApartmentStatus.CAN_HO_TRONG) { throw new HouseholdHasResidentsException(); }
            return store.update(id, floor, area, date, status);
        }
    }
}
