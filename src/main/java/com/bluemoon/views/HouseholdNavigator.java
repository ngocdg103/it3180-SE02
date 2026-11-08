package com.bluemoon.views;

import com.bluemoon.controllers.*;
import com.bluemoon.models.HouseholdProfile;
import com.bluemoon.services.HouseholdService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Composition root supplies the authenticated service and a host for module views.
 * This class never creates a session, database connection or production mock.
 * All navigation/lifecycle methods must be called on the JavaFX thread.
 */
public final class HouseholdNavigator implements DashboardModule {
    private final HouseholdService service;
    private final Consumer<Parent> host;
    private HouseholdController current;
    private final List<Dialog<?>> dialogs = new ArrayList<>();
    private final javafx.scene.layout.StackPane content = new javafx.scene.layout.StackPane();
    private final javafx.beans.property.ReadOnlyBooleanWrapper busy = new javafx.beans.property.ReadOnlyBooleanWrapper();
    private final com.bluemoon.services.AuthorizationService authorization = new com.bluemoon.services.RoleBasedAuthorizationService(null);
    public HouseholdNavigator(HouseholdService service) {
        this.service = Objects.requireNonNull(service); this.host = root -> content.getChildren().setAll(root);
        showList();
    }
    public HouseholdNavigator(HouseholdService service, Consumer<Parent> host) {
        this.service = Objects.requireNonNull(service); this.host = Objects.requireNonNull(host);
    }
    public void showList() { showList(null); }
    public void showList(String notice) {
        show("HouseholdListView.fxml", new HouseholdListController(service, this, notice));
    }
    public void showCreate() { show("HouseholdFormView.fxml", new HouseholdFormController(service, this, null)); }
    public void showDetail(String maHo) {
        show("HouseholdDetailView.fxml", new HouseholdDetailController(service, this, maHo));
    }
    public void showEdit(HouseholdProfile profile) {
        show("HouseholdFormView.fxml", new HouseholdFormController(service, this, Objects.requireNonNull(profile)));
    }
    public void trackDialog(Dialog<?> dialog) { dialogs.add(dialog); }
    public boolean can(com.bluemoon.models.Permission permission) { return authorization.canAccess(permission); }
    public void busy(boolean value) { busy.set(value); }
    @Override public javafx.beans.value.ObservableBooleanValue busyProperty() { return busy.getReadOnlyProperty(); }
    @Override public Parent view() { return content; }
    @Override public void close() { dispose(); content.getChildren().clear(); }
    /** Host calls dispose on module exit/logout; ignores late task callbacks and clears detached data. */
    public void dispose() {
        if (current != null) { current.deactivate(); current = null; }
        for (Dialog<?> dialog : dialogs) { dialog.setResult(null); dialog.close(); }
        dialogs.clear();
    }
    private void show(String name, HouseholdController controller) {
        dispose();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/bluemoon/views/" + name));
        loader.setControllerFactory(type -> controller);
        try {
            Parent root = loader.load(); current = controller; host.accept(root);
        } catch (IOException error) { controller.deactivate(); throw new UncheckedIOException("Cannot load household view", error); }
    }
}
