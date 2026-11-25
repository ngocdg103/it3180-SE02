package com.bluemoon.views;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TableFillTest {
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        try { Platform.startup(() -> ready.complete(null)); } catch (IllegalStateException started) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }
    private static void fx(Runnable r) throws Exception {
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> { try { r.run(); done.complete(null); } catch (Throwable t) { done.completeExceptionally(t); } });
        done.get(10, TimeUnit.SECONDS);
    }
    /** A table with one row; column i shows values[i] under titles[i]. */
    private static TableView<String[]> table(double width, String[] titles, String[] values) {
        var table = new TableView<String[]>(FXCollections.observableArrayList(java.util.List.<String[]>of(values)));
        for (int i = 0; i < titles.length; i++) {
            int index = i;
            var column = new TableColumn<String[], String>(titles[i]);
            column.setCellValueFactory(row -> new SimpleStringProperty(row.getValue()[index]));
            table.getColumns().add(column);
        }
        var root = new StackPane(table);
        new Scene(root, width, 300);
        TableFill.watch(table);     // installs wrapping titles/cells
        settle(table);
        return table;
    }
    private static void settle(TableView<?> table) {
        var root = table.getParent();
        root.applyCss(); root.layout();
        TableFill.refit(table);     // first pass may switch density
        root.applyCss(); root.layout();
        TableFill.refit(table);
        root.applyCss(); root.layout();
    }
    private static double sum(TableView<?> table) {
        return table.getVisibleLeafColumns().stream().mapToDouble(TableColumn::getWidth).sum();
    }
    private static double inner(TableView<?> table) {
        return table.getWidth() - table.snappedLeftInset() - table.snappedRightInset();
    }

    @Test void wideTableFillsItsWidth() throws Exception {
        fx(() -> {
            var table = table(700, new String[]{"Mã", "Tên"}, new String[]{"A1", "Phí quản lý"});
            assertEquals(inner(table), sum(table), 3, "columns fill the table");
            assertFalse(table.getStyleClass().contains(TableFill.COMPACT));
        });
    }
    @Test void narrowTableShowsEveryColumnWithoutHorizontalScroll() throws Exception {
        fx(() -> {
            var table = table(260, new String[]{"Tên khoản thu", "Hình thức tính", "Trạng thái"},
                new String[]{"Phí dịch vụ vệ sinh và an ninh", "Theo căn hộ (cố định)", "Đang áp dụng"});
            assertTrue(sum(table) <= inner(table) + 1, "all columns inside the table: " + sum(table) + " > " + inner(table));
        });
    }
    @Test void codesAreNeverCutAndCellTextIsKept() throws Exception {
        fx(() -> {
            var table = table(300, new String[]{"Hộ", "Ghi chú"}, new String[]{"HK-0101", "Một ghi chú khá dài cần xuống dòng"});
            var code = new javafx.scene.text.Text("HK-0101");
            assertTrue(table.getColumns().get(0).getWidth() > code.getLayoutBounds().getWidth(), "code keeps its full width");
            assertNotSame(TableColumn.DEFAULT_CELL_FACTORY, table.getColumns().get(1).getCellFactory(), "wrapping cells installed");
            assertEquals("Hộ", table.getColumns().get(0).getText(), "column text unchanged for code and tests");
        });
    }
}
