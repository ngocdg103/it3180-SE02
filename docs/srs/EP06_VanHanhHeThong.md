# EP-06: VẬN HÀNH HỆ THỐNG VÀ BẢO MẬT
**Dự án:** Quản lý chung cư BlueMoon (SE_02)  
**Người lập (PO / Scrum Master):** Đỗ Gia Ngọc  
**Phân công thực hiện (Sprint 1):** Dev-1 (Nguyễn Quang Minh), Dev-2 (Nguyễn Hữu Gia Vinh), QA (Phạm Gia Kiên)  

---

## 1. US-012: Đăng nhập và xác thực tài khoản

> **User Story:** Là thành viên Ban quản trị / Kế toán / Cư dân, tôi muốn đăng nhập bằng tài khoản và mật khẩu để truy cập vào hệ thống theo đúng vai trò và quyền hạn được cấp.

### 1.1. Bảng từ điển dữ liệu (Bảng `users`)

| Tên trường | Tên hiển thị | Kiểu dữ liệu | Bắt buộc | Quy tắc kiểm tra (Validation Rules) | Thông báo lỗi khi sai |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `username` | Tên đăng nhập | String(50) | **Có** | - Viết liền không dấu, từ 4 đến 50 ký tự.<br>- Không chứa ký tự đặc biệt.<br>- Duy nhất trong hệ thống. | *"Tên đăng nhập không hợp lệ hoặc không tồn tại!"* |
| `password_hash`| Mật khẩu mã hóa | String(255) | **Có** | - Mật khẩu gốc tối thiểu 6 ký tự.<br>- Bắt buộc phải được băm (hash) bằng SHA-256 hoặc BCrypt trước khi lưu vào CSDL. | *"Mật khẩu không chính xác!"* |
| `ho_ten` | Họ tên người dùng | String(50) | **Có** | - Tên hiển thị của người sử dụng tài khoản. | |
| `role` | Vai trò | Enum | **Có** | - `ADMIN`, `ACCOUNTANT`, `RESIDENT`. | |
| `trang_thai` | Trạng thái | Enum | **Có** | - `ACTIVE` (Đang hoạt động), `LOCKED` (Bị khóa). | *"Tài khoản của bạn đã bị tạm khóa!"* |
| `so_lan_sai` | Số lần sai pass | Integer | **Có** | - Đếm số lần đăng nhập sai liên tiếp. Mặc định 0. | |

### 1.2. Quy tắc nghiệp vụ (Business Rules)
- **BR-18 (Bảo vệ chống Brute-force):** 
  - Nếu người dùng nhập sai mật khẩu quá 5 lần liên tiếp $\rightarrow$ Tự động khóa tài khoản tạm thời trong vòng 5 phút (hoặc yêu cầu Quản trị viên mở khóa).
- **BR-19 (Thông báo lỗi chung để bảo mật):**
  - Không thông báo riêng *"Sai tài khoản"* hay *"Sai mật khẩu"*, mà thông báo chung: *"Tài khoản hoặc mật khẩu không chính xác!"* để tránh kẻ xấu dò quét danh sách tài khoản hợp lệ.
- **BR-20 (Duy trì trạng thái phiên - Session):**
  - Sau khi đăng nhập thành công, hệ thống lưu thông tin người dùng vào `SessionContext` toàn cục gồm: `userId`, `username`, `hoTen`, `role`.
  - Khi người dùng bấm "Đăng xuất", hủy toàn bộ Session và chuyển hướng về màn hình Đăng nhập.

### 1.3. Tiêu chí chấp nhận (Acceptance Criteria)
* **AC-01 (Đăng nhập đúng tài khoản Admin):**
  * **Given:** Tài khoản `admin` / `admin@123` tồn tại với vai trò `ADMIN`.
  * **When:** Nhập đúng username và password rồi bấm "Đăng nhập".
  * **Then:** Hệ thống mở giao diện Dashboard của Quản trị viên, góc trên hiển thị "Xin chào: Admin".
