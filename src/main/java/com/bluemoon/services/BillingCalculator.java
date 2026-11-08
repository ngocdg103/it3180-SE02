package com.bluemoon.services;

import com.bluemoon.dao.BillingInputs;
import com.bluemoon.models.AppliedRate;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.BillingWarning;
import com.bluemoon.models.ChargeLine;
import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.ChargeStatus;
import com.bluemoon.models.MonthlyCharge;
import com.bluemoon.models.SkippedHousehold;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure monthly-charge computation (EP-03 section 3.2). No I/O, no clock, no authorization.
 * Prices come only from the inputs; nothing is hard-coded. Each line is rounded HALF_UP to 1 VND (Q6).
 */
public final class BillingCalculator {
    private BillingCalculator() { }

    public record Result(List<MonthlyCharge> charges, List<SkippedHousehold> skipped, List<BillingWarning> warnings,
                         List<AppliedRate> rates, List<String> missingRateCodes) {
        public Result {
            charges = List.copyOf(charges);
            skipped = List.copyOf(skipped);
            warnings = List.copyOf(warnings);
            rates = List.copyOf(rates);
            missingRateCodes = List.copyOf(missingRateCodes);
        }

        public BigDecimal total() {
            return charges.stream().map(MonthlyCharge::tongTien).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2);
        }
    }

    /**
     * If any active fee has no rate, no charges are produced and missingRateCodes lists the codes: the whole
     * period must be aborted (Q7). Voluntary fees never reach this class (the loader returns BAT_BUOC only,
     * and ChargeLine rejects TU_NGUYEN).
     */
    public static Result compute(YearMonth period, LocalDate hanNop, BillingInputs inputs) {
        Objects.requireNonNull(period);
        Objects.requireNonNull(hanNop);
        Objects.requireNonNull(inputs);
        var missing = inputs.fees().stream().filter(f -> f.rate().isEmpty())
                .map(BillingInputs.FeeInput::maKhoanThu).toList();
        var rates = inputs.fees().stream().flatMap(f -> f.rate().stream()).toList();
        if (!missing.isEmpty()) { return new Result(List.of(), List.of(), List.of(), rates, missing); }

        String kyThu = period.toString();
        var charges = new ArrayList<MonthlyCharge>();
        var skipped = new ArrayList<SkippedHousehold>();
        var warnings = new ArrayList<BillingWarning>();
        for (var household : inputs.households()) {
            if (household.dienTich() == null || household.dienTich().signum() <= 0) {
                skipped.add(new SkippedHousehold(household.soCanHo(), SkippedHousehold.Reason.MISSING_AREA,
                        "Căn hộ " + household.soCanHo() + " chưa có diện tích hợp lệ, vui lòng cập nhật hồ sơ căn hộ."));
                continue;
            }
            if (household.trangThai() == ApartmentStatus.CAN_HO_TRONG && household.soNhanKhau() > 0) {
                warnings.add(new BillingWarning(household.soCanHo(), BillingWarning.Code.VACANT_WITH_RESIDENTS,
                        "Căn hộ " + household.soCanHo() + " ở trạng thái Căn hộ trống nhưng vẫn có nhân khẩu."));
            }
            var lines = new ArrayList<ChargeLine>();
            BigDecimal total = BigDecimal.ZERO;
            for (var fee : inputs.fees()) {
                var rate = fee.rate().orElseThrow();
                BigDecimal quantity = quantity(fee.hinhThuc(), household);
                BigDecimal amount = quantity.multiply(rate.donGia()).setScale(0, RoundingMode.HALF_UP);
                lines.add(new ChargeLine(fee.maKhoanThu(), fee.tenKhoanThu(), fee.hinhThuc(), quantity,
                        rate.donGia(), rate.ngayApDung(), amount));
                total = total.add(amount);
            }
            if (total.signum() == 0) {
                skipped.add(new SkippedHousehold(household.soCanHo(), SkippedHousehold.Reason.ZERO_TOTAL,
                        "Căn hộ " + household.soCanHo() + " có tổng phải thu bằng 0, không lập hóa đơn."));
                continue;
            }
            charges.add(new MonthlyCharge("HD-" + kyThu.replace("-", "") + "-" + household.soCanHo(), kyThu,
                    household.maHo(), household.soCanHo(), household.dienTich(), household.soNhanKhau(),
                    total, BigDecimal.ZERO, total, ChargeStatus.CHUA_DONG, hanNop, lines));
        }
        return new Result(charges, skipped, warnings, rates, List.of());
    }

    private static BigDecimal quantity(ChargeMethod method, BillingInputs.HouseholdInput household) {
        return switch (method) {
            case THEO_DIEN_TICH -> household.dienTich();
            case THEO_NHAN_KHAU -> BigDecimal.valueOf(household.soNhanKhau());
            case THEO_CAN_HO -> BigDecimal.ONE;
            case TU_NGUYEN -> throw new IllegalArgumentException("Voluntary fees are not billed monthly");
        };
    }

    /** The default due date used by the service: 15th of the next month. */
    public static LocalDate hanNop(YearMonth period) { return BillingPeriod.defaultHanNop(period); }
}
