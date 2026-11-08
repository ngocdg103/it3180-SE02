package com.bluemoon.views;

import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Parent;

/** FX-thread lifecycle for a module hosted in the existing dashboard Scene. */
public interface DashboardModule extends AutoCloseable {
    Parent view();
    ObservableBooleanValue busyProperty();
    @Override void close();
}
