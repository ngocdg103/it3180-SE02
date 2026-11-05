# EP-03: QUẢN LÝ THU PHÍ VÀ ĐÓNG GÓP
**Dự án:** Quản lý chung cư BlueMoon (SE_02)  
**Người lập (PO / Scrum Master):** Đỗ Gia Ngọc  
**Phân công thực hiện (Sprint 1):** Dev-1 (Nguyễn Quang Minh), Dev-2 (Nguyễn Hữu Gia Vinh), Dev-3 (Bùi Bằng Nam), QA (Phạm Gia Kiên)  

---

## 1. US-005: Tạo và phân loại khoản thu

> **User Story:** Là Ban quản trị, tôi muốn tạo và phân loại các khoản thu để quản lý riêng biệt các khoản phí bắt buộc và các khoản đóng góp tự nguyện.

### 1.1. Bảng từ điển dữ liệu & Quy tắc kiểm tra (Data Dictionary & Validation)

| Tên trường | Tên hiển thị | Kiểu dữ liệu | Bắt buộc | Quy tắc kiểm tra (Validation Rules) | Thông báo lỗi khi sai (Error Message) |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `ma_khoan_thu` | Mã khoản thu | String(20) | **Có** | - Tối đa 20 ký tự, viết hoa không dấu, không khoảng trắng (VD: `PHI_DV`, `PHI_QL`, `PHI_VS`, `QUY_NGHEO`).<br>- Duy nhất trong hệ thống. | *"Mã khoản thu không được để trống, không chứa khoảng trắng và không được trùng!"* |
| `ten_khoan_thu`| Tên khoản thu | String(100) | **Có** | - Từ 3 đến 100 ký tự. | *"Tên khoản thu phải từ 3 đến 100 ký tự!"* |
| `loai_khoan_thu`| Loại khoản thu | Enum | **Có** | - `BAT_BUOC` (Bắt buộc) hoặc `DONG_GOP` (Tự nguyện). | *"Vui lòng chọn loại khoản thu!"* |
| `hinh_thuc_thu`| Hình thức tính | Enum | **Có** | - `THEO_DIEN_TICH` ($m^2$)<br>- `THEO_NHAN_KHAU` (người)<br>- `THEO_CAN_HO` (cố định/căn)<br>- `TU_NGUYEN` (tùy tâm). | *"Vui lòng chọn hình thức tính phí!"* |
| `so_tien_mac_dinh`| Mức thu mặc định | Double | Không | - $\ge 0$. Khoản tự nguyện mặc định là 0. | *"Số tiền mặc định phải lớn hơn hoặc bằng 0!"* |
| `trang_thai` | Trạng thái | Enum | **Có** | - `DANG_AP_DUNG`, `NGUNG_AP_DUNG`. | *"Vui lòng chọn trạng thái!"* |

### 1.2. Quy tắc nghiệp vụ (Business Rules)
- **BR-06 (Phân loại rõ ràng):**
  - Khoản `BAT_BUOC`: Phải có công thức tính cụ thể (`THEO_DIEN_TICH`, `THEO_NHAN_KHAU`, hoặc `THEO_CAN_HO`).
  - Khoản `DONG_GOP`: Hình thức tính luôn là `TU_NGUYEN`, không ép buộc số tiền thu, chỉ ghi nhận theo thực tế hộ đóng góp.
- **BR-07 (Toàn vẹn dữ liệu):** Khoản thu đã phát sinh dữ liệu hóa đơn/phiếu thu thì **không được xóa cứng** khỏi CSDL, chỉ được chuyển trạng thái sang `NGUNG_AP_DUNG`.

### 1.3. Tiêu chí chấp nhận (Acceptance Criteria)
* **AC-01 (Tạo khoản thu bắt buộc thành công):**
  * **Given:** BQT nhập mã `PHI_QL`, tên "Phí quản lý chung cư", loại `BAT_BUOC`, hình thức `THEO_DIEN_TICH`.
  * **When:** Nhấn "Lưu".
  * **Then:** Hệ thống ghi nhận khoản thu mới và sẵn sàng để cấu hình đơn giá ở US-006.
* **AC-02 (Tạo khoản đóng góp tự nguyện):**
  * **Given:** BQT nhập mã `QUY_NGHEO_2026`, tên "Quỹ vì người nghèo năm 2026", loại `DONG_GOP`.
  * **When:** Nhấn "Lưu".
  * **Then:** Hệ thống tự động đặt số tiền mặc định = 0 và hình thức tính là `TU_NGUYEN`.

