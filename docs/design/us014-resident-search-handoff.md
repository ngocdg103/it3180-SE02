# US-014 — Resident search handoff

08/11/2026. Branch `feature/us014-resident-search`, base `24bfc0d5369d39b6429481b68fe90d6a3243740a` của PR #20 (US-002 UI). Main tại lúc tạo branch là `4c4833c4284ca666ba94237a7bf319bd26b5cc57`, đã có Shell #16 và hotfix #17 nhưng chưa có #20. Đây là PR phụ thuộc #20 để kết quả search mở chi tiết hộ có bảng nhân khẩu; không merge trước #20. Không sửa/ghi đè branches #16/#17/#20/#22 hoặc tự merge main.

## AC / phạm vi

- AC-01: tiêu chí số căn hộ chuẩn hóa `1204` → `P1204`, nhận prefix có/không P, không phân biệt chữ p hoa/thường; trả căn hộ, tầng, diện tích, chủ hộ và cư dân. Căn hộ chưa có nhân khẩu vẫn xuất hiện khi tìm phòng/tầng (LEFT JOIN).
- AC-02: tên substring không phân biệt hoa/thường, dấu tiếng Việt; normalize NFD keyword, map đ→d, SQL collation utf8mb4_0900_ai_ci + replace Đ/đ trong tên lưu. MySQL IT kiểm `ngoc`, `NgỌc`, `Đỗ Gia Ngọc`, `do gia ngoc`. Không sửa dữ liệu tên gốc.
- CCCD khớp chính xác chuỗi 9/12 số theo backend hiện hành, không substring. Có tiêu chí Tầng 1..30. `%`, `_`, `!` được escape khi LIKE; literal SQL injection không biến thành SQL.
- AC-03: bảng trống, thông báo đúng `Không tìm thấy hồ sơ cư dân phù hợp với từ khóa!`. Keyword rỗng/sai tiêu chí trả validation, không chạy broad query ngoài ý muốn.
- Click result → detail hộ hiện có, có panel US-002 từ #20. **Chưa có số điện thoại hoặc trạng thái nợ phí** vì schema/service hiện không cung cấp và nằm ngoài scope US-014 đợt này; không tạo dữ liệu giả hoặc sửa US-009.
- BR-23: ADMIN/ACCOUNTANT dùng RESIDENT_PROFILE_VIEW. Service requirePermission trước validation/data, UI menu/handler kiểm quyền. **PO đã trả lời: chưa có mapping user→căn hộ, giữ RESIDENT bị chặn và ghi dependency**. Không suy ra căn hộ từ username, không tái dùng permission payment-own để mở hồ sơ riêng. Nhánh RESIDENT-own của BR-23 chưa đạt, chờ trusted mapping/permission contract được duyệt.

## Layer / lifecycle

`ResidentSearchService.search(Field, keyword)` → `ResidentSearchRepository.search(criteria)` do **ResidentDAO hiện có** implement. Chỉ thêm đường đọc PreparedStatement; các method insert/transaction/Q17/uniqueness của ResidentDAO giữ nguyên. Không sửa ResidentService US-002 API, schema/migration hoặc US-006/007/008/009/011.

DTO search riêng ResidentSearchCriteria/ResidentSearchHit giữ nguyên type Resident cho dữ liệu người. SQL join apartment/household/resident/head trong một query, không N+1; JOIN head dùng generated unique head_ma_ho hiện có. Index/schema không đổi vì benchmark quy mô SRS đã dưới ngưỡng; tên substring có thể scan vài nghìn nhân khẩu như test, không hứa hiệu năng cho quy mô vượt SRS.

Household list có nút Tra cứu cư dân khi dependency/permission khả dụng. ResidentSearchController được host trong cùng HouseholdNavigator content, không tạo Stage/Scene mới. Chỉ bổ sung dependency search cho factory Household đang có trong ProductionModules, không đăng ký UI Dev-1/Dev-3. Task background, Enter/button đều guard busy, Shell nhận trạng thái busy; controls disabled khi pending; close cancel Task, disable old handlers/clear bảng/drop callback muộn.

