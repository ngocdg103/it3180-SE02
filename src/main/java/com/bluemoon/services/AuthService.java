package com.bluemoon.services;

import com.bluemoon.dao.AccountRepository;
import com.bluemoon.dao.AccountRepository.State;
import com.bluemoon.models.Account;
import org.mindrot.jbcrypt.BCrypt;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** US-012 only: authentication, consecutive failures, timed lock and session. */
public final class AuthService implements AuthenticationService {
    // SRS EP-06 BR-18 says "quá 5 lần liên tiếp"; SRS is the primary source.
    private static final int LOCK_THRESHOLD = 6;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    private static final String INVALID_MESSAGE = "Tài khoản hoặc mật khẩu không chính xác!";
    private static final String DUMMY_HASH = BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt(12));
    private static final Object AUTH_LOCK = new Object();
    private final AccountRepository accounts;
    private final Clock clock;

    public AuthService(AccountRepository accounts) { this(accounts, Clock.systemUTC()); }

    public AuthService(AccountRepository accounts, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public AuthResult login(String username, String password) {
        // Serialize session operations across service instances in this desktop process.
        synchronized (AUTH_LOCK) {
            SessionContext.clear();
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                return AuthResult.failure(AuthStatus.VALIDATION_ERROR,
                        "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            }
            if (!username.matches("[A-Za-z0-9]{4,50}") || password.length() < 6) {
                return AuthResult.failure(AuthStatus.VALIDATION_ERROR,
                        "Tên đăng nhập phải gồm 4–50 chữ cái không dấu hoặc chữ số; mật khẩu tối thiểu 6 ký tự!");
            }
            try {
                AuthResult result = accounts.inTransaction(username, (stored, updater) -> {
                    if (stored.isEmpty()) {
                        BCrypt.checkpw(password, DUMMY_HASH);
                        return invalidCredentials();
                    }
                    var user = stored.get();
                    var now = clock.instant();
                    int failures = user.failedAttempts();
                    if (user.state() == State.LOCKED) {
                        if (user.lockedUntil() == null || now.isBefore(user.lockedUntil())) {
                            return locked();
                        }
                        failures = 0;
                    }
                    if (BCrypt.checkpw(password, user.passwordHash())) {
                        updater.update(user.account().userId(), State.ACTIVE, 0, null);
                        return AuthResult.success(user.account());
                    }
                    failures++;
                    if (failures >= LOCK_THRESHOLD) {
                        updater.update(user.account().userId(), State.LOCKED, failures, now.plus(LOCK_DURATION));
                        return locked();
                    }
                    updater.update(user.account().userId(), State.ACTIVE, failures, null);
                    return invalidCredentials();
                });
                // Publish session only after the DAO has successfully committed.
                result.account().ifPresent(SessionContext::setCurrentUser);
                return result;
            } catch (SQLException | RuntimeException failure) {
                return AuthResult.failure(AuthStatus.SYSTEM_ERROR,
                        "Không thể đăng nhập do lỗi hệ thống. Vui lòng thử lại sau!");
            }
        }
    }

    @Override
    public void logout() {
        synchronized (AUTH_LOCK) { SessionContext.clear(); }
    }
    @Override
    public Optional<Account> currentUser() { return SessionContext.getCurrentUser(); }

    private static AuthResult invalidCredentials() {
        return AuthResult.failure(AuthStatus.INVALID_CREDENTIALS, INVALID_MESSAGE);
    }

    private static AuthResult locked() {
        return AuthResult.failure(AuthStatus.ACCOUNT_LOCKED, "Tài khoản của bạn đã bị tạm khóa!");
    }
}
