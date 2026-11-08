# US-007 Backend — Mốc 2b handoff

- Branch: `feature/us007-billing-backend`, base `origin/main` `ba9080c5413af86088e68e15aa0f51fb932e9c72` (PR #10 và #11 đã merge).
- Contract: `docs/design/us-007-backend-contract-review.md` (Revision 03). Public interface, DTO, `FeeRateLookup`, RBAC, US-001/002/005/006/018 không đổi.

## Đã triển khai

1. `database/migrations/us007_billing.sql`: `billing_periods`, `invoices`, `invoice_lines`. Không tạo `fee_rates`, không seed.
2. `BillingCalculator` (thuần, không I/O): AC-01, BR-10, BR-11 (phòng thủ), ZERO_TOTAL, MISSING_RATE, HALF_UP từng dòng, không hard-code giá.
3. `BillingRepository` / `BillingDAO` / `BillingTx` / `BillingInputs` / `BillingPeriodExistsException`.
4. `BillingServiceImpl(repository, [authorization,] clock [, currentUser])`: preview, generate, listPeriods, getPeriod, getInvoice.

## Quy tắc đã áp dụng

- Chỉ tháng hiện tại, tính từ `clock.instant()` quy đổi sang `Asia/Ho_Chi_Minh` (không phụ thuộc zone của Clock được inject). Áp dụng cho cả preview và generate.
- RBAC: preview/generate = `BILLING_PERIOD_CREATE`; list/get = `DEBT_REPORT_VIEW`; kiểm tra trước validate và trước repository.
- `generatePeriod` là một transaction REPEATABLE READ: (1) INSERT `billing_periods` lấy khóa duy nhất, (2) `loadInputs` đọc fee_types + fee_rates + households + apartments + residents trên **cùng connection**, (3) tính, (4) batch insert hóa đơn + dòng, (5) cập nhật tổng. Mọi lỗi/abort → rollback toàn kỳ.
- `fee_rates` được đọc bằng SQL trong `BillingTx.loadInputs` (đơn giá có `ngay_ap_dung` lớn nhất ≤ ngày đầu kỳ). Không gọi `FeeRateLookup`/`FeeRateDAO`.
- Mã lỗi MySQL: 1062 → `DUPLICATE`; 1213 → đọc lại kỳ, có → `DUPLICATE`, chưa có → thử lại 1 lần rồi `BUSY`; 1205 → `BUSY`; còn lại → `SYSTEM_ERROR` (thông báo chung).
- `MISSING_RATE`, `NOTHING_TO_BILL` rollback toàn kỳ. Hộ `ZERO_TOTAL`/thiếu diện tích bị bỏ qua và được đếm trong `so_ho_bo_qua`.
- Kỳ đã có: preview trả snapshot đã lưu (`alreadyGenerated = true`), không tính lại từ dữ liệu hiện tại.
- Không có update/delete cho `billing_periods`/`invoice_lines` trong code.

## Kiểm thử

Xem báo cáo trong PR. MySQL IT dùng schema `*_test` riêng và chỉ dùng migration chính thức `database/migrations/us006_fee_rates.sql` (US-006 đã merge, không còn bản mirror trong test). Có thêm test tạo rate qua `FeeRateDAO` thật và test đối chiếu `loadInputs` với `FeeRateDAO.findEffectiveRate`.

## Giới hạn / rủi ro đã biết

- DB không chặn `UPDATE` trực tiếp `invoice_lines`/`billing_periods` (R2). Có test ghi nhận: đọc lại sẽ fail-closed (`IllegalArgumentException` từ invariant của model) nhưng DB không từ chối.
- 1213 (deadlock) chưa tái hiện được trên MySQL thật; chỉ được kiểm bằng unit test với repository giả.
- `DatabaseConnection` đặt `socketTimeout = 10s`: một request chờ khóa lâu hơn 10s sẽ lỗi kết nối (→ `SYSTEM_ERROR`) trước khi MySQL báo 1205.
- Chưa nối `ApartmentChargeLookup` vào `HouseholdServiceImpl` (cần Lead duyệt riêng); `BillingRepository.hasCharges` đã sẵn.
- Chưa có UI/controller.
