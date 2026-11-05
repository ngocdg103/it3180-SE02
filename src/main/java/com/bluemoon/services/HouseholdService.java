package com.bluemoon.services;

import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.HouseholdForm;
import com.bluemoon.models.HouseholdProfile;
import com.bluemoon.models.HouseholdUpdateForm;
import java.util.List;

/**
 * US-001 contract for the UI (Dev-2). Controllers call only this interface, never SQL.
 * Every method may throw {@link AccessDeniedException} (role check, EP-06 BR-20):
 * ADMIN = all operations; ACCOUNTANT = read only; RESIDENT = none.
 * Calls block on JDBC: run them off the JavaFX thread.
 */
public interface HouseholdService {
    /** Creates the apartment and its household together (AC-01/AC-02). ADMIN only. */
    HouseholdResult<HouseholdProfile> createHousehold(HouseholdForm form);

    /**
     * Updates tang, dien_tich, ngay_nhan_nha, trang_thai of an existing household (AC-03). ADMIN only.
     * If dien_tich changes on an apartment that already has charges, returns CONFIRMATION_REQUIRED
     * until confirmAreaChange is true (BR-02).
     */
    HouseholdResult<HouseholdProfile> updateHousehold(String maHo, HouseholdUpdateForm form,
                                                      boolean confirmAreaChange);

    /** NOT_FOUND if ma_ho does not exist. */
    HouseholdResult<HouseholdProfile> getHouseholdById(String maHo);

    /** NOT_FOUND if the apartment has no household record. */
    HouseholdResult<HouseholdProfile> getHouseholdByApartment(String soCanHo);

    /** All households ordered by so_can_ho; filter == null for all. An empty list is a success, not an error. */
    HouseholdResult<List<HouseholdProfile>> listHouseholds(ApartmentStatus filter);
}
