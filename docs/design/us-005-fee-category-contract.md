# US-005 — Danh mục khoản thu: đề xuất Service contract (Mốc 1, Dev-3 → Lead Dev, Dev-2)

Nguồn: `docs/srs/EP03_ThuPhi.md` mục 1 (US-005), EP-06 dòng "Danh mục khoản thu & Đơn giá", WBS 1.2.1.2.1/.2.2/.2.3, sheet "03 HOP DONG TICH HOP" (`FeeTypeService: list/create/update/deactivate`, owner service = Dev-3 theo sheet nhưng chỉ đạo Iteration 02 giao DAO/Service cho Dev-2).
**Trạng thái: ĐỀ XUẤT, chờ Lead Dev duyệt. Chưa viết code UI.** **[ĐỀ XUẤT]** = SRS không quy định.

## 1. Hiện trạng
- Branch `feature/us005-fee-category-ui` tạo từ `cd49be5` (US-001 backend trên nền Dev-1 `4206c79`). Không merge `feature/us012-login-ui`.
- Chưa có: bảng `fee_types`, DAO, `FeeTypeService`, DTO US-005. Không có `AuthenticationNavigator`/Dashboard (nằm ở branch Dev-2), nên chưa có chỗ điều hướng vào màn hình.
- Có sẵn: JavaFX 17 + JUnit 5 (không có TestFX; test kiểu `Platform.startup` như `LoginUiTest` của Dev-2), `Role`, `AccessDeniedException`, mẫu `HouseholdResult`.

## 2. Yêu cầu SRS (không thay đổi)
Bảng 1.1: `ma_khoan_thu` (≤20, hoa không dấu, không khoảng trắng, duy nhất), `ten_khoan_thu` (3–100), `loai_khoan_thu` (BAT_BUOC/DONG_GOP), `hinh_thuc_thu` (THEO_DIEN_TICH/THEO_NHAN_KHAU/THEO_CAN_HO/TU_NGUYEN), `so_tien_mac_dinh` (≥0, không bắt buộc), `trang_thai` (DANG_AP_DUNG/NGUNG_AP_DUNG). BR-06, BR-07, AC-01, AC-02 như readme1.

## 3. Service contract đề xuất (Dev-2 cài đặt, UI chỉ phụ thuộc interface)

Package `com.bluemoon.services` (interface) và `com.bluemoon.models` (DTO). Dev-3 chỉ commit interface + DTO + enum ở Mốc 2 nếu được duyệt; không viết impl/DAO/schema.

```java
public interface FeeTypeService {
    FeeTypeResult<List<FeeType>> listFeeTypes(FeeStatus filter);          // null = tất cả; rỗng vẫn SUCCESS
    FeeTypeResult<FeeType>       getFeeType(String maKhoanThu);
    FeeTypeResult<FeeType>       createFeeType(FeeTypeForm form);          // AC-01, AC-02
    FeeTypeResult<FeeType>       updateFeeType(String maKhoanThu, FeeTypeForm form);   // [WBS] xem câu hỏi 1
    FeeTypeResult<FeeType>       deactivateFeeType(String maKhoanThu);     // BR-07: chỉ chuyển NGUNG_AP_DUNG, không xóa
}
```

Enum (UI dùng trực tiếp làm ComboBox, `toString()` = nhãn): `FeeKind {BAT_BUOC("Bắt buộc"), DONG_GOP("Tự nguyện")}`,
`ChargeMethod {THEO_DIEN_TICH("Theo diện tích (m²)"), THEO_NHAN_KHAU("Theo nhân khẩu (người)"), THEO_CAN_HO("Theo căn hộ (cố định)"), TU_NGUYEN("Tự nguyện")}`, `FeeStatus {DANG_AP_DUNG, NGUNG_AP_DUNG}`.
Danh sách enum lấy bằng `values()`; **[ĐỀ XUẤT]** thêm `ChargeMethod.forKind(FeeKind)` trả danh sách hợp lệ (BAT_BUOC → 3 cách đầu, DONG_GOP → `[TU_NGUYEN]`) để UI và Service dùng chung một luật.

