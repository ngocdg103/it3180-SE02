package com.bluemoon.views;

import javafx.application.Platform;
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
    private static TableView<String> table(double width, double... prefs) {
        var table = new TableView<String>();
        for (double pref : prefs) { var c = new TableColumn<String, String>(); c.setPrefWidth(pref); table.getColumns().add(c); }
        var root = new StackPane(table); new Scene(root, width, 300); root.applyCss(); root.layout();
        return table;
    }
    @Test void wideTableGrowsColumnsProportionallyToFill() throws Exception {
        fx(() -> {
            var table = table(600, 100, 200);
            TableFill.watch(table);
            double a = table.getColumns().get(0).getPrefWidth(), b = table.getColumns().get(1).getPrefWidth();
            assertEquals(2.0, b / a, 0.05, "keeps the module's proportions");
            assertTrue(a + b > 560 && a + b <= 600, "fills the width: " + (a + b));
        });
    }
    @Test void narrowTableKeepsOriginalWidthsAndScrolls() throws Exception {
        fx(() -> {
            var table = table(200, 150, 150);
            TableFill.watch(table);
            assertEquals(150, table.getColumns().get(0).getPrefWidth(), 0.5);
            assertEquals(150, table.getColumns().get(1).getPrefWidth(), 0.5);
        });
    }
    @Test void columnIsNeverNarrowerThanItsTitle() throws Exception {
        fx(() -> {
            var table = table(100, 10);
            table.getColumns().get(0).setText("Ngày nhận nhà");
            TableFill.watch(table);
            assertTrue(table.getColumns().get(0).getPrefWidth() > 60, "title fits");
        });
    }
}
