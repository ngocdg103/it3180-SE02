package com.bluemoon.services;

import com.bluemoon.models.Account;
import java.util.Optional;
import java.util.Objects;

/** Immutable UI result. The public API cannot carry credential hashes. */
public final class AuthResult {
    private final AuthStatus status;
    private final String message;
    private final Account account;

    private AuthResult(AuthStatus status, String message, Account account) {
        this.status = status;
        this.message = message;
        this.account = account;
    }

    public static AuthResult success(Account account) {
        return new AuthResult(AuthStatus.SUCCESS, "Đăng nhập thành công!", Objects.requireNonNull(account));
    }

    public static AuthResult failure(AuthStatus status, String message) {
        Objects.requireNonNull(status);
        Objects.requireNonNull(message);
        if (status == AuthStatus.SUCCESS) {
            throw new IllegalArgumentException("Use success(account) for successful authentication");
        }
        return new AuthResult(status, message, null);
    }

    public AuthStatus status() { return status; }
    public boolean isSuccess() { return status == AuthStatus.SUCCESS; }
    public String message() { return message; }
    public Optional<Account> account() { return Optional.ofNullable(account); }
}
