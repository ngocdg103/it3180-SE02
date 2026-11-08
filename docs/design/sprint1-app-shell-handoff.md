# Sprint 1 — App Shell / Dashboard navigation handoff

Ngày: 08/11/2026. Owner Dev-2. Branch `feature/sprint1-app-shell`, base `ac43254bf76da329c6f4eab235325bc2260a49d9` (origin/main sau fetch). Development checkout ban đầu sạch trên `feature/us006-fee-rate-backend`; đã tạo branch mới từ main, không dùng worktree merge cũ. Không còn thực hiện vai trò Temporary Merge Coordinator. Chỉ mở PR, không merge.

## Navigation và composition

`AuthenticationNavigator` tiếp tục là nơi duy nhất quản lý Stage/Scene. Login thành công phải có `auth.currentUser()` khớp Account mới được vào Dashboard. Root được thay trên cùng Scene, không gọi sizeToScene, setWidth/setHeight hoặc thay maximize/fullscreen trong navigation. Dashboard có header tên/role, menu trái, content scrollable (giúp dùng module ở cửa sổ nhỏ), trạng thái busy/error và logout.

`DashboardController` host một `DashboardModule` tại một thời điểm. `ModuleRegistration(id,title,permission,Supplier<DashboardModule>)` là extension point; `ProductionModules` đăng ký composition thật. `DashboardModule` có `view()`, `busyProperty()`, `close()` trên FX thread. Không tạo Stage/Scene cho module. Không có Navigator thứ hai cạnh tranh scene: `HouseholdNavigator` chỉ điều hướng bên trong content của module Household và không sở hữu Stage/Scene.

Khi mở lại module đang hiển thị: không tạo instance thứ hai. Trong lúc mở hoặc module đang load/write, chặn menu, home, logout, close-request của cửa sổ; handler cũng kiểm busy, không chỉ disabled UI. Khi xong, người dùng có thể quay Dashboard hoặc đổi module. Khi rời module: tháo busy listener của host trước close, clear host, dispose controller/dialog/executor. Không cache view cũ hoặc dữ liệu theo session.

Các thao tác JDBC ở worker thread. FeeCategoryController theo dõi task/pending count để Shell biết đang bận; close hủy read task, đóng executor và bỏ callback muộn. Giữ semantics write đã submit có thể hoàn thành, không tuyên bố cancel UI là rollback DB. Household controller đóng task khi deactivate và xóa dữ liệu view/dialog. Đường navigation/logout/window-close bình thường chỉ rời khi idle. Nếu OS/host cưỡng bức hide hoặc terminate lúc write, JDBC có thể vẫn hoàn tất trước timeout của connection: đây là giới hạn của desktop process, không dùng retry mù hoặc mở session mới để suy đoán kết quả write.

`BlueMoonApplication` load DatabaseConfig và khởi tạo AuthService (BCrypt) trên worker, sau đó tạo danh sách registration mà không mở JDBC. Config lỗi → Login unavailable có thông báo chung; endpoint DB lỗi → service trả lỗi chung trong module, có thể retry/back/logout sau khi task kết thúc. Không đưa SQL/credential vào UI. Setup callback không hiển thị view sau khi Stage đã đóng.

## Module đã kết nối

### US-005

`ProductionModules → FeeCategoryModule.open(DatabaseConnection) → FeeTypeServiceImpl → FeeTypeDAO`. Tái sử dụng composition đã merge, không fake/in-memory production. Có list/filter/retry và create; create thành công refresh list, lỗi giữ input. **Chưa có UI update/deactivate**, dù Service có API; không tuyên bố hoàn thành toàn bộ CRUD. Hành vi disposal cũ vẫn được regression kiểm tra, write đang chạy không bị giả định rollback.

### US-001

Đã kiểm tra UI PR #3 qua branch `origin/feature/us001-household-ui` tại `63b7846ed0323dda6cab8daf170819868f90659d`. Nhập có kiểm soát **chỉ** bốn controller, HouseholdNavigator, ba FXML, household.css và test UI; không cherry-pick, không lấy pom, backend/model/migration hoặc ghi đè source hiện có. Chữ ký HouseholdService/DTO tương thích main. Test cũ dùng constructor Supplier<Role> đã được cập nhật sang AuthorizationService hiện hành; toolkit test dùng lại JavaFX đã chạy, không Platform.exit giữa suite.

Production graph: `HouseholdNavigator → HouseholdServiceImpl(new HouseholdDAO(connections), ApartmentChargeLookup.NONE)`; dùng backend Q17/RBAC đã merge. Có list/filter/detail/create/edit/confirmation thông qua service thật. ACCOUNTANT xem nhưng create/edit disabled; ADMIN thao tác theo permission. Không tạo backend mới. Q17 chạy trong DAO hiện hành, không tái tạo guard tại UI.

**Giới hạn BR-02:** vẫn `ApartmentChargeLookup.NONE` theo composition hiện hành. Chưa nối dữ liệu billing thật; UI có xử lý CONFIRMATION_REQUIRED nhưng không tuyên bố production phát hiện charge history. Wiring lookup billing là dependency US-007 tiếp theo, không tạo fake thành công trong production. PO cần phối hợp với PR #3 cũ: App Shell đã bao gồm subset UI và các điều chỉnh lifecycle/RBAC này, không merge mù lại branch UI cũ.

### Chưa kết nối

