# P0 — MySQL IT fixture lifecycle hotfix

Ngày 08/11/2026. Branch `fix/mysql-it-fixture-lifecycle`, base `ac43254bf76da329c6f4eab235325bc2260a49d9` sau fetch. Development checkout ban đầu sạch tại App Shell HEAD `4d5e60854bc4f904b25eca7c86277cabcaeb76c7`; branch `feature/sprint1-app-shell` và PR #16 được giữ nguyên. Không dùng worktree merge cũ, không reset/clean/discard. PR hotfix riêng, không tự merge.

## Root cause và tái hiện

`FeeRateMySqlIT` áp migration US-005 rồi US-006, giữ bảng `fee_rates` có FK `fk_fee_rates_fee_type` tới `fee_types`. Cleanup của nó chỉ xóa các dòng fixture US-006, không DROP bảng. Sau đó `FeeTypeMySqlIT.@BeforeEach` cũ lại `DROP TABLE fee_types`: MySQL từ chối DROP bảng cha ngay cả khi bảng con đã rỗng. Hai test US-005 đều lỗi trước test body. Đổi thứ tự class không sửa được lifecycle giữa các lượt suite.

Đã dựng MySQL Community 8.4.0 portable riêng trong `.tools` ignored, datadir riêng, chỉ bind `127.0.0.1:33316`, schema disposable `bluemoon_fixture_hotfix_test`. Tài khoản test chỉ có quyền schema này. Không dùng MySQL production/shared, không cài service hệ thống. Config/password/log/runtime không commit. Fixture xác minh catalog thực tế kết thúc `_test` trước DDL/DML; không tắt foreign key checks.

Chạy **source main nguyên trạng** trước khi áp hotfix: `BLUEMOON_MYSQL_TEST=1`, `BLUEMOON_DB_CONFIG` trỏ config local ignored, `mvn -Pmysql-it clean verify`:

```text
Tests run: 481, Failures: 0, Errors: 2, Skipped: 0
FeeTypeMySqlIT.migrate: Cannot drop table 'fee_types' referenced by
foreign key constraint 'fk_fee_rates_fee_type' on table 'fee_rates'.
```

Hai lỗi đúng báo cáo Lead. Log local: `.tools/mysql-hotfix-before.log`. Metadata thực tế xác nhận FK tồn tại. Không chỉ suy luận từ static source.

## Ownership và thay đổi nhỏ nhất

| Fixture | Ownership sau sửa |
| --- | --- |
| FeeTypeMySqlIT | Chỉ các mã `US005_IT_PHI_QL`, `US005_IT_PHI`, `US005_IT_QUY`, `US005_IT_X`, `US005_IT_NONE` trong fee_types; tên/code sai bị constraint từ chối |
| FeeRateMySqlIT | Parent `US006_IT` và các fee_rates có ma_loai_thu=US006_IT; cleanup child trước parent như cũ |
| Household/Resident/Q17/Auth IT | Không thay đổi ownership hoặc lifecycle; hotfix không xóa bảng/dữ liệu của các fixture này |

- **FeeTypeMySqlIT.java:** bỏ DROP parent; chạy migration additive `CREATE TABLE IF NOT EXISTS`; cleanup chính xác các mã US-005 trước/sau mỗi test bằng PreparedStatement. Assert danh sách chỉ xét namespace US-005 thay vì giả định toàn bảng không có dữ liệu suite khác. Giữ các kiểm tra constraint/duplicate/rollback/status/filter, không nới lỏng rule nghiệp vụ. Mỗi SQL/cleanup kiểm tra catalog trước ghi.
- **FeeRateMySqlIT.java:** thêm regression giữ một FeeRate/parent US-006 đã persist, gọi chính setup/cleanup của FeeType fixture hai lượt, tạo/xóa dòng US-005 và assert parent + rate US-006 giữ nguyên (bao gồm generated id, giá, ngày, note). Không sao chép SQL cleanup vào test giả để tự chứng minh. Test này sẽ vấp chính DROP parent của lifecycle cũ.
- Hai class dùng cùng JUnit ResourceLock `fee-fixture-lifecycle` để namespace regression US-005 không chạy đồng thời với class US-005 nếu bật JUnit parallel trong một JVM. Không tuyên bố ResourceLock bảo vệ các process Maven độc lập; schema test phải được cấp riêng cho mỗi process.
- **Không thay đổi production DAO/models/schema/migration/pom**, không DROP bảng của suite khác, không disable FK checks. Nếu suite khác tham chiếu vào mã reserved US-005 trái ownership, cleanup sẽ fail bởi FK thay vì tự xóa dữ liệu child của suite đó.

