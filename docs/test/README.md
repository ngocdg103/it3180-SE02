# Testing Documentation

## Dev-1 / US-018 Mốc 2

- `mvn clean verify`: **115 tests PASS**, không failures/errors/skips.
- `mvn -Pjavafx-smoke clean verify`: **116 tests PASS**, không failures/errors/skips.
- `git diff --check`: PASS.
- RBAC: 97 tests (66 permission/role combinations, 22 no-session cases, 9 scope,
  resolver-failure, session-change, matrix-coverage and fake-operation guard checks).
- Authentication regression: 12 service tests + 2 contract tests vẫn PASS;
  config: 4 PASS; JavaFX profile thêm 1 Stage/FXML check PASS.
- Unit RBAC không yêu cầu MySQL. Chưa chạy tích hợp UI/service của Dev-2/Dev-3
  hoặc production ownership adapter; không khẳng định toàn bộ US-018 đã nghiệm thu.

API, phạm vi và dependency: [US-018 implementation handoff](../design/us018-rbac-implementation-handoff.md).
Các kết quả US-012 bên dưới là các lần kiểm thử Foundation trước Mốc 2.

Thư mục lưu trữ tài liệu kiểm thử:
- Test Plan (Kế hoạch kiểm thử)
- Test Cases (Kịch bản kiểm thử chi tiết)
- Bug Reports & Log
- Test Summary Report (Báo cáo kết quả kiểm thử)

## Dev-1 / US-012 automated checks

Latest Dev-2 handoff check: `mvn -Pjavafx-smoke clean verify` passed **19 tests**
(4 configuration, 12 authentication, 2 external mock-contract, 1 FXML startup).
`mvn javafx:run` also launched the BlueMoon window successfully; the verification
window was closed normally and Maven reported BUILD SUCCESS. The MySQL result
below is the earlier backend integration run; it was not rerun for the interface-only change.

JDK 17 and Maven 3.9.x:

- `mvn clean verify`: configuration + service tests, no GUI/MySQL needed.
- `mvn -Pjavafx-smoke verify`: includes an FXML/Stage startup/close check.
- `mvn -Pmysql-it verify`: includes live MySQL tests when `BLUEMOON_MYSQL_TEST=1`.

For MySQL tests, first configure an isolated MySQL 8.x database ending in `_test`
via `BLUEMOON_DB_CONFIG` or the DB environment overrides. The suite loads the
canonical schema/seed, uses the public SRS admin fixture and cleans up its own
uniquely named fixture users. See developer-setup.md for initialization.

Verified together on JDK 17.0.20.1 / MySQL 8.4.13:

```powershell
$env:BLUEMOON_DB_CONFIG = '<local ignored properties file for the isolated test DB>'
$env:BLUEMOON_MYSQL_TEST = '1'
mvn '-Dtest=DatabaseConfigTest,AuthServiceTest,FoundationSmokeIT,AuthMySqlIT' clean verify
```

Result: **22 tests passed, no failures/errors/skips**. Includes sixth consecutive
failure locking, exact five-minute expiry, rollback, concurrent JDBC updates,
generic invalid-credential messages, session publication only after commit,
schema/seed reruns and JavaFX startup. Full review:
[Dev-1 rules review](../design/dev-1-rules-review-report.md).

Manual checks remaining: Dev-2 login/Dashboard navigation, logout navigation,
role-dependent UI (US-018 ownership), and another developer's machine. A passing
backend suite is not a claim that the complete login UI/US-012 has been accepted.

