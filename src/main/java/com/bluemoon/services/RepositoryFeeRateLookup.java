package com.bluemoon.services;

import com.bluemoon.dao.FeeRateLookup;
import com.bluemoon.dao.FeeRateRepository;
import com.bluemoon.models.AppliedRate;
import com.bluemoon.models.FeeRate;
import com.bluemoon.models.Permission;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Standalone adapter only; never call it inside BillingTx (repository opens a fresh connection). */
public final class RepositoryFeeRateLookup implements FeeRateLookup {
    private final FeeRateRepository repository;
    private final AuthorizationService authorization;
    public RepositoryFeeRateLookup(FeeRateRepository repository, AuthorizationService authorization) {
        this.repository = Objects.requireNonNull(repository);
        this.authorization = Objects.requireNonNull(authorization);
    }
    @Override public Optional<AppliedRate> getEffectiveRate(String code, LocalDate date) {
        authorization.requirePermission(Permission.FEE_RATE_VIEW);
        FeeRate.requireCode(code);
        FeeRate.requireDate(date);
        try {
            return repository.findEffectiveRate(code, date)
                    .map(rate -> new AppliedRate(rate.maLoaiThu(), rate.donGia(), rate.ngayApDung()));
        } catch (SQLException failure) {
            throw new IllegalStateException("Không thể tra cứu đơn giá.", failure);
        }
    }
}
