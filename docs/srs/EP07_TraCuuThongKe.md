# EP-07: TRA CỨU VÀ TÌM KIẾM HỒ SƠ
**Dự án:** Quản lý chung cư BlueMoon (SE_02)  
**Người lập (PO / Scrum Master):** Đỗ Gia Ngọc  
**Phân công thực hiện (Sprint 1):** Dev-1 (Nguyễn Quang Minh), Dev-2 (Nguyễn Hữu Gia Vinh), QA (Phạm Gia Kiên)  

---

## 1. US-014: Tìm kiếm và tra cứu hồ sơ cư dân

> **User Story:** Là Ban quản trị và Kế toán, tôi muốn tìm kiếm nhanh hồ sơ hộ gia đình, căn hộ hoặc nhân khẩu theo tên, số phòng, số CCCD hoặc số điện thoại để tra cứu phục vụ công việc hàng ngày.

### 1.1. Bộ tiêu chí tìm kiếm đa năng (Search Criteria)

| Tiêu chí | Mô tả cơ chế tìm kiếm | Ví dụ đầu vào | Kết quả trả về |
| :--- | :--- | :--- | :--- |
| **Theo Số căn hộ** | Khớp chính xác hoặc khớp phần đầu (Prefix matching). | `1204` hoặc `P1204` | Căn hộ P1204, tầng 12, diện tích 80m², tên chủ hộ. |
| **Theo Tên cư dân** | Khớp gần đúng (Chứa chuỗi con, không phân biệt hoa/thường, hỗ trợ tiếng Việt có dấu/không dấu). | `ngoc` hoặc `Ngọc` | Danh sách các nhân khẩu có tên chứa "Ngọc", kèm số phòng đang ở. |
| **Theo Số CCCD/CMND**| Khớp chính xác chuỗi số. | `001200001234` | Đúng nhân khẩu mang số CCCD này và hộ khẩu tương ứng. |
| **Theo Tầng** | Lọc theo số tầng cụ thể. | `12` | Toàn bộ các căn hộ thuộc tầng 12. |

### 1.2. Cấu trúc kết quả tìm kiếm và Xem chi tiết (Detail View)
Khi người dùng bấm vào một kết quả tìm kiếm, hệ thống hiển thị màn hình Chi tiết:
1. **Thông tin căn hộ:** Số phòng, tầng, diện tích, trạng thái (`Đang ở`, `Trống`).
2. **Thông tin chủ hộ:** Họ tên chủ hộ, ngày sinh, số điện thoại, CCCD.
3. **Danh sách các nhân khẩu trong hộ:**
   - Bảng liệt kê: Họ tên, Giới tính, Ngày sinh, Quan hệ với chủ hộ, CCCD, Ngày chuyển đến.
4. **Tình trạng đóng phí nhanh:** Cho biết hộ này hiện có đang nợ phí kỳ nào không (liên kết nhanh đến hóa đơn thu phí).

### 1.3. Quy tắc nghiệp vụ (Business Rules)
- **BR-23 (Phạm vi tra cứu theo vai trò):**
  - `ADMIN` và `ACCOUNTANT`: Được tra cứu toàn bộ cư dân trong tòa nhà.
  - `RESIDENT`: Nếu đăng nhập chỉ được xem thông tin hồ sơ của căn hộ mình, không xem được thông tin căn hộ khác.
- **BR-24 (Tối ưu hiệu năng tìm kiếm):**
  - Thời gian phản hồi tìm kiếm phải dưới 1 giây đối với cơ sở dữ liệu quy mô toàn bộ tòa nhà (30 tầng x khoảng 15-20 căn/tầng = ~600 căn hộ).

### 1.4. Tiêu chí chấp nhận (Acceptance Criteria)
* **AC-01 (Tìm kiếm theo số phòng):**
  * **Given:** Trong CSDL có căn hộ `P1204`.
  * **When:** Người dùng gõ "1204" vào thanh tìm kiếm và bấm "Tìm kiếm" (hoặc Enter).
  * **Then:** Bảng kết quả hiển thị đúng bản ghi của căn hộ `P1204` và tên chủ hộ tương ứng.
* **AC-02 (Tìm kiếm theo tên gần đúng):**
  * **Given:** Có cư dân mang tên "Đỗ Gia Ngọc" sống tại phòng `P1502`.
  * **When:** Người dùng gõ từ khóa "ngoc" (chữ thường không dấu).
  * **Then:** Hệ thống hiển thị bản ghi "Đỗ Gia Ngọc - Căn hộ P1502".
* **AC-03 (Không tìm thấy kết quả):**
  * **Given:** Từ khóa tìm kiếm là `P9999` (không tồn tại trong tòa nhà).
  * **When:** Bấm tìm kiếm.
  * **Then:** Hệ thống hiển thị thông báo dịu: *"Không tìm thấy hồ sơ cư dân phù hợp với từ khóa!"*, bảng kết quả trống, không gây lỗi hệ thống.

