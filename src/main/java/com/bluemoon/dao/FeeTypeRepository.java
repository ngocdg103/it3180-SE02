package com.bluemoon.dao;

import com.bluemoon.models.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Persistence boundary; deliberately exposes no delete operation. */
public interface FeeTypeRepository {
    List<FeeType> findAll(FeeStatus filter) throws SQLException;
    Optional<FeeType> findByCode(String code) throws SQLException;
    void insert(FeeType fee) throws SQLException;
    /** Atomically creates the category and its first dated rate; never split into two DAO calls. */
    default void insert(FeeType fee, java.time.LocalDate initialDate) throws SQLException {
        if (initialDate != null) {
            throw new java.sql.SQLFeatureNotSupportedException("Atomic initial rate insert is required");
        }
        insert(fee);
    }
    Optional<FeeType> update(FeeType fee) throws SQLException;
    Optional<FeeType> deactivate(String code) throws SQLException;
}
