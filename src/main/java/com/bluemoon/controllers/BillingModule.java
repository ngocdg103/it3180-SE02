package com.bluemoon.controllers;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.dao.BillingDAO;
import com.bluemoon.models.Permission;
import com.bluemoon.services.AuthorizationService;
import com.bluemoon.services.BillingService;
import com.bluemoon.services.BillingServiceImpl;
import com.bluemoon.services.RoleBasedAuthorizationService;
import com.bluemoon.views.DashboardModule;
import com.bluemoon.views.ModuleRegistration;
import java.time.Clock;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Parent;

/**
 * US-007 screen as a dashboard module. Creates no Stage or Scene. Composition performs no JDBC or configuration I/O.
 * The host closes it on the FX thread when leaving the page, logging out or closing the window.
 *
 * Hand-off to the App Shell owner (one line in ProductionModules.create, not edited here):
 * {@code BillingModule.registration(connections)}.
 */
public final class BillingModule implements DashboardModule {
    private final BillingController controller;
    private final Parent view;

    /** Registration for ProductionModules. Visible to roles that may view billing data (ADMIN, ACCOUNTANT). */
    public static ModuleRegistration registration(DatabaseConnection connections) {
        Objects.requireNonNull(connections);
        return new ModuleRegistration("billing", "Lập kỳ thu / Tính phí", Permission.DEBT_REPORT_VIEW,
                () -> open(connections));
    }

    public static BillingModule open(DatabaseConnection connections) {
        requireFx();
        var authorization = new RoleBasedAuthorizationService(null);
        return new BillingModule(createService(connections, authorization), authorization, Clock.systemUTC());
    }

    /** Real service over the real DAO. Constructing it opens no connection. */
    public static BillingService createService(DatabaseConnection connections, AuthorizationService authorization) {
        return new BillingServiceImpl(new BillingDAO(Objects.requireNonNull(connections)), authorization,
                Clock.systemUTC());
    }

    BillingModule(BillingService service, AuthorizationService authorization, Clock clock) {
        this(new BillingController(service, authorization, clock));
    }

    BillingModule(BillingController controller) {
        requireFx();
        this.controller = controller;
        try { view = BillingController.load(controller); }
        catch (RuntimeException | Error failure) { controller.dispose(); throw failure; }
    }

    @Override public Parent view() { return view; }
    @Override public ObservableBooleanValue busyProperty() { return controller.busyProperty(); }

    @Override public void close() {
        requireFx();
        controller.dispose();
    }

    private static void requireFx() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Billing screen lifecycle requires the JavaFX thread");
        }
    }
}
