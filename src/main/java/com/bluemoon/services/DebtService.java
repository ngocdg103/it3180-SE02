package com.bluemoon.services;

import com.bluemoon.models.*;

/** Requires DEBT_REPORT_VIEW; ADMIN/ACCOUNTANT only. Blocking calls must run off JavaFX thread. */
public interface DebtService {
    DebtResult<DebtReport> getDebtByPeriod(DebtQuery query);
    DebtResult<HouseholdDebt> getHouseholdBalance(String maHo);
}