- US-006 Fee Rate: chờ Dev-1 bàn giao Service/UI/module lifecycle. Không lấy code chưa merge từ branch service-ui, không viết lại màn hình. Khi sẵn sàng, đăng ký factory với FEE_RATE_VIEW.
- US-007 Billing: chờ Billing UI/composition. Contract đã có nhưng chưa có module UI để đăng ký. Permission BILLING_PERIOD_CREATE/DEBT_REPORT_VIEW phải được owner phân rõ theo màn hình.
- Dashboard chỉ hiển thị hai thông báo “chưa khả dụng”; không có nút chức năng giả. Không mở rộng US-008/009/011/014.

## RBAC

Menu visibility dùng AuthorizationService, không hardcode role. Open handler kiểm lại session Account và `requirePermission` trước factory, kể cả gọi controller trực tiếp. US-005 dùng FEE_CATEGORY_VIEW; Household dùng HOUSEHOLD_VIEW. Create/edit Household dùng HOUSEHOLD_CREATE/UPDATE để điều khiển controls; service vẫn kiểm quyền trước validation/DAO. FeeTypeServiceImpl tiếp tục kiểm quyền mỗi action nghiệp vụ. RESIDENT/no session không mở module quản trị. Logout gọi auth.logout để xóa SessionContext, đóng module và quay Login trống.

## Verification

JDK 17.0.17, Maven 3.9.9, Enforcer enabled/pass:

- `mvn clean verify`: BUILD SUCCESS, **455 tests**, 0 failure/error/skip.
- `mvn -Pjavafx-smoke clean verify`: BUILD SUCCESS, **456 tests**, 0 failure/error/skip.
- `git diff --check`: PASS. Đã xem ảnh render Dashboard và Fee Category (artifact test), kiểm menu/content và form.

Hai lỗi trong quá trình tích hợp đã được sửa: fixture Household cũ dùng Supplier<Role> và disposal Fee Category vô tình clear dữ liệu view cũ. Các kết quả ở trên là lần final sau sửa; không bỏ qua test. Login cũng có test forced-hide trong lúc xác thực: không xuất hiện Dashboard muộn và session muộn được xóa.

AppShellTest kiểm login/module/home/logout, RBAC visibility/direct denial/revocation, session hết hạn, busy/duplicate open/write-exit, cleanup listener, window close, lỗi factory sanitized, production JDBC graph khi DB unavailable, maximize/restore và Scene identity. Test create qua Shell dùng service thật + repository test có latch; không phải MySQL acceptance. HouseholdUiTest nhập từ UI branch kiểm 15 ca list/create/detail/edit/confirmation/validation/lifecycle/layout. Suite hiện có bảo vệ US-001/005/012/018/Q17; không skip tests, không đổi pom.

MySQL IT/E2E DB thành công **NOT RUN**: chưa có config/db.properties hoặc môi trường schema `_test` an toàn. Test production navigation với endpoint unavailable chứng minh lỗi không crash, không chứng minh list/create thành công trên MySQL thật. QA cần chạy luồng dưới đây với tài khoản/config do môi trường QA quản lý.

## QA instructions

1. Checkout branch, dùng JDK 17/Maven 3.9.9; apply schema/users/auth và các migration hiện hành trong schema disposable `_test` (US-001/002 cần cho Household/Q17, US-005 cho Fee Category). Không sửa config production; config/db.properties ignored, không commit secret.
2. `mvn clean verify`, `mvn -Pjavafx-smoke clean verify`, `git diff --check`; khởi chạy `mvn javafx:run` với config QA. Không bypass Enforcer.
3. Login ADMIN: thấy tên/role và hai module. Mở danh mục khoản thu, list/filter; tạo mã khoản thu riêng fixture (VD SHELL_QA), kiểm refresh và dữ liệu DB; thử duplicate/validation. Home → Household → list/detail/create/edit; kiểm backend Q17 từ chối chuyển trống khi còn nhân khẩu. Không xóa dữ liệu thật để cleanup.
4. ACCOUNTANT: mở được hai module, tạo fee category theo matrix; Household chỉ xem, create/edit disabled và Service vẫn deny nếu gọi trực tiếp. RESIDENT: không có hai menu quản trị. Chưa login: không vào Dashboard. Logout: session rỗng, Login trắng.
5. Khi load/save: thử mở lại module, Home, Logout và đóng cửa sổ; đợi task xong, không có write lặp, không chuyển trang giữa write. Sau khi idle đổi module nhiều lần, kiểm không còn callback/listener của view cũ.
6. Cửa sổ custom size → maximize → mở module/home/logout/login → restore; kích thước/Scene giữ nguyên; thử cửa sổ nhỏ và scroll nội dung. Snapshot test ở `target/app-shell-previews/` và `target/ui-previews/` là artifact local, không commit.
7. Tắt DB QA hoặc cấu hình endpoint unavailable: Login/module báo lỗi chung, không hiển thị SQL; sau lỗi module vẫn Home/Logout được. Không coi DB unavailable test là MySQL IT pass.

## BLOCKER / NON-BLOCKER

- **Không có BLOCKER implementation sau final regression.** App Shell + US-005 + Household READY để peer review/QA; không tự merge.
- NON-BLOCKER phạm vi Shell: US-005 update/deactivate UI chưa có; US-006/007 module chưa bàn giao; BR-02 billing lookup chưa nối; thành công E2E MySQL cần QA verify.
- Không sửa Service/DAO/models/migration hiện hành. Không merge main trong nhiệm vụ này. Full HEAD/PR được báo riêng để tránh tự tham chiếu commit chứa handoff.
