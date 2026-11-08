# Báo Cáo Tổng Kết Bàn Giao Sprint 1 — Dự Án BlueMoon (SE_02)

**Dự án:** BlueMoon — Hệ Thống Quản Lý Chung Cư  
**Thời điểm lập báo cáo:** 23:50 ngày 08/11/2026\
**Người lập:** Product Owner (PO) / Merge Coordinator\
**Trạng thái kiểm tra trên `main`:** Commit SHA `aff01b6016bf802613eb5c5576a88b857757ee39`

---

## 1. Mục Tiêu & Phạm Vi Sprint 1

Mục tiêu trọng tâm của Sprint 1 là xây dựng hoàn chỉnh nền tảng kiến trúc phần mềm, xác thực bảo mật, quản lý căn hộ/cư dân và khép kín quy trình tài chính cơ bản cho Ban Quản Lý chung cư theo chuẩn SRS.

### Phạm vi bàn giao gồm 11 User Stories cốt lõi:
1. **US-012:** Xác thực người dùng, bảo mật mật khẩu BCrypt, chống brute-force khóa tạm 5 phút (BR-18).
2. **US-018:** Động cơ phân quyền tập trung (RBAC Engine) kiểm soát theo vai trò (`ADMIN`, `ACCOUNTANT`, `RESIDENT`).
3. **App Shell:** Khung giao diện quản trị Dashboard thống nhất, quản lý vòng đời và trạng thái bận.
4. **US-001:** Quản lý danh sách căn hộ, hộ gia đình, cập nhật diện tích sử dụng, hộp thoại xác nhận đổi diện tích BR-02 và hiển thị lịch sử thu phí.
5. **US-002:** Quản lý nhân khẩu trong căn hộ, thêm mới cư dân kèm thông tin định danh.
6. **US-014:** Tra cứu cư dân nâng cao đa tiêu chí, hỗ trợ tìm kiếm không dấu/có dấu.
7. **US-005:** Quản lý danh mục loại phí (bắt buộc, tự nguyện), vô hiệu hóa danh mục phí.
8. **US-006:** Quản lý định mức thu phí theo lịch sử hiệu lực (append-only), tra cứu đơn giá.
9. **US-007:** Lập kỳ thu phí tháng, tính toán tự động, chốt snapshot hóa đơn bất biến.
10. **US-008:** Thu phí, lập phiếu thu idempotent chống trùng lặp, ghi nhận số dư nộp thừa.
11. **US-009:** Báo cáo công nợ toàn tòa, thống kê KPI tỷ lệ thu phí, lọc theo tầng và trạng thái nợ.
12. **US-011:** Xem và xuất phiếu thông báo thu tiền định dạng in ấn HTML phân trang.

---

## 2. Phân Công Vai Trò Nhóm Dự Án

