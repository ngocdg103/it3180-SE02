package com.bluemoon.views;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.skin.TableColumnHeader;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import java.util.List;
import java.util.Objects;

/**
 * Presentation helper used by the dashboard shell: every module TableView shows all of its columns
 * inside its own width, without cutting text.
 *
 * <p>Nothing is hard-coded here: each column is measured from its rendered header and visible cells,
 * whose fonts and paddings come from the theme CSS. Then:
 * <ol>
 *   <li>if every column fits on one line, columns grow proportionally to fill the table;</li>
 *   <li>otherwise the table switches to the CSS class {@code bm-compact} (smaller paddings and type,
 *       defined in the theme) and is measured again; if values still do not fit on one line, columns
 *       shrink proportionally and text wraps at spaces, each column keeping at least its longest word
 *       (so codes such as HK-0101 and dates are never cut);</li>
 *   <li>only if that still does not fit does the table keep a horizontal scrollbar.</li>
 * </ol>
 * Wrapping needs the column width when row heights are computed, which default cells do not use in
 * JavaFX 17, so columns that use the default cell factory get a cell that draws its value as a wrapping
 * Text. Cell text and column titles keep their values (getText still works for code and tests);
 * columns with their own cell factory are left alone. Tables added later are picked up as well.
 */
public final class TableFill {
    private static final String WATCHED = "bm.fill.watched";
    private static final String TITLE = "bm.fill.title";
    private static final String PENDING = "bm.fill.pending";
    private static final String FITTED_WIDTH = "bm.fill.width";
    static final String COMPACT = "bm-compact";
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

    @SuppressWarnings("unchecked")
    private static void fill(TableView<?> table) {
        table.getVisibleLeafColumns().forEach(TableFill::prepare);
        table.getVisibleLeafColumns().addListener((ListChangeListener<TableColumn<?, ?>>) c -> {
            table.getVisibleLeafColumns().forEach(TableFill::prepare);
            redecide(table);
        });
        table.widthProperty().addListener((o, a, b) -> {
            // Setting column widths can move the table's own width by a rounding pixel; that is not a resize.
            Object fitted = table.getProperties().get(FITTED_WIDTH);
            if (fitted instanceof Double w && Math.abs(b.doubleValue() - w) <= 1) return;
            redecide(table);
        });
        table.skinProperty().addListener((o, a, b) -> redecide(table));
        ListChangeListener<Object> rows = c -> redecide(table);
        if (table.getItems() != null) ((ObservableList<Object>) (Object) table.getItems()).addListener(rows);
        table.itemsProperty().addListener((o, old, now) -> {
            if (now != null) ((ObservableList<Object>) (Object) now).addListener(rows);
            redecide(table);
        });
        redecide(table);
    }

    /** New data or width: measure again at normal density first (CSS is re-applied before the next layout). */
    private static void redecide(TableView<?> table) {
        table.getStyleClass().remove(COMPACT);
        schedule(table);
    }

