package com.bluemoon.services;

import com.bluemoon.models.Account;
import com.bluemoon.models.Role;
import java.util.Optional;

/** BR-20: application-wide desktop session, with no password data. */
public final class SessionContext {
    private static Account currentUser;

    private SessionContext() { }

    public static synchronized Optional<Account> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public static synchronized Role getCurrentRole() {
        if (currentUser == null) { throw new IllegalStateException("No authenticated session"); }
        return currentUser.role();
    }

    static synchronized void setCurrentUser(Account account) { currentUser = account; }
    static synchronized void clear() { currentUser = null; }
}
