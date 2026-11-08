# Sách Hướng Dẫn Sử Dụng Hệ Thống BlueMoon (Sprint 1)

Chào mừng bạn đến với **Hệ thống Quản lý Chung cư BlueMoon**. Tài liệu này hướng dẫn chi tiết cách vận hành các tính năng đã hoàn thiện trong Sprint 1 dành cho Quản trị viên (Admin), Kế toán (Accountant) và Cư dân (Resident).

---

## 1. Đăng Nhập & Tổng Quan Giao Diện

### 1.1. Màn hình Đăng nhập (Login Screen)
- Khởi chạy ứng dụng: `mvn javafx:run`.
- Nhập **Tên đăng nhập** và **Mật khẩu**.
- **Chính sách bảo mật:** Nếu nhập sai mật khẩu quá 5 lần liên tiếp, hệ thống sẽ tạm khóa tài khoản trong vòng 5 phút (theo quy định an toàn BR-18).
- **Tài khoản demo sẵn có:**
  - Quản trị viên: `admin` / `admin@123`
  - Kế toán: `accountant` / `accountant@123`
  - Cư dân: `resident` / `resident@123`

### 1.2. Khung Quản Trị Trung Tâm (Dashboard Shell)
Sau khi đăng nhập thành công, hệ thống mở màn hình Dashboard đa phân hệ:
- **Thanh menu điều hướng bên trái:** Hiển thị danh mục các module được cấp quyền tương ứng với vai trò của người dùng.
- **Khu vực hiển thị nội dung trung tâm:** Nơi người dùng trực tiếp thao tác trên các phân hệ. Mọi thao tác chuyển màn hình đều diễn ra mượt mà trên cùng một cửa sổ, giữ nguyên trạng thái phóng to/thu nhỏ (Maximize/Restore).
- **Thanh trạng thái bên trên:** Hiển thị tên hiển thị, vai trò đăng nhập và nút **Đăng xuất (Logout)**.

---

## 2. Quản Lý Hộ Gia Đình & Căn Hộ (US-001)

*Quyền truy cập: Quản trị viên (ADMIN).*

### 2.1. Danh sách Căn hộ & Hộ gia đình
1. Chọn menu **"Hộ gia đình"** (hoặc **"Căn hộ"**) trên Dashboard.
2. Danh sách căn hộ hiển thị thông tin: Mã căn hộ (Số phòng), Tầng, Diện tích ($m^2$), Trạng thái cư trú (`Đang ở` hoặc `Trống`), Tên chủ hộ và Số điện thoại liên hệ.
3. Sử dụng ô tìm kiếm để lọc nhanh căn hộ theo số phòng hoặc tên chủ hộ.

### 2.2. Xem Chi tiết & Cập nhật Diện tích Căn hộ
1. Nhấp đúp vào căn hộ hoặc chọn nút **"Xem chi tiết"**.
2. Màn hình chi tiết hiển thị thông tin chủ hộ, diện tích và danh sách thành viên cư trú.
3. Nhập diện tích mới và nhấn **"Lưu thay đổi"**:
   - Nếu căn hộ chưa phát sinh hóa đơn phí, diện tích được cập nhật thẳng.
   - Nếu căn hộ đã có hóa đơn trong các kỳ phí trước, hệ thống yêu cầu xác nhận để đảm bảo tính toàn vẹn (BR-02).

---

## 3. Quản Lý Nhân Khẩu & Cư Dân (US-002)

*Quyền truy cập: Quản trị viên (ADMIN).*

1. Tại màn hình **Chi tiết Hộ gia đình**, chuyển sang tab hoặc bảng **"Danh sách Cư dân / Nhân khẩu"**.
2. Nhấn nút **"Thêm nhân khẩu"** để mở hộp thoại nhập thông tin:
   - **Họ và tên:** Tối thiểu 2 ký tự, hỗ trợ đầy đủ tiếng Việt Unicode.
   - **Số CCCD / CMND:** 12 chữ số hợp lệ.
   - **Ngày sinh:** Định dạng ngày chuẩn.
   - **Quan hệ với chủ hộ:** Ví dụ: Chủ hộ, Vợ/Chồng, Con, Bố/Mẹ,...
   - **Số điện thoại & Nghề nghiệp** (nếu có).
3. Nhấn **"Xác nhận thêm"**: Cư dân được thêm vào hộ, hệ thống tự động cập nhật tổng số nhân khẩu thực tế của căn hộ.

---

## 4. Tra Cứu & Tìm Kiếm Cư Dân (US-014)

*Quyền truy cập: Quản trị viên (ADMIN).*

