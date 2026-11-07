package com.bluemoon.dao;

import com.bluemoon.models.Resident;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for US-002. Format validation and authorization belong to the Service; the rules that
 * must hold atomically with the write (household exists, apartment not vacant, first member is the head,
 * one head, confirmation still current, uniqueness) are decided here inside one locked transaction.
 */
public interface ResidentRepository {
    enum HeadPolicy { REJECT_IF_HEAD_EXISTS, REPLACE_HEAD_IF_UNCHANGED }

    sealed interface InsertOutcome permits Added, HeadExists, HeadChanged, HouseholdMissing, ApartmentVacant,
            FirstMemberMustBeHead { }

    record Added(Resident resident) implements InsertOutcome { }

    /** A head exists and no confirmation was given. Nothing written. */
    record HeadExists(Resident currentHead) implements InsertOutcome { }

    /**
     * Stale confirmation: expectedHeadId is not the current head (another person, or the household has no head).
     * Nothing written.
     */
    record HeadChanged(Optional<Resident> currentHead) implements InsertOutcome { }

    record HouseholdMissing() implements InsertOutcome { }

    /** Apartment status is CAN_HO_TRONG. Nothing written. */
    record ApartmentVacant() implements InsertOutcome { }

    /** The household has no residents yet and the new one is not CHU_HO. Nothing written. */
    record FirstMemberMustBeHead() implements InsertOutcome { }

    /**
     * Adds a resident (maNhanKhau is ignored). expectedHeadId is the ma_nhan_khau of the head the user confirmed
     * replacing, or null. It is only used when the new resident is CHU_HO; the old head becomes KHAC atomically.
     */
    InsertOutcome insert(Resident resident, HeadPolicy policy, Long expectedHeadId)
            throws SQLException, ResidentDuplicateException;

    /** Head first, then by ma_nhan_khau. */
    List<Resident> findByMaHo(String maHo) throws SQLException;

    Optional<Resident> findHead(String maHo) throws SQLException;

    /** Resident records of the household (Sprint 1 has no move-out, so all of them). Used by US-007 (BR-05). */
    int countByMaHo(String maHo) throws SQLException;
}
