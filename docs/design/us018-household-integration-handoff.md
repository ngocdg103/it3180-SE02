# US-018 — Household Service Integration handoff

## Git và phạm vi

Ngày 07/11/2026; Dev-1, WBS 1.4.2.2.3. Branch mới
`feature/us018-household-integration` từ origin/main tại
`3d0f86082263581f5f4a35c4295ca133c6b4b83f`. Đã fetch và xác minh commit này
chứa Authorization Layer, Foundation và backend US-001 (`cd49be5`).
Working tree sạch trước khi tạo branch; không có thay đổi chưa commit cần stash.
Không merge/rebase/cherry-pick nhánh Dev-2/Dev-3 và không sửa nhánh integration cũ.

## Thay đổi production

Chỉ `HouseholdServiceImpl.java`:

- Inject AuthorizationService thay Supplier<Role>; bỏ field currentRole và
  toàn bộ helper/policy requireRole. Chỉ RoleBasedAuthorizationService quyết định matrix.
- createHousehold → HOUSEHOLD_CREATE; updateHousehold → HOUSEHOLD_UPDATE.
- getHouseholdById/getHouseholdByApartment/listHouseholds → HOUSEHOLD_VIEW.
- Mỗi guard chạy ở đầu hàm, trước null/field validation, charge lookup hoặc
  truy cập repository. AccessDeniedException không bị nuốt thành kết quả thành công.
- Giữ logic dữ liệu và BR-02, kết quả/DTO, interface HouseholdService không đổi.

AuthService/AuthenticationService/Account/SessionContext, authorization layer,
exception chung, DAO, schema, migrations, seed và Login UI giữ nguyên.
Không ADMIN wildcard; RESIDENT không được dùng OWN để mở quyền hồ sơ hộ.

## Constructor và caller đã kiểm tra

Hai tham số production giữ nguyên:

```java
new HouseholdServiceImpl(repository, charges);
```

Nó delegate sang RoleBasedAuthorizationService(null), luôn đọc session hiện tại.
Null resolver không mở OWN; các permission Household thuần role không cần ownership.

Constructor đầy đủ đã đổi theo review được duyệt:

```java
new HouseholdServiceImpl(repository, charges, authorization, clock);
```

Đây là thay đổi source compatibility của constructor implementation, không phải
interface HouseholdService. Caller dùng Supplier<Role> cũ phải chuyển sang
AuthorizationService; không giữ adapter legacy dựng principal giả hoặc map role thứ hai.

Đã tìm toàn bộ caller trong main đang làm việc: HouseholdServiceTest và
HouseholdMySqlIT. Cả hai được đổi sang real RoleBasedAuthorizationService.
HouseholdTestAuthorization chỉ là fixture trong src/test/java cho test DAO khác
package, không được đóng gói cùng production. Không fake role trong production.
Đã cập nhật ví dụ constructor cũ trong docs/design/us-001-api-contract.md.
Owner các branch UI chưa merge cần đối chiếu nếu đang dùng constructor 4 tham số;
constructor 2 tham số không đổi. Không tự sửa các nhánh UI đó.

## Tests và verification

JDK 17.0.20.1 / Maven 3.9.11 trên Windows. Hai lifecycle chạy thật bằng portable
tools trong thư mục ignored, với tham số batch/log/cache local:

| Lệnh | Kết quả |
| --- | --- |
| mvn clean verify | BUILD SUCCESS, 204 tests, 0 failures/errors/skips |
| mvn -Pjavafx-smoke clean verify | BUILD SUCCESS, 205 tests, 0 failures/errors/skips |
| git diff --check | PASS |

29 test HouseholdAuthorizationIntegrationTest dùng real HouseholdService và
real RoleBasedAuthorizationService:

- 5 entry point × ADMIN/ACCOUNTANT/RESIDENT/không session (20 cases), kiểm tra
  đúng permission và DENY không gọi repository/charge lookup.
- 5 cases null input: guard chặn trước validation, không lộ kết quả dữ liệu.
- Cùng production service instance đọc đúng session sau đổi role/logout; boolean
  UI cho phép trước logout không giúp gọi service sau logout.
- OWN được xác minh vẫn không cấp HOUSEHOLD_VIEW cho RESIDENT.
- ACCOUNTANT không cập nhật được hoặc làm thay đổi dữ liệu hộ hiện có.
- Constructor không chấp nhận authorization null.

60 regression Household giữ nguyên nội dung nghiệp vụ, chỉ đổi injection; 97
RBAC + 12 AuthService + 2 Authentication contract + 4 config đều PASS.
Profile JavaFX thêm 1 FXML/Stage test PASS, warning unnamed-module như Foundation.

HouseholdMySqlIT được cập nhật constructor và compile thành công, nhưng chưa
chạy MySQL opt-in trong đợt này. Không báo đã kiểm thử DB integration mới hoặc
UI QA. Production DAO/schema không thay đổi.

## File bàn giao

- HouseholdServiceImpl.java: production injection/guards.
- HouseholdServiceTest.java, HouseholdMySqlIT.java: caller/test injection.
- HouseholdAuthorizationIntegrationTest.java: regression integration in-memory.
- HouseholdTestAuthorization.java: fixture test-only dùng policy RBAC thật.
- us-001-api-contract.md: ví dụ constructor và guard hiện tại.
- us018-rbac-service-integration-review.md: dẫn sang implementation đã duyệt.
- docs/test/README.md và tài liệu handoff này: bằng chứng và dependency.

## Dependency còn lại

1. UI của Dev-2 dùng canAccess cho hide/disable và catch AccessDeniedException;
   service guard đã thực thi độc lập, không thay thế QA giao diện.
2. Owner các branch chưa merge rà constructor 4 tham số; không giữ Supplier<Role> legacy.
3. Liên kết tài khoản–căn hộ/production resolver chưa có. OWN vẫn fail closed;
   không cần hoặc tự tạo adapter thật cho US-001.
4. ApartmentChargeLookup production của US-007 vẫn là dependency có từ trước,
   không được giải quyết bằng RBAC integration này.
5. DAO/UI quản trị tài khoản, gán role, bảo vệ Admin cuối cùng và các service
   nghiệp vụ khác ngoài US-001 chưa được triển khai trong nhiệm vụ này.

Chỉ commit/push nhánh mới và gửi PR review; không tự merge, không chuyển US khác.
