# Final App Shell composition — clean PR handoff

Dev-2, 2026-11-08. Base main `743b21f59937b3ca8dd066c1bfd5d966d241862b`.
Branch `feature/final-app-shell-integration`, worktree `.worktrees/final-shell-review`.

## Connected production modules

| Menu ID | Factory | Permission |
|---|---|---|
| feeRates | FeeRateModule.registration(connections) | FEE_RATE_VIEW |
| billing | BillingModule.registration(connections) | DEBT_REPORT_VIEW |
| notices | NoticeModule.registration(connections) | DEBT_REPORT_VIEW |
| receipts | ReceiptModule.open(connections) | PAYMENT_HISTORY_VIEW_ALL |
| debt | DebtModule.open(connections) | DEBT_REPORT_VIEW |

Factories are exactly those supplied by owners. All use real Service/DAO; no placeholder or fake busy property.
The Fee Rate Dashboard contract hotfix is already in main. Household, Resident, Search and Fee Category composition
is preserved as it exists on this base. No new Navigator/Scene/Stage, AuthenticationNavigator or lifecycle change.
Dashboard home no longer labels these modules unavailable. ADMIN/ACCOUNTANT menus are authorized by the existing
permission service; RESIDENT remains denied. Payment entry remains ACCOUNTANT-only in the owner UI and service.

## Dependency and BR-02 boundary

Main contains the source commits of #18/#19/#21/#24 and Fee Rate hotfix (verified Git ancestry). GitHub's API still
reported #18/#19 open during inspection, despite their commits being contained in main. #25 source
`6339cf2e10a9090a39bab3461aa2d85f9d240a94` is NOT in this base.

This clean PR contains no #25/backend/module source, migration, or BR-02 production UI. BR-02 is implemented and
verified independently in local `integration/final-shell-local` at `a61d85b248cbb93496e573472f019f3710499dcf`:
real BillingDAO area guard, snapshot/committed Payment history panel, busy confirmation/Cancel, background Service calls, RBAC/cleanup.
After PO merges #25, transfer only that Household UI/navigation/composition delta to a fresh branch from main.
Do not push the local composite history. Until then, main's Household composition still uses ApartmentChargeLookup.NONE;
this PR does not claim to remove the BR-02 blocker by itself.

## Two approved test-only compatibility changes

Lead explicitly approved adding MemoryBillingRepository.findChargesByApartment and NoticeServiceTest.Stub.getApartmentHistory.
Both filter their existing invoice collection/map by apartment, sort newest period first and return header snapshots.
The Notice stub preserves scripted failures. No old assertions were changed; a new fixture test verifies ordering,
filtering, empty results and snapshot preservation. Production BillingDAO/BillingService/NoticeService remain untouched.

These two extra methods intentionally omit @Override while compiling against the older interface on this base;
the exact same signatures implement the new abstract methods once #25 is merged. Merging this PR first allows
the subsequent #25 merge to compile its existing Billing/Notice UI tests without a temporary broken main.

## Verification on THIS clean branch

JDK 17.0.17 / Maven 3.9.9 / Enforcer enabled. No test skip.
| Profile | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| normal | 694 | 0 | 0 | 0 |
| javafx-smoke | 695 | 0 | 0 | 0 |
| mysql-it | 821 | 0 | 0 | 0 |

All three completed BUILD SUCCESS on this clean branch. MySQL 8.4.0, private loopback port 33316,
disposable `bluemoon_shell_review_test`, schema-scoped credentials in ignored external config.

All verification commands include `-Dsurefire.useManifestOnlyJar=false` to avoid the observed Windows V:/C:
manifest-classpath fork error. No pom.xml change and no change to test selection/assertions or Enforcer.
Logs under original workspace `.tools/final-review-normal.log`, `final-review-smoke.log`, `final-review-mysql.log`.

ProductionNavigationTest covers permission IDs, role menus/direct denial, actual FXML factories, repeat opens,
busy/home/logout/dispose and DB-unavailable behavior. ProductionNavigationMySqlIT repeats it on real MySQL and runs
BlueMoonApplication.start with real AuthService/AccountDAO: Login → five modules → Home → Logout, same Scene and session cleared.
The real navigation case uses a disposable BCrypt account with owned-row cleanup. Empty Receipt/Debt states are
explicitly asserted as empty data, not mistaken for DB success after a system error. Schema guards precede DDL;
only billing/payment tables absent on fixture entry are removed child-first. FK checks remain enabled.

Results from the separate BR-02 composite (699 normal / 700 smoke / 832 MySQL) are NOT this clean PR's test counts.

## QA / gate

1. Use a disposable *_test database and official migrations through US-008. Login as ADMIN/ACCOUNTANT, open each
   of the five modules, switch/Home/logout, maximize/restore and confirm the same window/Scene is retained.
2. Attempt navigation/logout/window close while loading or writing; repeated open/submit must not duplicate work.
   Stop only the test DB and verify sanitized errors and retry; after close/logout detached callbacks must not update UI.
3. RESIDENT must not see or directly enter administrative modules. ADMIN can view payment but cannot record it;
   ACCOUNTANT may record according to the existing permission matrix.
4. Manual business E2E remains QA work: generate a real period, partial/full payment, reload Debt and export Notice.
   The automated application navigation test does not claim that complete business workflow.
5. After #25, deliver the separate clean BR-02 PR: Cancel leaves area unchanged, OK changes it, invoice snapshots remain
   unchanged, history newest first, ACCOUNTANT reads and RESIDENT is denied. No source from an unmerged dependency in this PR.

READY for composition review; BR-02 production delivery remains dependent on #25.
No auto-merge; PO owns main merge order.
