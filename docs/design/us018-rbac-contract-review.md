# US-018 — Mốc 1: Contract Review, chờ Lead Dev duyệt

Ngày: 06/11/2026. Phạm vi Dev-1: WBS **1.4.2.2.3**, authorization nghiệp vụ.
Đây là bản thiết kế; chưa tạo production class, unit test hay thay đổi chính sách
US-012. Không chuyển sang Mốc 2 trước khi Lead Dev duyệt public API.

## 1. Git pre-flight và nguồn đã đọc

- Đã kiểm tra status, branch -vv, remote -v, log -5 và fetch origin.
- Branch mới: `feature/us018-rbac`, base `dev-1` tại
  `4206c7968c78fec17b71d63d8bf075666ff547ee`, trùng origin/dev-1 khi kiểm tra.
- Foundation, AuthenticationService/AuthService và SessionContext đều có trên base.
- Không lấy login-ui hoặc US-005 làm base; không reset, force-push hay merge.
- Trước khi tạo branch, working tree đã sạch. Repo tham chiếu độc lập
  `it3180-SE02/` được giữ nguyên và chỉ loại trừ qua `.git/info/exclude` local;
  không xóa hoặc thêm nó vào Git của dự án.
- Đọc EP-06 mục US-018/BR-21/BR-22/AC-01/AC-02 và workbook gốc:
  sheet 01, dòng 1.4.2.2.3 và các dòng phụ thuộc; sheet 03, hợp đồng US-018.
- Đọc Account, Role, SessionContext, AuthenticationService, AuthService,
  AccountRepository/AccountDAO và schema hiện có.
- Đọc riêng mã US-001 từ `origin/feature/us001-household-backend` bằng git show,
  không checkout/import. HouseholdServiceImpl đang kiểm tra role nội bộ;
  AccessDeniedException đã có ở nhánh đó. PK hộ/căn hộ là chuỗi, không phải long.

## 2. API hiện có và ảnh hưởng tương thích

- `Account(long userId, String username, String hoTen, Role role)`: không có căn hộ.
- `Role`: ADMIN, ACCOUNTANT, RESIDENT; không thêm role mới.
- `SessionContext.getCurrentUser()`: Optional<Account>.
- `SessionContext.getCurrentRole()`: ném IllegalStateException nếu chưa đăng nhập.
- AuthenticationService: login/logout/currentUser; AuthService đã triển khai.
- AccountDAO chỉ phục vụ credential/lock state; không có chức năng gán role hay
  liên kết tài khoản–căn hộ. Không tạo AccountDAO hoặc SessionContext thứ hai.
- Trên base chưa có Permission, AuthorizationService hay AccessDeniedException.
  Riêng nhánh Dev-3 có `com.bluemoon.services.AccessDeniedException(String message)`.

Đề xuất giữ nguyên Account, Role, SessionContext và toàn bộ hợp đồng xác thực.
Authorization đọc một snapshot Account qua getCurrentUser mỗi lần kiểm tra và
lấy role từ snapshot đó: cùng nguồn session của BR-22, tránh đọc userId/role từ
hai phiên khác nhau nếu logout/login xảy ra giữa hai getter. Không cache role
trong constructor. Lead Dev duyệt cách đọc snapshot này trong contract.

## 3. Permission enum / matrix đề xuất

Package: `com.bluemoon.models.Permission`. 22 permission, không DELETE.
Các tên trong một ô được khai báo thành từng enum constant riêng.

| Permission | ADMIN | ACCOUNTANT | RESIDENT |
| --- | --- | --- | --- |
| ACCOUNT_VIEW, ACCOUNT_CREATE, ACCOUNT_LOCK, ACCOUNT_UNLOCK, ACCOUNT_ASSIGN_ROLE | ALLOW | DENY | DENY |
| HOUSEHOLD_VIEW | ALLOW | ALLOW | DENY |
| HOUSEHOLD_CREATE, HOUSEHOLD_UPDATE | ALLOW | DENY | DENY |
| RESIDENT_PROFILE_VIEW | ALLOW | ALLOW | DENY |
| RESIDENT_PROFILE_CREATE, RESIDENT_PROFILE_UPDATE | ALLOW | DENY | DENY |
| FEE_CATEGORY_VIEW, FEE_CATEGORY_CREATE, FEE_CATEGORY_UPDATE | ALLOW | ALLOW | DENY |
| FEE_RATE_VIEW, FEE_RATE_CREATE, FEE_RATE_UPDATE | ALLOW | ALLOW | DENY |
| BILLING_PERIOD_CREATE | ALLOW | ALLOW | DENY |
| PAYMENT_RECORD | DENY | ALLOW | DENY |
| DEBT_REPORT_VIEW | ALLOW | ALLOW | DENY |
| PAYMENT_HISTORY_VIEW_ALL | ALLOW | ALLOW | DENY |
| PAYMENT_HISTORY_VIEW_OWN | DENY* | DENY* | ALLOW chỉ khi xác minh đúng căn hộ |

