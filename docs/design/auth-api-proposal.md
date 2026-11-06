# US-012 — implemented API for Dev-2

Status: implemented and tested against EP-06 from project_rules commit `64605dd`.
Dev-2/Lead Dev still need to review the public API before integrating their UI.

```java
import com.bluemoon.config.DatabaseConfig;
import com.bluemoon.config.DatabaseConnection;
import com.bluemoon.dao.AccountDAO;
import com.bluemoon.models.Account;
import com.bluemoon.services.AuthService;
import com.bluemoon.services.SessionContext;

var connections = new DatabaseConnection(DatabaseConfig.load());
var auth = new AuthService(new AccountDAO(connections));
// Inject this shared auth instance into LoginController.
var result = auth.login(username, password);
boolean success = result.isSuccess();
var status = result.status();
String message = result.message();
java.util.Optional<Account> account = result.account();
java.util.Optional<Account> session = auth.currentUser();
auth.logout();
```

`DatabaseConfig.load()` throws IOException for missing/invalid configuration.
Handle this at composition/setup with a generic system error; never display raw
exceptions, database URLs or credentials. SQL errors during login map to
`SYSTEM_ERROR`; no checked exception escapes `login`.

`Account` is an immutable record exposing `userId()`, `username()`, `hoTen()`
and `role()` (a single `ADMIN`, `ACCOUNTANT` or `RESIDENT`). No password data
is included. `SessionContext.getCurrentUser()` returns Optional<Account>;
`getCurrentRole()` returns Role and throws IllegalStateException if logged out.
The global session is shared across AuthService instances (EP-06 BR-20).

| AuthStatus | Behavior |
| --- | --- |
| SUCCESS | Account included; session published after DB commit |
| VALIDATION_ERROR | Missing username/password, username outside ASCII alphanumeric 4–50, or password shorter than 6 |
| INVALID_CREDENTIALS | Unknown user or wrong password: `Tài khoản hoặc mật khẩu không chính xác!` |
| ACCOUNT_LOCKED | `Tài khoản của bạn đã bị tạm khóa!`; no account/session |
| SYSTEM_ERROR | Generic system message; no account/session or exception details |

Missing input uses the exact AC-03 message:
`Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!`.
No username or password is silently trimmed. SQL comparison uses the selected
MySQL database/table collation; case sensitivity is not additionally invented.

SRS BR-18 is the primary requirement: the sixth consecutive wrong password locks
for 5 minutes. Attempts during lock do not extend its deadline. At expiry the
next attempt starts with zero failures; success resets the counter. The
triggering sixth failure returns ACCOUNT_LOCKED. LOCKED with no deadline remains
locked for administrative intervention; no account administration UI is added.
The wording discrepancy with WBS DEC-14 remains recorded for Lead Dev review.

Use a background JavaFX Task for blocking JDBC/BCrypt, and update controls on
the JavaFX application thread. Only enter the Dashboard after isSuccess().
Use account.hoTen() for the greeting. On logout, Dev-2 clears password controls
and navigates back to LoginView; the service clears the session only. A new login
attempt clears any previous identity, including failed attempts/system errors.

Do not log passwords or bind StoredAccount (DAO-only credentials) to UI.
Session operations are serialized across service instances. Permission guards
and role management are US-018 work, not implemented by this delivery.
No outbound message has been sent to team members.

## Mock-compatible controller injection

Dev-2 should type the controller dependency as
`com.bluemoon.services.AuthenticationService` (interface), implemented by the real
`AuthService`. Keep the existing `AuthResult login(String, String)` signature.
The interface also declares `void logout()` and `Optional<Account> currentUser()`.
Construct the real service at application composition as above; use a
`MockAuthService implements AuthenticationService` during UI development.
No DB configuration is needed for a mock.

Public result factories for mocks:

```java
AuthResult.success(new Account(1, "admin", "Admin", Role.ADMIN));
AuthResult.failure(AuthStatus.INVALID_CREDENTIALS,
        "Tài khoản hoặc mật khẩu không chính xác!");
```

`success` rejects null accounts; `failure` rejects SUCCESS and null arguments.
See `src/test/java/com/bluemoon/integration/AuthenticationContractTest.java` for
a complete database-free mock. This mock is a test fixture, not application code
or a replacement for Dev-2's LoginController/FXML.
