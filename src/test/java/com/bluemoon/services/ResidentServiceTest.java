package com.bluemoon.services;

import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.dao.InMemoryHouseholdRepository;
import com.bluemoon.dao.InMemoryResidentRepository;
import com.bluemoon.dao.ResidentDuplicateException;
import com.bluemoon.dao.ResidentRepository;
import com.bluemoon.models.Account;
import com.bluemoon.models.Apartment;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.Gender;
import com.bluemoon.models.Household;
import com.bluemoon.models.HouseholdProfile;
import com.bluemoon.models.RelationToHead;
import com.bluemoon.models.Resident;
import com.bluemoon.models.ResidentForm;
import com.bluemoon.models.Role;
import com.bluemoon.services.ResidentResult.ConfirmationReason;
import com.bluemoon.services.ResidentResult.Status;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** EP-01 section 2 (fields, BR-04, AC-01..AC-03), the PO decisions, and EP-06 role rules. */
class ResidentServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneOffset.UTC);

    private InMemoryHouseholdRepository households;
    private InMemoryResidentRepository residents;
    private AtomicReference<Optional<Role>> role;
    private ResidentService service;

    @BeforeEach
    void setUp() throws Exception {
        households = new InMemoryHouseholdRepository();
        add("P1002", "HK-1002", ApartmentStatus.DANG_O);
        add("P1204", "HK-1204", ApartmentStatus.CAN_HO_TRONG);
        add("P0101", "HK-0101", ApartmentStatus.CHO_THUE);
        residents = new InMemoryResidentRepository(households);
        role = new AtomicReference<>(Optional.of(Role.ADMIN));
        service = new ResidentServiceImpl(residents, households, authorization(), CLOCK);
    }

    private void add(String soCanHo, String maHo, ApartmentStatus status) throws Exception {
        households.insert(new HouseholdProfile(new Apartment(soCanHo, 10, new BigDecimal("60"), status),
                new Household(maHo, soCanHo, LocalDate.parse("2024-01-20"))));
    }

    private AuthorizationService authorization() {
        return new RoleBasedAuthorizationService(
                () -> role.get().map(r -> new Account(1, "testuser", "Test User", r)), null);
    }

    private static ResidentForm head(String hoTen, String cccd) {
        return new ResidentForm("HK-1002", hoTen, "1990-05-12", Gender.NAM, cccd, RelationToHead.CHU_HO, "2024-01-20");
    }

    private static ResidentForm member(String hoTen, String birth, String cccd, RelationToHead relation) {
        return new ResidentForm("HK-1002", hoTen, birth, Gender.NU, cccd, relation, "2024-01-20");
    }

    private Resident created(ResidentForm form) {
        var result = service.createForHousehold(form, null);
        assertTrue(result.isSuccess(), result.toString());
        return result.data().orElseThrow();
    }

    private void assertFieldError(ResidentResult<?> result, Status status, String field, String message) {
        assertEquals(status, result.status(), result.toString());
        assertEquals(message, result.fieldErrors().get(field));
    }

    // ---- AC-01 / first member / count ----

    @Test void ac01AddsFirstHeadAndHouseholdShowsHimWithCount() {
        var result = service.createForHousehold(head("Nguyễn Văn A", "001090000001"), null);
        assertTrue(result.isSuccess());
        assertTrue(result.message().contains("thành công"));
        var members = service.getHousehold("HK-1002").data().orElseThrow();
        assertEquals(1, members.memberCount());
        assertEquals("Nguyễn Văn A", members.head().orElseThrow().hoTen());
        assertTrue(result.data().orElseThrow().maNhanKhau() > 0);
    }

    @Test void firstMemberOfAnEmptyHouseholdMustBeTheHead() throws Exception {
        var result = service.createForHousehold(member("Trần Thị B", "1992-08-30", "001092000002", RelationToHead.VO), null);
        assertFieldError(result, Status.VALIDATION_ERROR, "quan_he_chu_ho", ResidentServiceImpl.MSG_FIRST_HEAD);
        assertEquals(0, residents.countByMaHo("HK-1002"));
    }

    @Test void afterTheHeadAnyRelationCanBeAddedAndCountGrows() throws Exception {
        created(head("Nguyễn Văn A", "001090000001"));
        created(member("Trần Thị B", "1992-08-30", "001092000002", RelationToHead.VO));
        created(member("Nguyễn Minh C", "2018-03-01", null, RelationToHead.CON));
        assertEquals(3, residents.countByMaHo("HK-1002"));
        assertEquals(RelationToHead.CHU_HO, service.getHousehold("HK-1002").data().orElseThrow().members().get(0).quanHeChuHo());
        assertEquals(0, residents.countByMaHo("HK-0101"));
    }

    // ---- household / vacant ----

    @Test void missingHouseholdChoiceIsAValidationError() {
        var form = new ResidentForm("  ", "Nguyễn Văn A", "1990-05-12", Gender.NAM, "001090000001",
                RelationToHead.CHU_HO, "2024-01-20");
        assertFieldError(service.createForHousehold(form, null), Status.VALIDATION_ERROR, "ma_ho",
                ResidentServiceImpl.MSG_MA_HO);
    }

    @Test void unknownHouseholdIsNotFoundAndNothingIsSaved() throws Exception {
        var form = new ResidentForm("HK-9999", "Nguyễn Văn A", "1990-05-12", Gender.NAM, "001090000001",
                RelationToHead.CHU_HO, "2024-01-20");
        assertEquals(Status.NOT_FOUND, service.createForHousehold(form, null).status());
        assertEquals(0, residents.countByMaHo("HK-9999"));
    }

    @Test void vacantApartmentRefusesResidentsAndKeepsItsStatus() throws Exception {
        var form = new ResidentForm("HK-1204", "Nguyễn Văn A", "1990-05-12", Gender.NAM, "001090000001",
                RelationToHead.CHU_HO, "2024-01-20");
        assertFieldError(service.createForHousehold(form, null), Status.VALIDATION_ERROR, "ma_ho",
                ResidentServiceImpl.MSG_VACANT);
        assertEquals(0, residents.countByMaHo("HK-1204"));
        assertEquals(ApartmentStatus.CAN_HO_TRONG, households.findByMaHo("HK-1204").orElseThrow().trangThai());
    }

    @Test void rentedApartmentAcceptsAResidentWithoutAnyRelationRestriction() {
        var form = new ResidentForm("HK-0101", "Lê Văn D", "1985-11-02", Gender.NAM, "001085000003",
                RelationToHead.CHU_HO, "2024-01-20");
        assertTrue(service.createForHousehold(form, null).isSuccess());
    }

    // ---- name ----

    @Test void nameIsTrimmedAndWhitespaceCollapsed() {
        assertEquals("Nguyễn Văn A", created(head("   Nguyễn \t Văn   A  ", "001090000001")).hoTen());
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "A", "Nguyen 2", "Nguyen@A", "Nguyen-A", "O'Neil", "1234",
            "Một hai ba bốn năm sáu bảy tám chín mười một hai ba bốn"})
    void invalidNamesAreRejected(String name) {
        assertFieldError(service.createForHousehold(head(name, "001090000001"), null), Status.VALIDATION_ERROR,
                "ho_ten", ResidentServiceImpl.MSG_HO_TEN);
    }

    @Test void nameLengthBoundaries() {
        assertTrue(service.createForHousehold(head("Lê", "001090000001"), null).isSuccess());
        assertEquals(Status.VALIDATION_ERROR,
                service.createForHousehold(member("A".repeat(51), "1991-01-01", "001091000009", RelationToHead.VO), null).status());
        assertTrue(service.createForHousehold(
                member("A".repeat(50), "1991-01-01", "001091000009", RelationToHead.VO), null).isSuccess());
    }

    // ---- dates ----

    @Test void birthDateMustBeBeforeToday() {
        var today = new ResidentForm("HK-1002", "Nguyễn Văn A", "2026-11-07", Gender.NAM, null,
                RelationToHead.CHU_HO, "2026-11-07");
        assertFieldError(service.createForHousehold(today, null), Status.VALIDATION_ERROR, "ngay_sinh",
                ResidentServiceImpl.MSG_NGAY_SINH);
        var yesterday = new ResidentForm("HK-1002", "Nguyễn Văn A", "2026-11-06", Gender.NAM, null,
                RelationToHead.CHU_HO, "2026-11-07");
        assertTrue(service.createForHousehold(yesterday, null).isSuccess());
    }

    @ParameterizedTest @ValueSource(strings = {"", "abc", "2026-13-01", "12/05/1990", "1990-5-12"})
    void malformedBirthDateIsRejected(String text) {
        var form = new ResidentForm("HK-1002", "Nguyễn Văn A", text, Gender.NAM, "001090000001",
                RelationToHead.CHU_HO, "2024-01-20");
        assertEquals(ResidentServiceImpl.MSG_NGAY_SINH,
                service.createForHousehold(form, null).fieldErrors().get("ngay_sinh"));
    }

    @Test void moveInDateRules() {
        var future = new ResidentForm("HK-1002", "Nguyễn Văn A", "1990-05-12", Gender.NAM, "001090000001",
                RelationToHead.CHU_HO, "2026-11-08");
        assertFieldError(service.createForHousehold(future, null), Status.VALIDATION_ERROR, "ngay_chuyen_den",
                ResidentServiceImpl.MSG_NGAY_CHUYEN_DEN);
        var beforeBirth = new ResidentForm("HK-1002", "Nguyễn Văn A", "1990-05-12", Gender.NAM, "001090000001",
                RelationToHead.CHU_HO, "1990-05-11");
        assertEquals(ResidentServiceImpl.MSG_NGAY_CHUYEN_DEN,
                service.createForHousehold(beforeBirth, null).fieldErrors().get("ngay_chuyen_den"));
        var missing = new ResidentForm("HK-1002", "Nguyễn Văn A", "1990-05-12", Gender.NAM, "001090000001",
                RelationToHead.CHU_HO, null);
        assertEquals(Status.VALIDATION_ERROR, service.createForHousehold(missing, null).status());
        // Born on the move-in date is fine; moving in today is fine; before the household's handover date is fine.
        var sameDay = new ResidentForm("HK-1002", "Nguyễn Văn A", "1990-05-12", Gender.NAM, "001090000001",
                RelationToHead.CHU_HO, "1990-05-12");
        assertTrue(service.createForHousehold(sameDay, null).isSuccess());
        var today = new ResidentForm("HK-1002", "Trần Thị B", "1992-08-30", Gender.NU, "001092000002",
                RelationToHead.VO, "2026-11-07");
        assertTrue(service.createForHousehold(today, null).isSuccess());
    }

    // ---- enums ----

    @Test void genderAndRelationAreRequired() {
        var form = new ResidentForm("HK-1002", "Nguyễn Văn A", "1990-05-12", null, "001090000001", null, "2024-01-20");
        var result = service.createForHousehold(form, null);
        assertEquals(ResidentServiceImpl.MSG_GIOI_TINH, result.fieldErrors().get("gioi_tinh"));
        assertEquals(ResidentServiceImpl.MSG_QUAN_HE, result.fieldErrors().get("quan_he_chu_ho"));
    }

    @Test void allErrorsAreReportedTogether() {
        var result = service.createForHousehold(new ResidentForm("", "", "", null, "12", null, ""), null);
        assertEquals(Status.VALIDATION_ERROR, result.status());
        assertEquals(7, result.fieldErrors().size(), result.fieldErrors().toString());
    }

    // ---- CCCD (AC-02) ----

    @Test void ac02AdultWithoutCccdIsRejected() {
        var form = member("Nguyễn Thị Hoa", "2000-03-01", "", RelationToHead.CHU_HO);
        assertFieldError(service.createForHousehold(form, null), Status.VALIDATION_ERROR, "so_cccd",
                ResidentServiceImpl.MSG_CCCD_REQUIRED);
    }

    @Test void cccdIsRequiredFromTheFourteenthBirthdayInclusive() {
        var turns14Today = member("Nguyễn Thị Hoa", "2012-11-07", null, RelationToHead.CHU_HO);
        assertEquals(ResidentServiceImpl.MSG_CCCD_REQUIRED,
                service.createForHousehold(turns14Today, null).fieldErrors().get("so_cccd"));
        var turns14Tomorrow = member("Nguyễn Thị Hoa", "2012-11-08", null, RelationToHead.CHU_HO);
        assertTrue(service.createForHousehold(turns14Tomorrow, null).isSuccess());
    }

    @ParameterizedTest @ValueSource(strings = {"001090000001", "123456789"})
    void nineAndTwelveDigitCccdAreAccepted(String cccd) {
        assertTrue(service.createForHousehold(head("Nguyễn Văn A", cccd), null).isSuccess());
    }

    @ParameterizedTest @ValueSource(strings = {"12345678", "1234567890", "12345678901", "1234567890123", "00109000000a",
            "001 090 000 001", "abcdefghijkl"})
    void badCccdFormatsAreRejectedForAdultsAndChildren(String cccd) {
        assertFieldError(service.createForHousehold(head("Nguyễn Văn A", cccd), null), Status.VALIDATION_ERROR,
                "so_cccd", ResidentServiceImpl.MSG_CCCD);
        created(head("Nguyễn Văn A", "001090000001"));
        var child = member("Nguyễn Minh C", "2018-03-01", cccd, RelationToHead.CON);
        assertEquals(ResidentServiceImpl.MSG_CCCD, service.createForHousehold(child, null).fieldErrors().get("so_cccd"));
    }

    @Test void underFourteenMayHaveNoCccd() throws Exception {
        created(head("Nguyễn Văn A", "001090000001"));
        var child = created(member("Nguyễn Minh C", "2018-03-01", "  ", RelationToHead.CON));
        assertNull(child.soCccd());
    }

    @Test void duplicateCccdIsRejectedSystemWideEvenAcrossHouseholdsAndForChildren() throws Exception {
        created(head("Nguyễn Văn A", "001090000001"));
        var other = new ResidentForm("HK-0101", "Lê Văn D", "1985-11-02", Gender.NAM, "001090000001",
                RelationToHead.CHU_HO, "2024-01-20");
        assertFieldError(service.createForHousehold(other, null), Status.DUPLICATE, "so_cccd", ResidentServiceImpl.MSG_CCCD);
        var child = member("Nguyễn Minh C", "2018-03-01", "001090000001", RelationToHead.CON);
        assertEquals(Status.DUPLICATE, service.createForHousehold(child, null).status());
        assertEquals(1, residents.countByMaHo("HK-1002"));
        assertEquals(0, residents.countByMaHo("HK-0101"));
    }

    @Test void sameChildWithoutCccdInTheSameHouseholdIsADuplicateCaseInsensitively() throws Exception {
        created(head("Nguyễn Văn A", "001090000001"));
        created(member("Nguyễn Minh C", "2018-03-01", null, RelationToHead.CON));
        var again = service.createForHousehold(member("nguyễn  minh c", "2018-03-01", null, RelationToHead.CON), null);
        assertFieldError(again, Status.DUPLICATE, "ho_ten", ResidentServiceImpl.MSG_PERSON_DUPLICATE);
        assertEquals(2, residents.countByMaHo("HK-1002"));
    }

    @Test void namesDifferingByAccentBirthDateOrGenderAreDifferentPeople() throws Exception {
        created(head("Nguyễn Văn A", "001090000001"));
        created(member("Nguyễn Minh C", "2018-03-01", null, RelationToHead.CON));
        created(member("Nguyễn Minh Ca", "2018-03-01", null, RelationToHead.CON));
        created(member("Nguyễn Minh C", "2018-03-02", null, RelationToHead.CON));
        var boy = new ResidentForm("HK-1002", "Nguyễn Minh C", "2018-03-01", Gender.NAM, null, RelationToHead.CON,
                "2024-01-20");
        assertTrue(service.createForHousehold(boy, null).isSuccess());
        assertEquals(5, residents.countByMaHo("HK-1002"));
    }

    // ---- BR-04 / AC-03 / stale confirmation ----

    @Test void ac03SecondHeadAsksForConfirmationAndWritesNothing() throws Exception {
        var a = created(head("Nguyễn Văn A", "001090000001"));
        var result = service.createForHousehold(head("Trần Thị B", "001092000002"), null);
        assertEquals(Status.CONFIRMATION_REQUIRED, result.status());
        assertEquals(ConfirmationReason.HEAD_EXISTS, result.confirmationReason().orElseThrow());
        assertEquals("Hộ này đã có chủ hộ. Bạn có muốn đổi Nguyễn Văn A sang quan hệ khác không?", result.message());
        assertEquals(a.maNhanKhau(), result.currentHead().orElseThrow().maNhanKhau());
        assertEquals(1, residents.countByMaHo("HK-1002"));
        assertEquals(RelationToHead.CHU_HO, residents.findHead("HK-1002").orElseThrow().quanHeChuHo());
    }

    @Test void confirmedReplacementDemotesTheOldHeadToKhacAndLeavesExactlyOneHead() throws Exception {
        var a = created(head("Nguyễn Văn A", "001090000001"));
        var result = service.createForHousehold(head("Trần Thị B", "001092000002"), a.maNhanKhau());
        assertTrue(result.isSuccess(), result.toString());
        var members = service.getHousehold("HK-1002").data().orElseThrow();
        assertEquals(2, members.memberCount());
        assertEquals(1, members.members().stream().filter(Resident::isHead).count());
        assertEquals("Trần Thị B", members.head().orElseThrow().hoTen());
        assertEquals(RelationToHead.KHAC, members.members().stream()
                .filter(r -> r.maNhanKhau() == a.maNhanKhau()).findFirst().orElseThrow().quanHeChuHo());
    }

    @Test void staleConfirmationForAnotherPersonAsksAgainAndWritesNothing() throws Exception {
        var a = created(head("Nguyễn Văn A", "001090000001"));
        var b = service.createForHousehold(head("Trần Thị B", "001092000002"), a.maNhanKhau()).data().orElseThrow();
        // A second user still holds the confirmation about A.
        var stale = service.createForHousehold(head("Lê Văn C", "001093000003"), a.maNhanKhau());
        assertEquals(Status.CONFIRMATION_REQUIRED, stale.status());
        assertEquals(ConfirmationReason.CONFIRMATION_STALE, stale.confirmationReason().orElseThrow());
        assertEquals(b.maNhanKhau(), stale.currentHead().orElseThrow().maNhanKhau());
        assertEquals(2, residents.countByMaHo("HK-1002"));
        assertEquals(b.maNhanKhau(), residents.findHead("HK-1002").orElseThrow().maNhanKhau());
    }

    @Test void staleConfirmationWhenTheHouseholdNowHasNoHeadIsNotAccepted() throws Exception {
        var noHead = new StubResidents(new ResidentRepository.HeadChanged(Optional.empty()));
        service = new ResidentServiceImpl(noHead, households, authorization(), CLOCK);
        var result = service.createForHousehold(head("Trần Thị B", "001092000002"), 7L);
        assertEquals(Status.CONFIRMATION_REQUIRED, result.status());
        assertEquals(ConfirmationReason.CONFIRMATION_STALE, result.confirmationReason().orElseThrow());
        assertTrue(result.currentHead().isEmpty());
        assertEquals(ResidentServiceImpl.MSG_STALE, result.message());
        assertEquals(ResidentRepository.HeadPolicy.REPLACE_HEAD_IF_UNCHANGED, noHead.policy);
        assertEquals(7L, noHead.expected);
    }

    @Test void confirmationIsIgnoredForNonHeadsAndNeverDemotesAnyone() throws Exception {
        var a = created(head("Nguyễn Văn A", "001090000001"));
        var result = service.createForHousehold(member("Trần Thị B", "1992-08-30", "001092000002", RelationToHead.VO),
                a.maNhanKhau());
        assertTrue(result.isSuccess());
        assertEquals(RelationToHead.CHU_HO, residents.findHead("HK-1002").orElseThrow().quanHeChuHo());
        assertEquals(a.maNhanKhau(), residents.findHead("HK-1002").orElseThrow().maNhanKhau());
    }

    @Test void confirmationForAnHeadlessFirstHeadIsNotAcceptedByTheRealRepository() throws Exception {
        var result = service.createForHousehold(head("Nguyễn Văn A", "001090000001"), 1L);
        assertEquals(Status.CONFIRMATION_REQUIRED, result.status());
        assertEquals(ConfirmationReason.CONFIRMATION_STALE, result.confirmationReason().orElseThrow());
        assertEquals(0, residents.countByMaHo("HK-1002"));
        assertTrue(service.createForHousehold(head("Nguyễn Văn A", "001090000001"), null).isSuccess());
    }

    @Test void aSecondHeadFromTheDatabaseConstraintIsReReadNotOverwritten() throws Exception {
        var a = created(head("Nguyễn Văn A", "001090000001"));
        var raced = new StubResidents(new ResidentDuplicateException(ResidentDuplicateException.Field.CHU_HO, null));
        raced.head = Optional.of(a);
        service = new ResidentServiceImpl(raced, households, authorization(), CLOCK);
        var result = service.createForHousehold(head("Trần Thị B", "001092000002"), null);
        assertEquals(ConfirmationReason.CONFIRMATION_STALE, result.confirmationReason().orElseThrow());
        assertEquals(a.maNhanKhau(), result.currentHead().orElseThrow().maNhanKhau());
    }

    // ---- RBAC ----

    private ResidentRepository forbiddenRepository() {
        return new StubResidents(new AssertionError("data accessed before the permission check"));
    }

    @Test void adminMayCreateAndView() {
        assertTrue(service.createForHousehold(head("Nguyễn Văn A", "001090000001"), null).isSuccess());
        assertTrue(service.getHousehold("HK-1002").isSuccess());
    }

    @Test void accountantMayViewButNotCreate() {
        created(head("Nguyễn Văn A", "001090000001"));
        role.set(Optional.of(Role.ACCOUNTANT));
        assertTrue(service.getHousehold("HK-1002").isSuccess());
        assertThrows(AccessDeniedException.class,
                () -> service.createForHousehold(head("Trần Thị B", "001092000002"), null));
        role.set(Optional.of(Role.ADMIN));
        assertEquals(1, service.getHousehold("HK-1002").data().orElseThrow().memberCount());
    }

    @Test void residentAndAnonymousAreDeniedBothMethodsBeforeAnyDataAccess() {
        service = new ResidentServiceImpl(forbiddenRepository(), new ForbiddenHouseholds(), authorization(), CLOCK);
        for (var who : List.of(Optional.of(Role.RESIDENT), Optional.<Role>empty())) {
            role.set(who);
            assertThrows(AccessDeniedException.class, () -> service.getHousehold("HK-1002"));
            assertThrows(AccessDeniedException.class,
                    () -> service.createForHousehold(head("Nguyễn Văn A", "001090000001"), null));
        }
        role.set(Optional.of(Role.ACCOUNTANT));
        assertThrows(AccessDeniedException.class,
                () -> service.createForHousehold(head("Nguyễn Văn A", "001090000001"), null));
    }

    @Test void deniedUserLearnsNothingAboutWhetherAHouseholdExists() {
        role.set(Optional.of(Role.RESIDENT));
        assertThrows(AccessDeniedException.class, () -> service.getHousehold("HK-NOPE"));
    }

    // ---- getHousehold ----

    @Test void getHouseholdRules() {
        assertEquals(Status.VALIDATION_ERROR, service.getHousehold(" ").status());
        assertEquals(Status.NOT_FOUND, service.getHousehold("HK-NOPE").status());
        var empty = service.getHousehold("HK-0101");
        assertTrue(empty.isSuccess(), "household without residents is a success, not an error");
        assertEquals(0, empty.data().orElseThrow().memberCount());
        assertTrue(empty.data().orElseThrow().head().isEmpty());
    }

    // ---- database failures ----

    @Test void databaseFailureOnCreateIsAGenericSystemError() {
        service = new ResidentServiceImpl(new StubResidents(new SQLException("Access denied for user 'root'@'x'")),
                households, authorization(), CLOCK);
        var result = service.createForHousehold(head("Nguyễn Văn A", "001090000001"), null);
        assertEquals(Status.SYSTEM_ERROR, result.status());
        assertEquals(ResidentServiceImpl.MSG_SYSTEM, result.message());
        assertFalse(result.message().contains("root"));
    }

    @Test void databaseFailureOnReadIsAGenericSystemError() {
        service = new ResidentServiceImpl(new StubResidents(new SQLException("boom")), households, authorization(), CLOCK);
        assertEquals(Status.SYSTEM_ERROR, service.getHousehold("HK-1002").status());
    }

    @Test void nullFormIsAProgrammingError() {
        assertThrows(NullPointerException.class, () -> service.createForHousehold(null, null));
    }

    /** Any call means data was read before the permission check. */
    private static final class ForbiddenHouseholds implements com.bluemoon.dao.HouseholdRepository {
        private static AssertionError touched() { return new AssertionError("household read before the permission check"); }
        @Override public void insert(HouseholdProfile profile) { throw touched(); }
        @Override public Optional<HouseholdProfile> findByMaHo(String maHo) { throw touched(); }
        @Override public Optional<HouseholdProfile> findBySoCanHo(String soCanHo) { throw touched(); }
        @Override public List<HouseholdProfile> findAll(ApartmentStatus status) { throw touched(); }
        @Override public boolean update(String maHo, int tang, BigDecimal dienTich, LocalDate ngayNhanNha,
                                        ApartmentStatus trangThai) { throw touched(); }
    }

    /** Returns a fixed outcome or throws; records what the Service asked for. */
    private static final class StubResidents implements ResidentRepository {
        private final InsertOutcome outcome;
        private final Throwable failure;
        Optional<Resident> head = Optional.empty();
        HeadPolicy policy;
        Long expected;

        StubResidents(InsertOutcome outcome) { this.outcome = outcome; this.failure = null; }
        StubResidents(Throwable failure) { this.outcome = null; this.failure = failure; }

        @Override public InsertOutcome insert(Resident resident, HeadPolicy policy, Long expectedHeadId)
                throws SQLException, ResidentDuplicateException {
            this.policy = policy;
            this.expected = expectedHeadId;
            if (failure instanceof ResidentDuplicateException duplicate) { throw duplicate; }
            throwFailure();
            return outcome;
        }

        @Override public List<Resident> findByMaHo(String maHo) throws SQLException { throwFailure(); return List.of(); }
        @Override public Optional<Resident> findHead(String maHo) throws SQLException { return head; }
        @Override public int countByMaHo(String maHo) throws SQLException { throwFailure(); return 0; }

        private void throwFailure() throws SQLException {
            if (failure instanceof SQLException sql) { throw new SQLException(sql.getMessage()); }
            if (failure instanceof Error error) { throw error; }
        }
    }
}
