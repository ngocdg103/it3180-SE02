package com.bluemoon.views;

import com.bluemoon.models.Permission;
import java.util.Objects;
import java.util.function.Supplier;

/** Add future production modules here only after their owner hands off composition/lifecycle. */
public record ModuleRegistration(String id, String title, Permission permission,
                                 Supplier<DashboardModule> open) {
    public ModuleRegistration {
        Objects.requireNonNull(id); Objects.requireNonNull(title);
        Objects.requireNonNull(permission); Objects.requireNonNull(open);
    }
}
