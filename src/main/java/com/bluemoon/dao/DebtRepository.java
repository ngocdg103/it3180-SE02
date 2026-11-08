package com.bluemoon.dao;

import com.bluemoon.models.*;
import java.sql.SQLException;
import java.util.*;

public interface DebtRepository {
    record PeriodData(String kyThu, List<DebtRow> rows) { public PeriodData { rows=List.copyOf(rows); } }
    /** null requests newest period. Empty means no such billing period. Entire read uses one RR transaction. */
    Optional<PeriodData> loadPeriod(String kyThu) throws SQLException;
    Optional<HouseholdDebt> getHouseholdBalance(String maHo) throws SQLException;
}