DTO vào `FeeTypeForm(maKhoanThu, tenKhoanThu, FeeKind loai, ChargeMethod hinhThuc, String soTienMacDinh, FeeStatus trangThai)`: chuỗi thô từ ô nhập, `null`/rỗng = để trống, enum `null` = chưa chọn.
DTO ra `FeeType(maKhoanThu, tenKhoanThu, loai, hinhThuc, BigDecimal soTienMacDinh, trangThai)`. **[ĐỀ XUẤT]** `BigDecimal` thay Double của SRS (giống `dien_tich` US-001, dùng cho US-006/007).

Kết quả `FeeTypeResult<T>` (cùng dạng `HouseholdResult`): `status()`, `isSuccess()`, `message()`, `data()`, `fieldErrors()`.
Status: `SUCCESS`, `VALIDATION_ERROR` (không lưu; `fieldErrors` có mọi lỗi cùng lúc), `DUPLICATE` (trùng mã; `fieldErrors["ma_khoan_thu"]`), `NOT_FOUND`, `SYSTEM_ERROR` (thông báo chung, không lộ SQL). Sai quyền → `AccessDeniedException` (như US-001).
Khóa `fieldErrors`: `ma_khoan_thu`, `ten_khoan_thu`, `loai_khoan_thu`, `hinh_thuc_thu`, `so_tien_mac_dinh`, `trang_thai`.

Thông báo lỗi dùng nguyên văn bảng SRS 1.1 (backend trả, UI hiển thị thẳng). Thông báo thành công **[ĐỀ XUẤT]**: `"Tạo khoản thu PHI_QL thành công"`.

Quyền (EP-06): ADMIN và ACCOUNTANT toàn quyền; RESIDENT bị từ chối.

## 4. Phân tách validation
| Việc | UI (Controller) | Service (Dev-2, bắt buộc độc lập) |
| --- | --- | --- |
| Thiếu mã / có khoảng trắng / không hoa không dấu / >20 | Chặn Lưu, hiện lỗi tại ô | Kiểm tra lại, trả `VALIDATION_ERROR` |
| Tên < 3 hoặc > 100 | Như trên | Như trên |
| Số tiền âm / không phải số | Như trên | Như trên |
| Chọn DONG_GOP | Tự đặt `TU_NGUYEN`, số tiền `0`, khóa 2 ô đó (BR-06, AC-02) | Chuẩn hóa/từ chối cùng luật (câu hỏi 3) |
| Chọn BAT_BUOC | ComboBox chỉ còn 3 hình thức theo `forKind`, chưa chọn thì lỗi | Từ chối `TU_NGUYEN` |
| Trùng mã | Không biết trước | `DUPLICATE`; UI hiện `fieldErrors` của Service |
| Lưu trùng khi đang xử lý | Khóa nút Lưu, một request tại một thời điểm | — |

## 5. Cấu trúc UI đề xuất (Mốc 2, sau duyệt)
- `src/main/resources/com/bluemoon/views/FeeCategoryView.fxml`: `TableView` (mã, tên, loại, hình thức, số tiền, trạng thái) + bộ lọc trạng thái; form (TextField mã/tên/số tiền, ComboBox loại/hình thức/trạng thái); nút Lưu/Hủy; Label lỗi từng ô; vùng thông báo kết quả; trạng thái loading/empty/error (WBS).
- `controllers/FeeCategoryController.java`: ctor `FeeCategoryController(FeeTypeService service)`; gọi Service trong `Task` nền, cập nhật view trên FX thread; không SQL/DAO.
- `src/main/resources/com/bluemoon/views/fee-category.css` (mới; không đụng `auth.css`, `LoginView`).
- Test `FeeCategoryControllerTest` dùng `FakeFeeTypeService` trong thư mục test, cho 8 tình huống ở mục VII.D của order. Không MySQL.
- Màn hình demo độc lập (nếu duyệt): launcher nằm trong `src/test`, gắn nhãn "DEMO — fake service", không sửa `BlueMoonApplication`.

