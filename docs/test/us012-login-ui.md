# US-012 Login UI — Dev-2 evidence

Branch: `feature/us012-login-ui`
Base: `cd49be571248330f5a454de6e31a3e386f7bf8ec` (Dev-3, includes Dev-1 `4206c79`).

## Implementation

JavaFX/FXML/CSS Login → minimal Dashboard → Logout. Application composition creates one real AuthService off the FX thread and injects AuthenticationService into controllers. JDBC/BCrypt login runs in a daemon JavaFX Task; controls are disabled while running and duplicate submission is ignored. Raw input is passed unchanged. Password controls are cleared after completion and a fresh Login scene replaces Dashboard on logout.

SUCCESS shows Account.hoTen and role only when the shared service reports that identity as current. INVALID_CREDENTIALS and missing input use exact AC-02/03 messages. ACCOUNT_LOCKED uses the backend lock message; SYSTEM_ERROR and unexpected exceptions use a generic message. Other VALIDATION_ERROR messages come from the existing backend. Missing configuration displays a disabled Login form with a generic system error; configure the database and restart. No production mock, backend, DAO, schema, seed or US-018 changes.

## Verification (2026-11-06)

Used temporary Microsoft JDK 17.0.21 and Maven 3.9.9 because default Java is 25 and Maven is absent from PATH. No toolchain or dependency changes were committed.

- `mvn clean verify`: BUILD SUCCESS, 85 tests, 0 failures/errors/skips.
- `mvn -Pjavafx-smoke clean verify`: BUILD SUCCESS, 86 tests, 0 failures/errors/skips.
- Seven LoginUiTest cases use real JavaFX controls/FXML and a fake AuthenticationService, without MySQL. Cover missing configuration, empty input combinations, all failure statuses, successful Dashboard greeting/role and logout/session clearing, async duplicate blocking, Enter action/raw input preservation, unexpected exception sanitization and retry.
- FoundationSmokeIT reuses an initialized toolkit so the Foundation and UI checks coexist in one test process.
- `mvn javafx:run`: command launched and remained running until stopped; no visual inspection of its window. Computer Use native pipe was unavailable. Configuration `config/db.properties` is absent. No real MySQL login or MySQL integration profile was run.
- `git diff --check`: passed.

## Review

Lead explicitly selected the existing SRS/backend policy: lock for five minutes on the sixth consecutive wrong password. WBS's five-attempt wording remains a documented discrepancy; backend unchanged.

QA/Lead must review the UI visually and run Login → Dashboard → Logout with a configured MySQL database before acceptance. The PR should target `feature/us001-household-backend` for a focused stacked review because main still lacks Foundation; coordinate final integration with Lead. No merge into main is performed.
