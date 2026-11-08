# US-002 Resident UI — Sprint 1 handoff

08/11/2026. Branch `feature/us002-resident-ui`, base main `1123219ed4244853ba4593c7cab284998dae0043` (đã merge PR #16). PR #16 HEAD `4d5e608...` và PR #17 HEAD `bb62b373...` được giữ nguyên; hotfix #17 chưa có trên base. Không dùng worktree merge, không merge main.

## AC và wiring

- AC-01: chi tiết hộ có bảng nhân khẩu/số lượng; dialog thêm nhận đúng ResidentForm (maHo cố định từ hộ, họ tên, ngày sinh, giới tính, CCCD, quan hệ, ngày chuyển đến). Thành công đóng dialog và reload qua service.
- AC-02: service hiện hành kiểm người >=14 tuổi cần CCCD, field error hiển thị cạnh input, giữ nguyên form khi lỗi.
- AC-03/BR-04: dùng CONFIRMATION_REQUIRED/currentHead của service; người dùng xác nhận mới gửi lại immutable payload với id chủ hộ hiện tại. CONFIRMATION_STALE hiển thị xác nhận mới và dùng id từ kết quả mới, không reuse id cũ. Cancel không gửi lại.
- CCCD duplicate, Q17 vacant apartment, first-member-head và toàn bộ validation do ResidentServiceImpl/ResidentDAO đã merge quyết định, Controller không chứa JDBC hoặc tự viết lại backend.
- ADMIN có RESIDENT_PROFILE_CREATE; ACCOUNTANT có VIEW nhưng không thêm; RESIDENT/no session bị backend từ chối, không hiển thị dữ liệu ngoài quyền. Shell/Household vẫn giữ guard hiện hành.

Chỉ bổ sung ResidentService production vào **factory Household hiện có** trong ProductionModules; không đăng ký UI Dev-1/Dev-3, không sửa US-006/007 hoặc module của họ. HouseholdNavigator nhận thêm dependency ResidentService; HouseholdDetailController host ResidentPanelController. Hai trạng thái task Household/Resident được OR thành busy của module, nên không mất busy khi một task con hoàn thành.

## Lifecycle

JDBC/service chạy JavaFX Task trên daemon worker. Submit lặp bị chặn bởi busy và disabled controls. Dialog modal được tính là busy với Shell, không logout/chuyển module ở giữa nhập/xác nhận/write. Trong task save, Cancel/close-request của dialog bị chặn. Close module cưỡng bức cancel Task, clear bảng/dialog và bỏ late callbacks; không tuyên bố cancel UI rollback một write JDBC đã submit. Scene/Stage do AuthenticationNavigator giữ nguyên. Không thêm navigator Scene.

## Verification / QA

JDK17.0.17/Maven3.9.9/Enforcer enabled: normal **461 PASS**, JavaFX smoke **462 PASS**; MySQL liên quan **34 PASS** (ResidentPanelUiMySqlIT 6, ResidentMySqlIT 21, HouseholdQ17MySqlIT 7). Tất cả 0 failure/error/skip; `git diff --check` PASS. MySQL Community 8.4.0 loopback:33316, schema disposable bluemoon_fixture_hotfix_test, user giới hạn schema. Lệnh IT: `mvn -Pmysql-it -Dtest=ResidentPanelUiMySqlIT,ResidentMySqlIT,HouseholdQ17MySqlIT clean verify` với opt-in hiện hành. Đây là UI/controller → Service → DAO → MySQL thật, không phải fake E2E toàn Login.

ResidentPanelUiTest dùng service thật với repository in-memory **chỉ trong test**. ResidentPanelUiMySqlIT kế thừa cùng sáu case nhưng dùng service/DAO/MySQL thật: create+refresh/double submit, adult missing CCCD, duplicate CCCD, vacant/Q17, xác nhận đổi chủ hộ, ACCOUNTANT/cleanup. Fixture chỉ sở hữu maHo UI_US002_2801 / P2801 và dữ liệu con, cleanup child trước parent trong schema `_test`; không DROP bảng.

QA E2E: trên DB test đã áp US-001/002, login ADMIN → Household → chi tiết hộ DANG_O → Thêm nhân khẩu. Kiểm sáu trường và mã hộ cố định; người đầu tiên phải CHU_HO. Thử CCCD thiếu/trùng; thêm CHU_HO thứ hai rồi Cancel/Confirm; giữ form khi lỗi và refresh sau thành công. Hộ CAN_HO_TRONG phải nhận thông báo backend từ chối. ACCOUNTANT xem nhưng nút thêm disabled; RESIDENT không vào module. Khi dialog/save mở, thử Home/Logout/close; sau Cancel/xong task phải điều hướng bình thường. Không dùng dữ liệu production để QA.

Không tuyên bố full MySQL main PASS khi hotfix #17 chưa merge (FeeType fixture cũ còn lỗi FK). MySQL verification trong PR này là suite liên quan được chỉ rõ, không che lỗi full suite. Commit/PR được báo riêng. READY để review sau verification; không merge trước PO.
