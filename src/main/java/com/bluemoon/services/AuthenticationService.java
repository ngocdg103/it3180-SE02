package com.bluemoon.services;

import com.bluemoon.models.Account;
import java.util.Optional;

/** UI boundary: inject AuthService in production or a Dev-2 mock without JDBC. */
public interface AuthenticationService {
    AuthResult login(String username, String password);
    void logout();
    Optional<Account> currentUser();
}
