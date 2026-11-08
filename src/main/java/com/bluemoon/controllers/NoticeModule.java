package com.bluemoon.controllers;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.dao.BillingDAO;
import com.bluemoon.dao.ResidentDAO;
import com.bluemoon.models.Permission;
import com.bluemoon.models.Resident;
import com.bluemoon.services.AuthorizationService;
import com.bluemoon.services.BillingService;
import com.bluemoon.services.BillingServiceImpl;
import com.bluemoon.services.NoticeService;
import com.bluemoon.services.NoticeServiceImpl;
import com.bluemoon.services.RoleBasedAuthorizationService;
import com.bluemoon.views.DashboardModule;
import com.bluemoon.views.ModuleRegistration;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Parent;

/**
 * US-011 screen as a dashboard module. Creates no Stage or Scene; the file dialog uses the host window found
 * through this module's view. Composition performs no JDBC or configuration I/O.
 *
 * Hand-off to the App Shell owner (one line in ProductionModules.create, not edited here):
 * {@code NoticeModule.registration(connections)}.
 */
public final class NoticeModule implements DashboardModule {
    private final NoticeController controller;
    private final Parent view;

    public static ModuleRegistration registration(DatabaseConnection connections) {
        Objects.requireNonNull(connections);
        return new ModuleRegistration("notices", "Thông báo thu phí", Permission.DEBT_REPORT_VIEW,
                () -> open(connections));
    }

    public static NoticeModule open(DatabaseConnection connections) {
        requireFx();
        var authorization = new RoleBasedAuthorizationService(null);
        var billing = new BillingServiceImpl(new BillingDAO(Objects.requireNonNull(connections)), authorization,
                Clock.systemUTC());
        var residents = new ResidentDAO(connections);
        NoticeService notices = new NoticeServiceImpl(billing,
                maHo -> residents.findHead(maHo).map(Resident::hoTen), authorization, Clock.systemUTC());
        var window = new AtomicReference<Parent>();
        var controller = new NoticeController(notices, billing, authorization, Clock.systemUTC(), () -> {
            var root = window.get();
            return root == null || root.getScene() == null ? null : root.getScene().getWindow();
        });
        var module = new NoticeModule(controller);
        window.set(module.view);
        return module;
    }

    NoticeModule(NoticeController controller) {
        requireFx();
        this.controller = controller;
        try { view = NoticeController.load(controller); }
        catch (RuntimeException | Error failure) { controller.dispose(); throw failure; }
    }

    /** Test seam over real services. */
    static NoticeModule over(NoticeService notices, BillingService billing, AuthorizationService authorization,
                             Clock clock, NoticeController.FileSelector selector) {
        return new NoticeModule(new NoticeController(notices, billing, authorization, clock,
                java.util.concurrent.Executors.newSingleThreadExecutor(job -> {
                    var thread = new Thread(job, "test-notice"); thread.setDaemon(true); return thread;
                }), true, selector));
    }

    @Override public Parent view() { return view; }
    @Override public ObservableBooleanValue busyProperty() { return controller.busyProperty(); }

    @Override public void close() {
        requireFx();
        controller.dispose();
    }

    private static void requireFx() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Notice screen lifecycle requires the JavaFX thread");
        }
    }
}
