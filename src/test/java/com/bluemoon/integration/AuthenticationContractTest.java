package com.bluemoon.integration;

import com.bluemoon.models.Account;
import com.bluemoon.models.Role;
import com.bluemoon.services.AuthenticationService;
import com.bluemoon.services.AuthResult;
import com.bluemoon.services.AuthStatus;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** A consumer outside the services package can implement Dev-2's mock. */
class AuthenticationContractTest {
    @Test void mockCanBeInjectedWithoutDatabaseOrRealServiceConstruction() {
        AuthenticationService service = new MockAuthService();
        assertTrue(service.login("admin", "mock-password").isSuccess());
        assertEquals(Role.ADMIN, service.currentUser().orElseThrow().role());
        service.logout();
        assertTrue(service.currentUser().isEmpty());
        var failed = service.login("other", "wrong-password");
        assertEquals(AuthStatus.INVALID_CREDENTIALS, failed.status());
        assertTrue(failed.account().isEmpty());
    }

    @Test void mockCanCreateEveryFailureStatusButCannotProduceInconsistentSuccess() {
        for (AuthStatus status : AuthStatus.values()) {
            if (status != AuthStatus.SUCCESS) {
                var result = AuthResult.failure(status, "Mock scenario");
                assertEquals(status, result.status());
                assertFalse(result.isSuccess());
                assertTrue(result.account().isEmpty());
            }
        }
        assertThrows(NullPointerException.class, () -> AuthResult.success(null));
        assertThrows(IllegalArgumentException.class, () -> AuthResult.failure(AuthStatus.SUCCESS, "invalid"));
    }

    private static final class MockAuthService implements AuthenticationService {
        private Account current;
        public AuthResult login(String username, String password) {
            current = null;
            if ("admin".equals(username) && "mock-password".equals(password)) {
                current = new Account(1, "admin", "Admin", Role.ADMIN);
                return AuthResult.success(current);
            }
            return AuthResult.failure(AuthStatus.INVALID_CREDENTIALS, "Tài khoản hoặc mật khẩu không chính xác!");
        }
        public void logout() { current = null; }
        public Optional<Account> currentUser() { return Optional.ofNullable(current); }
    }
}
