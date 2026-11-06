package com.bluemoon.dao;

import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.HouseholdProfile;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for US-001. No validation or authorization here: that is the Service's job. */
public interface HouseholdRepository {
    /** Inserts apartment + household atomically (all or nothing). */
    void insert(HouseholdProfile profile) throws SQLException, DuplicateRecordException;

    Optional<HouseholdProfile> findByMaHo(String maHo) throws SQLException;

    Optional<HouseholdProfile> findBySoCanHo(String soCanHo) throws SQLException;

    /** Ordered by so_can_ho. status == null means no filter. */
    List<HouseholdProfile> findAll(ApartmentStatus status) throws SQLException;

    /** Updates the editable fields atomically. Returns false when ma_ho does not exist. */
    boolean update(String maHo, int tang, BigDecimal dienTich, LocalDate ngayNhanNha, ApartmentStatus trangThai)
            throws SQLException;
}
