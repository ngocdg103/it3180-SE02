# US-009 — Debt report handoff (Dev-1, emergency 08/11/2026)

## Branch / dependency / scope

- Branch riêng `feature/us009-debt-report`, worktree `.tools/us009-debt-report`, bắt đầu sau khi US-008 đã push và mở PR #21 độc lập.
- Stacked base `fef5703558a73fca829cb6ff25cce1604dac7178` (feature/us008-payment); upstream main `1123219ed4244853ba4593c7cab284998dae0043` sau PR #16. Pending Billing PR #14 exact `7b4ac366242796575ddacdfc27ae7b6e2d9e36c5` là dependency đã có qua branch Payment. Không cherry-pick/copy schema.
- PR Debt target branch Payment để reviewer chỉ thấy phần US-009. Không merge vào Payment/main; sau khi các dependency được merge và PO điều phối thì retarget main + sync/regression. PR #21 không phải chờ US-009.
- Không sửa ProductionModules, Billing/Receipt DAO, Auth/RBAC hoặc schema; US-009 chỉ đọc, không tạo bảng/cache tổng công nợ. Root branch PR #15 và hai tài liệu untracked giữ nguyên.

## API / dữ liệu / RBAC

```java
DebtResult<DebtReport> getDebtByPeriod(DebtQuery query);
DebtResult<HouseholdDebt> getHouseholdBalance(String maHo);
```

`DebtQuery(kyThu, Set<ChargeStatus>, tangTu, tangDen)`: kỳ null/blank lấy kỳ mới nhất, kỳ khác strict YYYY-MM; null/empty statuses = tất cả, tái sử dụng ChargeStatus của Billing; khoảng tầng inclusive >=1, from<=to. Không tạo enum trạng thái tài chính khác. AC "Chưa đóng hoặc Đóng thiếu" dùng tập hai trạng thái.

`DebtRow`: mã hóa đơn/hộ/căn hộ, tầng, tên chủ hộ, diện tích, số người, tổng phải thu/đã thu/còn thiếu, trạng thái và lần nộp cuối. Diện tích/nhân khẩu lấy snapshot invoices, tên chủ hộ hiện tại theo Billing Q18; không giả tên khi thiếu. Lần nộp cuối = MAX(phieu_thu.ngay_nop), không phải thời điểm hệ thống tạo phiếu.

`DebtReport` có rows bất biến, KPI toàn tòa trong kỳ, và filteredDebt riêng cho footer các dòng đang hiển thị. Không đổi KPI theo filter trạng thái/tầng (BR-15 toàn tòa); footer tuân AC-01. Tỷ lệ thu = đã thu/phải thu *100, BigDecimal HALF_UP 2 chữ số; có thể >100 khi nộp thừa. Hộ hoàn thành gồm DA_DONG_DU và DONG_THUA. Tổng nợ = SUM(MAX(con_thieu,0)), không lấy credit hộ khác bù âm. `HouseholdDebt` trả số dư riêng, không tự consume credit.

Paid/state/con_thieu đọc từ invoices đã được transaction Receipt cập nhật nguyên tử; phiếu thu dùng cho lần nộp cuối. Không sum join phieu_thu gây nhân bản invoice/tổng phải thu. `DebtDAO` mở một REPEATABLE_READ READ_ONLY transaction để chọn kỳ và load rows, rollback read transaction rồi đóng resources; KPI/filter tính từ chính rows của snapshot đó. Không đọc uncommitted payment, không lưu tổng dư thừa.

`DebtServiceImpl(DebtRepository, AuthorizationService)` yêu cầu DEBT_REPORT_VIEW trước validation/I/O: ADMIN/ACCOUNTANT allowed; RESIDENT/no session ném AccessDeniedException. Result: SUCCESS, VALIDATION_ERROR, NO_DATA, BUSY, SYSTEM_ERROR; không có kỳ -> NO_DATA, filter không có dòng vẫn SUCCESS với KPI toàn kỳ + thông báo rỗng. 1205/1213 -> BUSY, lỗi SQL khác -> thông báo chung, không leak SQL hoặc biến lỗi DB thành báo cáo rỗng.

## UI / factory / QA

`DebtModule.open(DatabaseConnection)` implements DashboardModule, createService dùng DebtDAO thật; PO đăng ký dưới DEBT_REPORT_VIEW, không sửa registry chung trong PR này. Host owns Stage/Scene, đóng module theo lifecycle. View có period/status/floor filters, KPI, bảng chi tiết, footer nợ sau lọc, loading/empty/error. Mọi Service call chạy worker, latest request wins; close/logout bỏ callback cũ, busyProperty phản ánh pending tasks. Không JDBC trong controller.

QA sau khi apply migrations chính thức đến US-008 và PO host hai module:

1. Lập kỳ hiện tại cho P1204/P2004, mỗi hóa đơn1.384.000; để kỳ trống -> mặc định kỳ mới nhất, KPI2.768.000 chưa thu, tên/diện tích/nhân khẩu đúng.
2. ACCOUNTANT ghi1.000.000 cho P1204 qua ReceiptModule; tải lại báo cáo -> đã thu1.000.000, còn nợ1.768.000, P1204 DONG_THIEU/còn384.000, ngày nộp đúng.
3. Ghi tiếp384.000 -> P1204 DA_DONG_DU, KPI hộ hoàn thành1/2, tỷ lệ50%; filter "Chưa đóng hoặc Đóng thiếu" chỉ còn P2004, footer1.384.000.
4. Filter tầng10..15 -> P1204, tầng20 -> P2004; KPI toàn kỳ vẫn giữ. Kỳ không có -> NO_DATA; filter không có dòng -> bảng rỗng/footer0 và thông báo.
5. Nộp thừa cho P1204 -> DONG_THUA/credit riêng, nợ P2004 không bị trừ. Tự consume credit kỳ sau vẫn dependency của US-008/Billing, không nằm trong report.
6. ADMIN/ACCOUNTANT xem được; RESIDENT/no session bị chặn. Thử ngày/kỳ/tầng lỗi, SQL disconnect, đổi filter nhanh/đóng màn hình khi đang tải.

## Verification / gate

Temurin17.0.20.1/Maven3.9.9, Enforcer bật: `mvn clean verify` **581 PASS**, `mvn -Pjavafx-smoke clean verify` **582 PASS**. `mvn -Pmysql-it '-Dtest=DebtMySqlIT,ReceiptMySqlIT' verify` **16/16 PASS** (5 Debt + 11 Payment), đều 0 failures/errors/skips. MySQL8.4.13/schema mới `bluemoon_debt_00277a2c_test`, user chỉ có quyền đúng schema, kiểm cross-schema denial PASS; config/credentials nằm ngoài source trong `.tools/` ignored. Staged diff check PASS. Không có thay đổi production sau các checks này.

Tests mới: 21 Service, 7 controller; MySQL Debt kiểm payment partial/full, KPI/filter/footer, household credit, không đọc write chưa commit rồi đọc thấy receipt/balance cùng lúc sau commit, snapshot area/people + owner hiện tại, RBAC. Target MySQL runs trên schema *_test mới/user chỉ quyền schema. Không production fake hoặc credentials trong Git; không tuyên bố full mysql-it profile xanh khi fixture chung đang chờ hotfix.

AC-01 backend/filter/footer và controller PASS theo tests trên; UI E2E sau khi PO nối module còn PENDING. Draft chờ dependency PR #21/#14, peer review và QA; không merge hoặc mở rộng US-016/Sprint2. Các open questions mã phiếu/precision/carry-forward trong US-008 vẫn phải được PO/Lead chốt, report không tự thay chính sách đó.
