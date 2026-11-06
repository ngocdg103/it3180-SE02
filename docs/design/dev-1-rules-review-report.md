# Dev-1 — project_rules review and corrections

## 1. Completed

Read local `it3180-SE02` at `feature/project_rules`, commit `64605dd`, including
the complete EP-06 and workbook sheets 03/04. Merged that commit into `dev-1`,
preserving original SRS/workbook contents and authorship. No files in the user's
nested checkout were modified. Rechecked Foundation and implemented backend
US-012 now that its primary specification is available.

Corrections: the initial proposed accounts/roles/account_roles design did not
match SRS. Use a single `users` table, one Role per account, exact Vietnamese
AC messages and a global SessionContext. BCrypt remains mandatory under the
handoff; SQL access is prepared and state changes are transactional.

## 2. Files changed

- Added models Account/Role, DAO AccountRepository/AccountDAO, services
  AuthService/AuthResult/AuthStatus/SessionContext under `com.bluemoon`.
- Replaced canonical schema/seed placeholders with additive US-012 SQL.
- Added AuthServiceTest and opt-in AuthMySqlIT; added Maven mysql-it profile.
- Updated README, CHANGELOG, API/setup/test documentation. Earlier reports are
  explicitly historical and link here.
- Imported the unchanged team SRS and workbook via the project_rules merge.

## 3. API contracts

`AuthResult login(String username, String password)`, `void logout()`,
`Optional<Account> currentUser()` are implemented in `com.bluemoon.services.AuthService`.
AuthResult exposes status/isSuccess/message/account; Account has userId,
username, hoTen, one role and no password hash. See auth-api-proposal.md for
construction, injection and the UI error/session contract. Dev-2 has not yet
confirmed integration; no claim of a team agreement or sent message is made.

## 4. Database changes

`users`: id PK, username unique, password_hash, ho_ten, role,
trang_thai, so_lan_sai, locked_until. InnoDB and SELECT FOR UPDATE serialize
login counter transitions. locked_until is an additive UTC deadline for BR-18;
success resets failures, and the session is published only after commit.
Public SRS admin fixture stored as BCrypt hash; rerunning seed preserves users.
No household FK or US-018 permission management was invented.

## 5. Build/Test results

Windows, Temurin JDK 17.0.20.1, Maven 3.9.11, temporary MySQL **8.4.13** bound
to 127.0.0.1 on port 33316. Credentials generated locally in ignored .tools/.

Combined `clean verify` with explicit test selection: **22 tests, 0 failures,
0 errors, 0 skipped; BUILD SUCCESS**:

- 4 external-configuration tests.
- 12 authentication tests: valid/invalid/missing input, roles, session/logout,
  lock boundary/expiry/reset, administrative lock, DB/commit/corrupt-hash errors.
- 5 real MySQL tests: repeatable schema/seed and public admin login, persistent
  lock/expiry, rollback, concurrent row updates, prepared-query injection input.
- 1 JavaFX/FXML stage startup/close check (unnamed-module warning, check passed).

Exact command and environment setup are in docs/test/README.md. Initial sandboxed
tests hit OS temporary-directory cleanup permissions; an authorized rerun passed.
Only this machine is verified; not a second teammate's environment or Dev-2 UI.

## 6. Blockers / remaining discrepancies

- Missing SRS blocker is resolved. SRS BR-18 and sheet 03 say **over five**;
  DEC-14 says **after five**. Implemented sixth failure using the user's explicit
  SRS-first precedence; Lead Dev still needs to reconcile the workbook wording.
- The SRS data dictionary lists separate username/password errors, but BR-19
  and AC-02 require a generic credential error; implemented BR-19/AC-02.
- Username case sensitivity is not specified; it follows database collation.
- BCrypt's 72-byte password limit needs to be considered in future account
  provisioning/password-change work; those user stories are outside this change.
- Prior pushes were rejected by GitHub `commit_refs_rules`; no remote branch,
  PR, approval or merge has been verified for this delivery.

## 7. Coordination needed

Dev-2: integrate the documented API via a background Task; implement greeting,
Dashboard navigation and logout navigation. Dev-3: reference users.id only after
agreeing future household linkage; no household schema is owned here. Lead Dev:
review lock wording, API, schema and require at least one approval before merge.
The user-requested branch is dev-1, overriding DEC-13's suggested branch name.

## 8. Next proposed task

Integrate and QA Dev-2's login screen, verify setup on a second machine, resolve
GitHub publishing/review and obtain Lead Dev acceptance of US-012. Do not start
another user story automatically. The current app remains a Foundation placeholder.