## Verification và BR-24

Final tự chạy: **468 normal PASS**, **469 JavaFX smoke PASS**, **39 MySQL liên quan PASS** (search 5, Resident UI 6, Resident backend 21, Q17 7), tất cả 0 failure/error/skip. JDK17.0.17/Maven3.9.9, Enforcer enabled; diff check PASS. Đã xem ảnh render kết quả search thật.

Real MySQL Community 8.4.0 tại loopback:33316, schema disposable bluemoon_fixture_hotfix_test, user chỉ có quyền schema. Performance fixture sở hữu 600 căn P9000..P9599, stored tầng 1..30 (20 căn/tầng), 2396 người (599 căn ×4, 1 căn rỗng). Mã P9xxx dành riêng fixture để không đụng mã suite khác; không thêm rule floor/code consistency vào production. Seed một transaction; cleanup chỉ parent/child thuộc SEARCH_IT_* và các căn được đọc từ chính các hộ fixture trước cleanup. Không DROP bảng/disable FK hoặc xóa dữ liệu fixture khác. Các bảng hiện hành có thể còn data suite khác: assertions name/floor xét đúng rows fixture trong kết quả hợp lệ, không giả định toàn schema trống.

BR-24 test warm-up một lần mỗi tiêu chí rồi đo 5 lần ×4 tiêu chí =20 lookup Service→DAO→new JDBC connection→map result, assert **mọi mẫu <1000ms**. UI test riêng đo Enter→TableView items có kết quả, cũng assert <1000ms, rồi kiểm mở detail/empty result. Benchmark local/loopback không phải cam kết cho mọi mạng/thiết bị hay full Login/paint E2E. Screenshot local `target/us014-previews/search.png`, không commit PII/config.

Số liệu final: **p50 9.783 ms, max 27.063 ms** cho 20 service lookup; **UI Enter→results 73.375 ms**. BR-24 PASS trên môi trường/fixture nêu trên. Log local ignored `.tools/us014-normal.log`, `us014-smoke.log`, `us014-mysql.log`. Không lấy số liệu in-memory thay benchmark MySQL.

Suite liên quan: `mvn -Pmysql-it -Dtest=ResidentSearchMySqlIT,ResidentPanelUiMySqlIT,ResidentMySqlIT,HouseholdQ17MySqlIT clean verify` với opt-in hiện hành. Branch phụ thuộc #20 chưa chứa hotfix #17, nên không tuyên bố full MySQL main pass; owner/PO cập nhật main đã có hotfix khi tích hợp các PR. Không thay đổi test order hoặc skip ca lỗi để tuyên bố full suite.

## QA / dependency

1. Merge #20 trước theo điều phối PO; cập nhật base main/hotfix #17 khi tích hợp, không force-push branches cũ. PR #22 US-005 độc lập.
2. QA `_test` data hợp lệ: login ADMIN/ACCOUNTANT → Household → Tra cứu cư dân. Chọn Số căn hộ và nhập `1204`/`P12`; tên `ngoc`/`Ngọc`; CCCD chính xác; tầng12; Enter và button phải tương đương. Từ khóa P9999 báo đúng AC-03, không crash; thử ký tự `%`/`_` như literal ở tiêu chí tên.
3. Chọn result → Xem chi tiết hộ → xem bảng nhân khẩu US-002. Quay lại list/search rồi logout; maximize/restore giữ Scene hiện hành. Trong search pending, không submit lặp/đổi module; DB unavailable báo chung, không lộ SQL.
4. RESIDENT/no session phải bị deny, không có dữ liệu toàn tòa. Không thử suy đoán household ownership; mapping cá nhân là dependency đã PO xác nhận.
5. Chạy normal/smoke và MySQL liên quan, ghi số liệu BR-24 từ log. Không gọi in-memory/UI unit test là MySQL E2E.

READY cho staff-search review sau final checks; **BLOCKER riêng cho nhánh RESIDENT-own BR-23:** trusted mapping/authorization chưa có, đã PO đồng ý defer. Không tự merge. HEAD/PR báo riêng.
