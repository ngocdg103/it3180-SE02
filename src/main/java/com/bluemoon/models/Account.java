package com.bluemoon.models;

import java.util.Objects;

/** Safe identity for UI/session. Never contains password hashes or login state. */
public record Account(long userId, String username, String hoTen, Role role) {
    public Account {
        Objects.requireNonNull(username);
        Objects.requireNonNull(hoTen);
        Objects.requireNonNull(role);
    }
}
