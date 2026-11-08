# US-001 BR-02: nối lịch sử thu phí của căn hộ (Dev-3)

Base: `main` 09ecb6e. Không đổi snapshot, không tính lại hóa đơn, không sửa `ProductionModules` hay UI Household.

## Contract
- `ApartmentChargeLookup.hasCharges(soCanHo)` (hook BR-02, đã có) nay được `BillingDAO` implement (`SELECT 1 FROM invoices WHERE so_can_ho = ?`). Không có class trùng lặp.
- Mới: `BillingService.getApartmentHistory(String soCanHo)` → `BillingResult<List<MonthlyCharge>>`.
  - Quyền `DEBT_REPORT_VIEW` (ADMIN, ACCOUNTANT); RESIDENT/không session → `AccessDeniedException` trước mọi truy cập DB.
  - Mới nhất trước; chỉ header (`lines` rỗng); đọc từ invoice đã lưu: `kyThu`, `tongTien`, `hanNop`, `dienTich`/`soNhanKhau` lúc lập kỳ.
  - Căn hộ chưa lập kỳ → SUCCESS, danh sách rỗng. `soCanHo` trống → `VALIDATION_ERROR` (`so_can_ho`). Lỗi DB → `SYSTEM_ERROR`/`BUSY` chung.
  - `daThanhToan`, `conThieu`, `trangThai` là giá trị đã commit trong `invoices`. Payment (US-008, đã merge) cập nhật đúng các cột này trong cùng transaction với phiếu thu, nên lịch sử phản ánh thanh toán thật (DONG_THIEU, DA_DONG_DU, DONG_THUA); service không suy diễn gì thêm. IT: `apartmentHistoryShowsCommittedPaymentStateAndKeepsTheSnapshot`.
- `BillingRepository.findChargesByApartment(soCanHo)` (prepared statement, `ORDER BY ky_thu DESC`).

## Dev-2 nối vào Household (không thuộc PR này)
`ProductionModules.create(...)`: thay `ApartmentChargeLookup.NONE` bằng `new BillingDAO(connections)` (import `com.bluemoon.dao.BillingDAO`). Constructor không mở kết nối. BR-02 sẽ trả `CONFIRMATION_REQUIRED` khi đổi diện tích căn hộ đã có hóa đơn.
Để hiển thị lịch sử ở Household Detail: tạo `BillingServiceImpl(new BillingDAO(connections), authorization, clock)` rồi gọi `getApartmentHistory(soCanHo)` ngoài FX thread. Bảng/UI lịch sử chưa có trong PR này.

## Fixture test (Dev-2)
Interface mới buộc hai fixture có sẵn trên main phải thêm method: `MemoryBillingRepository.findChargesByApartment(String)` và `NoticeServiceTest.Stub.getApartmentHistory(String)`. Hai file này do Dev-2 cập nhật, PR này không sửa. Đến khi Dev-2 merge, `test-compile` của PR này sẽ báo đúng hai lỗi đó.

## Kiểm thử
`BillingServiceTest` (RBAC, rỗng/trim/inject/validation/system error), `BillingResultTest` (chữ ký contract, 6 method), `BillingMySqlIT` +2: lịch sử đọc snapshot không đổi khi diện tích hiện tại đổi; BR-02 thật qua `HouseholdServiceImpl` + `BillingDAO` (trước khi lập kỳ cập nhật thẳng, sau khi lập kỳ `CONFIRMATION_REQUIRED`, `confirmAreaChange=true` thành công).
