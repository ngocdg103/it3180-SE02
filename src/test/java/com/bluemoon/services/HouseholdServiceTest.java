package com.bluemoon.services;

import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.dao.ApartmentChargeLookup;
import com.bluemoon.dao.DuplicateRecordException;
import com.bluemoon.dao.HouseholdRepository;
import com.bluemoon.dao.InMemoryHouseholdRepository;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.HouseholdForm;
import com.bluemoon.models.HouseholdProfile;
import com.bluemoon.models.HouseholdUpdateForm;
import com.bluemoon.models.Role;
import com.bluemoon.services.HouseholdResult.Status;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Behaviour from EP-01 section 1 (fields, BR-01..BR-03, AC-01..AC-03) plus EP-06 role rules. */
class HouseholdServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), ZoneOffset.UTC);

    private InMemoryHouseholdRepository repository;
    private AtomicReference<Role> role;
    private AtomicReference<Boolean> billed;
    private HouseholdService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryHouseholdRepository();
        role = new AtomicReference<>(Role.ADMIN);
        billed = new AtomicReference<>(false);
        service = new HouseholdServiceImpl(repository, soCanHo -> billed.get(), role::get, CLOCK);
    }

    private static HouseholdForm valid() {
        return new HouseholdForm("P1204", "12", "85.0", "HK-1204", "2024-03-15", ApartmentStatus.DANG_O);
    }

    private HouseholdProfile created(HouseholdForm form) {
        var result = service.createHousehold(form);
        assertTrue(result.isSuccess(), result.toString());
        return result.data().orElseThrow();
    }

    // ---- AC-01 create ----

    @Test void createsValidHouseholdAndLinksItToItsApartment() {
        var result = service.createHousehold(valid());

        assertEquals(Status.SUCCESS, result.status());
        assertEquals("Tạo hồ sơ căn hộ P1204 thành công", result.message());
        var profile = result.data().orElseThrow();
        assertEquals("HK-1204", profile.maHo());
        assertEquals("P1204", profile.household().soCanHo());
        assertEquals(new BigDecimal("85.0"), profile.dienTich());
        assertEquals(12, profile.tang());
        // appears in list straight away and is readable both ways
        assertEquals(1, service.listHouseholds(null).data().orElseThrow().size());
        assertEquals("P1204", service.getHouseholdById("HK-1204").data().orElseThrow().soCanHo());
        assertEquals("HK-1204", service.getHouseholdByApartment("P1204").data().orElseThrow().maHo());
    }

    @Test void trimsInputWhitespace() {
        var form = new HouseholdForm(" P1204 ", " 12 ", " 85.5 ", " HK-1204 ", " 2024-03-15 ", ApartmentStatus.CHO_THUE);
        assertEquals("P1204", created(form).soCanHo());
    }

    @Test void emptyApartmentKeepsAreaBr03() {
        var form = new HouseholdForm("P3015", "30", "120", "HK-3015", "2025-02-01", ApartmentStatus.CAN_HO_TRONG);
        var profile = created(form);
        assertEquals(ApartmentStatus.CAN_HO_TRONG, profile.trangThai());
        assertEquals(0, new BigDecimal("120").compareTo(profile.dienTich()));
    }

    // ---- AC-02 duplicates / missing / invalid ----

    @Test void duplicateApartmentIsRejectedAndNothingIsSavedAc02() {
        created(valid());
        var again = new HouseholdForm("P1204", "12", "70", "HK-OTHER", "2024-03-15", ApartmentStatus.DANG_O);

        var result = service.createHousehold(again);

        assertEquals(Status.DUPLICATE, result.status());
        assertEquals("Số căn hộ đã tồn tại", result.fieldErrors().get("so_can_ho"));
        assertEquals(1, service.listHouseholds(null).data().orElseThrow().size());
        assertEquals(NOT_FOUND, service.getHouseholdById("HK-OTHER").status());
    }

    private static final Status NOT_FOUND = Status.NOT_FOUND;

    @Test void duplicateHouseholdCodeIsRejected() {
        created(valid());
        var other = new HouseholdForm("P1205", "12", "70", "HK-1204", "2024-03-15", ApartmentStatus.DANG_O);

        var result = service.createHousehold(other);

        assertEquals(Status.DUPLICATE, result.status());
        assertEquals("Mã hộ khẩu không được để trống hoặc bị trùng!", result.fieldErrors().get("ma_ho"));
        assertTrue(service.getHouseholdByApartment("P1205").status() == Status.NOT_FOUND);
    }

    @Test void everyMissingFieldIsReportedTogether() {
        var result = service.createHousehold(new HouseholdForm(null, " ", "", null, "", null));

        assertEquals(Status.VALIDATION_ERROR, result.status());
        assertEquals(List.of("so_can_ho", "ma_ho", "tang", "dien_tich", "ngay_nhan_nha", "trang_thai"),
                List.copyOf(result.fieldErrors().keySet()));
        assertEquals("Vui lòng chọn trạng thái căn hộ!", result.fieldErrors().get("trang_thai"));
        assertTrue(service.listHouseholds(null).data().orElseThrow().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1204", "p1204", "P120", "P12045", "PA204", "P 204", "Q1204", "P1204 x"})
    void rejectsBadApartmentNumber(String soCanHo) {
        var form = new HouseholdForm(soCanHo, "12", "85", "HK-1", "2024-03-15", ApartmentStatus.DANG_O);
        var result = service.createHousehold(form);
        assertEquals(Status.VALIDATION_ERROR, result.status());
        assertEquals("Số căn hộ không đúng định dạng hoặc đã tồn tại trên hệ thống!",
                result.fieldErrors().get("so_can_ho"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "31", "-1", "abc", "1.5", "", "99999999999"})
    void rejectsFloorOutside1To30(String tang) {
        var form = new HouseholdForm("P1204", tang, "85", "HK-1", "2024-03-15", ApartmentStatus.DANG_O);
        assertEquals("Tầng phải là số nguyên từ 1 đến 30!", service.createHousehold(form).fieldErrors().get("tang"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "30"})
    void acceptsFloorBoundaries(String tang) {
        var form = new HouseholdForm("P1204", tang, "85", "HK-1", "2024-03-15", ApartmentStatus.DANG_O);
        assertTrue(service.createHousehold(form).isSuccess());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "85.123", "abc", "", "1e3", "85,5", ".5", "1234567"})
    void rejectsNonPositiveOrMalformedArea(String dienTich) {
        var form = new HouseholdForm("P1204", "12", dienTich, "HK-1", "2024-03-15", ApartmentStatus.DANG_O);
        var result = service.createHousehold(form);
        assertEquals(Status.VALIDATION_ERROR, result.status());
        assertEquals("Diện tích phải là số dương lớn hơn 0!", result.fieldErrors().get("dien_tich"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "85", "85.5", "85.55", "999999.99"})
    void acceptsAreaWithUpToTwoDecimals(String dienTich) {
        var form = new HouseholdForm("P1204", "12", dienTich, "HK-1", "2024-03-15", ApartmentStatus.DANG_O);
        assertTrue(service.createHousehold(form).isSuccess());
    }

    @Test void rejectsFutureMoveInDateButAcceptsToday() {
        var tomorrow = new HouseholdForm("P1204", "12", "85", "HK-1", "2026-11-06", ApartmentStatus.DANG_O);
        assertEquals("Ngày nhận nhà không được lớn hơn ngày hiện tại!",
                service.createHousehold(tomorrow).fieldErrors().get("ngay_nhan_nha"));
        var today = new HouseholdForm("P1204", "12", "85", "HK-1", "2026-11-05", ApartmentStatus.DANG_O);
        assertTrue(service.createHousehold(today).isSuccess());
    }

    @ParameterizedTest
    @ValueSource(strings = {"15/03/2024", "2024-13-01", "2024-02-30", "20240315", "hôm nay"})
    void rejectsMalformedDate(String ngay) {
        var form = new HouseholdForm("P1204", "12", "85", "HK-1", ngay, ApartmentStatus.DANG_O);
        assertEquals("Ngày nhận nhà không đúng định dạng YYYY-MM-DD!",
                service.createHousehold(form).fieldErrors().get("ngay_nhan_nha"));
    }

    @Test void rejectsBlankOrTooLongHouseholdCode() {
        var blank = new HouseholdForm("P1204", "12", "85", "  ", "2024-03-15", ApartmentStatus.DANG_O);
        assertEquals(Status.VALIDATION_ERROR, service.createHousehold(blank).status());
        var tooLong = new HouseholdForm("P1204", "12", "85", "H".repeat(21), "2024-03-15", ApartmentStatus.DANG_O);
        assertEquals(Status.VALIDATION_ERROR, service.createHousehold(tooLong).status());
    }

    // ---- AC-03 update ----

    @Test void updatesStatusAndLeavesOtherDataUntouchedAc03() {
        created(new HouseholdForm("P1204", "12", "85.0", "HK-1204", "2024-03-15", ApartmentStatus.CAN_HO_TRONG));

        var result = service.updateHousehold("HK-1204",
                new HouseholdUpdateForm("12", "85.0", "2024-03-15", ApartmentStatus.DANG_O), false);

        assertEquals(Status.SUCCESS, result.status());
        var updated = result.data().orElseThrow();
        assertEquals(ApartmentStatus.DANG_O, updated.trangThai());
        assertEquals("P1204", updated.soCanHo());
        assertEquals("HK-1204", updated.maHo());
        assertEquals(LocalDate.parse("2024-03-15"), updated.ngayNhanNha());
        assertEquals(ApartmentStatus.DANG_O, service.getHouseholdById("HK-1204").data().orElseThrow().trangThai());
    }

    @Test void updatesAllEditableFields() {
        created(valid());
        var result = service.updateHousehold(" HK-1204 ",
                new HouseholdUpdateForm("13", "90.25", "2024-04-01", ApartmentStatus.CHO_THUE), false);
        var updated = result.data().orElseThrow();
        assertEquals(13, updated.tang());
        assertEquals(new BigDecimal("90.25"), updated.dienTich());
        assertEquals(LocalDate.parse("2024-04-01"), updated.ngayNhanNha());
        assertEquals(ApartmentStatus.CHO_THUE, updated.trangThai());
    }

    @Test void invalidUpdateIsRejectedAndOldDataIsKept() {
        created(valid());
        var result = service.updateHousehold("HK-1204",
                new HouseholdUpdateForm("40", "-1", "2999-01-01", null), false);
        assertEquals(Status.VALIDATION_ERROR, result.status());
        assertEquals(4, result.fieldErrors().size());
        var kept = service.getHouseholdById("HK-1204").data().orElseThrow();
        assertEquals(12, kept.tang());
        assertEquals(ApartmentStatus.DANG_O, kept.trangThai());
    }

    @Test void updateOfUnknownHouseholdIsNotFound() {
        var result = service.updateHousehold("HK-NOPE",
                new HouseholdUpdateForm("12", "85", "2024-03-15", ApartmentStatus.DANG_O), false);
        assertEquals(Status.NOT_FOUND, result.status());
    }

    // ---- BR-02 area change on a billed apartment ----

    @Test void areaChangeOnBilledApartmentNeedsConfirmation() {
        created(valid());
        billed.set(true);
        var change = new HouseholdUpdateForm("12", "90", "2024-03-15", ApartmentStatus.DANG_O);

        var first = service.updateHousehold("HK-1204", change, false);

        assertEquals(Status.CONFIRMATION_REQUIRED, first.status());
        assertEquals(0, new BigDecimal("85.0").compareTo(
                service.getHouseholdById("HK-1204").data().orElseThrow().dienTich()));
        var confirmed = service.updateHousehold("HK-1204", change, true);
        assertTrue(confirmed.isSuccess());
        assertEquals(0, new BigDecimal("90").compareTo(confirmed.data().orElseThrow().dienTich()));
    }

    @Test void sameAreaOrUnbilledApartmentNeedsNoConfirmation() {
        created(valid());
        billed.set(true);
        // same value written differently (85.0 vs 85.00): not an area change
        assertTrue(service.updateHousehold("HK-1204",
                new HouseholdUpdateForm("12", "85.00", "2024-03-15", ApartmentStatus.CHO_THUE), false).isSuccess());
        billed.set(false);
        assertTrue(service.updateHousehold("HK-1204",
                new HouseholdUpdateForm("12", "99", "2024-03-15", ApartmentStatus.CHO_THUE), false).isSuccess());
    }

    // ---- read ----

    @Test void unknownIdsAndEmptyListAreSoftResults() {
        assertEquals(Status.NOT_FOUND, service.getHouseholdById("HK-NOPE").status());
        assertEquals(Status.NOT_FOUND, service.getHouseholdByApartment("P9999").status());
        assertEquals(Status.NOT_FOUND, service.getHouseholdById(null).status());
        var list = service.listHouseholds(null);
        assertTrue(list.isSuccess());
        assertTrue(list.data().orElseThrow().isEmpty());
    }

    @Test void listIsSortedAndFilterableByStatus() {
        created(new HouseholdForm("P1502", "15", "95", "HK-1502", "2024-05-05", ApartmentStatus.DANG_O));
        created(new HouseholdForm("P0101", "1", "45.5", "HK-0101", "2023-06-01", ApartmentStatus.CHO_THUE));
        created(new HouseholdForm("P1002", "10", "75.5", "HK-1002", "2024-01-20", ApartmentStatus.DANG_O));

        var all = service.listHouseholds(null).data().orElseThrow();
        assertEquals(List.of("P0101", "P1002", "P1502"), all.stream().map(HouseholdProfile::soCanHo).toList());
        var occupied = service.listHouseholds(ApartmentStatus.DANG_O).data().orElseThrow();
        assertEquals(List.of("P1002", "P1502"), occupied.stream().map(HouseholdProfile::soCanHo).toList());
        assertThrows(UnsupportedOperationException.class, () -> all.add(all.get(0)));
    }

    // ---- roles (EP-06 BR-20) ----

    @Test void accountantCanReadButNotWrite() {
        created(valid());
        role.set(Role.ACCOUNTANT);
        assertTrue(service.listHouseholds(null).isSuccess());
        assertTrue(service.getHouseholdById("HK-1204").isSuccess());
        assertThrows(AccessDeniedException.class, () -> service.createHousehold(
                new HouseholdForm("P1205", "12", "85", "HK-1205", "2024-03-15", ApartmentStatus.DANG_O)));
        assertThrows(AccessDeniedException.class, () -> service.updateHousehold("HK-1204",
                new HouseholdUpdateForm("12", "85", "2024-03-15", ApartmentStatus.CHO_THUE), false));
        role.set(Role.ADMIN);
        assertEquals(1, service.listHouseholds(null).data().orElseThrow().size());
        assertEquals(ApartmentStatus.DANG_O, service.getHouseholdById("HK-1204").data().orElseThrow().trangThai());
    }

    @Test void residentIsDeniedEverything() {
        role.set(Role.RESIDENT);
        assertThrows(AccessDeniedException.class, () -> service.listHouseholds(null));
        assertThrows(AccessDeniedException.class, () -> service.getHouseholdById("HK-1204"));
        assertThrows(AccessDeniedException.class, () -> service.getHouseholdByApartment("P1204"));
        assertThrows(AccessDeniedException.class, () -> service.createHousehold(valid()));
    }

    @Test void noSessionIsDeniedAndNothingIsWritten() {
        Supplier<Role> noSession = () -> { throw new IllegalStateException("No authenticated session"); };
        var guarded = new HouseholdServiceImpl(repository, ApartmentChargeLookup.NONE, noSession, CLOCK);
        assertThrows(AccessDeniedException.class, () -> guarded.createHousehold(valid()));
        assertTrue(service.listHouseholds(null).data().orElseThrow().isEmpty());
    }

    // ---- database failure ----

    @Test void databaseFailureBecomesGenericSystemError() {
        var broken = new HouseholdServiceImpl(new FailingRepository(), ApartmentChargeLookup.NONE, role::get, CLOCK);
        for (var result : List.of(
                broken.createHousehold(valid()),
                broken.updateHousehold("HK-1204", new HouseholdUpdateForm("12", "85", "2024-03-15",
                        ApartmentStatus.DANG_O), false),
                broken.getHouseholdById("HK-1204"),
                broken.getHouseholdByApartment("P1204"),
                broken.listHouseholds(null))) {
            assertEquals(Status.SYSTEM_ERROR, result.status());
            assertFalse(result.message().contains("secret"), "SQL details must not leak");
        }
    }

    @Test void chargeLookupFailureIsSystemError() {
        created(valid());
        var failingLookup = new HouseholdServiceImpl(repository, soCanHo -> { throw new SQLException("secret"); },
                role::get, CLOCK);
        var result = failingLookup.updateHousehold("HK-1204",
                new HouseholdUpdateForm("12", "99", "2024-03-15", ApartmentStatus.DANG_O), false);
        assertEquals(Status.SYSTEM_ERROR, result.status());
        assertEquals(0, new BigDecimal("85.0").compareTo(repository.findByMaHo("HK-1204").orElseThrow().dienTich()));
    }

    @Test void resultsExposeNoDataOnFailureAndAreImmutable() {
        var failure = service.getHouseholdById("HK-NOPE");
        assertEquals(Optional.empty(), failure.data());
        assertThrows(UnsupportedOperationException.class, () -> failure.fieldErrors().put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> HouseholdResult.failure(Status.SUCCESS, "x", java.util.Map.of()));
    }

    private static final class FailingRepository implements HouseholdRepository {
        @Override public void insert(HouseholdProfile p) throws SQLException, DuplicateRecordException {
            throw new SQLException("secret connection string");
        }
        @Override public Optional<HouseholdProfile> findByMaHo(String maHo) throws SQLException {
            throw new SQLException("secret");
        }
        @Override public Optional<HouseholdProfile> findBySoCanHo(String soCanHo) throws SQLException {
            throw new SQLException("secret");
        }
        @Override public List<HouseholdProfile> findAll(ApartmentStatus status) throws SQLException {
            throw new SQLException("secret");
        }
        @Override public boolean update(String maHo, int tang, BigDecimal dienTich, LocalDate ngay,
                                        ApartmentStatus trangThai) throws SQLException {
            throw new SQLException("secret");
        }
    }
}
