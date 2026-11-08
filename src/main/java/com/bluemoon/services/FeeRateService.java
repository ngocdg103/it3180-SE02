package com.bluemoon.services;

import com.bluemoon.models.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Append-only US-006 API. CREATE for createRate; VIEW for all reads. Authorization precedes validation/I/O.
 * AccessDeniedException is propagated for missing session/permission. Calls belong off the FX thread.
 * getEffectiveRate SUCCESS may contain Optional.empty (missing rate); DB errors are never empty success.
 */
public interface FeeRateService {
    /** All mandatory fee types, including stopped ones for historical consultation. */
    FeeRateResult<List<FeeType>> listFeeTypes();
    FeeRateResult<List<FeeRate>> listHistory(String maKhoanThu);
    FeeRateResult<Optional<FeeRate>> getEffectiveRate(String maKhoanThu, LocalDate atDate);
    /** Always inserts a new rate. Duplicate code/date is rejected; historical rows are never overwritten. */
    FeeRateResult<FeeRate> createRate(FeeRateForm form);
}
