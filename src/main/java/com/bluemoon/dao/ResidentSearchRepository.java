package com.bluemoon.dao;

import com.bluemoon.models.*;
import java.sql.SQLException;
import java.util.List;

/** Read-only extension of the existing ResidentDAO; no new write path. */
public interface ResidentSearchRepository {
    List<ResidentSearchHit> search(ResidentSearchCriteria criteria) throws SQLException;
}