Sẽ tạo: FXML, controller, CSS, interface/DTO/enum (mục 3), test + fake. Sẽ sửa: không file của Dev khác.

## 6. Phụ thuộc / blocker
- Dev-2: bảng `fee_types`, `FeeTypeDAO`, `FeeTypeServiceImpl` (WBS 1.2.1.2.1 + nghiệp vụ). Cần xác nhận khớp interface mục 3 trước khi Dev-2 bắt đầu.
- Dev-2 branch Login/Dashboard chưa merge vào nền của tôi → chưa có điều hướng tới màn hình; chưa thể gọi US-005 end-to-end. AC-01/02 chỉ được coi là hoàn thành khi có Service + DB thật.
- BR-07 "đã phát sinh hóa đơn" cần bảng hóa đơn (US-007, chưa tồn tại): `deactivate` hiện chỉ đổi trạng thái; **không có thao tác xóa cứng** nào trong contract.

## 7. Câu hỏi cho Lead Dev / PO
1. Iteration 02 chỉ UI tạo + danh sách (theo order) hay cả sửa/ngừng áp dụng (theo WBS 1.2.1.2.2 và hợp đồng tích hợp)? Đề xuất: giữ interface đủ 5 method, UI làm list + create trước, update/deactivate là bước sau.
2. "Hoa không dấu": UI tự `toUpperCase` khi gõ (đề xuất), Service từ chối chữ thường/dấu. Ký tự cho phép **[ĐỀ XUẤT]** `A–Z 0–9 _` (SRS ví dụ có `_` và số `QUY_NGHEO_2026`; chữ số không được nêu rõ).
3. DONG_GOP: Service tự chuẩn hóa `TU_NGUYEN` khi UI gửi khác, hay từ chối? Số tiền mặc định DONG_GOP > 0 có cho phép không (SRS: "mặc định là 0")? Đề xuất: chuẩn hóa hình thức; giữ số tiền người dùng nhập nếu ≥0, trống = 0.
4. BAT_BUOC để trống số tiền: lưu 0 (đề xuất, US-006 sẽ đặt giá) hay bắt buộc nhập?
5. `trang_thai` khi tạo mới: mặc định `DANG_AP_DUNG` trong form (SRS ghi bắt buộc nhưng AC không nhập).
6. `ma_khoan_thu` không sửa được sau khi tạo (là khóa, US-006 tham chiếu).
7. Ai sở hữu file interface/DTO: Dev-3 commit (đề xuất) rồi Dev-2 implement, hay Dev-2 tạo?
8. Cách vào màn hình khi chưa có dashboard (launcher demo trong test hay chờ merge Dev-2?).

## 8. Quyết định của Lead Dev (readme2.md) — đã áp dụng vào implementation
1. UI Iteration 02 chỉ dùng `listFeeTypes` và `createFeeType`; `getFeeType`, `updateFeeType`, `deactivateFeeType` chỉ khai báo trong interface.
2. Mã khoản thu: bắt buộc, ≤ 20, regex `[A-Z0-9_]+`, UI tự viết hoa khi gõ, Service vẫn kiểm tra độc lập, bất biến sau khi tạo.
3. DONG_GOP: hình thức luôn `TU_NGUYEN`, số tiền mặc định luôn 0, form khóa hai ô này (không cho nhập khác 0).
4. BAT_BUOC để trống số tiền: UI gửi `0` (chưa cấu hình đơn giá, không phải mức phí chính thức).
5. Trạng thái mặc định `DANG_AP_DUNG`; `NGUNG_AP_DUNG` có trong enum và bộ lọc; chưa có thao tác ngừng áp dụng ở UI.
6. Không có thao tác xóa cứng (BR-07); không phụ thuộc bảng hóa đơn US-007.
7. Phân quyền: ADMIN, ACCOUNTANT; RESIDENT bị từ chối; UI xử lý `AccessDeniedException`, kiểm tra thật ở Service (Dev-2).
8. Dev-3 sở hữu interface/DTO/enum; Dev-2 cài DAO, bảng `fee_types`, `FeeTypeServiceImpl` (xem `us-005-serviceimpl-guide.md`).