---

## 2. US-006: Quản lý đơn giá theo thời điểm áp dụng

> **User Story:** Là Ban quản trị, tôi muốn quản lý đơn giá phí theo thời điểm áp dụng để hệ thống tự động áp dụng đúng mức phí khi lập kỳ tính tiền mà không làm sai lệch dữ liệu cũ.

### 2.1. Bảng từ điển dữ liệu & Quy tắc kiểm tra

| Tên trường | Tên hiển thị | Kiểu dữ liệu | Bắt buộc | Quy tắc kiểm tra (Validation Rules) | Thông báo lỗi khi sai (Error Message) |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `ma_khoan_thu` | Khoản thu | String(20) | **Có** | - Phải chọn từ danh mục khoản thu bắt buộc còn hiệu lực. | *"Vui lòng chọn khoản thu cần đặt giá!"* |
| `don_gia` | Đơn giá (VNĐ) | Double | **Có** | - Số thực dương > 0. | *"Đơn giá phải là số dương lớn hơn 0!"* |
| `ngay_ap_dung` | Ngày bắt đầu áp dụng | Date | **Có** | - Định dạng `YYYY-MM-DD`. | *"Ngày áp dụng không hợp lệ!"* |
| `ghi_chu` | Quyết định / Căn cứ | String(255) | Không | - Lưu số quyết định hoặc lý do điều chỉnh đơn giá. | |

### 2.2. Quy tắc nghiệp vụ (Business Rules)
- **BR-08 (Đơn giá chuẩn của BlueMoon):**
  - Phí quản lý chung cư: `7.000 VNĐ / m² / tháng`.
  - Phí dịch vụ chung cư: `10.000 VNĐ / m² / tháng`.
  - Phí vệ sinh: `6.000 VNĐ / người / tháng`.
- **BR-09 (Bảo lưu lịch sử giá - Effective Dating):** Khi có đơn giá mới, hệ thống tạo bản ghi mới với `ngay_ap_dung` mới chứ không ghi đè bản ghi cũ.
- Khi tính tiền cho kỳ thu tháng $M$, hệ thống sẽ lấy đơn giá có `ngay_ap_dung` gần nhất nhưng nhỏ hơn hoặc bằng ngày đầu tiên của kỳ thu đó.

### 2.3. Tiêu chí chấp nhận (Acceptance Criteria)
* **AC-01 (Cập nhật đơn giá mới):**
  * **Given:** Phí quản lý đang là 7.000đ. BQT nhập đơn giá mới 8.000đ áp dụng từ `2027-01-01`.
  * **When:** Nhấn "Lưu đơn giá".
  * **Then:** Kỳ thu tháng `2026-11` vẫn tính theo giá 7.000đ. Kỳ thu từ tháng `2027-01` trở đi sẽ tự động áp giá 8.000đ.

---

## 3. US-007: Lập kỳ thu và tính phí tự động

> **User Story:** Là Ban quản trị, tôi muốn lập danh sách khoản phải thu cho một kỳ và để hệ thống tự tính phí theo diện tích và nhân khẩu nhằm hạn chế tối đa tính toán thủ công.

### 3.1. Bảng từ điển dữ liệu (Bảng `hoa_don_ky` & `chi_tiet_hoa_don`)

| Tên trường | Tên hiển thị | Kiểu dữ liệu | Bắt buộc | Ghi chú |
| :--- | :--- | :---: | :---: | :--- |
| `ma_hoa_don` | Mã hóa đơn | String(30) | **Có** | Định dạng: `HD-[Kỳ]-[SốCănHộ]` (VD: `HD-202611-P1204`). |
| `ky_thu` | Kỳ thu | String(7) | **Có** | Định dạng `YYYY-MM` (VD: `2026-11`). |
| `so_can_ho` | Căn hộ | String(10) | **Có** | Khóa ngoại tham chiếu căn hộ. |
| `tong_tien` | Tổng tiền phải nộp | Double | **Có** | Làm tròn đến hàng đơn vị VNĐ. |
| `da_thanh_toan`| Số tiền đã nộp | Double | **Có** | Mặc định 0 VNĐ khi mới lập kỳ. |
| `con_thieu` | Số tiền còn nợ | Double | **Có** | `tong_tien - da_thanh_toan`. |
| `trang_thai` | Trạng thái | Enum | **Có** | `CHUA_DONG`, `DONG_THIEU`, `DA_DONG_DU`, `DONG_THUA`. |
| `han_nop` | Hạn chót nộp | Date | **Có** | Mặc định ngày 15 của tháng kế tiếp. |