* **AC-02 (Đăng nhập sai mật khẩu):**
  * **Given:** Tài khoản `admin` tồn tại.
  * **When:** Nhập mật khẩu sai và bấm "Đăng nhập".
  * **Then:** Hệ thống hiển thị hộp thoại cảnh báo: *"Tài khoản hoặc mật khẩu không chính xác!"*, không chuyển màn hình.
* **AC-03 (Bắt lỗi để trống):**
  * **Given:** Đang ở màn hình đăng nhập.
  * **When:** Bấm nút "Đăng nhập" khi chưa nhập Username hoặc Password.
  * **Then:** Hệ thống hiển thị thông báo: *"Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!"*.

---

## 2. US-018: Phân quyền truy cập theo vai trò (Role-Based Access Control)

> **User Story:** Là người phụ trách quản trị hệ thống, tôi muốn phân quyền truy cập theo vai trò để mỗi tài khoản chỉ được xem và thao tác trong đúng phạm vi trách nhiệm của mình.

### 2.1. Ma trận phân quyền chi tiết (RBAC Permission Matrix)

| Chức năng cụ thể trên phần mềm | Vai trò `ADMIN` | Vai trò `ACCOUNTANT` | Vai trò `RESIDENT` |
| :--- | :---: | :---: | :---: |
| **Quản lý tài khoản:** Tạo tài khoản, Khóa/Mở tài khoản, Đổi vai trò | **Toàn quyền** | Không hiển thị menu | Không hiển thị menu |
| **Hồ sơ căn hộ & hộ khẩu:** Thêm hộ mới, sửa thông tin căn hộ | **Toàn quyền** | Chỉ xem (Read-only) | Không có quyền |
| **Hồ sơ nhân khẩu:** Thêm nhân khẩu, sửa thông tin nhân khẩu | **Toàn quyền** | Chỉ xem (Read-only) | Không có quyền |
| **Danh mục khoản thu & Đơn giá:** Thêm/sửa khoản phí, đổi giá | **Toàn quyền** | **Toàn quyền** | Không có quyền |
| **Lập kỳ thu tiền:** Sinh hóa đơn tháng tự động | **Toàn quyền** | **Toàn quyền** | Không có quyền |
| **Ghi nhận nộp tiền:** Lập phiếu thu tiền | Không có quyền | **Toàn quyền** | Không có quyền |
| **Báo cáo & Thống kê nợ:** Xem danh sách nợ toàn tòa nhà | **Toàn quyền** | **Toàn quyền** | Không có quyền |
| **Cá nhân:** Xem lịch sử đóng phí của căn hộ mình | Xem tất cả | Xem tất cả | Chỉ xem đúng căn hộ mình |

### 2.2. Quy tắc nghiệp vụ (Business Rules)
- **BR-21 (Kiểm soát trên giao diện - UI Control):** 
  - Các nút chức năng hoặc Menu mà tài khoản không có quyền sẽ tự động bị ẩn (Hidden) hoặc làm mờ vô hiệu hóa (Disabled).
- **BR-22 (Kiểm soát tại tầng Service/Controller - Backend Security):**
  - Mọi hàm xử lý nghiệp vụ trước khi thực thi đều kiểm tra `SessionContext.getCurrentRole()`. Nếu không đủ thẩm quyền, chặn ngay và quăng ngoại lệ `AccessDeniedException`.

### 2.3. Tiêu chí chấp nhận (Acceptance Criteria)
* **AC-01 (Kế toán không được vào trang quản trị người dùng):**
  * **Given:** Tài khoản `ketoan` đang đăng nhập với vai trò `ACCOUNTANT`.
  * **When:** Nhìn vào thanh điều hướng bên trái.
  * **Then:** Menu "Quản lý tài khoản người dùng" hoàn toàn không hiển thị.
* **AC-02 (Admin không được trực tiếp lập phiếu thu tiền):**
  * **Given:** Tài khoản `admin` đang đăng nhập với vai trò `ADMIN`.
  * **When:** Vào màn hình danh sách hóa đơn thu phí.
  * **Then:** Nút "Thu tiền" bị vô hiệu hóa (hoặc ẩn), chỉ có Kế toán mới được phép thực hiện chức năng này để đảm bảo kiểm soát tài chính độc lập.

