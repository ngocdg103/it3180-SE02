# US-012 — QA Issue #5: preserve window layout on navigation

Issue: https://github.com/ngocdg103/it3180-SE02/issues/5
Date: 2026-11-06, Asia/Saigon. Branch: feature/us012-login-ui.
Starting commit: c429031 (includes the AC-02 Warning Alert fix).

## Root cause and fix

AuthenticationNavigator.show created a new Scene(root, 720, 600) on every navigation. That imposed initial client dimensions again when switching Login/Dashboard, even while the native Stage still reported maximized.

The navigator now creates a Scene only if stage.getScene() is null. Otherwise it calls scene.setRoot(root). Stage geometry, maximize/full-screen flags and existing scene configuration are not reassigned. Each FXML root already declares auth.css; no scene stylesheets or handlers are appended. Old roots detach from the Scene on logout/navigation.

No AuthService, SessionContext, lock policy, DAO, database, SRS, public API or dependency change. No other feature branch is merged/rebased.

## Regression evidence

Windows desktop, Microsoft JDK 17.0.21, Maven 3.9.9.

Three new LoginUiTest cases cover:

1. A Stage without a Scene opens valid Login FXML at the initial 720x600 client size, with styled, visible controls.
2. Three normal-window Login/Dashboard/Login cycles retain the same Scene object, user-selected Stage and Scene sizes, CSS and layout. One service login/logout call per cycle; old roots detach and password/session clear.
3. Real Stage maximize, two Login/Dashboard/Login cycles, restore to the original custom size and another cycle. Checks desktop geometry, Scene dimensions and root/control bounds rather than only isMaximized().

Measured maximized geometry in this Windows session: Stage 1936x1056, Scene 1920x1017 (native borders included in Stage dimensions). Both transitions retain these sizes. Restore retains the custom normal-window size. The test waits for JavaFX render frames before measuring.

Existing tests still cover AC-02's exact Warning Alert and dismissal, all failure statuses, login/logout, background execution, duplicate submission and input preservation. Existing AuthServiceTest covers actual SessionContext lifecycle without database access.

Commands:

- mvn clean verify: BUILD SUCCESS, 89 tests, 0 failures/errors/skips.
- mvn -Pjavafx-smoke clean verify: BUILD SUCCESS, 90 tests, 0 failures/errors/skips.
- git diff --check: passed.
- Additional UI-only evidence run: mvn -Dtest=LoginUiTest -Dprism.order=sw -Dbluemoon.issue5.snapshots=true test: 11 passed. This software-renderer option is only a CLI option for snapshot capture; pom.xml and production rendering are unchanged.

Optional snapshots are emitted to target/issue5-previews as maximized-login.png, maximized-dashboard.png, normal-login.png and normal-dashboard.png. Maximize Login/Dashboard Scene snapshots were visually reviewed. Tests use a fake AuthenticationService; they do not claim real MySQL authentication.

## Manual QA checklist / limits

Automated maximize and restore were exercised on an actual Windows Stage. Manual mouse/keyboard checking was not completed: Computer Use reported native pipe unavailable (os error 2). A Scene snapshot does not include native window chrome. Multi-monitor/DPI and full-screen transitions have not been manually verified.

With a configured QA database and valid QA account:

1. Run mvn javafx:run; resize the normal window away from 720x600. Log in/out repeatedly, checking dimensions, controls and CSS remain stable.
2. Maximize using the Windows title bar. Log in then log out; verify Restore Down remains shown, the root fills the client area and no 720x600 region replaces it.
3. Restore Down; verify the previous normal size is recovered. Repeat transitions and maximize/restore in both views.
4. Enter invalid credentials; verify the AC-02 Warning Alert, dismiss it, then retry a valid login/logout.
5. If available, repeat on another monitor/DPI and in full-screen, confirming navigation does not alter the chosen state.

Issue #5 is left open for QA/Lead review. No PR merge or issue closure is performed.
