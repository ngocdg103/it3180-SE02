# US-005 — Update / Deactivate UI completion

08/11/2026. Branch `feature/us005-fee-category-completion`, base `4c4833c4284ca666ba94237a7bf319bd26b5cc57` (main sau fetch, chứa App Shell và hotfix #17). Độc lập PR US-002 #20, không mang Resident UI vào PR này. Không sửa FeeTypeService/DAO/migration/business rules.

## Hành vi và RBAC

- Chọn hàng → Sửa khoản thu: populate form hiện hành, mã không editable và Service vẫn kiểm mã bất biến; sửa tên/loại/hình thức/số tiền mặc định/trạng thái theo đúng validation Service hiện có. Không tự thêm business rule mới.
- Save update dùng updateFeeType(code, immutable form); nếu đổi trạng thái, bắt buộc confirmation. Cancel không gửi write và giữ input. Success reset form và reload list; lỗi field/generic giữ input. Result Service `OK` được UI hiển thị thành thông báo cập nhật rõ ràng, không sửa Service API.
- Ngừng áp dụng: selected active row → confirmation → deactivateFeeType. Row đã NGUNG_AP_DUNG không bấm lại; không delete/upsert/xóa lịch sử. Confirmation trước mọi thay đổi trạng thái, kể cả edit form.
- Hai action kiểm FEE_CATEGORY_UPDATE bằng AuthorizationService; ADMIN/ACCOUNTANT theo matrix, RESIDENT/no session disabled/deny. Service vẫn kiểm quyền trước nghiệp vụ/JDBC; không chỉ ẩn/disable UI.
- Dùng executor/Task/lifecycle FeeCategoryModule đã có. Action busy chặn submit/chọn action lặp; modal confirmation đóng góp busy cho App Shell. Dispose đóng dialog và bỏ callback muộn. Không đổi AuthenticationNavigator, Stage/Scene, ProductionModules hoặc US-006/007.

## Verification

Final tự chạy: **459 normal PASS**, **460 JavaFX smoke PASS**, **517 full MySQL PASS**, tất cả 0 failure/error/skip; diff check PASS. JDK17.0.17/Maven3.9.9, Enforcer bật. MySQL Community 8.4.0 riêng tại loopback:33316, schema bluemoon_fixture_hotfix_test. Test mới FeeCategoryCompletionUiTest có bốn ca update immutable code, deactivate Cancel/Confirm, edit status confirmation và permission bị thu hồi. MySQL subclass **5/5 PASS** kế thừa bốn ca dùng Service/DAO thật và thêm ca rate history được giữ nguyên sau deactivate; fixture chỉ sở hữu UI_FEE_1 và rates của mã này, cleanup child trước parent, guard catalog `_test`, không DROP bảng/disable FK. Đây là UI/controller-Service-DAO-MySQL verification, không tuyên bố toàn Login E2E đã chạy.

QA: login ADMIN/ACCOUNTANT → Dashboard → Danh mục khoản thu. Chọn row fixture → Sửa → đổi tên, giữ mã → Lưu, kiểm list/DB; status-change phải confirm, Cancel giữ DB nguyên. Ngừng áp dụng cũng phải confirm; lọc ngừng áp dụng tìm được row. Với fee có rate history, kiểm toàn bộ rate/id/date/amount không đổi. RESIDENT không được mở; nếu session/quyền mất sau render, backend deny. Trong write/confirmation không mở module/logout/submit lặp. Test validation/DB unavailable giữ form, không lộ SQL. Không dùng data production để QA.

READY cho peer review/QA sau verification, không tự merge. PR/HEAD báo riêng. UI giờ bao phủ create/read/update/deactivate; không tuyên bố có hard-delete CRUD (BR-07 cấm xóa cứng). Không merge các PR khác.