1. Chọn menu **"Tra cứu cư dân"** trên Dashboard.
2. Nhập một hoặc nhiều tiêu chí tìm kiếm:
   - **Từ khóa:** Tìm kiếm theo họ tên, số điện thoại hoặc số CCCD (hỗ trợ tìm kiếm không dấu và có dấu).
   - **Số phòng / Căn hộ:** Lọc theo mã căn hộ cụ thể.
   - **Trạng thái cư trú:** Thường trú, Tạm trú.
3. Nhấn nút **"Tìm kiếm"**: Kết quả tra cứu trả về danh sách chi tiết các cư dân thỏa mãn điều kiện chỉ sau vài mili-giây.

---

## 5. Quản Lý Danh Mục Loại Phí (US-005)

*Quyền truy cập: Quản trị viên (ADMIN).*

1. Chọn menu **"Danh mục phí"** trên Dashboard.
2. Xem danh sách các loại phí hiện hành (ví dụ: Phí quản lý chung cư, Phí dịch vụ, Phí trông giữ xe máy/ô tô, Quỹ vì người nghèo,...).
3. **Thêm loại phí mới:**
   - Nhập Mã loại phí, Tên loại phí, Tính chất (`Bắt buộc` hoặc `Tự nguyện`).
   - Nhấn **"Thêm mới"**.
4. **Ngừng áp dụng loại phí (Deactivate):**
   - Chọn loại phí và nhấn **"Vô hiệu hóa"**.
   - Hộp thoại xác nhận xuất hiện giải thích rõ loại phí này sẽ không còn xuất hiện trong các kỳ thu phí tương lai nhưng vẫn bảo lưu lịch sử cũ. Nhấn **"Đồng ý"** để hoàn tất.

---

## 6. Quản Lý Định Mức Thu Phí (US-006)

*Quyền truy cập: Quản trị viên (ADMIN).*

1. Chọn menu **"Định mức phí"** trên Dashboard.
2. Xem lịch sử đơn giá định mức của từng loại phí theo ngày hiệu lực.
3. **Thêm định mức mới:**
   - Chọn Loại phí cần áp dụng.
   - Nhập **Đơn giá mới** (đơn vị: VNĐ nguyên, ví dụ: `10000`).
   - Chọn **Ngày bắt đầu áp dụng** (Effective Date).
   - Nhấn **"Lưu định mức"**: Hệ thống ghi nhận định mức mới theo cơ chế append-only, không làm mất đơn giá cũ trong quá khứ.

---

## 7. Quy Trình Lập Kỳ Thu Phí Tháng (US-007)

*Quyền truy cập: Quản trị viên (ADMIN), Kế toán (ACCOUNTANT).*

1. Chọn menu **"Lập kỳ thu"** trên Dashboard.
2. Chọn **Kỳ thu phí** cần lập (định dạng `YYYY-MM`, ví dụ: `2026-11`).
3. Nhấn nút **"Xem trước (Preview)"**:
   - Hệ thống tự động tính toán số tiền cho toàn bộ căn hộ dựa trên:
     - Phí quản lý: $\text{Diện tích } (m^2) \times \text{Đơn giá định mức}$.
     - Phí dịch vụ: $\text{Số nhân khẩu} \times \text{Đơn giá định mức}$.
     - Phí gửi xe & Khoản đóng góp tự nguyện (nếu có).
   - Bảng xem trước liệt kê chi tiết từng căn hộ, tổng tiền và ngày hết hạn nộp tiền.
4. Kiểm tra kỹ số liệu và nhấn nút **"Chốt & Phát hành hóa đơn (Publish)"**:
   - Toàn bộ hóa đơn được lưu vào cơ sở dữ liệu dưới dạng **Snapshot bất biến**. Sau khi chốt, diện tích hay nhân khẩu căn hộ có thay đổi cũng không làm thay đổi số tiền đã chốt của kỳ này.

---

## 8. Ghi Nhận Thanh Toán & Lập Phiếu Thu (US-008)

*Quyền truy cập: Kế toán (ACCOUNTANT). (Admin chỉ có quyền xem lịch sử).*

1. Chọn menu **"Thu phí & Phiếu thu"** trên Dashboard.
2. Danh sách các hóa đơn còn thiếu tiền được hiển thị.
3. Chọn hóa đơn của căn hộ cần đóng tiền:
   - Hệ thống hiển thị tổng tiền phải thu, số tiền đã đóng và số tiền còn thiếu.
4. Nhập thông tin thanh toán:
   - **Người nộp tiền:** Tên người trực tiếp thanh toán (2 - 50 ký tự).
   - **Số tiền nộp:** Số tiền VNĐ nguyên (ví dụ: `1384000`).
   - **Ngày nộp:** Mặc định là ngày hiện tại.
   - **Hình thức:** Chọn `Tiền mặt` hoặc `Chuyển khoản`.
   - **Ghi chú:** (Tùy chọn).
