# Iteration 01 — Dev-1 requirement review

## Existing repository

Baseline: `5a11f321f0d440dc3c7db6983b4150261847d5bf` on `main`.
Only folder placeholders, README files, an example database configuration,
and placeholder `database/schema.sql` / `database/seed.sql` exist.
There is no Java implementation or Maven build.

## Sources reviewed

- `docs/srs/README.md`: folder description only; no US-012 specification.
- `SE_02-W02-UserStory.xlsx`, Sheet1, US-012: valid login grants access;
  invalid credentials refuse access; missing required input asks for completion.
- `SE_02-W02-Features.xlsx`, FE-061: authenticate before access.
- `SE_02_2.WBS.xlsx`: scheduling and assignment context.
- `SE_02_Implementation_WBS.xlsx`: `01 TASK THUC THI` US-012,
  `02 THEO USER STORY`, `03 HOP DONG TICH HOP`, and `04 QUYET DINH CHUNG`.

## Missing information / conflicts for Lead Dev

1. The required SRS is absent, including `docs/srs/EP06_VanHanhHeThong.md`
   referenced by DEC-05. Authentication must not be called SRS-compliant yet.
2. Integration sheet 03 says **over 5** incorrect passwords locks for 5 minutes;
   DEC-14 says **after 5** incorrect passwords. Confirm the exact threshold,
   whether failures must be consecutive, expiry/reset rules, and whether the
   triggering attempt returns a lock result or a generic credential error.
3. Sheet 03 specifies username length 4–50 and password length >=6. Confirm
   username case sensitivity/whitespace handling and disabled account behavior
   against the SRS. No such policies are invented in Foundation.
4. The handoff mandates BCrypt, while DEC-14 permits BCrypt/SHA-256: use BCrypt.
5. Original README allows multiple JDKs/DBMSs; the active handoff and decisions
   require Java 17, JavaFX/FXML, Maven, MySQL 8.x and JDBC. Setup reflects these.
6. DEC-13 suggests `feature/<US-ID>-<name>`; the user explicitly requires `dev-1`,
   created at the first push. Follow `dev-1`; no merge without review/approval.
7. Dev-3 household/apartment schema and account foreign keys are not available.
   Do not invent them or implement US-001 in Dev-1 work.
8. WBS asks for startup/connection checks on two machines. This session can
   verify only this machine; another team member must verify their environment.

## Implementation plan and ownership

New Foundation files:

- `pom.xml`: Java 17 compiler/enforcer, JavaFX/FXML, MySQL JDBC, BCrypt, JUnit.
- `com.bluemoon.BlueMoonApplication`: FXML application shell.
- `com.bluemoon.config.DatabaseConfig`: external properties/environment values.
- `com.bluemoon.config.DatabaseConnection`: fresh, caller-owned JDBC connections.
- `src/main/resources/com/bluemoon/views/FoundationView.fxml`: minimal placeholder.
- Setup and configuration tests.

Proposed next files, pending missing SRS/rule clarification:

- `com.bluemoon.models.Account`: public account identity and roles, no hash.
- `com.bluemoon.models.Role`: ADMIN, ACCOUNTANT, RESIDENT (DEC-05).
- `com.bluemoon.dao.AccountDAO`: prepared queries and transactional credential /
  failure-state access; private persistence data must not be returned to UI.
- `com.bluemoon.services.AuthService` and `AuthResult`: see API proposal.
- Canonical schema/seed: proposed `roles`, `accounts`, `account_roles` with
  BCrypt hash storage and lock metadata once rules are confirmed. PKs, unique
  username semantics, FK names and role cardinality require team agreement.

Login UI belongs to Dev-2. Household/apartment tables belong to Dev-3.
The canonical SQL files and all SRS/WBS sources remain untouched in this phase.
