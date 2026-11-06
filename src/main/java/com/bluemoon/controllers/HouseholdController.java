package com.bluemoon.controllers;

import com.bluemoon.services.*;
import com.bluemoon.views.HouseholdNavigator;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared UI task lifecycle. Detached views cannot apply late results. */
public abstract class HouseholdController {
    protected final HouseholdService service;
    protected final HouseholdNavigator navigator;
    protected boolean busy;
    private boolean active = true;
    @FXML protected Label message;
    @FXML private ProgressIndicator progress;
    protected HouseholdController(HouseholdService service, HouseholdNavigator navigator) {
        this.service = service;
        this.navigator = navigator;
    }
    public final void deactivate() { active = false; clearData(); }
    protected abstract void busyChanged(boolean value);
    protected abstract void clearData();
    protected final <T> void execute(Supplier<HouseholdResult<T>> operation,
                                     Consumer<HouseholdResult<T>> completed) {
        if (busy || !active) return;
        setBusy(true);
        message.setText("Đang xử lý…");
        Task<HouseholdResult<T>> task = new Task<>() {
            @Override protected HouseholdResult<T> call() { return operation.get(); }
        };
        task.setOnSucceeded(event -> {
            if (!active) return;
            setBusy(false);
            HouseholdResult<T> result = task.getValue();
            if (result == null) { message.setText("Lỗi hệ thống, vui lòng thử lại sau."); return; }
            message.setText(result.message());
            completed.accept(result);
        });
        task.setOnFailed(event -> {
            if (!active) return;
            setBusy(false);
            if (task.getException() instanceof AccessDeniedException) {
                clearData();
                message.setText("Bạn không có quyền thực hiện chức năng này.");
            } else {
                message.setText("Lỗi hệ thống, vui lòng thử lại sau.");
            }
        });
        Thread worker = new Thread(task, "bluemoon-household");
        worker.setDaemon(true);
        worker.start();
    }
    private void setBusy(boolean value) {
        busy = value;
        progress.setVisible(value);
        progress.setManaged(value);
        busyChanged(value);
    }
}
