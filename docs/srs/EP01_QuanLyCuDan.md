# EP-01: QUẢN LÝ CƯ DÂN
**Dự án:** Quản lý chung cư BlueMoon (SE_02)  
**Người lập (PO / Scrum Master):** Đỗ Gia Ngọc  
**Phân công thực hiện (Sprint 1):** Dev-2 (Nguyễn Hữu Gia Vinh), Dev-3 (Bùi Bằng Nam), QA (Phạm Gia Kiên)  

---

## 1. US-001: Quản lý hồ sơ hộ gia đình và căn hộ

> **User Story:** Là Ban quản trị, tôi muốn tạo, xem và cập nhật hồ sơ hộ gia đình và căn hộ để quản lý tập trung thông tin phục vụ các nghiệp vụ khác (tính phí, gửi thông báo).

### 1.1. Bảng từ điển dữ liệu & Quy tắc kiểm tra (Data Dictionary & Validation)
Khi tạo hoặc cập nhật một hồ sơ Hộ gia đình / Căn hộ, các trường bắt buộc và ràng buộc kỹ thuật:

| Tên trường | Tên hiển thị | Kiểu dữ liệu | Bắt buộc | Quy tắc kiểm tra (Validation Rules) | Thông báo lỗi khi sai (Error Message) |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `so_can_ho` | Số căn hộ | String(10) | **Có** | - Định dạng chuẩn: `P[Tầng][Phòng]` (VD: `P1002`, `P0508`).<br>- Không được trùng lặp trong toàn chung cư. | *"Số căn hộ không đúng định dạng hoặc đã tồn tại trên hệ thống!"* |
| `tang` | Tầng | Integer | **Có** | - Từ 1 đến 30 (theo mô tả chung cư BlueMoon). | *"Tầng phải là số nguyên từ 1 đến 30!"* |
| `dien_tich` | Diện tích ($m^2$) | Double | **Có** | - Số thực dương > 0, tối đa 2 chữ số thập phân (VD: `75.50`). | *"Diện tích phải là số dương lớn hơn 0!"* |
| `ma_ho` | Mã hộ khẩu | String(20) | **Có** | - Mã định danh duy nhất (VD: `HK-1002`). | *"Mã hộ khẩu không được để trống hoặc bị trùng!"* |
| `ngay_nhan_nha` | Ngày nhận nhà | Date | **Có** | - Định dạng `YYYY-MM-DD`.<br>- Không được lớn hơn ngày hiện tại. | *"Ngày nhận nhà không được lớn hơn ngày hiện tại!"* |
| `trang_thai` | Trạng thái | Enum | **Có** | - Chọn 1 trong 3: `Đang ở`, `Căn hộ trống`, `Cho thuê`. | *"Vui lòng chọn trạng thái căn hộ!"* |

### 1.2. Quy tắc nghiệp vụ (Business Rules - BR)
- **BR-01 (Tính duy nhất của Căn hộ):** Mỗi căn hộ tại một thời điểm chỉ gắn với **1 hồ sơ hộ khẩu đang hoạt động**.
- **BR-02 (Ràng buộc Diện tích với Thu phí):** Diện tích căn hộ là căn cứ tính **Phí quản lý** và **Phí dịch vụ** ở EP-03. Khi một căn hộ đã phát sinh các kỳ thu phí, việc sửa diện tích phải có cảnh báo xác nhận từ Ban quản trị.
- **BR-03 (Xử lý Căn hộ trống):** Nếu trạng thái là `Căn hộ trống`, hồ sơ hộ vẫn được lưu diện tích để sau này tính phí bảo dưỡng định kỳ, nhưng danh sách thành viên nhân khẩu sẽ bằng 0.

### 1.3. Tiêu chí chấp nhận (Acceptance Criteria - Given / When / Then)
* **AC-01 (Tạo hồ sơ thành công):**
  * **Given:** BQT nhập đúng định dạng số căn hộ `P1204`, diện tích `85.0`, mã hộ `HK-1204`, ngày nhận nhà hợp lệ.
  * **When:** Nhấn nút "Lưu hồ sơ".
  * **Then:** Hệ thống lưu thành công, thông báo *"Tạo hồ sơ căn hộ P1204 thành công"*, dữ liệu xuất hiện ngay trên bảng danh sách.
* **AC-02 (Bắt lỗi trùng số phòng):**
  * **Given:** Căn hộ `P1204` đã có trong CSDL.
  * **When:** BQT tạo hồ sơ mới và nhập lại số phòng `P1204`.
  * **Then:** Hệ thống chặn lại, hiển thị cảnh báo lỗi *"Số căn hộ đã tồn tại"*, không lưu vào CSDL.
* **AC-03 (Cập nhật thông tin):**
  * **Given:** Hồ sơ hộ `HK-1204` đã tồn tại.
  * **When:** BQT sửa trạng thái từ `Căn hộ trống` sang `Đang ở` và lưu.
  * **Then:** Hệ thống ghi nhận trạng thái mới, không làm thay đổi lịch sử các dữ liệu khác.

