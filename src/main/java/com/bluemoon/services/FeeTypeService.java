package com.bluemoon.services;

import com.bluemoon.models.FeeStatus;
import com.bluemoon.models.FeeType;
import com.bluemoon.models.FeeTypeForm;
import java.util.List;

/**
 * US-005 contract (approved by Lead Dev; see docs/design/us-005-fee-category-contract.md).
 * Owner of this interface: Dev-3. Implementation (DAO, validation, authorization): Dev-2.
 * ADMIN and ACCOUNTANT may manage; any other role or no session throws AccessDeniedException.
 * Calls may block on JDBC: do not call from the JavaFX Application Thread.
 */
public interface FeeTypeService {
    /** filter == null means all. An empty list is still SUCCESS. */
    FeeTypeResult<List<FeeType>> listFeeTypes(FeeStatus filter);

    FeeTypeResult<FeeType> getFeeType(String maKhoanThu);

    /** AC-01, AC-02, BR-06. Duplicate code returns DUPLICATE with fieldErrors["ma_khoan_thu"]. */
    FeeTypeResult<FeeType> createFeeType(FeeTypeForm form);

    /** Declared for the contract; not used by the UI in Iteration 02. ma_khoan_thu is immutable. */
    FeeTypeResult<FeeType> updateFeeType(String maKhoanThu, FeeTypeForm form);

    /** Declared for the contract; BR-07: only sets NGUNG_AP_DUNG, never deletes. */
    FeeTypeResult<FeeType> deactivateFeeType(String maKhoanThu);
}
