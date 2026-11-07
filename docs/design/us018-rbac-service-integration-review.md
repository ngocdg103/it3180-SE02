# US-018 — Service Integration Preparation, Mốc 1

> Review lịch sử đã được Lead Dev phê duyệt. Implementation trên branch mới từ
> main đã được ghi trong [Household integration handoff](us018-household-integration-handoff.md).
> Các mô tả “hiện tại/chưa triển khai” bên dưới phản ánh thời điểm review trước integration.

Ngày: 07/11/2026. Chỉ review/đề xuất cho WBS **1.4.2.2.3**.
Chưa sửa production code, chưa tạo branch integration, chưa merge/rebase.

## 1. Git và nguồn đã xác minh

- Working tree sạch khi bắt đầu; giữ branch `feature/us018-rbac` tại
  `1944a3c9b8e6f33fe9c86df44353758a04aa3275`.
- Base Foundation của nhánh: `4206c7968c78fec17b71d63d8bf075666ff547ee`.
- Đã fetch `origin/main` và remote backend US-001, không checkout/import code.
- Main mới: `215d5ff95d000626e673c333f266738f4f564b1a`, đã có Foundation
  (merge `85e9236`) và US-001 (merge `215d5ff`). Đã kiểm tra ancestry:
  `cd49be571248330f5a454de6e31a3e386f7bf8ec` của backend US-001 nằm trong main.
- HouseholdService/Impl và HouseholdServiceTest trên main không khác bản remote
  US-001 đã đọc. Account, AuthenticationService, AuthService, SessionContext
  không khác giữa main và nhánh RBAC.
- Main chưa có Permission/AuthorizationService/RoleBasedAuthorizationService/
  ApartmentOwnershipResolver. Các lớp đó hiện có trên nhánh RBAC được bàn giao.
- Đã đối chiếu SRS EP-06 US-018 BR-21/BR-22 và WBS 1.4.2.2.3. Các hợp đồng
  role/ownership đã duyệt ở Mốc 2 được giữ nguyên; không thêm assignRole ở đây.

## 2. Service nào đã có kiểm tra quyền

| Service / phạm vi | Hiện trạng đã đọc |
| --- | --- |
| HouseholdServiceImpl trên main/remote US-001 | Cả 5 entry point có requireRole ở đầu hàm, trước validation/DAO |
| RoleBasedAuthorizationService trên nhánh RBAC | Đã có matrix tập trung, deny khi thiếu session, OWN có scope; chưa được US-001 gọi |
| AuthService | Xác thực/lock/session US-012; không phải nơi đặt guard nghiệp vụ phân quyền |
| Service quản lý tài khoản, khoản thu, nhân khẩu, payment/history/report | Chưa có implementation tương ứng trong main đã fetch; không thể kết luận đã được bảo vệ |

HouseholdServiceImpl đang dùng Supplier<Role> + requireRole(boolean write):
ADMIN đọc/ghi; ACCOUNTANT chỉ đọc; RESIDENT/không session bị chặn.
Đây là guard có hiệu lực hiện tại, không phải service hoàn toàn thiếu bảo vệ.
Điểm cần thay là nguồn policy phân tán, không bổ sung thêm một lớp if-role nữa.

## 3. Một nguồn quyết định RBAC

Thay từng requireRole bằng requirePermission rồi bỏ field currentRole và helper
requireRole, không giữ cả hai. Map được quyết định chỉ trong RoleBasedAuthorizationService.

| Entry point HouseholdServiceImpl | Guard thay thế |
| --- | --- |
| createHousehold | requirePermission(HOUSEHOLD_CREATE) |
| updateHousehold | requirePermission(HOUSEHOLD_UPDATE) |
| getHouseholdById | requirePermission(HOUSEHOLD_VIEW) |
| getHouseholdByApartment | requirePermission(HOUSEHOLD_VIEW) |
| listHouseholds | requirePermission(HOUSEHOLD_VIEW) |

Guard vẫn chạy trước validation, lookup, charge check và đọc/ghi repository.
Không bắt AccessDeniedException để biến nó thành success hoặc tiếp tục xử lý.
Giữ nguyên contract kết quả, field validation, transaction DAO và BR-02 xác nhận
đổi diện tích của US-001. ADMIN không có PAYMENT_RECORD; không tái sử dụng
logic ADMIN toàn quyền cho service thanh toán trong tương lai.

