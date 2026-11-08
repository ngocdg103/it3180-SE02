package com.bluemoon.services;

import com.bluemoon.models.*;
import java.util.List;

public interface ResidentSearchService {
    /** Requires RESIDENT_PROFILE_VIEW before validation/data access; blocks on JDBC, run off FX. */
    ResidentResult<List<ResidentSearchHit>> search(ResidentSearchCriteria.Field field,String keyword);
}
