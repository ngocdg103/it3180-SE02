# US-005 UI–Backend integration handoff

Branch: `feature/us005-ui-backend-integration`.
Base: `2b9b90e656ef25a944583bb7282f28fe21d976fc` (`origin/main`).
Preflight: clean working tree, origin fetched, confirmed merge ancestor exit 0, target branch absent before creation.

## Delivered scope and dependencies

Main currently starts only FoundationView from BlueMoonApplication. There is no Dashboard/navigation host. Following the explicit fallback in the integration order, this change supplies independently testable production composition only. It does **not** make the fee screen reachable from the application shell. No US-012 code was imported and BlueMoonApplication is unchanged.

The approved FeeCategoryController/FXML supports list/create only. Its UI has no get/update/deactivate actions. Those three methods remain available and verified at the production service composition boundary; **UI coverage of all five methods is not complete**. Adding those UI flows or a navigation host requires Lead coordination and is deferred. No shared contract, enum, RBAC, existing UI, backend, schema or other story is modified.

## Files added

- `src/main/java/com/bluemoon/controllers/FeeCategoryModule.java`: real production graph and owned screen lifecycle.
- `src/test/java/com/bluemoon/config/FeeCategoryWiringTest.java`: real service/DAO composition through a test JDBC Driver, authorization for all five methods.
- `src/test/java/com/bluemoon/controllers/FeeCategoryModuleTest.java`: real FXML/controller/service/session RBAC with controlled persistence.
- `src/test/java/com/bluemoon/services/FeeCategorySessionFixture.java`: test-only session setup/cleanup; never included in production.
- This handoff document.

## Host integration contract

Prepare `new DatabaseConnection(DatabaseConfig.load())` outside the JavaFX Application Thread; configuration errors must be handled by the future host using a generic unavailable state (no fallback fake service and no fabricated session). DatabaseConfig retains its existing environment/property rules.

On the FX thread, call `FeeCategoryModule.open(connections)` and retain the returned module. This constructs `FeeTypeDAO -> FeeTypeServiceImpl -> FeeCategoryController` and loads the existing FXML using constructor injection. `module.view()` is the Parent to place inside the host's existing content/Scene. The module creates no Stage or Scene. Composition opens no JDBC connection; controller tasks invoke the service using its owned background executor. Existing RoleBasedAuthorizationService reads the live SessionContext.

Call `module.close()` on the FX thread before leaving the module, on logout, and on window close. The host must wire these events once that host exists. Close is idempotent and delegates to controller.dispose(), shuts down its owned executor gracefully, and suppresses late callbacks. It does not interrupt/roll back an in-flight write; that write may commit. Open a fresh module and reload when returning. Failed FXML loading disposes the controller before propagating the error. Opening/closing from another thread is rejected.

The existing controller continues to render loading, successful create, field validation, duplicate, denied access, and generic system error states. New integration tests exercise real service precision validation that passes UI convenience checks, duplicate 1062 mapping, successful create, retained input on errors, denied sessions, off-FX repository execution, and disposal during an in-flight write. Existing controller tests cover loading and late list/error callbacks. The service graph test uses a registered test JDBC Driver that throws SQLException; it verifies actual DAO invocation and no connection before authorization without requiring MySQL.

## Verification

JDK Microsoft 17.0.21.0.2, Maven 3.9.9; Enforcer enabled and passed, no skipped tests.

- `mvn clean verify`: PASS, 273 tests, zero failures/errors/skips.
- `mvn -Pjavafx-smoke clean verify`: PASS, 274 tests, zero failures/errors/skips.
- `git diff --check`: PASS.
- Six new tests; test fixtures use real existing RBAC/session and do not introduce production authorization bypasses.

MySQL end-to-end **not run**: no config/db.properties or configured dedicated MySQL test database. Controlled repository and JDBC test-driver tests do not validate SQL execution, connectivity, migrations or live DB round trips. Existing opt-in FeeTypeMySqlIT remains available from the backend handoff.

Remaining dependencies: complete navigation/session host and lifecycle event wiring; approved get/update/deactivate UI flows for five-method UI coverage; real MySQL end-to-end validation. Commit identifier and PR link are supplied in the delivery message. No merge into main is performed.
