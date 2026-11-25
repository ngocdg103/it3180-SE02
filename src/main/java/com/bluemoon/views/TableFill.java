package com.bluemoon.views;

import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Presentation helper used by the dashboard shell: columns of every TableView inside a module grow
 * proportionally to fill the table width, so no empty filler area is left on the right.
 * They never shrink below the width the module gave them; a narrow table keeps its horizontal scroll.
 * Tables added later (detail panels, dialogs inside the module) are picked up through child-list listeners.
 * JavaFX 17 has no "flex" resize policy; CONSTRAINED_RESIZE_POLICY would squeeze columns instead.
 */
public final class TableFill {
    private static final String BASE = "bm.fill.base";
    private static final String WATCHED = "bm.fill.watched";
    private TableFill() { }

    /** Applies to tables in {@code root} now and to tables added under it later. */
    public static void watch(Node root) {
        if (root.getProperties().putIfAbsent(WATCHED, Boolean.TRUE) != null) return;
        if (root instanceof TableView<?> table) { fill(table); return; }
        if (root instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(TableFill::watch);
            parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) change.getAddedSubList().forEach(TableFill::watch);
            });
        }
    }

    private static void fill(TableView<?> table) {
        Runnable refill = () -> refill(table);
        table.widthProperty().addListener((o, a, b) -> refill.run());
        table.getVisibleLeafColumns().addListener((ListChangeListener<TableColumn<?, ?>>) c -> refill.run());
        table.skinProperty().addListener((o, a, b) -> refill.run());
        // Rows can show/hide the vertical scrollbar; re-measure after that layout pass.
        ListChangeListener<Object> rows = c -> javafx.application.Platform.runLater(refill);
        if (table.getItems() != null) ((javafx.collections.ObservableList<Object>) (Object) table.getItems()).addListener(rows);
        table.itemsProperty().addListener((o, old, now) -> {
            if (now != null) ((javafx.collections.ObservableList<Object>) (Object) now).addListener(rows);
            javafx.application.Platform.runLater(refill);
        });
        refill.run();
    }

    static void refill(TableView<?> table) {
        var columns = table.getVisibleLeafColumns();
        if (columns.isEmpty() || table.getWidth() <= 0) return;
        double base = 0;
        for (var column : columns) {
            // Never narrower than the header title (theme: SemiBold 14px, 14px padding each side).
            base += (double) column.getProperties().computeIfAbsent(BASE,
                k -> Math.max(column.getPrefWidth(), headerWidth(column.getText())));
        }
        double available = table.getWidth() - table.snappedLeftInset() - table.snappedRightInset()
            - verticalScrollBarWidth(table) - 2; // 2px slack avoids a needless horizontal scrollbar
        double scale = base > 0 && available > base ? available / base : 1.0;
        for (var column : columns) {
            column.setPrefWidth(Math.floor((double) column.getProperties().get(BASE) * scale));
        }
    }

    private static final javafx.scene.text.Font HEADER_FONT = javafx.scene.text.Font.font("Be Vietnam Pro SemiBold", 14);
    private static double headerWidth(String title) {
        if (title == null || title.isBlank()) return 0;
        var text = new javafx.scene.text.Text(title);
        text.setFont(HEADER_FONT);
        return Math.ceil(text.getLayoutBounds().getWidth()) + 48; // label padding 14+14, header insets, sort arrow
    }

    private static double verticalScrollBarWidth(TableView<?> table) {
        for (Node node : table.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL && bar.isVisible()) {
                return bar.getWidth();
            }
        }
        return 0;
    }
}