### 3.2. Công thức tính tiền & Quy tắc xử lý ngoại lệ (Business Rules)
- **Công thức tính cho căn hộ $A$:**
  $$\text{Tiền dịch vụ} = \text{Diện tích}_A \times \text{Đơn giá DV}$$
  $$\text{Tiền quản lý} = \text{Diện tích}_A \times \text{Đơn giá QL}$$
  $$\text{Tiền vệ sinh} = \text{Số nhân khẩu đang ở}_A \times \text{Đơn giá VS}$$
  $$\text{Tổng phải thu} = \text{Tiền DV} + \text{Tiền QL} + \text{Tiền VS} + \text{Khoản đóng góp (nếu đã đăng ký)}$$
- **BR-10 (Xử lý căn hộ trống):** Căn hộ không có người ở thì số nhân khẩu = 0 $\rightarrow$ Tiền vệ sinh = 0, nhưng vẫn tính tiền quản lý và dịch vụ đầy đủ.
- **BR-11 (Xử lý thiếu diện tích):** Nếu căn hộ chưa có thông tin diện tích, hệ thống dừng lập hóa đơn riêng cho căn hộ đó và thông báo yêu cầu cập nhật hồ sơ căn hộ.

### 3.3. Tiêu chí chấp nhận (Acceptance Criteria)
* **AC-01 (Tự động tính đúng tiền):**
  * **Given:** Căn hộ `P1204` diện tích $80m^2$, có 4 nhân khẩu. Đơn giá: QL 7.000đ, DV 10.000đ, VS 6.000đ.
  * **When:** Kế toán bấm "Lập kỳ thu tháng 2026-11".
  * **Then:** Hóa đơn căn hộ `P1204` có: DV = 800.000đ, QL = 560.000đ, VS = 24.000đ $\rightarrow$ Tổng tiền = **1.384.000 VNĐ**, trạng thái `CHUA_DONG`.
* **AC-02 (Chặn lập trùng kỳ thu):**
  * **Given:** Kỳ thu tháng `2026-11` đã được tạo thành công.
  * **When:** Người dùng vô tình bấm "Lập kỳ thu tháng 2026-11" lần thứ hai.
  * **Then:** Hệ thống cảnh báo *"Kỳ thu 2026-11 đã tồn tại trên hệ thống!"* và không sinh dữ liệu trùng lặp.

---

## 4. US-008: Ghi nhận hộ gia đình nộp tiền

> **User Story:** Là cán bộ kế toán, tôi muốn ghi nhận mỗi lần cư dân nộp tiền để cập nhật dữ liệu thu tiền thực tế và phục vụ công tác đối soát.

### 4.1. Bảng từ điển dữ liệu (Bảng `phieu_thu`)

| Tên trường | Tên hiển thị | Kiểu dữ liệu | Bắt buộc | Quy tắc kiểm tra (Validation Rules) | Thông báo lỗi |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `ma_phieu_thu` | Mã phiếu thu | String(30) | **Có** | - Tự động sinh: `PT-[Timestamp]-[MãHộ]`. | |
| `ma_hoa_don` | Thuộc hóa đơn | String(30) | **Có** | - Phải chọn hóa đơn chưa thanh toán đủ. | *"Vui lòng chọn hóa đơn cần thanh toán!"* |
| `nguoi_nop` | Người nộp tiền | String(50) | **Có** | - Tên người trực tiếp nộp tiền (2-50 ký tự). | *"Vui lòng nhập tên người nộp tiền!"* |
| `so_tien_nop` | Số tiền nộp (VNĐ) | Double | **Có** | - Phải là số dương > 0. | *"Số tiền nộp phải lớn hơn 0!"* |
| `ngay_nop` | Ngày nộp | Date | **Có** | - Mặc định ngày hiện tại. Không lớn hơn ngày hiện tại. | *"Ngày nộp tiền không hợp lệ!"* |
| `hinh_thuc` | Hình thức nộp | Enum | **Có** | - `TIEN_MAT` hoặc `CHUYEN_KHOAN`. | *"Vui lòng chọn hình thức thanh toán!"* |
| `ghi_chu` | Ghi chú | String(255) | Không | | |

