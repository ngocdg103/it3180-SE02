# Dev-1 — Phase 1 report

> Historical Foundation-only report. The project_rules checkout was provided
> afterward; see [the current rules review report](dev-1-rules-review-report.md)
> for implemented authentication, live MySQL checks and remaining review items.

## 1. Completed

Reviewed the repository and four supplied workbooks, including sheet
`04 QUYET DINH CHUNG`. Added a Maven/JDK 17 Foundation, JavaFX/FXML placeholder,
external MySQL JDBC configuration and setup documentation. This is partial
Iteration 01 delivery, not completion of US-012.

## 2. Files changed

- New: `pom.xml`, `src/main/java/com/bluemoon/BlueMoonApplication.java`.
- New: `src/main/java/com/bluemoon/config/{DatabaseConfig,DatabaseConnection}.java`.
- New: `src/main/resources/com/bluemoon/views/FoundationView.fxml`.
- New: `src/test/java/com/bluemoon/config/DatabaseConfigTest.java`,
  `src/test/java/com/bluemoon/FoundationSmokeIT.java`.
- New: requirement review, API proposal, this report and developer setup guide.
- Updated: `.gitignore`, `config/db.properties.example`, `README.md`.
- SRS, workbook sources and canonical SQL files were not modified.

## 3. API contracts

Implemented: `DatabaseConfig.load()` and
`new DatabaseConnection(config).getConnection()`.
Connections are caller-owned; configuration failures use `IOException`, JDBC
failures use `SQLException`. These raw failures are for backend callers, not UI.

`AuthResult login(String username, String password)` remains the required
authentication signature. The proposed AuthResult, session and construction API
is documented in `auth-api-proposal.md`, not yet implemented/agreed with Dev-2.

## 4. Database changes

None. Schema/seed remain placeholders pending SRS and team schema agreement.
There is no operational account seed or claim of a successful MySQL connection.

## 5. Build/Test results

On Windows, portable Temurin JDK 17.0.20.1 and Maven 3.9.11:

- `mvn clean verify`: BUILD SUCCESS, 4 configuration tests passed.
- `mvn -Pjavafx-smoke clean verify`: BUILD SUCCESS, 5 tests passed,
  including loading the actual application FXML, showing the Stage and closing.
- Commands used local ignored tools/cache, equivalent to the commands above.
- `git diff --check`: passed before delivery.
- JavaFX smoke emitted an unnamed-module configuration warning; the stage
  check passed. The normal JavaFX Maven launch uses the JavaFX plugin.

Not verified: real MySQL connection/schema/seed/login, authentication flows,
integration with Dev-2, and a second developer machine.

## 6. Blockers

Missing full US-012 SRS and `docs/srs/EP06_VanHanhHeThong.md`.
Conflicting fifth-versus-sixth failure lock threshold between WBS sheets 03/04.
No local MySQL instance or credentials supplied for live verification.
See `iteration-01-review.md` for remaining business/schema questions.

## 7. Coordination needed

Lead Dev: supply SRS or explicitly authorize a provisional source, confirm lock
rules. Dev-2: review proposed AuthResult/session API; retain login UI ownership.
Dev-3: agree schema naming/keys before canonical schema integration. Another
developer: verify setup on their machine. No merge until at least one approval.
No outbound message was sent to any team member.

## 8. Next proposed task

After requirement clarification, implement transactional AccountDAO, BCrypt
authentication, lock behavior and account/role schema/seed; test against MySQL
8.x and publish the concrete API for Dev-2. Remain within US-012/Iteration 01.
