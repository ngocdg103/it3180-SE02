package com.bluemoon.controllers;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.dao.FeeTypeDAO;
import com.bluemoon.services.FeeTypeService;
import com.bluemoon.services.FeeTypeServiceImpl;
import javafx.application.Platform;
import javafx.scene.Parent;
import java.util.Objects;

/**
 * Production composition for a future navigation host. Creates no Stage or Scene.
 * Load DatabaseConfig before entering the FX thread; constructing the DAO opens no connection.
 * The host must close this module on the FX thread when leaving, logging out, or closing its window.
 */
public final class FeeCategoryModule implements com.bluemoon.views.DashboardModule {
    private final FeeCategoryController controller;
    private final Parent view;

    public static FeeCategoryModule open(DatabaseConnection connections) {
        requireFxThread();
        return new FeeCategoryModule(createService(connections));
    }

    /** No configuration I/O or JDBC is performed during composition. Uses existing session RBAC. */
    public static FeeTypeService createService(DatabaseConnection connections) {
        return new FeeTypeServiceImpl(new FeeTypeDAO(Objects.requireNonNull(connections)));
    }

    // Test seam: real service with a controlled repository; production open always uses JDBC above.
    FeeCategoryModule(FeeTypeService service) {
        requireFxThread();
        controller = new FeeCategoryController(service);
        try { view = FeeCategoryController.load(controller); }
        catch (RuntimeException | Error failure) { controller.dispose(); throw failure; }
    }

    public Parent view() { return view; }
    @Override public javafx.beans.value.ObservableBooleanValue busyProperty() { return controller.busyProperty(); }

    /** Idempotent. In-flight writes may still commit; subsequent UI callbacks are discarded. */
    @Override public void close() {
        requireFxThread();
        controller.dispose();
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Fee category screen lifecycle requires the JavaFX thread");
        }
    }
}
