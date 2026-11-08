package com.bluemoon.controllers;

import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.dao.BillingInputs;
import com.bluemoon.dao.BillingInputs.FeeInput;
import com.bluemoon.dao.BillingInputs.HouseholdInput;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.AppliedRate;
import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.ChargeLine;
import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.MonthlyCharge;
import com.bluemoon.models.Role;
import com.bluemoon.services.BillingServiceImpl;
import com.bluemoon.services.FeeCategorySessionFixture;
import com.bluemoon.services.RoleBasedAuthorizationService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real FXML + controller + BillingServiceImpl + RBAC over a memory repository: no MySQL claim, no mocked service. */
class BillingControllerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneOffset.UTC);
    private final MemoryBillingRepository repository = new MemoryBillingRepository();
    private final AtomicInteger confirmations = new AtomicInteger();
    private volatile boolean answer = true;
    private volatile String lastQuestion = "";
    private BillingModule module;

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @AfterEach void cleanup() throws Exception {
        repository.gate.countDown();
        if (module != null) { fx(() -> { module.close(); return null; }); }
        FeeCategorySessionFixture.use(null);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var done = new CompletableFuture<T>();
        Platform.runLater(() -> { try { done.complete(action.call()); } catch (Throwable t) { done.completeExceptionally(t); } });
        return done.get(10, TimeUnit.SECONDS);
    }

    private void until(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < end) { if (fx(condition::getAsBoolean)) { return; } Thread.sleep(10); }
        fail("UI result did not arrive");
    }

    private static FeeInput fee(String code, ChargeMethod method, String price) {
        return new FeeInput(code, "Khoan " + code, method,
                Optional.of(new AppliedRate(code, new BigDecimal(price), LocalDate.of(2026, 1, 1))));
    }

    private void ac01Data() {
        repository.inputs = new BillingInputs(List.of(fee("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "10000"),
                fee("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "7000"), fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "6000")),
                List.of(new HouseholdInput("HK-1204", "P1204", new BigDecimal("80"), ApartmentStatus.DANG_O, 4)));
    }

    private void open(Role role) throws Exception {
        FeeCategorySessionFixture.use(role);
        var auth = new RoleBasedAuthorizationService(null);
        var service = new BillingServiceImpl(repository, auth, CLOCK);
        var executor = Executors.newSingleThreadExecutor(job -> { var t = new Thread(job, "test-billing"); t.setDaemon(true); return t; });
        var controller = new BillingController(service, auth, CLOCK, executor, true, question -> {
            confirmations.incrementAndGet(); lastQuestion = question; return answer;
        });
        module = fx(() -> new BillingModule(controller));
        until(this::idle); // initial period list finished: buttons are enabled only when idle
    }

    @SuppressWarnings("unchecked") private <T> T node(String id) { return (T) module.view().lookup("#" + id); }
    private String text(String id) { return ((Label) node(id)).getText(); }
    private Button button(String id) { return node(id); }
    private TableView<MonthlyCharge> chargeTable() { return node("chargeTable"); }
    private TableView<ChargeLine> lineTable() { return node("lineTable"); }
    private ListView<String> issues() { return node("issuesList"); }
    private boolean idle() { return !module.busyProperty().get(); }
    private void click(String id) throws Exception { fx(() -> { button(id).fire(); return null; }); }

    private void previewReady() throws Exception {
        click("previewButton");
        until(() -> idle() && !chargeTable().getItems().isEmpty());
    }

    @Test void opensWithCurrentPeriodAndNoDataAndLoadsExistingPeriods() throws Exception {
        repository.addStoredPeriod(new BillingPeriod("2026-10", LocalDate.of(2026, 11, 15), 0, 0, BigDecimal.ZERO,
                Instant.EPOCH), List.of());
        open(Role.ACCOUNTANT);
        until(() -> idle() && !this.<ComboBox<String>>node("periodBox").getItems().isEmpty());
        assertEquals("2026-11", text("currentPeriodLabel"));
        assertEquals(List.of("2026-10"), fx(() -> List.copyOf(this.<ComboBox<String>>node("periodBox").getItems())));
        assertTrue(fx(() -> chargeTable().getItems().isEmpty()));
        assertTrue(fx(() -> button("generateButton").isDisabled()), "nothing to publish before a preview");
    }

    @Test void previewShowsServiceNumbersLinesAndWritesNothing() throws Exception {
        ac01Data();
        open(Role.ACCOUNTANT);
        previewReady();
        assertEquals(1, fx(() -> chargeTable().getItems().size()));
        assertTrue(text("summaryLabel").contains("1.384.000"), text("summaryLabel"));
        assertTrue(text("bannerLabel").contains("CHƯA phát hành"));
        fx(() -> { chargeTable().getSelectionModel().select(0); return null; });
        until(() -> lineTable().getItems().size() == 3);
        assertTrue(fx(() -> issues().getItems().stream().anyMatch(s -> s.contains("PHI_QL"))), "applied rates are listed");
        assertEquals(0, repository.writeTransactions.get());
        assertTrue(fx(() -> !button("generateButton").isDisabled()));
    }

    @Test void generateNeedsPreviewAndConfirmationThenShowsTheStoredSnapshot() throws Exception {
        ac01Data();
        open(Role.ACCOUNTANT);
        click("generateButton"); // disabled: no preview yet
        Thread.sleep(100);
        assertEquals(0, repository.writeTransactions.get());
        previewReady();
        answer = false;
        click("generateButton");
        until(() -> text("messageLabel").contains("hủy"));
        assertEquals(1, confirmations.get());
        assertTrue(lastQuestion.contains("2026-11") && lastQuestion.contains("1.384.000"));
        assertEquals(0, repository.writeTransactions.get(), "declined: nothing written");
        answer = true;
        click("generateButton");
        until(() -> repository.commits.get() == 1 && idle() && text("bannerLabel").contains("đã phát hành"));
        assertTrue(text("messageLabel").contains("thành công"));
        assertEquals(List.of("2026-11"), fx(() -> List.copyOf(this.<ComboBox<String>>node("periodBox").getItems())));
        until(() -> lineTable().getItems().size() == 3);
        assertEquals("P1204", fx(() -> chargeTable().getItems().get(0).soCanHo()));
        assertTrue(fx(() -> button("generateButton").isDisabled()), "published: cannot publish again");
    }

    @Test void previewOfAnExistingPeriodShowsItIsClosed() throws Exception {
        ac01Data();
        open(Role.ACCOUNTANT);
        previewReady();
        click("generateButton");
        until(() -> repository.commits.get() == 1 && idle());
        click("previewButton");
        until(() -> idle() && text("bannerLabel").contains("ĐÃ phát hành"));
        assertTrue(fx(() -> button("generateButton").isDisabled()));
        assertEquals(1, repository.commits.get());
    }

    @Test void duplicateFromAnotherUserIsReportedAndTheExistingPeriodIsShown() throws Exception {
        ac01Data();
        open(Role.ACCOUNTANT);
        previewReady();
        // Someone else publishes the same period after our preview.
        var other = new BillingServiceImpl(repository, new RoleBasedAuthorizationService(null), CLOCK);
        assertTrue(other.generatePeriod("2026-11").isSuccess());
        int commits = repository.commits.get();
        click("generateButton");
        until(() -> text("messageLabel").contains("đã tồn tại trên hệ thống") && idle());
        assertEquals("Kỳ thu 2026-11 đã tồn tại trên hệ thống!", text("periodError"));
        assertEquals(commits, repository.commits.get(), "no duplicate data");
        until(() -> text("bannerLabel").contains("đã phát hành"));
    }

    @Test void missingRateBlocksPublishingAndNamesTheFee() throws Exception {
        ac01Data();
        repository.inputs = new BillingInputs(List.of(
                new FeeInput("PHI_X", "Khoan moi", ChargeMethod.THEO_CAN_HO, Optional.empty())),
                repository.inputs.households());
        open(Role.ACCOUNTANT);
        click("previewButton");
        until(() -> idle() && text("messageLabel").contains("PHI_X"));
        assertTrue(fx(() -> issues().getItems().stream().anyMatch(s -> s.contains("PHI_X"))));
        assertTrue(fx(() -> button("generateButton").isDisabled()));
        assertEquals(0, repository.writeTransactions.get());
    }

    @Test void nothingToBillIsExplained() throws Exception {
        open(Role.ACCOUNTANT);
        click("previewButton");
        until(() -> idle() && text("messageLabel").contains("Không có khoản thu"));
        assertTrue(fx(() -> button("generateButton").isDisabled()));
    }

    @Test void zeroTotalAndWarningsAreListedAndSkippedHouseholdsAreNotBilled() throws Exception {
        repository.inputs = new BillingInputs(List.of(fee("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "6000")),
                List.of(new HouseholdInput("HK-1", "P1", new BigDecimal("50"), ApartmentStatus.CAN_HO_TRONG, 0),
                        new HouseholdInput("HK-2", "P2", new BigDecimal("50"), ApartmentStatus.CAN_HO_TRONG, 2)));
        open(Role.ACCOUNTANT);
        previewReady();
        assertEquals(1, fx(() -> chargeTable().getItems().size()));
        assertTrue(fx(() -> issues().getItems().stream().anyMatch(s -> s.contains("Không lập") && s.contains("P1"))));
        assertTrue(fx(() -> issues().getItems().stream().anyMatch(s -> s.contains("⚠") && s.contains("P2"))));
    }

    @Test void busyDatabaseKeepsThePreviewSoTheUserCanRetry() throws Exception {
        ac01Data();
        open(Role.ACCOUNTANT);
        previewReady();
        repository.generateFailure = new SQLException("Lock wait timeout", "HY000", 1205);
        click("generateButton");
        until(() -> idle() && text("messageLabel").contains("đang bận"));
        assertFalse(fx(() -> button("generateButton").isDisabled()), "retry allowed");
        repository.generateFailure = null;
        click("generateButton");
        until(() -> repository.commits.get() == 1 && idle());
    }

    @Test void databaseFailureShowsGenericMessageWithoutDetails() throws Exception {
        ac01Data();
        open(Role.ACCOUNTANT);
        previewReady();
        repository.generateFailure = new SQLException("secret detail users.password", "HY000", 2013);
        click("generateButton");
        until(() -> idle() && !text("messageLabel").isEmpty());
        assertFalse(text("messageLabel").contains("secret"));
        assertEquals(0, repository.commits.get());
    }

    @Test void doubleSubmitCreatesOneWriteAndLocksTheControlsWhileRunning() throws Exception {
        ac01Data();
        open(Role.ACCOUNTANT);
        previewReady();
        repository.gate = new CountDownLatch(1);
        click("generateButton");
        until(() -> module.busyProperty().get());
        assertTrue(fx(() -> button("generateButton").isDisabled() && button("previewButton").isDisabled()
                && button("refreshButton").isDisabled()));
        click("generateButton");
        click("generateButton");
        repository.gate.countDown();
        until(() -> repository.commits.get() == 1 && idle());
        assertEquals(1, repository.writeTransactions.get(), "exactly one transaction was started");
        assertEquals(1, confirmations.get(), "second click never reached the confirmation");
    }

    @Test void residentAndNoSessionCannotSeeActionsOrReachTheRepository() throws Exception {
        ac01Data();
        for (Role role : new Role[] {Role.RESIDENT, null}) {
            open(role);
            assertTrue(fx(() -> !button("previewButton").isVisible() && !button("generateButton").isVisible()));
            until(this::idle);
            assertEquals(0, repository.writeTransactions.get());
            assertTrue(fx(() -> chargeTable().getItems().isEmpty()));
            fx(() -> { module.close(); return null; });
            module = null;
        }
    }

    @Test void closeIsIdempotentDropsLateCallbacksButAnInFlightPublishStillCommits() throws Exception {
        ac01Data();
        open(Role.ACCOUNTANT);
        previewReady();
        repository.gate = new CountDownLatch(1);
        click("generateButton");
        until(() -> module.busyProperty().get());
        fx(() -> { module.close(); module.close(); return null; });
        repository.gate.countDown();
        until(() -> repository.commits.get() == 1);
        assertEquals(1, repository.commits.get(), "write in flight commits; UI is gone");
    }

    @Test void moduleOwnsNoSceneAndRejectsOffFxLifecycle() throws Exception {
        ac01Data();
        open(Role.ACCOUNTANT);
        assertNull(fx(() -> module.view().getScene()), "the host owns Stage and Scene");
        assertThrows(IllegalStateException.class, () -> module.close());
    }
}
