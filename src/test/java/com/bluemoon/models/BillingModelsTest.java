package com.bluemoon.models;

import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.dao.FeeRateLookup;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Contract-level checks only: no calculation, storage, RBAC or transaction behaviour exists yet. */
class BillingModelsTest {
    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DUE = LocalDate.of(2026, 12, 15);

    private static BigDecimal d(String value) { return new BigDecimal(value); }

    private static ChargeLine line(String code, ChargeMethod method, String qty, String rate, String amount) {
        return new ChargeLine(code, "Khoan " + code, method, d(qty), d(rate), EFFECTIVE, d(amount));
    }

    /** AC-01: P1204, 80 m2, 4 people. */
    private static List<ChargeLine> ac01Lines() {
        return List.of(line("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "80", "10000", "800000"),
                line("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "80", "7000", "560000"),
                line("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "4", "6000", "24000"));
    }

    private static MonthlyCharge charge(List<ChargeLine> lines, String total, String paid, String owed) {
        return new MonthlyCharge("HD-202611-P1204", "2026-11", "HK-1204", "P1204", d("80.00"), 4,
                d(total), d(paid), d(owed), ChargeStatus.CHUA_DONG, LocalDate.of(2026, 12, 15), lines);
    }

    // ---- AppliedRate ----
    @Test void appliedRateKeepsValues() {
        var rate = new AppliedRate("PHI_QL", d("7000"), EFFECTIVE);
        assertEquals("PHI_QL", rate.maKhoanThu());
        assertEquals(d("7000"), rate.donGia());
        assertEquals(EFFECTIVE, rate.ngayApDung());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "-0.01"})
    void appliedRateRejectsNonPositivePrice(String price) {
        assertThrows(IllegalArgumentException.class, () -> new AppliedRate("PHI_QL", d(price), EFFECTIVE));
    }

    @Test void appliedRateRejectsMissingParts() {
        assertThrows(IllegalArgumentException.class, () -> new AppliedRate(null, d("1"), EFFECTIVE));
        assertThrows(IllegalArgumentException.class, () -> new AppliedRate(" ", d("1"), EFFECTIVE));
        assertThrows(NullPointerException.class, () -> new AppliedRate("PHI_QL", null, EFFECTIVE));
        assertThrows(NullPointerException.class, () -> new AppliedRate("PHI_QL", d("1"), null));
    }

    // ---- FeeRateLookup (shape only; the lambda is test-local, not a production fake) ----
    @Test void feeRateLookupReturnsEffectiveDateNotQueryDate() {
        FeeRateLookup lookup = (code, at) -> Optional.of(new AppliedRate(code, d("7000"), EFFECTIVE));
        var rate = lookup.getEffectiveRate("PHI_QL", LocalDate.of(2026, 11, 1)).orElseThrow();
        assertEquals(EFFECTIVE, rate.ngayApDung());
        FeeRateLookup none = (code, at) -> Optional.empty();
        assertTrue(none.getEffectiveRate("PHI_QL", EFFECTIVE).isEmpty());
    }

    // ---- ChargeLine ----
    @Test void chargeLineStoresMoneyAtScaleTwo() {
        var line = line("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "80", "10000", "800000");
        assertEquals(2, line.thanhTien().scale());
        assertEquals(new BigDecimal("800000.00"), line.thanhTien());
    }

    @Test void chargeLineRequiresHalfUpRounding() {
        // 3 x 6000.50 = 18001.50 -> 18002 (HALF_UP)
        assertDoesNotThrow(() -> line("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "3", "6000.50", "18002"));
        assertThrows(IllegalArgumentException.class,
                () -> line("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "3", "6000.50", "18001"));
        assertThrows(IllegalArgumentException.class,
                () -> line("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "3", "6000.50", "18001.50"));
    }

    @Test void chargeLineAllowsZeroQuantityLine() {
        var vacant = line("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "0", "6000", "0");
        assertEquals(0, vacant.thanhTien().signum());
    }

    @Test void chargeLineRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> line("QUY", ChargeMethod.TU_NGUYEN, "1", "1000", "1000"), "voluntary is excluded (Q1)");
        assertThrows(IllegalArgumentException.class,
                () -> line("PHI_CH", ChargeMethod.THEO_CAN_HO, "2", "1000", "2000"), "fixed fee needs quantity 1");
        assertThrows(IllegalArgumentException.class,
                () -> line("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "-1", "10000", "0"));
        assertThrows(IllegalArgumentException.class,
                () -> line("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "80", "0", "0"));
        assertThrows(IllegalArgumentException.class,
                () -> line("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "80", "10000", "799999"));
        assertThrows(IllegalArgumentException.class,
                () -> line("", ChargeMethod.THEO_DIEN_TICH, "80", "10000", "800000"));
        assertThrows(NullPointerException.class,
                () -> new ChargeLine("PHI_DV", "Phi", null, d("1"), d("1"), EFFECTIVE, d("1")));
        assertThrows(NullPointerException.class,
                () -> new ChargeLine("PHI_DV", "Phi", ChargeMethod.THEO_CAN_HO, d("1"), d("1"), null, d("1")));
    }

    // ---- ChargeLine: THEO_NHAN_KHAU needs whole people (Revision 03) ----
    @ParameterizedTest @ValueSource(strings = {"0", "1", "2", "2.00", "0.00"})
    void peopleQuantityAcceptsWholeNumbers(String people) {
        var rate = d("6000");
        var amount = d(people).multiply(rate).setScale(0).toPlainString();
        assertDoesNotThrow(() -> line("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, people, "6000", amount));
    }

    @ParameterizedTest @ValueSource(strings = {"0.5", "1.5", "2.01", "-1", "-0.5"})
    void peopleQuantityRejectsFractionsAndNegatives(String people) {
        assertThrows(IllegalArgumentException.class,
                () -> new ChargeLine("PHI_VS", "Phi ve sinh", ChargeMethod.THEO_NHAN_KHAU, d(people), d("6000"),
                        EFFECTIVE, BillingMoney.toStored(d(people).multiply(d("6000")).max(BigDecimal.ZERO))));
    }

    @Test void areaQuantityStillAllowsDecimals() {
        // 75.55 m2 x 7000 = 528850: THEO_DIEN_TICH is not forced to a whole number.
        assertDoesNotThrow(() -> line("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "75.55", "7000", "528850"));
        assertDoesNotThrow(() -> line("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "0.5", "7000", "3500"));
    }

    @Test void fixedFeeRulesUnchanged() {
        assertDoesNotThrow(() -> line("PHI_CH", ChargeMethod.THEO_CAN_HO, "1", "50000", "50000"));
        assertDoesNotThrow(() -> line("PHI_CH", ChargeMethod.THEO_CAN_HO, "1.00", "50000", "50000"));
        assertThrows(IllegalArgumentException.class,
                () -> line("PHI_CH", ChargeMethod.THEO_CAN_HO, "0", "50000", "0"));
    }

    // ---- MonthlyCharge: status vs amounts (EP-03 BR-13) ----
    private static MonthlyCharge status(ChargeStatus status, String total, String paid, String owed,
                                        List<ChargeLine> lines) {
        return new MonthlyCharge("HD-202611-P1204", "2026-11", "HK-1204", "P1204", d("80.00"), 4,
                d(total), d(paid), d(owed), status, DUE, lines);
    }

    @Test void statusAcceptsConsistentCombinations() {
        assertDoesNotThrow(() -> status(ChargeStatus.CHUA_DONG, "1384000", "0", "1384000", List.of()));
        assertDoesNotThrow(() -> status(ChargeStatus.DONG_THIEU, "1384000", "1000000", "384000", List.of()));
        assertDoesNotThrow(() -> status(ChargeStatus.DA_DONG_DU, "1384000", "1384000", "0", List.of()));
        assertDoesNotThrow(() -> status(ChargeStatus.DONG_THUA, "1384000", "1400000", "-16000", List.of()));
        // header-only and full invoice both work
        assertDoesNotThrow(() -> status(ChargeStatus.DA_DONG_DU, "1384000", "1384000", "0", ac01Lines()));
        assertDoesNotThrow(() -> status(ChargeStatus.CHUA_DONG, "1384000", "0", "1384000", ac01Lines()));
    }

    @Test void paidInFullRequiresNothingOwing() {
        // DA_DONG_DU with nothing paid and 100 still owed is contradictory.
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.DA_DONG_DU, "100", "0", "100", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.DA_DONG_DU, "1384000", "1000000", "384000", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.DA_DONG_DU, "1384000", "1400000", "-16000", List.of()));
    }

    @Test void unpaidRequiresNothingPaid() {
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.CHUA_DONG, "1384000", "1000", "1383000", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.CHUA_DONG, "1384000", "1384000", "0", List.of()));
    }

    @Test void partiallyPaidRequiresSomePaidAndSomeOwing() {
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.DONG_THIEU, "1384000", "0", "1384000", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.DONG_THIEU, "1384000", "1384000", "0", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.DONG_THIEU, "1384000", "1400000", "-16000", List.of()));
    }

    @Test void overpaidRequiresNegativeOwed() {
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.DONG_THUA, "1384000", "1384000", "0", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.DONG_THUA, "1384000", "1000000", "384000", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> status(ChargeStatus.DONG_THUA, "1384000", "0", "1384000", List.of()));
    }

    // ---- MonthlyCharge ----
    @Test void monthlyChargeAc01Snapshot() {
        var charge = charge(ac01Lines(), "1384000", "0", "1384000");
        assertEquals(new BigDecimal("1384000.00"), charge.tongTien());
        assertEquals(new BigDecimal("1384000.00"), charge.conThieu());
        assertEquals(ChargeStatus.CHUA_DONG, charge.trangThai());
        assertEquals(new BigDecimal("80.00"), charge.dienTich());
        assertEquals(4, charge.soNhanKhau());
        assertEquals(3, charge.lines().size());
    }

    @Test void monthlyChargeRejectsInconsistentAmounts() {
        assertThrows(IllegalArgumentException.class, () -> charge(ac01Lines(), "1384001", "0", "1384001"));
        assertThrows(IllegalArgumentException.class, () -> charge(ac01Lines(), "1384000", "0", "1"));
        assertThrows(IllegalArgumentException.class, () -> charge(ac01Lines(), "1384000.50", "0", "1384000.50"));
        assertThrows(IllegalArgumentException.class, () -> charge(ac01Lines(), "-1", "0", "-1"));
        assertThrows(IllegalArgumentException.class, () -> charge(ac01Lines(), "1384000", "-1", "1384001"));
    }

    @Test void monthlyChargeAllowsHeaderOnlyListing() {
        assertTrue(charge(List.of(), "1384000", "0", "1384000").lines().isEmpty());
    }

    @Test void monthlyChargeValidatesIdentityAndSnapshotFields() {
        var lines = ac01Lines();
        assertThrows(IllegalArgumentException.class, () -> new MonthlyCharge("HD-202611-P9999", "2026-11", "HK-1204",
                "P1204", d("80"), 4, d("1384000"), d("0"), d("1384000"), ChargeStatus.CHUA_DONG, DUE, lines));
        assertThrows(IllegalArgumentException.class, () -> new MonthlyCharge("HD-202611-P1204", "2026-11", "HK-1204",
                "P1204", d("0"), 4, d("1384000"), d("0"), d("1384000"), ChargeStatus.CHUA_DONG, DUE, lines));
        assertThrows(IllegalArgumentException.class, () -> new MonthlyCharge("HD-202611-P1204", "2026-11", "HK-1204",
                "P1204", d("80"), -1, d("1384000"), d("0"), d("1384000"), ChargeStatus.CHUA_DONG, DUE, lines));
        assertThrows(NullPointerException.class, () -> new MonthlyCharge("HD-202611-P1204", "2026-11", "HK-1204",
                "P1204", d("80"), 4, d("1384000"), d("0"), d("1384000"), null, DUE, lines));
        assertThrows(NullPointerException.class, () -> new MonthlyCharge("HD-202611-P1204", "2026-11", "HK-1204",
                "P1204", d("80"), 4, d("1384000"), d("0"), d("1384000"), ChargeStatus.CHUA_DONG, DUE, null));
    }

    @Test void monthlyChargeCopiesLinesDefensively() {
        var source = new ArrayList<>(ac01Lines());
        var charge = charge(source, "1384000", "0", "1384000");
        source.clear();
        assertEquals(3, charge.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> charge.lines().clear());
    }

    // ---- BillingPeriod ----
    @Test void periodHanNopIsFifteenthOfNextMonth() {
        assertEquals(LocalDate.of(2026, 12, 15), BillingPeriod.defaultHanNop(YearMonth.of(2026, 11)));
        assertEquals(LocalDate.of(2027, 1, 15), BillingPeriod.defaultHanNop(YearMonth.of(2026, 12)));
        assertEquals(LocalDate.of(2026, 3, 15), BillingPeriod.defaultHanNop(YearMonth.of(2026, 2)));
    }

    @Test void periodRejectsWrongDueDateAndNegatives() {
        var now = Instant.parse("2026-11-08T00:00:00Z");
        var ok = new BillingPeriod("2026-11", DUE, 10, 0, d("1384000"), now);
        assertEquals(new BigDecimal("1384000.00"), ok.tongTien());
        assertThrows(IllegalArgumentException.class,
                () -> new BillingPeriod("2026-11", LocalDate.of(2026, 12, 14), 10, 0, d("1"), now));
        assertThrows(IllegalArgumentException.class, () -> new BillingPeriod("2026-11", DUE, -1, 0, d("1"), now));
        assertThrows(IllegalArgumentException.class, () -> new BillingPeriod("2026-11", DUE, 1, -1, d("1"), now));
        assertThrows(IllegalArgumentException.class, () -> new BillingPeriod("2026-11", DUE, 1, 0, d("-1"), now));
        assertThrows(IllegalArgumentException.class, () -> new BillingPeriod("2026-11", DUE, 1, 0, d("0.5"), now));
        assertThrows(NullPointerException.class, () -> new BillingPeriod("2026-11", DUE, 1, 0, d("1"), null));
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "2026-1", "2026-13", "2026-00", "2026/11", "202611",
            " 2026-11", "2026-11 ", "2026-11-01", "abcd-ef"})
    void kyThuMustBeStrictYearMonth(String value) {
        assertThrows(IllegalArgumentException.class, () -> BillingPeriod.parseKyThu(value));
    }

    @Test void kyThuAcceptsValidValuesAndRejectsNull() {
        assertEquals(YearMonth.of(2026, 11), BillingPeriod.parseKyThu("2026-11"));
        assertEquals(YearMonth.of(2027, 1), BillingPeriod.parseKyThu("2027-01"));
        assertThrows(IllegalArgumentException.class, () -> BillingPeriod.parseKyThu(null));
    }

    // ---- BillingPreview / BillingPeriodDetail / helpers ----
    @Test void previewCopiesAllCollectionsAndChecksTotal() {
        var charges = new ArrayList<>(List.of(charge(ac01Lines(), "1384000", "0", "1384000")));
        var skipped = new ArrayList<>(List.of(new SkippedHousehold("P1301", SkippedHousehold.Reason.MISSING_AREA, "x")));
        var warnings = new ArrayList<>(List.of(
                new BillingWarning("P1302", BillingWarning.Code.VACANT_WITH_RESIDENTS, "y")));
        var rates = new ArrayList<>(List.of(new AppliedRate("PHI_QL", d("7000"), EFFECTIVE)));
        var preview = new BillingPreview("2026-11", DUE, false, charges, skipped, warnings, rates, d("1384000"));
        charges.clear(); skipped.clear(); warnings.clear(); rates.clear();
        assertEquals(1, preview.charges().size());
        assertEquals(1, preview.skipped().size());
        assertEquals(1, preview.warnings().size());
        assertEquals(1, preview.rates().size());
        assertThrows(UnsupportedOperationException.class, () -> preview.charges().clear());
        assertThrows(UnsupportedOperationException.class, () -> preview.skipped().clear());
        assertThrows(UnsupportedOperationException.class, () -> preview.warnings().clear());
        assertThrows(UnsupportedOperationException.class, () -> preview.rates().clear());
        assertThrows(IllegalArgumentException.class, () -> new BillingPreview("2026-11", DUE, false,
                preview.charges(), List.of(), List.of(), List.of(), d("1")));
        assertThrows(NullPointerException.class, () -> new BillingPreview("2026-11", DUE, false,
                null, List.of(), List.of(), List.of(), d("0")));
    }

    @Test void periodDetailCopiesCharges() {
        var period = new BillingPeriod("2026-11", DUE, 1, 0, d("1384000"), Instant.EPOCH);
        var charges = new ArrayList<>(List.of(charge(List.of(), "1384000", "0", "1384000")));
        var detail = new BillingPeriodDetail(period, charges);
        charges.clear();
        assertEquals(1, detail.charges().size());
        assertThrows(UnsupportedOperationException.class, () -> detail.charges().clear());
        assertThrows(NullPointerException.class, () -> new BillingPeriodDetail(null, List.of()));
    }

    @Test void skippedAndWarningRequireText() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkippedHousehold("", SkippedHousehold.Reason.ZERO_TOTAL, "m"));
        assertThrows(IllegalArgumentException.class,
                () -> new SkippedHousehold("P1", SkippedHousehold.Reason.ZERO_TOTAL, " "));
        assertThrows(NullPointerException.class, () -> new SkippedHousehold("P1", null, "m"));
        assertThrows(IllegalArgumentException.class,
                () -> new BillingWarning("P1", BillingWarning.Code.VACANT_WITH_RESIDENTS, ""));
    }

    @Test void chargeStatusMatchesSrs() {
        assertEquals(List.of("CHUA_DONG", "DONG_THIEU", "DA_DONG_DU", "DONG_THUA"),
                java.util.Arrays.stream(ChargeStatus.values()).map(Enum::name).toList());
    }

    // ---- Money type rule ----
    @Test void noBillingRecordUsesFloatingPointTypes() {
        for (Class<?> type : List.of(AppliedRate.class, ChargeLine.class, MonthlyCharge.class, BillingPeriod.class,
                BillingPreview.class, BillingPeriodDetail.class, SkippedHousehold.class, BillingWarning.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                assertNotEquals(double.class, component.getType(), type + "." + component.getName());
                assertNotEquals(float.class, component.getType(), type + "." + component.getName());
                assertNotEquals(Double.class, component.getType(), type + "." + component.getName());
                assertNotEquals(Float.class, component.getType(), type + "." + component.getName());
            }
        }
    }

    @Test void moneyHelperRoundsHalfUpToWholeVndAtScaleTwo() {
        assertEquals(new BigDecimal("18002.00"), BillingMoney.toStored(d("18001.5")));
        assertEquals(new BigDecimal("18001.00"), BillingMoney.toStored(d("18001.49")));
        assertEquals(new BigDecimal("0.00"), BillingMoney.toStored(d("0.4")));
    }
}
