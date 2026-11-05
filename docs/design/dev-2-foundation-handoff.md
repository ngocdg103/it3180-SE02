# Dev-1 → Dev-2: shared Foundation and authentication contract

Both technical prerequisites are available on `dev-1`. Do not create a second
Maven project. Latest fetched main: `5a11f321f0d440dc3c7db6983b4150261847d5bf`;
the current branch is already based on that commit.

## Shared project

- Java 17, Maven 3.9.x; `pom.xml` at repository root.
- Entry point: `com.bluemoon.BlueMoonApplication`.
- Java: `src/main/java/com/bluemoon/{models,services,controllers,dao,config}`;
  add controller source in `com.bluemoon.controllers`.
- FXML/resources: `src/main/resources/com/bluemoon/views/`.
- Current view: `FoundationView.fxml`, a minimal placeholder for Dev-2's UI.
- Build/test: `mvn clean verify`; start: `mvn javafx:run`.
- The placeholder starts without MySQL/configuration. Use a mock for UI development.

## Injectable contract

Controller dependency: `com.bluemoon.services.AuthenticationService`.

```java
AuthResult login(String username, String password);
void logout();
Optional<Account> currentUser();
```

`AuthService implements AuthenticationService`. For production, construct
`new AuthService(new AccountDAO(new DatabaseConnection(DatabaseConfig.load())))`
once at composition and inject it into the controller. Configuration can throw
IOException; login returns SYSTEM_ERROR for database/system failures.

Mock: `MockAuthService implements AuthenticationService`, constructed without
database setup. Public factories: `AuthResult.success(account)` and
`AuthResult.failure(status, message)`. Result methods: `status()`, `isSuccess()`,
`message()`, `account()` (Optional). Statuses: SUCCESS, VALIDATION_ERROR,
INVALID_CREDENTIALS, ACCOUNT_LOCKED, SYSTEM_ERROR. Account exposes userId,
username, hoTen and role; never a password hash.

Full contract, construction example and exact error messages:
[Auth API](auth-api-proposal.md). A compilable mock outside the services package
is in `src/test/java/com/bluemoon/integration/AuthenticationContractTest.java`.
Run JDBC/BCrypt in a background JavaFX Task and update views on the FX thread.

## Verification and review status

- `mvn -Pjavafx-smoke clean verify`: 19 passed, no failures/errors/skips.
- `mvn javafx:run`: BlueMoon window verified, normal close, BUILD SUCCESS.
- Earlier backend suite: 22 passed including 5 real MySQL 8.4.13 checks.
- No LoginController/LoginView or Dashboard was implemented for Dev-2.
- Dev-2/Lead Dev still review the proposed API; availability does not mean their
  approval or integration has already occurred. No automatic merge.
- Lock wording discrepancy remains documented: SRS BR-18 says over five
  consecutive failures; implementation follows SRS with locking on the sixth.