## 4. Constructor injection và tương thích cần duyệt

Đề xuất giữ constructor production 2 tham số
`HouseholdServiceImpl(repository, charges)` để caller hiện có tiếp tục dùng;
nó delegate sang một RoleBasedAuthorizationService(null), không cấp OWN khi
chưa có adapter. Thêm/thay constructor đầy đủ bằng:

```java
HouseholdServiceImpl(HouseholdRepository repository,
                     ApartmentChargeLookup charges,
                     AuthorizationService authorization,
                     Clock clock)
```

Constructor hiện có nhận Supplier<Role>, Clock cần thay theo hợp đồng mới;
đây là thay đổi source compatibility phải Lead Dev/Dev-3 xác nhận trước triển khai.
Không giữ constructor cũ bằng cách dựng Account giả hoặc một map role thứ hai.

Trong main đã kiểm tra, constructor Supplier<Role> được dùng ở
HouseholdServiceTest và HouseholdMySqlIT; các call site test này cần đổi.
Chưa xác minh caller trên tất cả các nhánh UI đang QA, nên owner cần rà lại
trước khi chốt. Không tự sửa Login UI hoặc Household UI trong mốc review này.

## 5. Exception và session dùng chung

AccessDeniedException hai nhánh có cùng FQCN
`com.bluemoon.services.AccessDeniedException`, extends RuntimeException,
constructor String; diff chỉ có Javadoc. Main còn chú thích BR-20 thay vì BR-22.
Khi tích hợp RBAC, giữ một file/class, thống nhất Javadoc; không đổi exception
type và không tạo package exception thứ hai. Chưa khẳng định có merge conflict
thực tế; cần Lead Dev xử lý phần trùng nội dung khi duyệt tích hợp.

SessionContext hai nhánh giống nhau. Authorization đọc getCurrentUser mỗi lần
kiểm tra, lấy role/userId từ cùng snapshot và không cache role. Không sửa
SessionContext, Account, AuthenticationService hay policy khóa AuthService.
Thông báo thiếu session có thể trở thành generic AccessDeniedException từ guard
chung; UI nên dựa vào type, không phụ thuộc nguyên văn message cũ của requireRole.

## 6. ADMIN / ACCOUNTANT / RESIDENT và ownership

- US-001: ADMIN được đọc/tạo/sửa; ACCOUNTANT chỉ đọc; RESIDENT không có quyền
  đọc hoặc ghi hồ sơ hộ, kể cả căn hộ của mình. OWN chỉ áp dụng lịch sử đóng phí,
  không được dùng để mở HOUSEHOLD_VIEW hay RESIDENT_PROFILE_VIEW.
- Lịch sử toàn tòa: ADMIN/ACCOUNTANT dùng PAYMENT_HISTORY_VIEW_ALL.
- Lịch sử căn hộ: nếu không có VIEW_ALL thì phải requireApartmentPermission
  với PAYMENT_HISTORY_VIEW_OWN cho đúng String soCanHo trước query.
- Không có session, không có resolver/link hoặc resolver lỗi: deny; không suy
  diễn liên kết từ username, maHo hoặc dữ liệu UI.
- users và migration US-001 chưa có liên kết tài khoản–căn hộ. Chưa triển khai
  adapter thật; đây là dependency của bên dữ liệu, không chặn guard role-only
  cho US-001 nhưng chặn ALLOW OWN của RESIDENT trên dữ liệu production.
- Ownership kiểm tra và DAO phải dùng cùng đối tượng; chính sách đồng thời và
  thời điểm hiệu lực liên kết cần chốt khi có adapter/service lịch sử thật.

## 7. UI dùng canAccess mà không bỏ qua Service

Có thể dùng API đã bàn giao cho menu/nút: HOUSEHOLD_VIEW cho menu, CREATE/UPDATE
cho nút tương ứng; ACCOUNT_VIEW và PAYMENT_RECORD phục vụ hai AC của US-018.
Controller nhận cùng interface AuthorizationService, không tự so role.
Mỗi lần gọi service vẫn requirePermission, kể cả UI vừa cho phép.
UI cần refresh khi session thay đổi; không lưu boolean quyền để dùng làm bằng
chứng trong lần gọi service sau. Catch AccessDeniedException để hiển thị lỗi,
không bypass guard hoặc gọi DAO trực tiếp.