5. Nhấn **"Lập phiếu thu (Lưu thanh toán)"**:
   - Hệ thống sinh mã phiếu thu tự động theo định dạng `PT-[Timestamp]-[MãHộ]` (tối đa 64 ký tự).
   - Nếu đóng thiếu: Trạng thái hóa đơn chuyển thành `DONG_THIEU`.
   - Nếu đóng đủ: Trạng thái chuyển thành `DA_DONG_DU`.
   - Nếu nộp thừa: Trạng thái chuyển thành `DONG_THUA`, phần tiền thừa được tự động lưu vào số dư căn hộ (`apartment_credit`).

---

## 9. Báo Cáo & Thống Kê Công Nợ (US-009)

*Quyền truy cập: Quản trị viên (ADMIN), Kế toán (ACCOUNTANT).*

1. Chọn menu **"Báo cáo công nợ"** trên Dashboard.
2. Chọn kỳ thu cần tra cứu (hoặc để trống để lấy kỳ mới nhất).
3. **Các chỉ số KPI toàn tòa:**
   - **Tổng phải thu:** Tổng tiền của toàn bộ hóa đơn đã chốt trong kỳ.
   - **Đã thu:** Tổng tiền thực tế đã thu được qua các phiếu thu.
   - **Còn nợ:** Tổng số tiền còn thiếu.
   - **Tỷ lệ thu phí:** Tỷ lệ % hoàn thành thu phí toàn tòa.
4. **Bộ lọc danh sách nợ:**
   - Lọc theo khoảng tầng (ví dụ: từ tầng 1 đến tầng 15).
   - Lọc theo trạng thái: `Chưa đóng hoặc Đóng thiếu`, `Đã đóng đủ`, `Đóng thừa`.
5. Dưới chân bảng hiển thị tổng số nợ thực tế sau khi áp dụng bộ lọc (Filtered Outstanding Debt).

---

## 10. Quản Lý & Xuất Phiếu Thông Báo Thu Tiền (US-011)

*Quyền truy cập: Quản trị viên (ADMIN), Kế toán (ACCOUNTANT).*

1. Chọn menu **"Thông báo thu phí"** trên Dashboard.
2. Nhập kỳ thu phí (ví dụ `2026-11`) và nhấn **"Tải hóa đơn của kỳ"**.
3. Danh sách các căn hộ có hóa đơn được nạp vào danh sách. Chọn một căn hộ cụ thể:
   - Xem trước nội dung thông báo: Tên chủ hộ, diện tích, nhân khẩu, bảng chi tiết từng dòng phí, tổng tiền và hạn nộp.
   - Cú pháp chuyển khoản tự động: `[SốPhòng] Dong phi thang [MM/YYYY]`.
4. Nhập **Thông tin tài khoản ngân hàng** (Tên ngân hàng, Số tài khoản, Chủ tài khoản).
5. **Xuất phiếu thông báo:**
   - **Xuất thông báo căn hộ đang chọn:** Xuất một tệp HTML cho căn hộ hiện tại.
   - **Xuất toàn bộ kỳ thu (Batch Export):** Xuất toàn bộ thông báo của cả tòa nhà ra một tệp HTML phân trang in ấn chuyên nghiệp.
   - Mở tệp HTML trên trình duyệt (Chrome, Edge) và nhấn `Ctrl + P` để in ra máy in hoặc lưu thành file PDF.

---

## 11. Ma Trận Phân Quyền Vai Trò (RBAC Summary)

| Tính năng / Module | Quản Trị Viên (`ADMIN`) | Kế Toán (`ACCOUNTANT`) | Cư Dân (`RESIDENT`) |
|---|:---:|:---:|:---:|
| Đăng nhập & Đổi mật khẩu | ✅ | ✅ | ✅ |
| Quản lý Hộ gia đình & Căn hộ | ✅ (Toàn quyền) | ❌ | 👁️ (Xem phòng mình) |
| Thêm/Sửa Cư dân & Nhân khẩu | ✅ (Toàn quyền) | ❌ | ❌ |
| Tra cứu Cư dân | ✅ | ❌ | ❌ |
| Quản lý Danh mục & Định mức phí | ✅ | ❌ | ❌ |
| Lập kỳ thu & Chốt hóa đơn | ✅ | ✅ | ❌ |
| Lập phiếu thu tiền | 👁️ (Chỉ xem lịch sử) | ✅ (Ghi nhận & Lập phiếu) | ❌ |
| Báo cáo & Thống kê Công nợ | ✅ | ✅ | ❌ |
| Xuất Thông báo thu tiền | ✅ | ✅ | ❌ |
