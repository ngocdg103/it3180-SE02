package com.bluemoon;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in desktop check: mvn -Pjavafx-smoke verify (requires a graphical session). */
class FoundationSmokeIT {
    @Test
    void applicationLoadsFxmlAndShowsStage() throws Exception {
        var finished = new CompletableFuture<Void>();
        Platform.startup(() -> {
            Stage stage = new Stage();
            try {
                new BlueMoonApplication().start(stage);
                assertTrue(stage.isShowing());
                assertNotNull(stage.getScene().getRoot());
                assertEquals("BlueMoon", stage.getTitle());
                finished.complete(null);
            } catch (Throwable failure) {
                finished.completeExceptionally(failure);
            } finally {
                stage.close();
            }
        });
        finished.get(30, TimeUnit.SECONDS);
    }

    @AfterAll static void stopToolkit() { Platform.exit(); }
}
