# US-001 — Household / Apartment backend: API contract (Dev-3 → Dev-2, Lead Dev)

Nguồn: `docs/srs/EP01_QuanLyCuDan.md` mục 1, EP-06 (RBAC), WBS sheet 03 (US-001 "ĐÃ CHỐT").
**Trạng thái: ĐỀ XUẤT, chờ Lead Dev / Dev-2 xác nhận.** Mục đánh dấu **[ĐỀ XUẤT]** là phần SRS không quy định.

## 1. Cách dùng (Dev-2)

```java
HouseholdService service = new HouseholdServiceImpl(
        new HouseholdDAO(new DatabaseConnection(DatabaseConfig.load())), ApartmentChargeLookup.NONE);
// Chạy UI không cần MySQL (dữ liệu trong RAM, cùng luật unique với schema):
AuthorizationService authorization = new RoleBasedAuthorizationService(null);
HouseholdService inMemory = new HouseholdServiceImpl(new InMemoryHouseholdRepository(),
        ApartmentChargeLookup.NONE, authorization, Clock.systemDefaultZone());
```

Constructor production hai tham số giữ nguyên. Constructor đầy đủ nay nhận
`AuthorizationService` thay `Supplier<Role>` theo US-018 integration đã duyệt.
Cả hai cách đều kiểm tra session hiện tại; dữ liệu RAM không bỏ qua xác thực/phân quyền.
Không dựng role ADMIN giả trong production. Các fixture principal chỉ nằm trong test.

Controller chỉ gọi `HouseholdService`; không viết SQL, không tự kiểm tra luật nghiệp vụ.
Mọi lệnh gọi chặn theo JDBC: chạy trong `Task` nền, cập nhật view trên FX thread.

## 2. Service

| Method | Vai trò | Ghi chú |
| --- | --- | --- |
| `createHousehold(HouseholdForm)` | ADMIN | Tạo căn hộ + hộ trong 1 transaction. AC-01/02 |
| `updateHousehold(String maHo, HouseholdUpdateForm, boolean confirmAreaChange)` | ADMIN | AC-03, BR-02 |
| `getHouseholdById(String maHo)` | ADMIN, ACCOUNTANT | |
| `getHouseholdByApartment(String soCanHo)` | ADMIN, ACCOUNTANT | |
| `listHouseholds(ApartmentStatus filter)` | ADMIN, ACCOUNTANT | `null` = tất cả, sắp theo `so_can_ho`; rỗng vẫn là SUCCESS |

RESIDENT bị từ chối mọi method (EP-06: "Không có quyền"). Sai quyền hoặc chưa đăng nhập → ném
`AccessDeniedException` (unchecked, theo EP-06 BR-22); không có dữ liệu nào bị đổi.

## 3. DTO

- `HouseholdForm(soCanHo, tang, dienTich, maHo, ngayNhanNha, trangThai)` — 5 chuỗi thô từ ô nhập + `ApartmentStatus`.
  `tang` = `"12"`, `dienTich` = `"85.5"` (dấu chấm), `ngayNhanNha` = `"2024-03-15"`. `null` = để trống.
- `HouseholdUpdateForm(tang, dienTich, ngayNhanNha, trangThai)` — **[ĐỀ XUẤT]** `so_can_ho` và `ma_ho` không sửa được (là khóa).
- `ApartmentStatus`: `DANG_O("Đang ở")`, `CAN_HO_TRONG("Căn hộ trống")`, `CHO_THUE("Cho thuê")`. Dùng làm ComboBox (`toString()` = nhãn).
- `HouseholdProfile` (kết quả): `apartment()`, `household()` và getter phẳng `maHo() soCanHo() tang() dienTich() ngayNhanNha() trangThai()`.
  `dienTich` là `BigDecimal` (SRS ghi Double; dùng BigDecimal để tính phí chính xác ở US-007).

## 4. Kết quả `HouseholdResult<T>`

`status()`, `isSuccess()`, `message()` (hiển thị thẳng), `data()` (Optional), `fieldErrors()` (Map tên trường SRS → thông báo).

| Status | Khi nào | UI làm gì |
| --- | --- | --- |
| `SUCCESS` | Thành công | Hiện `message()`, nạp lại bảng |
| `VALIDATION_ERROR` | Thiếu/sai dữ liệu, **không lưu** | Tô đỏ các ô trong `fieldErrors()` (tất cả lỗi cùng lúc) |
| `DUPLICATE` | Trùng `so_can_ho` hoặc `ma_ho`, không lưu | Tô đỏ ô trong `fieldErrors()` |
| `NOT_FOUND` | `ma_ho`/`so_can_ho` không có | Báo không tìm thấy |
| `CONFIRMATION_REQUIRED` | BR-02: đổi diện tích căn hộ đã có kỳ thu | Hộp thoại xác nhận (`message()`); đồng ý → gọi lại với `confirmAreaChange=true` |
| `SYSTEM_ERROR` | Lỗi DB; thông báo chung, không lộ SQL | Báo lỗi hệ thống |

