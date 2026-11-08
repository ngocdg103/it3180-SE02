package com.bluemoon.dao;

import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.AppliedRate;
import com.bluemoon.models.ChargeMethod;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything one billing computation reads, loaded on a single connection/snapshot by BillingTx.loadInputs.
 * Pure data: no I/O and no business rules.
 */
public record BillingInputs(List<FeeInput> fees, List<HouseholdInput> households) {
    public BillingInputs {
        fees = List.copyOf(Objects.requireNonNull(fees));
        households = List.copyOf(Objects.requireNonNull(households));
    }

    /** An active mandatory fee type with its rate in force at the first day of the period (empty = missing). */
    public record FeeInput(String maKhoanThu, String tenKhoanThu, ChargeMethod hinhThuc, Optional<AppliedRate> rate) {
        public FeeInput {
            Objects.requireNonNull(maKhoanThu);
            Objects.requireNonNull(tenKhoanThu);
            Objects.requireNonNull(hinhThuc);
            Objects.requireNonNull(rate);
        }
    }

    /**
     * A billable household. dienTich is null only if stored data is broken (BR-11 defensive case).
     * soNhanKhau counts residents with ngay_chuyen_den on or before the last day of the period.
     */
    public record HouseholdInput(String maHo, String soCanHo, BigDecimal dienTich, ApartmentStatus trangThai,
                                 int soNhanKhau) {
        public HouseholdInput {
            Objects.requireNonNull(maHo);
            Objects.requireNonNull(soCanHo);
            Objects.requireNonNull(trangThai);
        }
    }
}
