package com.bluemoon.controllers;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.dao.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import java.util.Objects;

/** Embeddable production composition; the navigation host owns Stage/Scene and closes the module on logout. */
public final class FeeRateModule implements AutoCloseable {
    private final FeeRateController controller;
    private final Parent view;
    public static FeeRateModule open(DatabaseConnection connections) {
        requireFx();
        var authorization = new RoleBasedAuthorizationService(null);
        return new FeeRateModule(createService(connections, authorization), authorization);
    }
    /** Construction performs no configuration/JDBC I/O. */
    public static FeeRateService createService(DatabaseConnection connections, AuthorizationService authorization) {
        Objects.requireNonNull(connections);
        return new FeeRateServiceImpl(new FeeRateDAO(connections), new FeeTypeDAO(connections), authorization);
    }
    /** Shared standalone lookup; never use inside a BillingTx. Construction opens no connection. */
    public static FeeRateLookup createLookup(DatabaseConnection connections, AuthorizationService authorization) {
        return new RepositoryFeeRateLookup(new FeeRateDAO(Objects.requireNonNull(connections)), authorization);
    }
    FeeRateModule(FeeRateService service, AuthorizationService authorization) {
        requireFx(); controller = new FeeRateController(service, authorization);
        try { view = FeeRateController.load(controller); }
        catch (RuntimeException | Error failure) { controller.dispose(); throw failure; }
    }
    public Parent view() { return view; }
    @Override public void close() { requireFx(); controller.dispose(); }
    private static void requireFx() {
        if (!Platform.isFxApplicationThread()) { throw new IllegalStateException("Fee rate lifecycle requires FX thread"); }
    }
}
