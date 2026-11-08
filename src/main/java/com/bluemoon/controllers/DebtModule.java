package com.bluemoon.controllers;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.dao.DebtDAO;
import com.bluemoon.services.*;
import com.bluemoon.views.DashboardModule;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Parent;

/** PO registers this factory under DEBT_REPORT_VIEW; no concurrent edits to ProductionModules. */
public final class DebtModule implements DashboardModule {
    private final DebtController controller;
    private final Parent view;
    public static DebtService createService(DatabaseConnection db) { return new DebtServiceImpl(new DebtDAO(db),new RoleBasedAuthorizationService(null)); }
    public static DebtModule open(DatabaseConnection db) { return new DebtModule(createService(db),new RoleBasedAuthorizationService(null)); }
    DebtModule(DebtService service,AuthorizationService authorization) {
        if(!Platform.isFxApplicationThread()) { throw new IllegalStateException("FX thread required"); }
        controller=new DebtController(service,authorization); try { view=DebtController.load(controller); } catch(RuntimeException|Error failure) { controller.dispose(); throw failure; }
    }
    public Parent view() { return view; }
    public ObservableBooleanValue busyProperty() { return controller.busyProperty(); }
    public void close() { controller.dispose(); }
}
