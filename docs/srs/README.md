# TÀI LIỆU ĐẶC TẢ YÊU CẦU PHẦN MỀM (SRS) - DỰ ÁN BLUEMOON
**Phân đoạn:** Sprint 1  
**Mục tiêu:** Xây dựng khung vận hành hệ thống, quản lý cư dân/hộ khẩu, thiết lập danh mục khoản thu và cơ chế tính phí tự động.

---

## Danh mục tài liệu đặc tả theo Epic (Sprint 1)

| Mã Epic | Tên Epic / Module | Các User Story trực thuộc | Tài liệu chi tiết | Phân công WBS |
| :---: | :--- | :--- | :---: | :---: |
| **EP-01** | **Quản lý cư dân** | - **US-001:** Quản lý hồ sơ hộ gia đình và căn hộ<br>- **US-002:** Thêm nhân khẩu vào hộ/căn hộ | [EP01_QuanLyCuDan.md](EP01_QuanLyCuDan.md) | Dev-2, Dev-3, QA |
| **EP-03** | **Quản lý thu phí & Đóng góp** | - **US-005:** Tạo và phân loại khoản thu<br>- **US-006:** Quản lý đơn giá theo thời điểm áp dụng<br>- **US-007:** Lập kỳ thu và tính phí tự động<br>- **US-008:** Ghi nhận hộ gia đình nộp tiền<br>- **US-009:** Theo dõi công nợ và tình trạng đóng phí | [EP03_ThuPhi.md](EP03_ThuPhi.md) | Dev-1, Dev-2, Dev-3, QA |
| **EP-04** | **Thông báo & Truyền thông** | - **US-011:** Tạo và phát hành thông báo thu tiền theo từng hộ | [EP04_ThongBao.md](EP04_ThongBao.md) | Dev-1, QA |
| **EP-06** | **Vận hành hệ thống & Bảo mật** | - **US-012:** Đăng nhập và xác thực tài khoản<br>- **US-018:** Phân quyền truy cập theo vai trò (RBAC) | [EP06_VanHanhHeThong.md](EP06_VanHanhHeThong.md) | Dev-1, Dev-2, QA |
| **EP-07** | **Tra cứu & Tìm kiếm hồ sơ** | - **US-014:** Tìm kiếm và tra cứu hồ sơ cư dân | [EP07_TraCuuThongKe.md](EP07_TraCuuThongKe.md) | Dev-1, Dev-2, QA |

---

## Cấu trúc nội dung chuẩn trong mỗi tài liệu Epic
Mỗi tệp Epic ở trên đều được chuẩn hóa theo 4 phần dành cho PO, Dev và QA:
1. **Từ điển dữ liệu & Bắt lỗi hợp lệ (Data Dictionary & Validation Rules):** Định nghĩa chi tiết tên trường, kiểu dữ liệu, tính bắt buộc, regex và thông báo lỗi.
2. **Quy tắc nghiệp vụ (Business Rules - BR):** Các điều kiện ràng buộc logic, thuật toán tính toán và xử lý ngoại lệ.
3. **Tiêu chí chấp nhận (Acceptance Criteria - AC):** Kịch bản kiểm thử theo chuẩn `Given - When - Then` để QA lập Test Cases và PO nghiệm thu.
4. **Phân công trách nhiệm:** Rõ ràng từng thành viên theo cấu trúc phân rã công việc WBS.
