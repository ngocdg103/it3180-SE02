# US-005 — Hướng dẫn Dev-2 cài `FeeTypeServiceImpl` tương thích UI

Dev-3 sở hữu interface/DTO/enum; Dev-2 sở hữu bảng `fee_types`, `FeeTypeDAO`, `FeeTypeServiceImpl`. **Không đổi tên method, khóa `fieldErrors` hay enum.** Cần đổi thì báo Lead Dev.

## File dùng chung (đã có trong branch `feature/us005-fee-category-ui`)
`services/FeeTypeService`, `services/FeeTypeResult`, `models/FeeType`, `FeeTypeForm`, `FeeKind`, `ChargeMethod` (có `forKind`), `FeeStatus`.

## Hành vi UI mong đợi từ Service
| Tình huống | Service trả | UI làm |
| --- | --- | --- |
| Tạo thành công | `success("Tạo khoản thu <MA> thành công", FeeType)` | Hiện `message()`, xóa form, nạp lại danh sách |
| Sai dữ liệu | `VALIDATION_ERROR` + `fieldErrors` (mọi lỗi cùng lúc) | Hiện lỗi dưới từng ô, **giữ nội dung đã nhập** |
| Trùng mã | `DUPLICATE` + `fieldErrors["ma_khoan_thu"]` | Như trên |
| Lỗi DB | `SYSTEM_ERROR`, message chung | UI chỉ hiện câu chung, không hiện `message()` |
| Sai quyền / chưa đăng nhập | **ném** `AccessDeniedException` (không trả result) | Khóa form, hiện "Bạn không có quyền…" |
| Lỗi bất ngờ (RuntimeException khác) | — | UI hiện câu chung, không hiện nội dung exception |
| `listFeeTypes` | `success` (danh sách có thể rỗng) | Rỗng → "Chưa có khoản thu nào."; failure → trạng thái lỗi + nút "Tải lại" |

`listFeeTypes(null)` = tất cả; UI gửi `DANG_AP_DUNG`/`NGUNG_AP_DUNG` khi lọc. Nên sắp theo `ma_khoan_thu`.

## Quy tắc Service phải tự kiểm tra (UI chỉ hỗ trợ)
- `ma_khoan_thu`: bắt buộc, ≤ 20, regex `[A-Z0-9_]+`, duy nhất, bất biến. Thông báo SRS: "Mã khoản thu không được để trống, không chứa khoảng trắng và không được trùng!"
- `ten_khoan_thu`: 3–100 ký tự (sau trim). "Tên khoản thu phải từ 3 đến 100 ký tự!"
- `loai_khoan_thu` null → "Vui lòng chọn loại khoản thu!"
- `hinh_thuc_thu` phải thuộc `ChargeMethod.forKind(loai)` (BR-06). Null/sai → "Vui lòng chọn hình thức tính phí!"
- `so_tien_mac_dinh`: chuỗi số thập phân dấu chấm, ≥ 0. "Số tiền mặc định phải lớn hơn hoặc bằng 0!" DONG_GOP: bắt buộc bằng 0 (quyết định Lead Dev; UI luôn gửi `"0"`).
- `trang_thai` null → "Vui lòng chọn trạng thái!" (UI mặc định `DANG_AP_DUNG`).
- Quyền (EP-06): ADMIN, ACCOUNTANT được; RESIDENT/chưa đăng nhập → `AccessDeniedException`.
- BR-07: không có thao tác xóa cứng; `deactivateFeeType` chỉ đặt `NGUNG_AP_DUNG`.

## Khung cài đặt
```java
public final class FeeTypeServiceImpl implements FeeTypeService {
    public FeeTypeServiceImpl(FeeTypeRepository repo, Supplier<Role> currentRole) { ... }
    @Override public FeeTypeResult<FeeType> createFeeType(FeeTypeForm form) {
        requireManager();                       // AccessDeniedException
        Map<String,String> errors = validate(form);   // dùng ChargeMethod.forKind
        if (!errors.isEmpty()) return FeeTypeResult.failure(VALIDATION_ERROR, "Dữ liệu không hợp lệ", errors);
        try { repo.insert(...); } catch (DuplicateRecordException e) { return duplicate result with ma_khoan_thu error; }
        catch (SQLException e) { return SYSTEM_ERROR generic; }
        return FeeTypeResult.success("Tạo khoản thu " + code + " thành công", saved);
    }
}
```
Mẫu hoàn chỉnh cùng phong cách: `HouseholdServiceImpl` + `HouseholdDAO` (US-001). Dùng lại `DatabaseConnection`, PreparedStatement, transaction khi cần.

## Dùng UI
`Parent view = FeeCategoryController.load(feeTypeService);` — gọi trên FX thread; Service luôn chạy trên thread nền. Chưa có mục điều hướng: chờ dashboard của Dev-2 gắn vào.

Demo dữ liệu giả (không phải backend): `src/test/java/com/bluemoon/controllers/FeeCategoryDemo`.