## Verification cuối

JDK 17.0.17, Maven 3.9.9, Enforcer enabled/pass. Đã tự chạy đủ các lượt sau sửa:

| Lượt | Tests | Failures | Errors | Skipped | Kết quả |
| --- | ---: | ---: | ---: | ---: | --- |
| Baseline full MySQL (main trước sửa) | 481 | 0 | 2 | 0 | FAIL đúng 2 lỗi DROP parent |
| `mvn clean verify` | 429 | 0 | 0 | 0 | PASS |
| `mvn -Pjavafx-smoke clean verify` | 430 | 0 | 0 | 0 | PASS |
| `mvn -Pmysql-it clean verify` lần 1 | 482 | 0 | 0 | 0 | PASS |
| `mvn -Pmysql-it clean verify` lần 2 | 482 | 0 | 0 | 0 | PASS |

Mỗi lượt MySQL sau sửa: FeeTypeMySqlIT **2/2 PASS**, FeeRateMySqlIT **6/6 PASS** (5 test gốc + 1 regression lifecycle). Số test tăng 1 do regression mới, không bỏ hoặc skip test cũ. Các suite Household/Resident/Q17/Auth cùng chạy trong full suite và pass; không thay fixture của chúng. `git diff --check`: PASS.

Các log `.tools/mysql-hotfix-before.log`, `mysql-hotfix-normal.log`, `mysql-hotfix-smoke.log`, `mysql-hotfix-mysql-1.log`, `mysql-hotfix-mysql-2.log` là artifact local ignored. Metadata sau chạy vẫn có các FK fee_rates→fee_types, households→apartments, residents→households; foreign_key_checks=1, không còn dòng fixture fee US-005/US-006. Runtime portable được shutdown sau kiểm tra; không xóa datadir/log local.

Full MySQL chạy lặp trên **cùng server/schema sau baseline**, không drop database/reset datadir giữa các lượt. Đây là kiểm chứng lifecycle lặp, không phải hai lần chạy trên schema mới trống.

## QA và dependency PR #14

1. Dùng schema disposable `_test` riêng, user chỉ được quyền schema đó; cấu hình bằng cơ chế DatabaseConfig/BLUEMOON_DB_CONFIG hiện có, không commit credential.
2. Với JDK 17/Maven 3.9.9, chạy `mvn clean verify`, `mvn -Pjavafx-smoke clean verify`; bật `BLUEMOON_MYSQL_TEST=1` rồi `mvn -Pmysql-it clean verify` **hai lần trên cùng schema**, không xóa database ở giữa. Không skip test hoặc thay đổi test order để che lỗi.
3. Kiểm hai test FeeType đều pass, FeeRate gồm regression bảo toàn parent/rate cũng pass; xem FK còn tồn tại và không còn mã `US005_IT_*`/`US006_IT` sau cleanup. Không chạy hai Maven process chung một schema.
4. Sau QA xác nhận và PO merge hotfix, owner PR #14 phải cập nhật main mới và chạy lại regression/full MySQL trên bản tích hợp. Kết quả hotfix trên main hiện tại không thay thế kiểm thử Billing backend PR #14; fixture Billing mới vẫn phải tự quản lý dependency/snapshot tables của nó.

Commit/PR được báo riêng. Chỉ PO điều phối merge; không sửa/merge PR #14 hoặc PR #16 trong hotfix này.

**READY, không còn BLOCKER kỹ thuật sau verification.** QA xác nhận và PO merge vẫn là bước bàn giao bắt buộc trước khi PR #14 cập nhật main/chạy lại full regression. Không coi kết quả tại base này là bằng chứng PR #14 đã pass.