canAccess(PAYMENT_HISTORY_VIEW_OWN) luôn false theo contract. Với dữ liệu riêng,
UI phải có căn hộ được backend xác minh rồi dùng canAccessApartment. Không tự
mở menu/toàn bộ dữ liệu chỉ vì role RESIDENT hoặc sửa role-only API thành ALLOW.
Mốc review này không sửa Login UI đang QA; chưa xác nhận AC UI đã nghiệm thu.

## 8. File thật sự cần sửa sau khi được duyệt

Bắt buộc:

1. HouseholdServiceImpl.java: dependency AuthorizationService, constructor và 5
   guard; xóa policy role cũ. Giữ phần nghiệp vụ/DAO như hiện tại.
2. HouseholdServiceTest.java: đổi injection và giữ toàn bộ regression US-001.
3. HouseholdMySqlIT.java: cập nhật constructor test; không đụng DAO/schema.
4. Thêm HouseholdAuthorizationIntegrationTest.java (tên đề xuất): dùng real
   RoleBasedAuthorizationService với session và fake/spying repository.
5. Tài liệu handoff/test: cập nhật API wiring, dependency và kết quả chạy thật.

Tùy tích hợp: HouseholdService.java chỉ Javadoc guard/BR-22, không đổi phương thức;
AccessDeniedException.java chỉ hợp nhất Javadoc trong một class hiện có.
Không cần sửa authorization matrix/API, AuthService, Account, SessionContext,
schema, AccountDAO hay application/Login UI cho phần US-001 service integration.

## 9. Regression cần viết/chạy sau khi triển khai

- Matrix 5 methods × 4 trạng thái principal: ADMIN, ACCOUNTANT, RESIDENT, không session.
- Xác nhận mỗi method gọi đúng permission, dùng real authorization ở test bảo mật;
  không viết lại matrix if-role trong fake production adapter.
- DENY không gọi bất kỳ repository method hoặc ApartmentChargeLookup nào; guard
  vẫn chạy trước validation kể cả form null/không hợp lệ.
- ADMIN có CRUD được SRS cho phép (chỉ create/update/read, không thêm delete);
  ACCOUNTANT đọc được nhưng create/update không làm đổi dữ liệu; RESIDENT bị chặn.
- Giữ cùng service instance khi đổi session/logout: không cache role, không stale allow.
- Gọi service trực tiếp hoặc UI đã cho phép trước logout vẫn bị chặn sau logout.
- Regression US-001: validate/duplicate/khóa liên kết, diện tích cần confirmation,
  not-found/filter/order, dữ liệu bất biến, generic system error và rollback có sẵn.
- Authentication 12 tests + contract tests và 97 RBAC tests phải tiếp tục PASS.
- OWN/ALL giữ nguyên: đúng/sai căn hộ, thiếu link/resolver, resolver lỗi, role-only
  không bypass scope; không cần adapter thật cho các unit test.
- Sau implementation chạy mvn clean verify, mvn -Pjavafx-smoke clean verify,
  git diff --check. MySQL suite opt-in sau khi có môi trường test riêng; không báo
  đã chạy khi chưa chạy. Không chạy Maven ở Mốc 1 vì chỉ review tài liệu.

## 10. Thứ tự tích hợp và quyết định cần Lead Dev

1. Foundation + US-001 trên main: **đã xác minh**, blocker này được gỡ.
2. Lead Dev thống nhất đưa RBAC đã duyệt vào main, giữ một AccessDeniedException;
   main hiện chưa chứa authorization layer, chưa tự thao tác PR/merge ở review này.
3. Lead Dev xác nhận tên branch integration và commit base sau khi các dependency
   cần thiết đã có trên main. Không tự lấy nhánh UI hoặc tạo branch khác trong Mốc 1.
4. Duyệt constructor injection và ảnh hưởng caller/test với Dev-3/Dev-2.
5. Triển khai thay guard trên branch được xác nhận; chạy regression và gửi PR,
   không tự merge. Sau đó owner UI dùng API checks và QA kiểm thử tích hợp.
6. Ownership production, gán role/Admin cuối cùng và service ngoài US-001 chờ
   hợp đồng dữ liệu/owner riêng; không tự mở rộng phạm vi trong đợt này.

Kết thúc Mốc 1: chỉ thêm tài liệu review này trên branch US-018 hiện có.
Commit/push tài liệu phục vụ Lead Dev review; chưa thay production code/test,
chưa merge/rebase hoặc triển khai integration.
