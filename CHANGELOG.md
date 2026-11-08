# Changelog

Tất cả các thay đổi đáng chú ý của dự án **BlueMoon** sẽ được ghi lại trong tệp này.

Định dạng dựa trên [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
và dự án này tuân thủ theo [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Pending Merge
- **PR #28 — Household BR-02 Confirmation & Billing History UI:**
  - Hộp thoại cảnh báo xác nhận khi thay đổi diện tích căn hộ đã phát sinh hóa đơn tính phí (`CONFIRMATION_REQUIRED` $\rightarrow$ OK xác nhận / Cancel giữ nguyên).
  - Bảng lịch sử các kỳ phí của căn hộ hiển thị trong màn hình Chi tiết Hộ gia đình (`HouseholdDetailController`).
  - *Tình trạng:* Đã nạp backend (PR #25) vào `main`; nhánh `feature/us001-br02-ui` đang trong quá trình Independent QA và Peer Review, chưa merge vào `main`.

---

## [0.1.0] - 2026-11-08 — Sprint 1 Delivery

### Added
- **Authentication & RBAC (US-012, US-018):**
  - Đăng nhập bảo mật sử dụng mật mã băm BCrypt cost-12; tự động khóa tài khoản tạm thời 5 phút sau 5 lần nhập sai liên tiếp theo SRS BR-18.
  - Cơ chế quản lý phiên làm việc tập trung `SessionContext` và công cụ phân quyền `RoleBasedAuthorizationService` với 3 vai trò: `ADMIN`, `ACCOUNTANT`, `RESIDENT`.
- **App Shell & Dashboard Navigation (PR #16, PR #27):**
  - Khung ứng dụng đa phân hệ thống nhất (`DashboardController`), điều hướng mượt mà trên cùng một Stage/Scene, không tạo trùng lặp cửa sổ.
  - Quản lý trạng thái bận (`busyProperty`) khi đang thực thi tác vụ nền, tự động hủy bỏ listener và giải phóng tài nguyên (`close()`).
  - Kết nối 5 module nghiệp vụ sản xuất: Phí (`feeRates`), Lập hóa đơn (`billing`), Thông báo (`notices`), Thu tiền (`receipts`), Báo cáo nợ (`debt`).
- **Quản lý Căn hộ & Hộ gia đình (US-001 - PR #12, PR #16):**
  - Quản lý danh sách căn hộ theo tầng và trạng thái; thêm mới hộ gia đình; cập nhật diện tích và danh sách thành viên.
  - Hook kiểm tra lịch sử thu phí BR-02 qua `BillingDAO.hasCharges()`.
- **Quản lý Nhân khẩu & Cư dân (US-002 - PR #20):**
  - Màn hình quản lý danh sách nhân khẩu theo hộ gia đình (`ResidentPanelController`), xem chi tiết và thêm mới cư dân kèm quan hệ với chủ hộ.
- **Tra cứu & Tìm kiếm Cư dân (US-014 - PR #23):**
  - Công cụ tìm kiếm cư dân đa tiêu chí (họ tên, CCCD, số điện thoại, số phòng, trạng thái cư trú); tối ưu hóa truy vấn CSDL kèm chỉ mục hiệu năng cao.
- **Quản lý Danh mục Khoản thu (US-005 - PR #13, PR #22):**
  - Quản lý danh mục loại phí (bắt buộc, tự nguyện); tạo mới, cập nhật tên/mô tả và hộp thoại xác nhận ngừng hoạt động danh mục phí.
- **Quản lý Định mức Thu phí (US-006 - PR #13, PR #15, Hotfix #44ee4fa):**
  - Quản lý định mức thu phí theo cơ chế bổ sung liên tục (Append-only / Effective-dated); tra cứu biểu phí hiệu lực tại một thời điểm (`FeeRateLookup`).
- **Lập Kỳ Thu phí Hàng tháng (US-007 - PR #11, PR #14, PR #18):**
  - Công cụ tính toán tiền phí tự động dựa trên diện tích, số nhân khẩu và định mức hiệu lực.
  - Snapshot hóa đơn bất biến tại thời điểm chốt kỳ (`invoices`, `invoice_lines`), bảo đảm toàn vẹn giao dịch với mức cô lập `REPEATABLE READ`.
- **Quản lý Thu phí & Lập Phiếu thu (US-008 - PR #21):**
  - Ghi nhận thanh toán từng phần (`DONG_THIEU`), thanh toán đủ (`DA_DONG_DU`) hoặc nộp thừa (`DONG_THUA`).
  - Lập phiếu thu tiền với mã định danh idempotent an toàn tối đa 64 ký tự (`ma_phieu_thu VARCHAR(64)`), chống ghi trùng lặp.
  - Ghi nhận số dư nộp thừa của căn hộ vào bảng sổ cái `apartment_credit`.
- **Báo cáo & Thống kê Công nợ (US-009 - PR #24):**
  - Báo cáo công nợ toàn tòa theo kỳ thu; tính toán KPI tỷ lệ thu phí, tổng tiền phải thu, đã thu và còn nợ.
  - Bộ lọc công nợ theo khoảng tầng và trạng thái thanh toán; hiển thị số nợ còn thiếu thực tế sau khi lọc.
- **Quản lý & Xuất Thông báo Thu phí (US-011 - PR #19):**
  - Xem trước phiếu thông báo thu tiền từ snapshot hóa đơn US-007; cấu hình số tài khoản ngân hàng và cú pháp chuyển khoản; xuất tệp HTML phân trang in ấn hàng loạt.
- **Lịch sử Phí Căn hộ (PR #25):**
  - API `BillingService.getApartmentHistory()` tra cứu lịch sử các kỳ phí của căn hộ từ snapshot hóa đơn đã commit, hiển thị trạng thái thanh toán từ Payment (US-008).

### Fixed & Improved
- Khắc phục lỗi vòng đời fixture MySQL chia sẻ bảng giữa các suite kiểm thử (PR #17).
- Ngăn ngừa xung đột đồng thời khi tạo nhân khẩu trên căn hộ đang chuyển trạng thái trống (Q17 Household-Resident Guard, PR #12).
- Khắc phục tình trạng mất layout/kích thước cửa sổ khi chuyển đổi giữa màn hình đăng nhập và Dashboard (Issue #5, PR #16).
- Căn chỉnh vòng đời và trạng thái bận của `FeeRateModule` tương thích hoàn toàn với App Shell (`fix/fee-rate-dashboard-contract`).

### Known Limitations (Sprint 1 Scope)
- **BR-13 (Auto-deduction):** Tự động khấu trừ số dư nộp thừa (`apartment_credit`) vào hóa đơn kỳ tiếp theo được hoãn sang Sprint 2 để tích hợp chặt chẽ trong transaction lập kỳ thu phí mới.
- **BR-17 (Export format):** Xuất phiếu thông báo thu tiền định dạng HTML có thuộc tính CSS in ấn (`page-break`), người dùng in trực tiếp hoặc lưu PDF qua trình duyệt máy tính; chưa tích hợp thư viện sinh PDF trực tiếp.
- **BR-23 (Resident-own filtering):** Chức năng cư dân tự tra cứu giới hạn trong căn hộ của mình được quy hoạch cho cổng thông tin cư dân trong Sprint 2.
