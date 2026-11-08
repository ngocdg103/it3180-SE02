package com.bluemoon.dao;

import com.bluemoon.models.FeeRate;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Standalone persistence boundary. Never use these calls inside a billing transaction. */
public interface FeeRateRepository {
    /** New rate must have null id. Returns generated id after commit.
     * SQLState 45000: existing parent is not active/mandatory; 02000: parent missing.
     * JDBC failures retain their original vendor code and SQLState. */
    FeeRate insert(FeeRate newRate) throws SQLException;
    /** Immutable history, effective date descending; unknown code returns an empty list. */
    List<FeeRate> findHistory(String maLoaiThu) throws SQLException;
    /** Latest effective date <= targetDate, or empty; never falls back to a default price. */
    Optional<FeeRate> findEffectiveRate(String maLoaiThu, LocalDate targetDate) throws SQLException;
}