*ADMIN/ACCOUNTANT xem mọi căn hộ bằng VIEW_ALL; không cần cấp thêm VIEW_OWN.
Không có phủ định rằng hai role này không được xem căn hộ cụ thể.
VIEW cho quản lý tài khoản/danh mục/đơn giá là điểm kiểm soát màn hình/dữ liệu
của cùng module, không cấp thêm nghiệp vụ ngoài ma trận. ACCOUNT_ASSIGN_ROLE
chỉ là quyền thực hiện, không tự triển khai thay đổi role hoặc rule Admin cuối cùng.

Implementation dự kiến: một EnumMap<Permission, Set<Role>> bất biến cho quyền
thuần role; VIEW_OWN được xử lý riêng vì cần bằng chứng phạm vi. Không có
ADMIN wildcard/superuser. Permission chưa khai báo/không có ánh xạ luôn DENY.

## 4. AuthorizationService contract đề xuất

Package: `com.bluemoon.services`. Chữ ký CHƯA được duyệt:

```java
public interface AuthorizationService {
    boolean canAccess(Permission permission);
    void requirePermission(Permission permission);
    boolean canAccessApartment(String soCanHo, Permission permission);
    void requireApartmentPermission(String soCanHo, Permission permission);
}
```

- API không nhận role/userId từ UI: principal lấy từ session hiện tại.
- `canAccess` dành cho role-only permissions. Gọi VIEW_OWN mà không có căn hộ
  trả false, kể cả khi session là RESIDENT. Không dùng nó để mở quyền dữ liệu riêng.
- API apartment chỉ chấp nhận PAYMENT_HISTORY_VIEW_OWN, session RESIDENT và
  một soCanHo được resolver xác minh. Dùng role-only permission ở API apartment
  trả false để tránh gán nhầm phạm vi. Hai require có hành vi đối xứng với canAccess.
- Không có session: canAccess* false; require* ném AccessDeniedException.
- Null permission, soCanHo null/rỗng hoặc resolver chưa có bằng chứng: DENY.
- Không trim hoặc đổi mã căn hộ để đoán ra một đối tượng khác. Resolver nhận
  định danh chuẩn từ backend và kiểm tra liên kết tin cậy của chính đối tượng đó.

Implementation dự kiến: `RoleBasedAuthorizationService`.
Production constructor đề xuất `RoleBasedAuthorizationService(ApartmentOwnershipResolver resolver)`;
nguồn principal cố định là SessionContext hiện có. Khi chưa có adapter dữ liệu,
inject resolver luôn false một cách tường minh; không có default ALLOW.
Constructor package-private nhận Supplier<Optional<Account>> và resolver để unit
test các session mà không thêm setter công khai vào SessionContext hoặc cần login/DB.

## 5. Phạm vi RESIDENT và dependency dữ liệu

Port đề xuất, cần Lead Dev và Dev-2/Dev-3 duyệt:

```java
public interface ApartmentOwnershipResolver {
    boolean isLinked(long userId, String soCanHo);
}
```

Đây chỉ là interface đọc sự liên kết hợp lệ, không phải DAO/migration mới.
Production adapter do bên dữ liệu cung cấp; không lấy danh sách sở hữu hay
userId do UI truyền vào làm bằng chứng. Chưa quyết định cardinality/mốc hiệu lực
của liên kết hoặc tự tạo FK. Account hiện không đủ thông tin để trả ALLOW cho OWN.

Resolver không có dữ liệu/trả false/lỗi truy xuất: kết quả quyền luôn false;
require ném AccessDeniedException chung, không trả dữ liệu nhạy cảm. Chi tiết lỗi
hạ tầng chỉ dành cho chẩn đoán nội bộ, không đưa vào UI. Không fallback thành
kiểm tra role-only, không suy ra liên kết từ tên đăng nhập.

Đọc lịch sử căn hộ (pseudocode, chưa sửa service):

```java
if (!authorization.canAccess(PAYMENT_HISTORY_VIEW_ALL)) {
    authorization.requireApartmentPermission(soCanHo, PAYMENT_HISTORY_VIEW_OWN);
}
// DAO chỉ đọc đúng soCanHo đã kiểm tra; không đọc cả tòa rồi lọc trên UI.
```

Endpoint/list toàn tòa luôn requirePermission(VIEW_ALL), không thay bằng OWN.
Các service phải dùng cùng định danh cho kiểm tra quyền và query; việc thay đổi
liên kết/quyền đồng thời cần hợp đồng transaction khi có adapter. Một lần kiểm tra
UI không bảo đảm quyền còn đúng cho lần gọi service tiếp theo.

## 6. AccessDeniedException và kế hoạch tích hợp

Thống nhất một type `com.bluemoon.services.AccessDeniedException extends RuntimeException`,
constructor String, tương thích đúng type Dev-3 đã dùng. Không tạo thêm type trong
package exceptions/security. Cần Dev-3/Lead Dev chốt nguồn commit dùng chung của
class trước Mốc 2 để tránh hai định nghĩa hoặc xung đột add/add. Không tự merge nhánh US-001.