---

## 2. US-002: Thêm nhân khẩu vào hộ/căn hộ

> **User Story:** Là Ban quản trị, tôi muốn thêm nhân khẩu vào một hộ/căn hộ để hồ sơ phản ánh đúng các thành viên đang sinh sống, làm cơ sở quản lý an ninh và tính phí vệ sinh.

### 2.1. Bảng từ điển dữ liệu & Quy tắc kiểm tra (Data Dictionary & Validation)

| Tên trường | Tên hiển thị | Kiểu dữ liệu | Bắt buộc | Quy tắc kiểm tra (Validation Rules) | Thông báo lỗi khi sai (Error Message) |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `ho_ten` | Họ và tên | String(50) | **Có** | - Từ 2 đến 50 ký tự, chỉ chứa chữ cái và khoảng trắng. | *"Họ tên không hợp lệ (từ 2-50 ký tự, không chứa ký tự đặc biệt)!"* |
| `ngay_sinh` | Ngày sinh | Date | **Có** | - Phải nhỏ hơn ngày hiện tại. | *"Ngày sinh không hợp lệ!"* |
| `gioi_tinh` | Giới tính | Enum | **Có** | - `Nam` hoặc `Nữ`. | *"Vui lòng chọn giới tính!"* |
| `so_cccd` | Số CCCD/CMND | String(12) | **Điều kiện** | - Bắt buộc nếu tuổi $\ge 14$ tính đến thời điểm nhập.<br>- Đúng 12 chữ số (hoặc 9 số cũ).<br>- Duy nhất trong toàn hệ thống. | *"CCCD phải đúng 12 chữ số và không được trùng lặp!"* |
| `quan_he_chu_ho`| Quan hệ với chủ hộ | Enum | **Có** | - `Chủ hộ`, `Vợ`, `Chồng`, `Con`, `Bố/Mẹ`, `Người thuê`, `Khác`. | *"Vui lòng chọn quan hệ với chủ hộ!"* |
| `ngay_chuyen_den`| Ngày chuyển đến | Date | **Có** | - Không được lớn hơn ngày hiện tại. | *"Ngày chuyển đến không hợp lệ!"* |
| `ma_ho` | Thuộc hộ | String(20) | **Có** | - Khóa ngoại, phải chọn từ danh sách hộ hợp lệ. | *"Vui lòng chọn hộ gia đình/căn hộ!"* |

### 2.2. Quy tắc nghiệp vụ (Business Rules - BR)
- **BR-04 (Quy tắc Chủ hộ duy nhất):** 
  - Mỗi hộ gia đình bắt buộc phải có **đúng 1 người** mang quan hệ là `Chủ hộ`.
  - Nếu thêm một nhân khẩu mới với vai trò `Chủ hộ` vào hộ đã có chủ hộ $\rightarrow$ Hệ thống bắt buộc người dùng chọn: Hoặc đổi chủ hộ cũ thành `Khác`, hoặc từ chối thao tác.
- **BR-05 (Ràng buộc tính phí vệ sinh):**
  - Số lượng nhân khẩu thường trú/tạm trú đang ở trong hộ sẽ tự động là hệ số nhân cho **Phí vệ sinh môi trường** (6.000 VNĐ / người / tháng) ở US-007.

### 2.3. Tiêu chí chấp nhận (Acceptance Criteria - Given / When / Then)
* **AC-01 (Thêm nhân khẩu thành công):**
  * **Given:** Đang ở màn hình chi tiết hộ `P1204`.
  * **When:** BQT nhập họ tên "Nguyễn Văn A", sinh năm 1990, CCCD hợp lệ 12 số, quan hệ "Chủ hộ" và nhấn "Lưu".
  * **Then:** Hệ thống ghi nhận nhân khẩu, hiển thị người này vào danh sách thành viên của hộ `P1204`, số lượng thành viên của hộ tăng lên 1.
* **AC-02 (Bắt lỗi thiếu CCCD với người trên 14 tuổi):**
  * **Given:** BQT nhập một thành viên sinh năm 2000 (26 tuổi).
  * **When:** Để trống ô CCCD và nhấn "Lưu".
  * **Then:** Hệ thống chặn lại, hiển thị thông báo *"Công dân từ 14 tuổi trở lên bắt buộc phải nhập số CCCD!"*.
* **AC-03 (Bắt lỗi trùng 2 chủ hộ):**
  * **Given:** Hộ `P1204` đã có chủ hộ là "Nguyễn Văn A".
  * **When:** BQT thêm thành viên "Trần Thị B" và chọn tiếp quan hệ là "Chủ hộ".
  * **Then:** Hệ thống hiển thị hộp thoại xác nhận: *"Hộ này đã có chủ hộ. Bạn có muốn đổi Nguyễn Văn A sang quan hệ khác không?"*.

