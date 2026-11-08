package com.bluemoon.services;

import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.dao.BillingInputs;
import com.bluemoon.dao.BillingInputs.FeeInput;
import com.bluemoon.dao.BillingInputs.HouseholdInput;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.AppliedRate;
import com.bluemoon.models.BillingWarning;
import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.ChargeStatus;
import com.bluemoon.models.SkippedHousehold;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pure calculation only: no database, clock or authorization. */
class BillingCalculatorTest {
    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final LocalDate DUE = LocalDate.of(2026, 12, 15);
    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 1, 1);

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static FeeInput fee(String code, ChargeMethod method, String price) {
        return new FeeInput(code, "Khoan " + code, method,
                Optional.of(new AppliedRate(code, d(price), EFFECTIVE)));
    }

    private static FeeInput noRate(String code, ChargeMethod method) {
        return new FeeInput(code, "Khoan " + code, method, Optional.empty());
    }

    private static HouseholdInput home(String apartment, String area, int people) {
        return new HouseholdInput("HK-" + apartment, apartment, area == null ? null : d(area),
                ApartmentStatus.DANG_O, people);
    }

    private static List<FeeInput> standardFees() {
        return List.of(fee("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "10000"),
                fee("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "7000"),
                fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "6000"));
    }

    private static BillingCalculator.Result run(List<FeeInput> fees, HouseholdInput... homes) {
        return BillingCalculator.compute(NOV, DUE, new BillingInputs(fees, List.of(homes)));
    }

    @Test void ac01P1204() {
        var result = run(standardFees(), home("P1204", "80", 4));
        var charge = result.charges().get(0);
        assertEquals("HD-202611-P1204", charge.maHoaDon());
        assertEquals(new BigDecimal("1384000.00"), charge.tongTien());
        assertEquals(new BigDecimal("1384000.00"), charge.conThieu());
        assertEquals(BigDecimal.ZERO.setScale(2), charge.daThanhToan());
        assertEquals(ChargeStatus.CHUA_DONG, charge.trangThai());
        assertEquals(DUE, charge.hanNop());
        assertEquals(new BigDecimal("1384000.00"), result.total());
        var amounts = charge.lines().stream().collect(java.util.stream.Collectors.toMap(
                l -> l.maKhoanThu(), l -> l.thanhTien().intValueExact()));
        assertEquals(800000, amounts.get("PHI_DV"));
        assertEquals(560000, amounts.get("PHI_QL"));
        assertEquals(24000, amounts.get("PHI_VS"));
        assertEquals(80, charge.dienTich().intValueExact());
        assertEquals(4, charge.soNhanKhau());
        assertEquals(3, result.rates().size());
        assertTrue(result.skipped().isEmpty());
    }

    @Test void emptyHouseholdPaysManagementAndServiceButNoSanitation() {
        var charge = run(standardFees(), home("P1204", "80", 0)).charges().get(0);
        assertEquals(new BigDecimal("1360000.00"), charge.tongTien());
        var sanitation = charge.lines().stream().filter(l -> l.maKhoanThu().equals("PHI_VS")).findFirst().orElseThrow();
        assertEquals(0, sanitation.thanhTien().signum());
        assertEquals(0, sanitation.soLuong().signum());
    }

    @Test void fixedFeeUsesQuantityOne() {
        var charge = run(List.of(fee("PHI_CH", ChargeMethod.THEO_CAN_HO, "50000")), home("P1204", "80", 4)).charges().get(0);
        assertEquals(new BigDecimal("50000.00"), charge.tongTien());
        assertEquals(0, charge.lines().get(0).soLuong().compareTo(BigDecimal.ONE));
    }

    @Test void roundsEachLineHalfUp() {
        // 3 people x 6000.50 = 18001.50 -> 18002; 75.55 m2 x 7000.50 = 528,887.775 -> 528888
        var fees = List.of(fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "6000.50"),
                fee("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "7000.50"));
        var charge = run(fees, home("P1204", "75.55", 3)).charges().get(0);
        var by = charge.lines().stream().collect(java.util.stream.Collectors.toMap(
                l -> l.maKhoanThu(), l -> l.thanhTien()));
        assertEquals(new BigDecimal("18002.00"), by.get("PHI_VS"));
        assertEquals(new BigDecimal("528888.00"), by.get("PHI_QL"));
        assertEquals(new BigDecimal("546890.00"), charge.tongTien(), "total is the sum of rounded lines");
    }

    @Test void halfUpBoundaryJustBelowAndAtHalf() {
        // 0.01 m2 x 7049 = 70.49 -> 70; 0.01 m2 x 7050 = 70.50 -> 71
        var low = run(List.of(fee("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "7049")), home("P1", "0.01", 0)).charges().get(0);
        var half = run(List.of(fee("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "7050")), home("P1", "0.01", 0)).charges().get(0);
        assertEquals(new BigDecimal("70.00"), low.tongTien());
        assertEquals(new BigDecimal("71.00"), half.tongTien());
    }

    @Test void extremeAreaAndPeopleStayExact() {
        // DECIMAL(8,2) maximum area
        var charge = run(List.of(fee("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "10000"),
                fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "6000")), home("P1204", "999999.99", 50)).charges().get(0);
        assertEquals(new BigDecimal("9999999900.00").add(new BigDecimal("300000.00")), charge.tongTien());
    }

    @Test void zeroTotalHouseholdIsSkippedNotBilled() {
        var fees = List.of(fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "6000"));
        var result = run(fees, home("P1", "50", 0), home("P2", "50", 2));
        assertEquals(1, result.charges().size());
        assertEquals("P2", result.charges().get(0).soCanHo());
        assertEquals(1, result.skipped().size());
        assertEquals(SkippedHousehold.Reason.ZERO_TOTAL, result.skipped().get(0).reason());
        assertEquals("P1", result.skipped().get(0).soCanHo());
    }

    @Test void missingOrInvalidAreaSkipsOnlyThatHousehold() {
        var result = run(standardFees(), home("P1", null, 2), home("P2", "0", 2), home("P3", "80", 4));
        assertEquals(List.of("P3"), result.charges().stream().map(c -> c.soCanHo()).toList());
        assertEquals(2, result.skipped().size());
        assertTrue(result.skipped().stream().allMatch(s -> s.reason() == SkippedHousehold.Reason.MISSING_AREA));
        assertTrue(result.skipped().get(0).message().contains("cập nhật hồ sơ căn hộ"));
    }

    @Test void missingRateAbortsWholePeriodAndListsCodes() {
        var fees = List.of(fee("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "10000"),
                noRate("PHI_QL", ChargeMethod.THEO_DIEN_TICH), noRate("PHI_VS", ChargeMethod.THEO_NHAN_KHAU));
        var result = run(fees, home("P1204", "80", 4));
        assertTrue(result.charges().isEmpty());
        assertEquals(List.of("PHI_QL", "PHI_VS"), result.missingRateCodes());
    }

    @Test void vacantApartmentWithResidentsWarnsButStillCountsResidents() {
        var vacant = new HouseholdInput("HK-P1", "P1", d("60"), ApartmentStatus.CAN_HO_TRONG, 2);
        var result = BillingCalculator.compute(NOV, DUE, new BillingInputs(standardFees(), List.of(vacant)));
        assertEquals(1, result.warnings().size());
        assertEquals(BillingWarning.Code.VACANT_WITH_RESIDENTS, result.warnings().get(0).code());
        assertEquals(2, result.charges().get(0).soNhanKhau());
    }

    @Test void vacantWithoutResidentsHasNoWarning() {
        var vacant = new HouseholdInput("HK-P1", "P1", d("60"), ApartmentStatus.CAN_HO_TRONG, 0);
        var result = BillingCalculator.compute(NOV, DUE, new BillingInputs(standardFees(), List.of(vacant)));
        assertTrue(result.warnings().isEmpty());
        assertEquals(1, result.charges().size());
    }

    @Test void pricesComeFromInputsNotFromConstants() {
        // Arbitrary prices: a hard-coded 7000/10000/6000 would produce a different number.
        var fees = List.of(fee("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "123"),
                fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "7"));
        assertEquals(new BigDecimal("9868.00"), run(fees, home("P1", "80", 4)).charges().get(0).tongTien());
    }

    @Test void snapshotCarriesRateEffectiveDateAndInputs() {
        var rate = new AppliedRate("PHI_QL", d("7000"), LocalDate.of(2025, 7, 1));
        var fees = List.of(new FeeInput("PHI_QL", "Phi quan ly", ChargeMethod.THEO_DIEN_TICH, Optional.of(rate)));
        var line = run(fees, home("P1204", "80.50", 3)).charges().get(0).lines().get(0);
        assertEquals(LocalDate.of(2025, 7, 1), line.ngayApDungGia());
        assertEquals(d("7000"), line.donGia());
        assertEquals(0, line.soLuong().compareTo(d("80.50")));
        assertEquals("Phi quan ly", line.tenKhoanThu());
    }

    @Test void householdsAreIndependentAndKeepInputOrder() {
        var result = run(standardFees(), home("P1101", "50", 1), home("P1204", "80", 4));
        assertEquals(List.of("P1101", "P1204"), result.charges().stream().map(c -> c.soCanHo()).toList());
        assertEquals(new BigDecimal("856000.00"), result.charges().get(0).tongTien());
        assertEquals(new BigDecimal("1384000.00"), result.charges().get(1).tongTien());
        assertEquals(new BigDecimal("2240000.00"), result.total());
    }

    @Test void voluntaryFeeNeverBecomesALine() {
        var voluntary = new FeeInput("QUY", "Quy", ChargeMethod.TU_NGUYEN,
                Optional.of(new AppliedRate("QUY", d("1000"), EFFECTIVE)));
        assertThrows(IllegalArgumentException.class, () -> run(List.of(voluntary), home("P1", "50", 1)));
    }

    @Test void invoiceIdAndDueDateFollowThePeriod() {
        var december = BillingCalculator.compute(YearMonth.of(2026, 12), LocalDate.of(2027, 1, 15),
                new BillingInputs(standardFees(), List.of(home("P1204", "80", 4))));
        assertEquals("HD-202612-P1204", december.charges().get(0).maHoaDon());
        assertEquals("2026-12", december.charges().get(0).kyThu());
        assertEquals(LocalDate.of(2027, 1, 15), BillingCalculator.hanNop(YearMonth.of(2026, 12)));
    }

    @Test void noHouseholdsMeansNoChargesAndNoMissing() {
        var result = BillingCalculator.compute(NOV, DUE, new BillingInputs(standardFees(), List.of()));
        assertTrue(result.charges().isEmpty());
        assertTrue(result.missingRateCodes().isEmpty());
        assertEquals(BigDecimal.ZERO.setScale(2), result.total());
    }

    @Test void resultListsAreImmutable() {
        var result = run(standardFees(), home("P1204", "80", 4));
        assertThrows(UnsupportedOperationException.class, () -> result.charges().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.skipped().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.rates().clear());
    }
}
