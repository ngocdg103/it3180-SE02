# US-018 — Mốc 2: Authorization Implementation handoff

## Phạm vi và Git

Dev-1, WBS **1.4.2.2.3**, branch `feature/us018-rbac`.
Base Foundation: `4206c7968c78fec17b71d63d8bf075666ff547ee`.
Working tree sạch trước khi triển khai. Không merge/rebase/cherry-pick nhánh của
Dev-2/Dev-3. Mốc 2 được thực hiện sau lệnh phê duyệt có điều kiện của Lead Dev.

Giữ nguyên AuthService, AuthenticationService, Account, SessionContext,
Role, Login UI và database/schema.sql/seed.sql. Không tích hợp vào HouseholdServiceImpl.
Không thay đổi chính sách khóa tài khoản US-012.

## API cho Dev-2 và Dev-3

```java
import com.bluemoon.models.Permission;
import com.bluemoon.services.AuthorizationService;
import com.bluemoon.services.RoleBasedAuthorizationService;
import com.bluemoon.services.ApartmentOwnershipResolver;

// Chưa có production ownership adapter: truyền null để OWN luôn bị từ chối.
// Sau khi bên dữ liệu cung cấp adapter thật, inject vào cùng constructor.
AuthorizationService authorization = new RoleBasedAuthorizationService(null);
```

Không tạo hoặc inject tài khoản RESIDENT giả trong production. Khi chưa có
adapter, role-only checks vẫn hoạt động, nhưng không tài khoản nào được cấp OWN.

Bốn phương thức đúng contract đã duyệt:

```java
boolean canAccess(Permission permission);
void requirePermission(Permission permission);
boolean canAccessApartment(String soCanHo, Permission permission);
void requireApartmentPermission(String soCanHo, Permission permission);
```

Port dữ liệu duy nhất, không phải DAO triển khai:

```java
boolean isLinked(long userId, String soCanHo);
```

Permission có đúng 22 enum constant như matrix đã duyệt. Mapping thuần role
lưu trong map bất biến; không có ADMIN wildcard hoặc quyền DELETE. ADMIN không
có PAYMENT_RECORD; ACCOUNTANT không có quyền quản trị tài khoản hoặc tạo/sửa
hộ và nhân khẩu. RESIDENT không có VIEW_ALL.

## Session, scope và exception

- Mỗi canAccess* đọc một snapshot Account hiện tại. Production dùng
  SessionContext.getCurrentUser, không cache principal/role. require* gọi lại
  phép kiểm tra tương ứng, không dựa vào kết quả UI trước đó.
- Thiếu session, null permission hoặc quyền không được ánh xạ: DENY.
- canAccess chỉ cấp quyền thuần role; PAYMENT_HISTORY_VIEW_OWN luôn false ở đây.
- API apartment chỉ cho PAYMENT_HISTORY_VIEW_OWN với RESIDENT, soCanHo khác
  null/blank, resolver tồn tại và xác minh chính xác userId/căn hộ đó.
- Không trim hoặc đổi mã căn hộ. ADM/ACCOUNTANT xem căn hộ bằng quyền VIEW_ALL,
  không dùng API OWN. Không dùng OWN để mở truy vấn lịch sử toàn tòa.
- Thiếu resolver, thiếu liên kết hoặc resolver ném RuntimeException: DENY;
  requireApartmentPermission cũng ném AccessDeniedException chung, không lộ
  message/cause nhạy cảm của resolver.
- Exception duy nhất: com.bluemoon.services.AccessDeniedException extends
  RuntimeException, constructor String. Đây là type tương thích bản Dev-3;
  **cần đối chiếu class cùng tên khi tích hợp hai PR**, không tạo type thứ hai.

Resolver adapter phải lấy liên kết tin cậy từ backend. Không biến claim sở hữu
do UI truyền vào thành quyền. Port không khai báo checked exception; adapter
lỗi truy xuất có thể bọc trong RuntimeException để authorization từ chối.

## Cách tích hợp sau khi owner được duyệt

UI dùng canAccess* để hide/disable. Business service gọi require* trước thao tác
dữ liệu dù UI đã ẩn nút. Ví dụ minh họa, chưa gắn vào service thật:

```java
authorization.requirePermission(Permission.PAYMENT_RECORD);
// Chỉ sau guard mới gọi thao tác ghi nhận thanh toán.
```

```java
if (!authorization.canAccess(Permission.PAYMENT_HISTORY_VIEW_ALL)) {
    authorization.requireApartmentPermission(soCanHo, Permission.PAYMENT_HISTORY_VIEW_OWN);
}
// Chỉ query đúng soCanHo đã kiểm tra, không tải cả tòa rồi lọc trên UI.
```

Dev-3: inject AuthorizationService ở giai đoạn tích hợp được duyệt, thay guard
nội bộ theo HOUSEHOLD_VIEW/CREATE/UPDATE; thống nhất exception cùng package.
Dev-2: dùng API UI, cung cấp dữ liệu/resolver và tích hợp module khoản thu sau
khi duyệt. Không sửa lại AuthenticationService để gán role.

## Kết quả kiểm thử đã chạy

Windows, Temurin **JDK 17.0.20.1**, Maven **3.9.11**; dùng portable tools và cache
local trong các thư mục Git bỏ qua. Maven chỉ thêm tham số batch/no-transfer-progress,
log output và đường dẫn cache; hai lifecycle/profile bắt buộc được chạy thật:

| Lệnh | Kết quả |
| --- | --- |
| mvn clean verify | BUILD SUCCESS; 115 tests, 0 failures/errors/skips |
| mvn -Pjavafx-smoke clean verify | BUILD SUCCESS; 116 tests, 0 failures/errors/skips |
| git diff --check | PASS |

RBAC có **97 tests**: 66 tổ hợp của 22 permissions × 3 roles, 22 trường hợp không
session, 9 test matrix coverage/scope/failure/session/guard. Expected matrix độc
lập với implementation; unit tests không dùng MySQL hoặc BCrypt để tạo session.
Test production constructor đọc chính SessionContext hiện có và xác nhận quyền
thay đổi sau đổi role/logout. Fake business operation xác nhận DENY không chạm DAO.

Regression giữ nguyên: 12 AuthService tests, 2 Authentication contract tests,
4 config tests đều PASS. JavaFX profile thêm 1 FXML/Stage smoke test PASS, có
warning unnamed-module giống Foundation trước đó. Không chạy lại MySQL integration
trong đợt RBAC này; không có thay đổi schema/DAO.

## Dependency chưa hoàn thành, ngoài phạm vi Mốc 2

1. Production adapter/liên kết tài khoản–căn hộ và hợp đồng dữ liệu/thời điểm hiệu lực.
2. UI quản lý tài khoản và AccountDAO quản trị tài khoản.
3. Gán/đổi role, bảo vệ Admin cuối cùng, đồng bộ session khi role đổi ngoài session hiện tại.
4. Tích hợp authorization vào service của Dev-2/Dev-3; kiểm soát đồng thời giữa
   xác minh quyền/phạm vi và transaction nghiệp vụ khi adapter được xây dựng.
5. UI hide/disable BR-21 và kiểm thử tích hợp/QA hai AC giao diện đầy đủ.
6. Review và thống nhất AccessDeniedException giữa các PR trước tích hợp.

Mốc 2 hoàn thiện phần độc lập, chưa phải hoàn thành toàn bộ US-018. PR chỉ gửi
để Lead Dev review, không tự merge và không chuyển sang US khác. Tài liệu này
là API handoff trong repository; không tự gửi tin nhắn cho thành viên khác.
