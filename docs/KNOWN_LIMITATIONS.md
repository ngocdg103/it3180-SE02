# Danh Mục Giới Hạn Kỹ Thuật & Kế Hoạch Sprint 2 (Known Limitations)

Tài liệu này ghi nhận chi tiết các quyết định phạm vi kỹ thuật, các điểm điều chỉnh so với đặc tả SRS ban đầu và danh mục các tính năng được quy hoạch cho Sprint 2 đã được Product Owner (PO) và Ban Kỹ Thuật phê duyệt.

---

## 1. Phân Biệt "Hoàn Thành Theo Phạm Vi Sprint 1" & "Toàn Bộ AC Gốc"

Nhằm bảo đảm tiến độ chuyển giao trước thời hạn và duy trì tính ổn định của kiến trúc, một số tiêu chí chấp thuận (Acceptance Criteria) đã được tinh chỉnh phạm vi thực hiện cho Sprint 1:

| Hạng mục / Quy tắc | Trạng thái trong Sprint 1 | Kế hoạch trong Sprint 2 | Cơ sở quyết định của PO |
|---|---|---|---|
| **BR-13: Khấu trừ nộp thừa (Credit carry-forward)** | **Ghi nhận sổ cái:** Số tiền đóng thừa được lưu trữ an toàn, nguyên tử vào bảng `apartment_credit` và phân loại trạng thái `DONG_THUA`. | **Tự động cấn trừ:** Module lập kỳ thu mới (Billing) sẽ tự động kiểm tra số dư `apartment_credit` của căn hộ để trừ vào tổng phải thu của kỳ sau. | Tránh sửa đổi snapshot hóa đơn US-007 đã đóng; bảo đảm kiểm soát giao dịch chống trừ nợ hai lần (double-use). |
| **BR-17: Định dạng xuất thông báo thu tiền** | **Printable HTML:** Xuất tệp HTML định dạng in ấn chuyên nghiệp (sử dụng CSS `page-break`), in trực tiếp hoặc lưu thành PDF qua trình duyệt. | **Native PDF Export:** Tích hợp thư viện sinh PDF nhị phân nội bộ (nếu yêu cầu triển khai không phụ thuộc trình duyệt). | Tránh phụ thuộc thêm thư viện ngoài nặng nề trong Sprint 1; giải pháp HTML đáp ứng 100% nhu cầu in ấn thực tế. |
| **BR-23: Cư dân tự tra cứu** | **Tra cứu BQL:** Cung cấp công cụ tra cứu cư dân đa tiêu chí hiệu năng cao dành riêng cho Quản trị viên (`ADMIN`). | **Cổng thông tin Cư dân:** Giao diện tự phục vụ dành cho vai trò `RESIDENT` chỉ tra cứu dữ liệu trong phạm vi căn hộ của mình. | Tập trung hoàn thiện công cụ quản trị cốt lõi cho BQL trước theo thứ tự ưu tiên nghiệp vụ. |
| **PR #28: Household BR-02 UI** | **Backend sẵn sàng:** Backend `BillingDAO.hasCharges()` và `BillingService.getApartmentHistory()` đã tích hợp hoàn tất trên `main`. | **Hoàn tất Merge:** Nhánh giao diện `feature/us001-br02-ui` đang trong giai đoạn QA độc lập và sẽ được merge ngay khi đạt tiêu chuẩn. | Tuân thủ tuyệt đối quy trình Quality Gate: Không merge khi chưa có phê duyệt QA. |
| **US-016: Quản lý Phương tiện (Vehicle Management)** | **Chưa triển khai trong Sprint 1:** Phí gửi xe tạm thời hạch toán theo biểu phí định mức cố định. | **Triển khai toàn diện:** Bổ sung thực thể xe, biển số, thẻ xe và phân loại phí gửi xe theo từng loại phương tiện. | Thuộc phạm vi chính thức của Sprint 2 theo kế hoạch WBS tổng thể. |

---

## 2. Các Quyết Định Nghiệp Vụ Quan Trọng Của PO (P0 Decisions)

1. **Định dạng và độ dài Mã Phiếu Thu (`ma_phieu_thu`):**
   - *Đặc tả ban đầu:* Giới hạn tối đa 30 ký tự (`PT-[Timestamp]-[MãHộ]`).
   - *Quyết định PO:* Nâng trần độ dài lên **`VARCHAR(64)`** trong cơ sở dữ liệu và mã nguồn.
   - *Căn cứ:* Định dạng `PT-[epochMillis]-[requestTag]-[maHo]` bảo đảm tính duy nhất tuyệt đối (zero-collision) và cơ chế xử lý idempotent khi người dùng bấm lưu nhiều lần hoặc mạng chập chờn, vượt qua giới hạn độ dài của mã căn hộ/hộ gia đình phức tạp.
2. **Độ chính xác tiền tệ (Currency Precision):**
   - *Đặc tả:* Số tiền dương.
   - *Quyết định PO:* Bắt buộc áp dụng tiền **VNĐ nguyên** (`DECIMAL(19, 2)` với ràng buộc `CHECK (so_tien_nop = ROUND(so_tien_nop, 0))`). Từ chối mọi giao dịch có số tiền lẻ xu/hào để tương thích hoàn toàn với bất biến hóa đơn của US-007.

---

## 3. Backlog Kế Hoạch Cho Sprint 2

1. **Sprint 2 - Epic Thu Phí Nâng Cao:**
   - Triển khai thuật toán tự động tiêu thụ số dư `apartment_credit` khi lập kỳ thu phí mới.
   - Tích hợp cổng thanh toán trực tuyến hoặc đối soát tự động sao kê ngân hàng qua mã QR.
2. **Sprint 2 - Epic Quản Lý Phương Tiện (US-016):**
   - Quản lý danh sách phương tiện (ô tô, xe máy, xe đạp điện) theo từng căn hộ.
   - Tính toán phí trông giữ xe linh hoạt theo số lượng phương tiện thực tế.
3. **Sprint 2 - Epic Tương Tác Cư Dân:**
   - Giao diện đăng nhập và tự tra cứu dành riêng cho Cư dân (Mobile-friendly hoặc Web Portal).
   - Tiếp nhận phản ánh, khiếu nại và đánh giá dịch vụ tòa nhà.
