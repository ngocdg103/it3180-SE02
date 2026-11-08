package com.bluemoon.views;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.controllers.FeeCategoryModule;
import com.bluemoon.dao.ApartmentChargeLookup;
import com.bluemoon.dao.HouseholdDAO;
import com.bluemoon.models.Permission;
import com.bluemoon.services.HouseholdServiceImpl;
import java.util.List;

/** Composition only: existing services/DAOs; constructors perform no JDBC or configuration I/O. */
public final class ProductionModules {
    private ProductionModules() { }
    public static List<ModuleRegistration> create(DatabaseConnection connections) {
        return List.of(
            new ModuleRegistration("households", "Hộ gia đình / Căn hộ", Permission.HOUSEHOLD_VIEW,
                () -> new HouseholdNavigator(new HouseholdServiceImpl(new HouseholdDAO(connections),
                    ApartmentChargeLookup.NONE))),
            new ModuleRegistration("feeCategories", "Danh mục khoản thu", Permission.FEE_CATEGORY_VIEW,
                () -> FeeCategoryModule.open(connections)));
    }
}