require* ném trước validation nghiệp vụ, truy vấn dữ liệu được bảo vệ hoặc ghi DB.
Message chung: `Bạn không có quyền thực hiện chức năng này.` Không trả dữ liệu
đối tượng, SQL hoặc credential; không bắt rồi tiếp tục thao tác. Controller có thể
bắt để hiển thị cảnh báo; service vẫn phải chặn dù UI đang bật nút.

Vị trí tích hợp đề xuất, chờ owner duyệt:

- US-001 createHousehold → HOUSEHOLD_CREATE; updateHousehold → HOUSEHOLD_UPDATE;
  getHouseholdById/getHouseholdByApartment/listHouseholds → HOUSEHOLD_VIEW.
  Sau khi được duyệt, Dev-3 thay requireRole nội bộ bằng dependency chung và
  thống nhất constructor injection; không tự sửa hàng loạt trong nhiệm vụ này.
- US-005: Dev-2/owner service đặt FEE_CATEGORY_VIEW/CREATE/UPDATE đúng entry point;
  Dev-1 không xây DAO/UI thay họ.
- Ghi nhận thu tiền: require PAYMENT_RECORD trước mọi ghi DB; ADMIN bị từ chối.
- Dev-2 dùng canAccess* để hide/disable BR-21, đồng thời service dùng require* BR-22.
- Không sửa Login UI đang QA, AuthenticationService, AuthService, policy khóa hay schema.

## 7. File dự kiến ở Mốc 2, chưa tạo

- `src/main/java/com/bluemoon/models/Permission.java`.
- `src/main/java/com/bluemoon/services/AuthorizationService.java`.
- `src/main/java/com/bluemoon/services/RoleBasedAuthorizationService.java`.
- `src/main/java/com/bluemoon/services/ApartmentOwnershipResolver.java` (port chờ duyệt).
- `src/main/java/com/bluemoon/services/AccessDeniedException.java`: dùng chung với
  type đã có của Dev-3 sau khi thống nhất, không tạo một exception khác.
- Unit tests cho matrix, scope và guard; cập nhật tài liệu tích hợp/test.
- Không cần sửa pom.xml hoặc Account/SessionContext. Không có DAO/schema mới.

## 8. Bộ test dự kiến (chưa viết/chạy)

1. Parameterized test toàn bộ 22 permission × 3 role với expected matrix tường
   minh độc lập, không lấy expected từ implementation. OWN không có context DENY.
2. Mỗi permission khi không có session: canAccess false, require ném đúng type.
3. AC-01: ACCOUNTANT không có ACCOUNT_VIEW/CREATE/LOCK/UNLOCK/ASSIGN_ROLE.
4. AC-02: ADMIN không có PAYMENT_RECORD; ACCOUNTANT có; RESIDENT không có.
5. ADMIN được CREATE/UPDATE/VIEW hộ; ACCOUNTANT chỉ VIEW; RESIDENT không có.
6. OWN: đúng user + đúng căn hộ ALLOW; căn hộ khác/user khác/mất liên kết DENY.
7. Thiếu resolver/link, resolver lỗi, null/rỗng permission/context: không ALLOW.
8. ALL: ADMIN/ACCOUNTANT được, RESIDENT không được; OWN không mở list toàn tòa.
9. Role-only quyền không gọi resolver; scoped permission không được bypass bằng
   generic canAccess hoặc một permission không phù hợp ở API apartment.
10. Session thay đổi/logout giữa hai lần gọi: quyền đổi ngay, không cache principal.
11. Guard test với fake business operation: nhánh DENY không đọc/ghi DAO; ALLOW
    mới thực hiện thao tác. Đây là fixture hợp đồng, không sửa service Dev khác.
12. Authentication regression giữ nguyên API/session/khóa US-012; chạy Maven test
    và javafx-smoke ở Mốc 3. Unit RBAC chỉ dùng fake principal/resolver, không MySQL.

Không xác nhận hoàn toàn AC giao diện trước khi Dev-2 triển khai UI và QA tích hợp.

## 9. Các điểm cần duyệt trước Mốc 2

1. Duyệt enum/matrix, bốn API và phân biệt OWN/ALL; OWN không có context là DENY.
2. Duyệt String soCanHo theo schema US-001, port resolver và nguồn dữ liệu liên kết.
3. Thống nhất một AccessDeniedException với Dev-3; duyệt snapshot session/test injection.
4. WBS còn ghi assignRole và không hạ quyền Admin cuối cùng. Nhiệm vụ hiện tại
   giới hạn authorization thuần. assignRole, kiểm tra Admin cuối cùng trong
   transaction, cập nhật session khi đổi role và DAO/UI là hợp đồng tích hợp sau,
   không thêm vào AuthService trong đợt này.
5. Backend US-001 hiện nằm ở branch riêng; chỉ đề xuất điểm gắn guard. UI US-012
   đang QA và nhánh US-005 không phải base của nhiệm vụ.

Kết quả Mốc 1: chỉ có tài liệu thiết kế này. Không sửa production code; chưa chạy
test RBAC hoặc mở PR implementation. Commit/push và PR tài liệu chỉ phục vụ
Contract Review, không đồng nghĩa API đã được duyệt. Dừng để Lead Dev duyệt.
