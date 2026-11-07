package com.bluemoon.services;

import com.bluemoon.models.HouseholdMembers;
import com.bluemoon.models.Resident;
import com.bluemoon.models.ResidentForm;

/**
 * US-002 contract for the UI (Dev-2). Controllers call only this interface, never SQL.
 * Both methods throw {@link AccessDeniedException} before touching data when the role may not run them.
 * Calls block on JDBC: run them off the JavaFX thread.
 */
public interface ResidentService {
    /**
     * Requires Permission.RESIDENT_PROFILE_CREATE (ADMIN). Adds a resident to a household (AC-01..03, BR-04).
     * <p>The first resident of a household must be CHU_HO. If the form is CHU_HO and the household already has a
     * head, returns CONFIRMATION_REQUIRED (HEAD_EXISTS, currentHead()); to replace, call again with
     * confirmReplaceHeadId = that head's maNhanKhau (the old head becomes KHAC). If the head changed in the
     * meantime the result is CONFIRMATION_REQUIRED (CONFIRMATION_STALE) and nothing is written: ask again, never
     * reuse the old id. Pass null when nothing was confirmed.
     */
    ResidentResult<Resident> createForHousehold(ResidentForm form, Long confirmReplaceHeadId);

    /** Requires Permission.RESIDENT_PROFILE_VIEW (ADMIN, ACCOUNTANT). NOT_FOUND if the household does not exist. */
    ResidentResult<HouseholdMembers> getHousehold(String maHo);
}
