# US-012 — API proposal for Dev-2

Status: **proposal, not implemented or agreed with Dev-2**. Full SRS is missing.
The signature `AuthResult login(String username, String password)` is required
by the handoff. WBS additionally lists `logout` and `currentUser`.

Proposed package: `com.bluemoon.services`.

```java
// Proposed composition at application/controller setup:
var connections = new DatabaseConnection(DatabaseConfig.load());
var auth = new AuthService(new AccountDAO(connections));

AuthResult result = auth.login(username, password);
auth.logout();
Optional<Account> account = auth.currentUser();
```

Proposed result API:

```java
AuthStatus status();
boolean isSuccess();
String message();
Optional<Account> account();
```

Proposed statuses: `SUCCESS`, `VALIDATION_ERROR`, `INVALID_CREDENTIALS`,
`ACCOUNT_LOCKED`, `SYSTEM_ERROR`. A disabled status depends on the SRS.
`Account` exposes account ID, username and assigned roles, never credential hash.
Failure results do not contain an account. Incorrect credentials always use
`Tài khoản hoặc mật khẩu không chính xác`, regardless of account existence.
System errors must not expose SQL, database credentials, or stack traces to UI.

Dev-2 should run the blocking JDBC/BCrypt call on a background `Task`, update
controls on the JavaFX application thread, and enter authenticated views only
after `isSuccess()`. Do not log passwords or bind persistence objects to views.

Before implementation: Lead Dev supplies SRS / confirms provisional source and
lock rules; Dev-2 agrees result API and session lifecycle; Dev-3 agrees schema
names and any future account/household relationship. No message has been sent
to other team members from this session.