| Thành viên / Vai trò | Nhiệm Vụ Trọng Tâm |
|---|---|
| **Lead Developer** | Định hướng kiến trúc phân lớp, thiết lập hợp đồng kỹ thuật (API Contracts), rà soát thiết kế CSDL và phê duyệt nguyên tắc tích hợp. |
| **Product Owner (PO) / Merge Coordinator** | Quyết định các yêu cầu nghiệp vụ P0 (mã phiếu thu 64 ký tự, đơn vị VNĐ nguyên, hoãn cấn trừ nợ BR-13), điều phối tích hợp nhánh tuần tự, kiểm soát Quality Gates và nghiệm thu User Stories. |
| **Developer 1 (Dev-1)** | Triển khai Authentication (US-012), RBAC Core (US-018), Module Thu phí & Phiếu thu (US-008), Module Báo cáo công nợ (US-009), Peer Review PR #27. |
| **Developer 2 (Dev-2)** | Triển khai App Shell & Dashboard, Hộ gia đình UI (US-001), Nhân khẩu UI (US-002), Hoàn thiện Danh mục phí UI (US-005), Tra cứu cư dân (US-014), Tích hợp App Shell hoàn thiện (PR #27) và Household BR-02 UI (PR #28). |
| **Developer 3 (Dev-3)** | Triển khai Định mức phí Backend (US-006), Động cơ tính phí tháng (US-007 Backend & UI), Xuất thông báo thu tiền (US-011), Lịch sử phí căn hộ Backend (PR #25), Peer Review PR #28. |
| **Quality Assurance (QA)** | Thực hiện kiểm thử độc lập các PR, thẩm định Acceptance Criteria trên MySQL thật, chạy kiểm thử hồi quy và đánh giá E2E luồng nghiệp vụ. |

---

## 3. Lịch Sử Tích Hợp Trên `origin/main` (Traceability Matrix)

Mọi thay đổi đều được kiểm thử và tích hợp tuần tự vào nhánh `origin/main` mà không sử dụng force-push:

| PR / Branch | Commit Merge | Tóm Tắt Nội Dung Tích Hợp | Trạng Thái QA / Review |
|:---:|:---:|:---|:---:|
| **PR #16** | `1123219` | App Shell, Dashboard Navigation, US-001 UI, US-005 Wiring | QA APPROVED |
| **PR #17** | `4c4833c` | Hotfix vòng đời fixture chia sẻ bảng MySQL IT | QA APPROVED |
| **PR #15** | `4c42cf1` | US-006 Fee Rate Service & JavaFX UI Module | QA APPROVED |
| **PR #14** | `09ecb6e` | US-007 Billing Backend (Calculator, DAO, Service, Snapshot) | QA APPROVED |
| **PR #18** | `ed89777` | US-007 Billing UI Module (Xem trước, chốt hóa đơn) | QA APPROVED |
| **PR #20** | `43f3897` | US-002 Resident UI (Danh sách nhân khẩu & Thêm cư dân) | QA APPROVED |
| **PR #22** | `986f99f` | US-005 Fee Category Completion (Sửa và xác nhận vô hiệu hóa) | QA APPROVED |
| **PR #23** | `10f95cd` | US-014 Resident Search (Bộ lọc tìm kiếm cư dân tối ưu) | QA APPROVED |
| **PR #19** | `4ee0821` | US-011 Fee Notice Module (Xem và xuất HTML in ấn) | QA APPROVED |
| **Hotfix** | `44ee4fa` | `fix/fee-rate-dashboard-contract`: Đồng bộ vòng đời FeeRateModule | PO VERIFIED |
| **PR #21** | `02ccf29` | US-008 Payment & Receipts (Ghi thu, chống trùng, nộp thừa) | QA APPROVED |
| **PR #24** | `743b21f` | US-009 Debt Report (Báo cáo nợ, KPI toàn tòa, lọc tầng) | QA APPROVED |
| **PR #27** | `a1be332` | Final App Shell Composition: Nối 5 module và fixture methods | QA APPROVED |
| **PR #25** | `89b3a28` | US-001 BR-02 Billing History Backend (Đọc snapshot đã commit) | QA APPROVED |
| **PR #28** | `aff01b6` | US-001 BR-02 UI (Hộp thoại xác nhận đổi diện tích & Bảng lịch sử phí) | QA APPROVED / DEV-3 APPROVE |

---

## 4. Bằng Chứng Kiểm Thử Tự Động (Automated Test Metrics)

Kiểm thử xác thực trên `main` có PR #28 (`aff01b6`):
- **Môi trường:** JDK 17 (Eclipse Temurin 17.0.14+7), Apache Maven 3.9.9, Maven Enforcer: **PASS**.
- **Normal Regression Test Suite (`mvn test`):**
  - Số lượng kiểm thử: **699 tests**.
  - Failures: **0**, Errors: **0**, Skipped: **0**.
  - Kết quả: **BUILD SUCCESS**.
- **JavaFX Smoke Test Suite (`mvn -Pjavafx-smoke test`):**
  - Số lượng kiểm thử: **700 tests**.
  - Failures: **0**, Errors: **0**, Skipped: **0**.
  - Kết quả: **BUILD SUCCESS**.
- **Full MySQL Integration Tests (`-Pmysql-it verify`):**
  - Báo cáo kiểm thử tích hợp độc lập: **832 tests PASS** (0 failures, 0 errors, 0 skipped).

---

## 5. Các Vấn Đề Kỹ Thuật Đã Giải Quyết

1. **Bảo tồn kích thước cửa sổ (Issue #5):** Giải quyết dứt điểm hiện tượng mất kích thước và trạng thái Maximize khi chuyển từ màn hình Đăng nhập sang App Shell thông qua cơ chế chuyển đổi Scene trên cùng Stage chính.
2. **Xung đột ghi đồng thời (Q17 Household-Resident Guard):** Sử dụng cơ chế khóa hàng và transaction tuần tự để bảo đảm không thể thêm nhân khẩu vào căn hộ đang trong quá trình chuyển sang trạng thái trống.
3. **Chống trùng lặp thanh toán (Idempotency in Payment):** Áp dụng `request_id` duy nhất và định dạng mã phiếu thu `VARCHAR(64)` để bảo đảm giao dịch mạng chậm hoặc người dùng nhấp đúp không ghi nhận thanh toán hai lần.
4. **Bất biến snapshot tài chính:** Đảm bảo toàn vẹn dữ liệu khi có thay đổi diện tích hoặc nhân khẩu sau khi kỳ phí đã chốt, số liệu của hóa đơn đã phát hành giữ nguyên 100%.
5. **Cảnh báo đổi diện tích BR-02:** Kiểm soát an toàn khi thay đổi diện tích căn hộ đã có hóa đơn trong quá khứ (`CONFIRMATION_REQUIRED` $\rightarrow$ xác nhận người dùng trước khi cập nhật).

---

## 6. Trạng Thái Nghiệm Thu Hiện Tại & Sẵn Sàng Bàn Giao

- **Tất cả 11 User Stories cam kết trong Sprint 1 đã hoàn thành tích hợp và nghiệm thu thành công.**
- **Các PR kỹ thuật và giao diện (#27, #25, #28) đã được merge sạch vào `origin/main`.**
- **Toàn bộ bộ kiểm thử tự động normal (699) và smoke (700) đều XANH 100%.**
- **Hồ sơ tài liệu bàn giao (README, CHANGELOG, USER_GUIDE, SPRINT1_REPORT, KNOWN_LIMITATIONS) đã hoàn tất và sẵn sàng phát hành.**
