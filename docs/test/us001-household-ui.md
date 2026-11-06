# US-001 Household UI — verification evidence

Date: 2026-11-06 (Asia/Saigon). Branch: feature/us001-household-ui.
Base: cd49be571248330f5a454de6e31a3e386f7bf8ec.
Scope: WBS 1.1.1.2.2 only.

## Environment and final checks

Windows graphical session; temporary Microsoft JDK 17.0.21 and Maven 3.9.9.

- mvn clean verify: BUILD SUCCESS, 93 tests, 0 failures/errors/skips.
- mvn -Pjavafx-smoke -Dbluemoon.ui.snapshots=true clean verify: BUILD SUCCESS, 94 tests, 0 failures/errors/skips (includes original FoundationSmokeIT).
- git diff --check: passed (including staged/new files before commit).
- JavaFX controls, FXML and owned dialogs exercised on the FX toolkit. Scene snapshots for list/detail/edit form are generated under ignored target/ui-previews when bluemoon.ui.snapshots=true; no production preview mode is added. All three final Scene snapshots were visually inspected; manual mouse/keyboard QA and real-MySQL end-to-end remain pending.
- During an earlier snapshot run, the JavaFX fork exited natively with code -1073740791. Tests now use prism.order=sw and isolated JVMs; subsequent checks passed. The existing unnamed-module JavaFX warning remains non-fatal. Production renderer is unchanged.

## HouseholdUiTest (15 tests)

1. Six-column list and status filtering through the service.
2. Loading, empty, system-error and reload/retry states.
3. AC-01 exact success message, one create and refreshed list.
4. AC-02 visible warning dialog with exact duplicate text, field error, retained input and no navigation; dialog closed in test.
5. All six backend fieldErrors and raw input retention, including malformed date text.
6. Detail read and AC-03 status update with read-only identifiers and unchanged remaining payload fields.
7. BR-02 approval uses the same DTO instance, first false then true.
8. BR-02 cancel does not submit a second update.
9. NOT_FOUND and SYSTEM_ERROR leave detail empty, edit disabled and back enabled.
10. AccessDeniedException clears list, detail and form data; ignores exception details.
11. Double submission blocked while a latch holds the service; FX thread stays responsive and service runs off-thread.
12. Unexpected exception sanitized, controls restored.
13. Detached-view result cannot navigate to another screen.
14. Three-view GUI/layout and optional snapshots, waiting for JavaFX render frames.
15. Real HouseholdServiceImpl + existing InMemoryHouseholdRepository integration: create, list, detail and status update preserving area. Test-only ADMIN supplier; no production role bypass.

## Not claimed / integration dependencies

No real MySQL integration was run for this UI delivery. BlueMoonApplication and Foundation remain unchanged; Login UI and US-018 are not merged. Real application end-to-end remains pending authenticated composition/session and a configured MySQL database with the existing migration.

BR-02's real charge lookup is still unavailable; confirmation UI is tested using a fake service response. No resident counts are fabricated for BR-03. The approved 1:1 relationship, immutable keys and existing apartment/floor validation are retained.

See docs/design/us001-ui-handoff.md for future composition and lifecycle instructions. QA/Lead acceptance is still required.
