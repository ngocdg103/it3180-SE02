# Household BR-02 UI — final PR handoff

Dev-2, 2026-11-08. Branch `feature/us001-br02-ui` in `.worktrees/household-br02-final`.
Base main: `89b3a289517d20a36bf71784c2a8ff02e1e767f2`.
PO supplied `89b3a2861e6878bb111663e0eb60f5451bfd1487`; fetched main has a different full SHA.
Verified the actual main parents/ancestry include PR #27 `93ce0494a80fac0a431991ef4e1ea2c70fe7f5b8`
and PR #25 `6339cf2e10a9090a39bab3461aa2d85f9d240a94`.
Its tree is `973f369cd97bbafb2da8f12fe19d878ebc0995e7`, identical to the independently reviewed dependency base.

## Source and scope

Applied `review/br02-ui-patch` package (artifact HEAD `4ed2d52e89f7d8b11534def90d82186ce0f70f01`),
BR-02-only delta `b2f54dab736a442f0413dc9fbbc62c4c6f7a3994`, originating from local source
`a61d85b248cbb93496e573472f019f3710499dcf`. Patch apply check passed on the new main.
This feature branch starts directly from main; no composite merge/cherry-pick history and no dependency source is added.

Exactly nine changed files: this handoff, ApartmentHistoryPanel, HouseholdDetailController, HouseholdFormController,
HouseholdNavigator, ProductionModules, HouseholdDetailView.fxml, ApartmentHistoryPanelTest and HouseholdBr02MySqlIT.
The only integration adjustment to the reviewed patch preserves main's exact Fee Rate/Billing/Notice/Payment/Debt
registration order, factories and permissions. Resident UI/Search are retained. AuthenticationNavigator, Stage/Scene,
production BillingDAO/BillingService, owner module code, migrations and already merged test fixtures are unchanged.

## Behavior and acceptance

* HouseholdServiceImpl now receives the real BillingDAO as ApartmentChargeLookup instead of NONE.
* Changing area after an invoice exists triggers the backend CONFIRMATION_REQUIRED and a real Alert.
  Busy holds while the dialog is pending, prevents duplicate save/navigation/logout, and exact payload is sent
  with confirmation only after OK. Cancel performs no confirmed update. Existing snapshot values are untouched.
* Household Detail loads newest-first invoice headers through BillingService.getApartmentHistory off the FX thread.
  Columns show period, invoice ID, total, due date, historical area/people, and committed paid/owing/status fields.
  Payment fields are authoritative per PR #25's revised handoff and Payment's transaction; no status inference,
  balance recalculation, invoice update or direct JDBC in the UI.
* DEBT_REPORT_VIEW is checked before I/O and before applying completion. ADMIN/ACCOUNTANT read; RESIDENT denied.
* Empty/error/retry, history/Household/Resident busy aggregation, detach/listener cleanup and ignored late callbacks
  remain covered. Original household/resident/search flows remain wired.

## Verification on this feature branch

JDK 17.0.17 / Maven 3.9.9 / Enforcer enabled; no tests skipped.
All Maven commands use `-Dsurefire.useManifestOnlyJar=false` for the observed Windows V:/C: manifest-classpath issue.
No pom.xml or test-selection change. All three runs completed with BUILD SUCCESS: normal 699, JavaFX smoke 700, full MySQL 832 tests; each has 0 failures, 0 errors and 0 skipped. Both working and staged diff checks passed.

Commands:

```powershell
mvn -Dsurefire.useManifestOnlyJar=false clean verify
mvn -Dsurefire.useManifestOnlyJar=false -Pjavafx-smoke clean verify
mvn -Dsurefire.useManifestOnlyJar=false -Pmysql-it clean verify
git diff --check
```

MySQL: isolated portable 8.4.0, loopback 33316, disposable `bluemoon_br02_final_test`, dedicated schema-scoped user
and external ignored config. Guard *_test before DDL; no disabled FK checks or production/shared DB.
Fixture owns only BR02_UI_2981/P2981, invoice HD-209701-P2981, period 2097-01 and user 880002; cleanup is child-first.
Only billing/payment tables absent on fixture entry may be removed; other suite-owned tables/rows are retained.
Logs: original workspace `.tools/br02-final-normal.log`, `br02-final-smoke.log`, `br02-final-mysql.log`.

Tests include:

* Cancel leaves area 80; OK changes it to 95.50; original invoice remains unchanged. UI history retains original
  area/total/people/due date. ACCOUNTANT can read; RESIDENT cannot enter Household through Dashboard.
* Real AuthService ACCOUNTANT login and ReceiptService/DAO payment of 23,000: UI history shows Dong thieu,
  paid 23,000 and owing 100,000 while billed total/area/people/due date remain unchanged.
* Existing application/MySQL navigation tests run BlueMoonApplication.start with real authentication and visit all
  five modules, preserve Scene, then logout/clear session. Existing shell/login regression covers maximize/restore.

## Dev-3 and QA

Dev-3 peer review: verify source boundaries, snapshot/payment reads, confirmation payload/cancel/busy lifecycle,
permission recheck and dispose behavior. QA: use a disposable *_test schema and run:

1. ADMIN: Household Detail, newest-first history; change area after issuing invoice -> Cancel -> unchanged -> OK ->
   area updated, stored invoice input snapshots/total unchanged. Retry after a test DB disconnect.
2. ACCOUNTANT reads history and committed partial/full payment status; RESIDENT cannot see/open the module.
3. While loading/writing/awaiting confirmation, repeat save and try Home/module switch/logout/window close.
   After close/logout, detached panels must not receive late data. Maximize/restore across module switches.
4. Verify Resident UI/Search and five-module navigation remain available. Manual complete business flow
   generation -> payment -> Debt -> Notice export is still QA work, not claimed by the navigation test alone.

No automatic main merge. PO owns approval and merge; no old worktree/PR was modified by this delivery.
