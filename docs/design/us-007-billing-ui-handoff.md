# US-007 Billing UI — handoff (Dev-3)

- Branch: `feature/us007-billing-ui`, PR base `feature/us007-billing-backend` (PR #14). Retarget to `main` after PR #14 merges.
- Dependency: PR #14 (BillingService/BillingServiceImpl/BillingDAO, migration `us007_billing.sql`) and the US-006 fee rates. No Billing backend code was copied or changed here.

## Files

- `controllers/BillingController.java`, `controllers/BillingModule.java`, `views/BillingView.fxml` (uses `fee-category.css`).
- Tests: `BillingControllerTest` (14), `MemoryBillingRepository` (test-only memory repository; the real `BillingServiceImpl` and real RBAC run on top of it).

## What the screen does

1. Shows the current billing month (Clock, `Asia/Ho_Chi_Minh`, same rule as the service) and the list of already generated periods.
2. **Xem trước**: calls `previewPeriod`; shows per-household invoices, lines (select a row), totals, warnings (`VACANT_WITH_RESIDENTS`), skipped households (`ZERO_TOTAL`, missing area) and the unit prices applied. A period that already exists is shown as stored ("ĐÃ phát hành"), never recomputed.
3. **Phát hành kỳ thu**: enabled only after a successful preview of the same period; asks for confirmation (dialog) with household count and total, then calls `generatePeriod`. Afterwards it reads the stored snapshot with `getPeriod` + `getInvoice`.
4. Statuses: `MISSING_RATE` (fee codes listed, publish blocked), `NOTHING_TO_BILL`, `DUPLICATE` (exact SRS message, existing period shown), `BUSY` (preview kept so the user can retry), `SYSTEM_ERROR` (generic message), `VALIDATION_ERROR`.
5. Money is displayed exactly as returned (formatting only). No arithmetic in the UI.

## RBAC / threading / lifecycle

- View needs `DEBT_REPORT_VIEW`; preview and publish need `BILLING_PERIOD_CREATE` (buttons hidden without it, service still enforces). RESIDENT / no session: nothing is loaded and nothing is shown.
- Every service call runs on a single worker thread. Buttons are disabled while busy, so double-submit starts one transaction (tested). `busyProperty()` feeds the dashboard.
- `BillingModule implements DashboardModule`, creates no Stage/Scene, `close()` is FX-thread only and idempotent; a publish already in flight can still commit but its callback is dropped.

## Registration (for the App Shell owner; NOT edited here)

In `ProductionModules.create(...)` add:

```java
BillingModule.registration(connections)
```

It returns `new ModuleRegistration("billing", "Lập kỳ thu / Tính phí", Permission.DEBT_REPORT_VIEW, ...)`. Production composition: `BillingServiceImpl(new BillingDAO(connections), RoleBasedAuthorizationService, Clock.systemUTC())`.

## Verification

JDK 17.0.21, Enforcer on, Maven 3.10.0 (not 3.9.9). `mvn clean verify`, `mvn -Pjavafx-smoke clean verify`, `git diff --check`: see the PR. UI tests use a memory repository (no MySQL). The MySQL behaviour of the backend is covered by PR #14's `BillingMySqlIT`.

## QA E2E (needs the App Shell registration above and a real MySQL test schema)

1. Apply migrations us001, us002, us005, us006, us007; create fee types PHI_DV, PHI_QL, PHI_VS and rates (7.000 / 10.000 / 6.000); one apartment P1204 (80 m², 4 residents).
2. Log in as ACCOUNTANT → "Lập kỳ thu". Click **Xem trước**: one invoice, total 1.384.000, 3 lines.
3. Click **Phát hành**, confirm. Expect "Lập kỳ thu 2026-11 thành công", the period appears in the list, the invoice and its lines load from the stored snapshot.
4. Click **Xem trước** again: banner "ĐÃ phát hành", publish disabled. Try publish from a second window: "Kỳ thu 2026-11 đã tồn tại trên hệ thống!".
5. Remove a rate (or add an active mandatory fee without a rate) before a new period: preview shows the missing fee code and publish stays disabled.
6. Log in as RESIDENT: no billing data.

## Not done / limits

- Not registered in the App Shell (owner: PO). Only the current month can be billed (Q11); older periods are view-only.
- The confirmation dialog is a JavaFX `Alert` (not exercised in tests; tests inject the confirmation).
