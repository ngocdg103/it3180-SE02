package com.bluemoon.services;

import com.bluemoon.models.Account;
import com.bluemoon.models.Role;

/** Test-only access to the same session consumed by production RBAC. */
public final class FeeCategorySessionFixture {
    private FeeCategorySessionFixture() { }
    public static void use(Role role) {
        if (role == null) { SessionContext.clear(); }
        else { SessionContext.setCurrentUser(new Account(71, "fee-test", "Fee test", role)); }
    }
}
