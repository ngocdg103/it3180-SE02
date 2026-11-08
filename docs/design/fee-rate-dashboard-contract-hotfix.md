# Fee Rate App Shell contract hotfix

Base: `09ecb6ed784865620c4897de754f8f61ead1a487` (`origin/main`).
Branch: `fix/fee-rate-dashboard-contract`.

## Composition handoff

`FeeRateModule` now implements `DashboardModule`. Dev-2 can register the lazy
production factory in the composition list with:

```java
FeeRateModule.registration(connections)
```

`connections` is the existing `DatabaseConnection`. Registration ID is
`feeRates`, permission is `FEE_RATE_VIEW`. Registration performs no JDBC or
view construction. The host calls its supplier on the JavaFX thread and owns
the returned module's `view()`, `busyProperty()` and `close()` lifecycle.
This patch does not edit `ProductionModules` or `AuthenticationNavigator`.

## Busy state and disposal

The read-only busy property tracks all submitted tasks, including queued
loads, writes and chained refreshes. It settles after success, failure,
cancellation or submission rejection. Chained callbacks run before the
completed task is removed, preventing a false idle transition during refresh.
Accounting and notifications run on the JavaFX thread.

`close()`/`dispose()` are idempotent and require the JavaFX thread. Existing
in-flight writes can finish; closing does not imply transaction cancellation.
Busy remains true until outstanding tasks finish. Disposed controllers ignore
late success/failure callbacks and reject new task submission; only task
accounting settles afterward. The host must release listeners when discarding
the module. No new executor, DAO, migration or business rule is introduced.

## Verification

JDK 17.0.20.1, Maven 3.9.9, Maven Enforcer enabled.

- Focused FeeRateControllerTest/FeeRateWiringTest: 22 tests PASS.
- `mvn clean verify`: 576 tests PASS, zero failures/errors/skips.
- `mvn -Pjavafx-smoke clean verify`: 577 tests PASS, zero failures/errors/skips.
- `git diff --check`: PASS.
- MySQL IT: NOT RUN for this UI lifecycle/composition-only patch; database
  access and SQL are unchanged. No UI E2E result is claimed.

Tests cover DashboardModule compatibility, lazy registration and its permission,
queued/chained loads, writes, failure/rejection cleanup, denied sessions, and
closing during loading/writing without late UI updates. Existing service/RBAC
tests remain in the regression suite. PR #21 Payment and PR #24 Debt are
unchanged; their QA follow-up remains separate from this P0 hotfix.