### 4.2. Quy tắc nghiệp vụ (Business Rules)
- **BR-12 (Cộng dồn nhiều lần nộp):** Một hóa đơn cho phép nộp tiền thành nhiều lần. Mỗi lần nộp tiền thành công:
  $$\text{da\_thanh\_toan}_{\text{mới}} = \text{da\_thanh\_toan}_{\text{cũ}} + \text{so\_tien\_nop}$$
  $$\text{con\_thieu}_{\text{mới}} = \text{tong\_tien} - \text{da\_thanh\_toan}_{\text{mới}}$$
- **BR-13 (Cập nhật trạng thái tự động):**
  - Nếu $\text{con\_thieu} > 0 \rightarrow$ Trạng thái: `DONG_THIEU`.
  - Nếu $\text{con\_thieu} = 0 \rightarrow$ Trạng thái: `DA_DONG_DU`.
  - Nếu $\text{con\_thieu} < 0 \rightarrow$ Trạng thái: `DONG_THUA`. Phần tiền thừa được lưu vào số dư căn hộ để trừ kỳ sau.

### 4.3. Tiêu chí chấp nhận (Acceptance Criteria)
* **AC-01 (Nộp tiền một phần - Đóng thiếu):**
  * **Given:** Căn hộ `P1204` nợ 1.384.000 VNĐ.
  * **When:** Kế toán ghi nhận anh A nộp 1.000.000 VNĐ tiền mặt.
  * **Then:** Hệ thống lưu phiếu thu, cập nhật `da_thanh_toan` = 1.000.000 VNĐ, `con_thieu` = 384.000 VNĐ, chuyển trạng thái thành `DONG_THIEU`.
* **AC-02 (Nộp đủ tiền):**
  * **Given:** Căn hộ `P1204` đang còn thiếu 384.000 VNĐ.
  * **When:** Kế toán ghi nhận nộp tiếp 384.000 VNĐ chuyển khoản.
  * **Then:** `con_thieu` trở về 0 VNĐ, trạng thái chuyển thành `DA_DONG_DU`.

---

## 5. US-009: Theo dõi công nợ và tình trạng đóng phí

> **User Story:** Là Ban quản trị và Kế toán, tôi muốn theo dõi công nợ theo từng kỳ để biết hộ nào đã hoàn thành, hộ nào còn thiếu bao nhiêu tiền để phục vụ đôn đốc và báo cáo.

### 5.1. Bảng dữ liệu hiển thị (Công nợ tổng hợp)
Màn hình theo dõi hiển thị danh sách các hộ trong kỳ được chọn với các cột:
1. `Số căn hộ` | `Tên chủ hộ` | `Diện tích` | `Số người`
2. `Tổng tiền phải thu` | `Đã thu` | `Còn nợ` | `Trạng thái` | `Lần nộp cuối`

### 5.2. Quy tắc lọc và cảnh báo (Business Rules)
- **BR-14 (Bộ lọc đa chiều):**
  - Lọc theo kỳ thu (Mặc định kỳ gần nhất).
  - Lọc theo trạng thái (`Tất cả`, `Chưa đóng`, `Đóng thiếu`, `Đã xong`).
  - Lọc theo tầng (VD: Lọc các hộ nợ phí ở Tầng 10 đến Tầng 15).
- **BR-15 (Chỉ số KPI hiển thị trên đầu trang):**
  - Tổng số tiền phải thu toàn tòa nhà trong kỳ.
  - Tổng số tiền đã thực thu (và tỷ lệ %).
  - Tổng số nợ đọng còn lại.
  - Số hộ đã hoàn thành / Tổng số hộ.

### 5.3. Tiêu chí chấp nhận (Acceptance Criteria)
* **AC-01 (Lọc danh sách nợ):**
  * **Given:** BQT chọn kỳ `2026-11` và chọn bộ lọc "Chưa đóng hoặc Đóng thiếu".
  * **When:** Bấm nút "Lọc".
  * **Then:** Bảng chỉ hiển thị các hộ còn nợ tiền, kèm tổng số nợ hiển thị ở chân trang.

