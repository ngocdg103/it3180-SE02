# EP-04: THÔNG BÁO VÀ TRUYỀN THÔNG
**Dự án:** Quản lý chung cư BlueMoon (SE_02)  
**Người lập (PO / Scrum Master):** Đỗ Gia Ngọc  
**Phân công thực hiện (Sprint 1):** Dev-1 (Nguyễn Quang Minh), QA (Phạm Gia Kiên)  

---

## 1. US-011: Tạo và phát hành thông báo thu tiền theo từng hộ

> **User Story:** Là Ban quản trị, tôi muốn tạo và phát hành thông báo thu tiền chi tiết theo từng hộ để cư dân nắm rõ các khoản phí cần đóng, số tiền và thời hạn nộp.

### 1.1. Bảng từ điển dữ liệu phiếu thông báo (Notification Data Dictionary)

| Tên trường | Tên hiển thị | Kiểu dữ liệu | Bắt buộc | Ghi chú & Quy tắc kiểm tra |
| :--- | :--- | :---: | :---: | :--- |
| `ma_thong_bao` | Mã thông báo | String(30) | **Có** | Định dạng: `TB-[Kỳ]-[SốCănHộ]` (VD: `TB-202611-P1204`). |
| `ngay_phat_hanh`| Ngày phát hành | Date | **Có** | Ngày tạo thông báo (mặc định ngày hiện tại). |
| `han_nop_tien` | Hạn chót nộp | Date | **Có** | Mặc định ngày 15 của tháng kế tiếp kỳ thu. |
| `thong_tin_ngan_hang`| Tài khoản nhận | String(255) | **Có** | Tên ngân hàng, Số tài khoản, Chủ tài khoản Ban quản trị BlueMoon. |
| `noi_dung_chuyen_khoan`| Cú pháp CK | String(50) | **Có** | Cú pháp chuẩn: `[SoPhong] Dong phi thang [MM/YYYY]` (VD: `P1204 Dong phi thang 11/2026`). |

### 1.2. Cấu trúc nội dung chuẩn của Giấy thông báo thu phí (Template)
Mỗi phiếu thông báo bao gồm 4 phần chính:
1. **Tiêu đề:**  
   *BAN QUẢN TRỊ TÒA NHÀ CHUNG CƯ BLUEMOON*  
   *GIẤY THÔNG BÁO THU PHÍ THÁNG [MM/YYYY]*
2. **Thông tin căn hộ:**  
   - Căn hộ số: `P1204` | Chủ hộ: `Nguyễn Văn A`  
   - Diện tích tính phí: `80.0 m²` | Số nhân khẩu: `4 người`
3. **Bảng kê chi tiết các khoản phí:**
   - Phí dịch vụ chung cư: `80 m² x 10.000 = 800.000 VNĐ`
   - Phí quản lý chung cư: `80 m² x 7.000 = 560.000 VNĐ`
   - Phí vệ sinh môi trường: `4 người x 6.000 = 24.000 VNĐ`
   - Các khoản đóng góp tự nguyện (nếu có): `0 VNĐ`
   - **TỔNG TIỀN PHẢI NỘP:** `1.384.000 VNĐ`
4. **Hướng dẫn thanh toán & Ký tên:**
   - Nộp trực tiếp tại Văn phòng Ban quản trị (Tầng 1).
   - Hoặc chuyển khoản qua STK Ban quản trị với cú pháp đã nêu.
   - Hạn cuối thanh toán: `Ngày 15/12/2026`.

### 1.3. Quy tắc nghiệp vụ (Business Rules)
- **BR-16 (Điều kiện phát hành):** Chỉ phát hành thông báo khi kỳ thu đó đã được tính toán phí tự động (US-007) thành công.
- **BR-17 (Hỗ trợ xem trước và in ấn):** Hệ thống hỗ trợ:
  - Xem trước phiếu thông báo trực tiếp trên giao diện màn hình.
  - Cho phép Kế toán xuất hàng loạt phiếu thông báo cho toàn bộ các hộ của kỳ thu dưới dạng PDF hoặc xem dạng bảng in.

### 1.4. Tiêu chí chấp nhận (Acceptance Criteria)
* **AC-01 (Tạo thông báo hợp lệ):**
  * **Given:** Kỳ thu tháng `2026-11` đã có danh sách hóa đơn.
  * **When:** Kế toán chọn căn hộ `P1204` và bấm "Xem thông báo thu phí".
  * **Then:** Hệ thống hiển thị phiếu thông báo đầy đủ các mục chi tiết, tổng tiền khớp 100% với hóa đơn.
* **AC-02 (Chặn phát hành khi chưa lập kỳ thu):**
  * **Given:** Kỳ thu tháng `2026-12` chưa được tính phí ở US-007.
  * **When:** Người dùng chọn phát hành thông báo tháng 12.
  * **Then:** Hệ thống từ chối và thông báo: *"Chưa có dữ liệu hóa đơn cho kỳ thu này. Vui lòng lập kỳ thu trước!"*.