    /** Wrapping title graphic bound to the column text; wrapping cells for default-factory columns. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void prepare(TableColumn<?, ?> column) {
        if (column.getProperties().containsKey(TITLE)) return;
        if (column.getGraphic() == null) {
            Text title = new Text();
            title.textProperty().bind(column.textProperty());
            title.getStyleClass().add("bm-column-title");
            column.setGraphic(title);
            column.getProperties().put(TITLE, title);
        } else {
            column.getProperties().put(TITLE, Boolean.FALSE);
        }
        if (column.getCellFactory() == TableColumn.DEFAULT_CELL_FACTORY) {
            ((TableColumn) column).setCellFactory(c -> new WrappingCell<>());
        }
    }

    /** Draws the cell value as a Text that wraps at the column width, so rows grow instead of cutting. */
    static final class WrappingCell<S, T> extends TableCell<S, T> {
        private final Text text = new Text();
        WrappingCell() {
            text.getStyleClass().add("bm-cell-text");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            tableColumnProperty().addListener((o, old, column) -> {
                if (column != null) column.widthProperty().addListener((w, a, b) -> wrap());
                wrap();
            });
            paddingProperty().addListener((o, a, b) -> wrap());
        }
        private void wrap() {
            var column = getTableColumn();
            if (column != null) text.setWrappingWidth(Math.max(1, column.getWidth() - snappedLeftInset() - snappedRightInset()));
        }
        @Override protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            String value = empty || item == null ? null : item.toString();
            setText(value);
            text.setText(value == null ? "" : value);
            setGraphic(value == null ? null : text);
            wrap();
        }
    }

    /** Runs one refit after the next layout pass, when headers and cells show the current data. */
    private static void schedule(TableView<?> table) {
        if (table.getProperties().putIfAbsent(PENDING, Boolean.TRUE) != null) return;
        Runnable run = () -> { table.getProperties().remove(PENDING); refit(table); };
        Scene scene = table.getScene();
        if (scene == null) { Platform.runLater(run); return; }
        Runnable[] once = new Runnable[1];
        once[0] = () -> { scene.removePostLayoutPulseListener(once[0]); run.run(); };
        scene.addPostLayoutPulseListener(once[0]);
        Platform.requestNextPulse();
    }

    /** Measured needs of one column: full one-line width and longest-word width, paddings included. */
    record Need(double natural, double word, double headerPad) { }

    static void refit(TableView<?> table) {
        List<TableColumn<?, ?>> columns = List.copyOf(table.getVisibleLeafColumns());
        if (columns.isEmpty() || table.getSkin() == null) return;
        double scrollBar = verticalScrollBarWidth(table);
        table.getProperties().put(FITTED_WIDTH, table.getWidth());
        double available = table.getWidth() - table.snappedLeftInset() - table.snappedRightInset() - scrollBar - 1;
        if (available <= 0) return;
        var needs = columns.stream().map(c -> measure(table, c)).toList();
        if (needs.stream().anyMatch(Objects::isNull)) { schedule(table); return; } // headers not rendered yet

        double natural = needs.stream().mapToDouble(Need::natural).sum();
        double words = needs.stream().mapToDouble(Need::word).sum();
        if (natural > available && !table.getStyleClass().contains(COMPACT)) {
            table.getStyleClass().add(COMPACT); // values do not fit on one line: try the denser variant first
            schedule(table);
            return;
        }
        double used = 0;
        for (int i = 0; i < columns.size(); i++) {
            Need n = needs.get(i);
            double w;
            if (natural <= available) w = n.natural() * available / natural;                       // grow to fill
            else if (words < available) w = n.word() + (available - words)                         // shrink, wrap
                * (n.natural() - n.word()) / Math.max(1, natural - words);
            else w = n.word();                                                                     // scroll
            w = i == columns.size() - 1 && words < available ? Math.max(n.word(), available - used) : Math.floor(w);
            columns.get(i).setPrefWidth(w);
            used += w;
            if (columns.get(i).getProperties().get(TITLE) instanceof Text title) {
                title.setWrappingWidth(Math.max(1, w - n.headerPad()));
            }
        }
        // Wrapping changes row heights, which can show or hide the vertical scrollbar: fit again if so.
        Platform.runLater(() -> { if (verticalScrollBarWidth(table) != scrollBar) schedule(table); });
    }

    private static Need measure(TableView<?> table, TableColumn<?, ?> column) {
        TableColumnHeader header = null;
        for (Node node : table.lookupAll(".column-header")) {
            if (node instanceof TableColumnHeader h && h.getTableColumn() == column) { header = h; break; }
        }
        if (header == null || !(header.lookup(".label") instanceof Labeled label)) return null;
        double headerPad = insets(header) + insets(label);
        Font titleFont = column.getProperties().get(TITLE) instanceof Text t ? t.getFont() : label.getFont();
        double natural = width(column.getText(), titleFont) + headerPad;
        double word = longestWord(column.getText(), titleFont) + headerPad;
        for (Node node : table.lookupAll(".table-cell")) {
            if (!(node instanceof TableCell<?, ?> cell) || cell.getTableColumn() != column || cell.isEmpty()
                || !cell.isVisible() || cell.getTableRow() == null || !cell.getTableRow().isVisible()
                || cell.getTableRow().isEmpty()) continue; // skip hidden cells kept for reuse (stale values)
            double pad = insets(cell);
            String value = cell.getText();
            if (value != null && !value.isEmpty()) {
                Font font = cell.getGraphic() instanceof Text t ? t.getFont() : cell.getFont();
                natural = Math.max(natural, width(value, font) + pad);
                word = Math.max(word, longestWord(value, font) + pad);
            } else if (cell.getGraphic() != null) {
                double g = cell.getGraphic().prefWidth(-1) + pad;
                natural = Math.max(natural, g); word = Math.max(word, g);
            }
        }
        return new Need(Math.ceil(natural), Math.ceil(word), headerPad);
    }

    private static double insets(Region region) { return region.snappedLeftInset() + region.snappedRightInset(); }
    private static double width(String s, Font font) {
        if (s == null || s.isEmpty()) return 0;
        Text text = new Text(s);
        text.setFont(font);
        return text.getLayoutBounds().getWidth();
    }
    /**
     * Widest whitespace-separated word. JavaFX Text wraps only at whitespace (a longer word is cut
     * mid-character, e.g. "HK-0101" becomes "HK-0"/"101"), so codes and dates must keep their full width.
     */
    private static double longestWord(String s, Font font) {
        if (s == null || s.isBlank()) return 0;
        double max = 0;
        for (String word : s.strip().split("\\s+")) max = Math.max(max, width(word, font));
        return max;
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
