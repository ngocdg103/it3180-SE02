package com.bluemoon.controllers;

import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.dao.ReceiptDAO;
import com.bluemoon.services.*;
import com.bluemoon.views.DashboardModule;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Parent;
import java.time.Clock;

/** PO may register this factory under PAYMENT_HISTORY_VIEW_ALL. No ProductionModules edits here. */
public final class ReceiptModule implements DashboardModule {
    private final ReceiptController controller;
    private final Parent view;
    public static ReceiptService createService(DatabaseConnection db) { return new ReceiptServiceImpl(new ReceiptDAO(db),new RoleBasedAuthorizationService(null),Clock.systemUTC()); }
    public static ReceiptModule open(DatabaseConnection db) { return new ReceiptModule(createService(db),new RoleBasedAuthorizationService(null)); }
    ReceiptModule(ReceiptService service, AuthorizationService authorization) {
        if(!Platform.isFxApplicationThread()) { throw new IllegalStateException("FX thread required"); }
        controller=new ReceiptController(service,authorization);
        try { view=ReceiptController.load(controller); } catch(RuntimeException|Error failure) { controller.dispose(); throw failure; }
    }
    public Parent view() { return view; }
    public ObservableBooleanValue busyProperty() { return controller.busyProperty(); }
    public void close() { controller.dispose(); }
}