Khóa `fieldErrors`: `so_can_ho`, `tang`, `dien_tich`, `ma_ho`, `ngay_nhan_nha`, `trang_thai`.

## 5. Quy tắc dữ liệu (đúng bảng SRS 1.1)

| Trường | Quy tắc | Thông báo |
| --- | --- | --- |
| `so_can_ho` | `P` + 4 chữ số (`P1204`), chữ P hoa, duy nhất | "Số căn hộ không đúng định dạng hoặc đã tồn tại trên hệ thống!" (sai định dạng); "Số căn hộ đã tồn tại" (trùng, theo AC-02) |
| `tang` | số nguyên 1–30 | "Tầng phải là số nguyên từ 1 đến 30!" |
| `dien_tich` | > 0, tối đa 2 số lẻ | "Diện tích phải là số dương lớn hơn 0!" |
| `ma_ho` | bắt buộc, ≤ 20 ký tự, duy nhất | "Mã hộ khẩu không được để trống hoặc bị trùng!" |
| `ngay_nhan_nha` | `YYYY-MM-DD`, không sau hôm nay | "Ngày nhận nhà không được lớn hơn ngày hiện tại!" |
| `trang_thai` | 1 trong 3 giá trị | "Vui lòng chọn trạng thái căn hộ!" |

Tạo thành công: `"Tạo hồ sơ căn hộ P1204 thành công"`. Cập nhật: `"Cập nhật hồ sơ căn hộ P1204 thành công"`.
Dữ liệu mẫu để thử UI: `database/migrations/us001_seed.sql` (6 căn; `P1204` đang `CAN_HO_TRONG` để demo AC-02/AC-03, dùng `P1205` để demo AC-01).

## 6. Database (file riêng, chưa gộp vào `database/schema.sql` của Dev-1)

`database/migrations/us001_household_apartment.sql` (chạy lại an toàn, cần MySQL ≥ 8.0.16):

- `apartments(so_can_ho PK, tang, dien_tich DECIMAL(8,2), trang_thai ENUM)` + CHECK format/tầng/diện tích.
- `households(ma_ho PK, so_can_ho UNIQUE NOT NULL, ngay_nhan_nha DATE)`, FK `fk_households_apartment` → `apartments(so_can_ho)` (RESTRICT).
- Quan hệ: 1 căn hộ ↔ 1 hộ (BR-01). Nhân khẩu (US-002, Dev-1): `residents.ma_ho` nên FK → `households(ma_ho)`.

**Gộp vào schema:** Dev-1 chạy file này sau `schema.sql` hoặc dán nội dung vào; tôi không sửa `schema.sql`.

## 7. Điểm SRS không quy định — cần Lead Dev / PO xác nhận

1. **[ĐỀ XUẤT]** `ngay_nhan_nha` và `trang_thai` đặt ở bảng nào: tôi đặt trạng thái ở `apartments`, ngày nhận nhà ở `households`.
2. **[ĐỀ XUẤT]** Quan hệ 1–1 (`UNIQUE(so_can_ho)`); chưa có cờ "hồ sơ đang hoạt động" vì US-001 không có xóa/ngừng hộ. Khi cần đổi chủ/hộ mới cho cùng căn hộ phải thêm cột và đổi ràng buộc.
3. **[ĐỀ XUẤT]** Không cho sửa `so_can_ho`, `ma_ho`. SRS chỉ nói "thông tin được phép".
4. Số căn hộ `P[Tầng][Phòng]` ngụ ý tầng trùng 2 số đầu (P1204 → 12). Hiện **chỉ kiểm tra định dạng**, không ép `tang` khớp; xác nhận có cần không.
5. Trùng số căn hộ: bảng SRS và AC-02 dùng 2 câu khác nhau; tôi dùng câu bảng cho sai định dạng, câu AC-02 cho trùng.
6. SRS chỉ có thông báo cho vi phạm luật; câu cho "ngày sai định dạng" là **[ĐỀ XUẤT]**.
7. BR-02 (xác nhận khi đổi diện tích căn hộ đã có kỳ thu): bảng phí là US-007 chưa tồn tại → `ApartmentChargeLookup.NONE` luôn trả "chưa có kỳ thu". Cần nối khi US-007 xong.
8. BR-03 (căn hộ trống thì nhân khẩu = 0) và tên chủ hộ/số nhân khẩu cho EP-03/EP-04/EP-07 phụ thuộc bảng nhân khẩu của Dev-1; US-001 chưa đọc những dữ liệu này.
9. Ma trận quyền US-001: ADMIN tạo/sửa/xem, ACCOUNTANT chỉ xem, RESIDENT bị từ chối. Quyền OWN của US-018 chỉ áp dụng lịch sử đóng phí, không mở HOUSEHOLD_VIEW.
10. Dùng class chung `com.bluemoon.services.AccessDeniedException` đã tích hợp trên main; không tạo class thứ hai. Guard Household gọi AuthorizationService, không còn requireRole nội bộ.
