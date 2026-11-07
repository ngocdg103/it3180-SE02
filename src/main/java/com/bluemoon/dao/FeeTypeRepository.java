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
    Optional<FeeType> update(FeeType fee) throws SQLException;
    Optional<FeeType> deactivate(String code) throws SQLException;
}
