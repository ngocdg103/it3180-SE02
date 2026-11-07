# US-002 — API contract (Dev-3 → Dev-2, Lead Dev)

Thêm nhân khẩu vào hộ. Nguồn: `docs/srs/EP01_QuanLyCuDan.md` §2, `docs/design/us-002-contract-review.md` (Final Technical Corrections) và quyết định PO đã được Lead Dev duyệt.

## 1. Service (`com.bluemoon.services.ResidentService`)
```java
ResidentResult<Resident> createForHousehold(ResidentForm form, Long confirmReplaceHeadId);
ResidentResult<HouseholdMembers> getHousehold(String maHo);
```
- Chạy ngoài JavaFX thread (JDBC chặn).
- Phân quyền qua `AuthorizationService` (kiểm **trước** mọi truy vấn); vi phạm → `AccessDeniedException`:

| Method | Permission | ADMIN | ACCOUNTANT | RESIDENT | Chưa đăng nhập |
|---|---|---|---|---|---|
| `createForHousehold` | `RESIDENT_PROFILE_CREATE` | cho phép | từ chối | từ chối | từ chối |
| `getHousehold` | `RESIDENT_PROFILE_VIEW` | cho phép | cho phép | từ chối | từ chối |

Production: `new ResidentServiceImpl(new ResidentDAO(connection), new HouseholdDAO(connection), authorizationService)`.
Không có DB: `new ResidentServiceImpl(new InMemoryResidentRepository(householdRepo), householdRepo, authorizationService)`.

## 2. DTO
- `ResidentForm(maHo, hoTen, ngaySinh, gioiTinh, soCccd, quanHeChuHo, ngayChuyenDen)`: chuỗi thô (`ngay*` = `YYYY-MM-DD`), `gioiTinh`/`quanHeChuHo` là enum (null = chưa chọn). `soCccd` trống = không có.
- `Resident(maNhanKhau, maHo, hoTen, ngaySinh, gioiTinh, soCccd, quanHeChuHo, ngayChuyenDen)`.
- `HouseholdMembers(maHo, members)`: `memberCount()`, `head()`; chủ hộ đứng đầu danh sách. Hộ chưa có nhân khẩu là kết quả thành công với danh sách rỗng.
- Enum `Gender {NAM, NU}`, `RelationToHead {CHU_HO, VO, CHONG, CON, BO_ME, NGUOI_THUE, KHAC}` (có `label()` tiếng Việt và `parse`).

## 3. Kết quả (`ResidentResult`)
`status()` ∈ `SUCCESS, VALIDATION_ERROR, DUPLICATE, NOT_FOUND, CONFIRMATION_REQUIRED, SYSTEM_ERROR`; `message()`; `data()`; `fieldErrors()` (khóa: `ma_ho, ho_ten, ngay_sinh, gioi_tinh, so_cccd, quan_he_chu_ho, ngay_chuyen_den`).
Với `CONFIRMATION_REQUIRED`: `confirmationReason()` ∈ `HEAD_EXISTS | CONFIRMATION_STALE` và `currentHead()`.

## 4. Luồng đổi chủ hộ (BR-04, AC-03) — UI phải làm đúng
1. Gọi `createForHousehold(form, null)`.
2. Nhận `CONFIRMATION_REQUIRED` + `HEAD_EXISTS`: hiện hộp thoại với `message()` (đã có tên chủ hộ). **Từ chối** → không gọi gì thêm (không có gì được lưu).
3. **Đồng ý** → gọi lại **cùng form** với `confirmReplaceHeadId = currentHead().maNhanKhau()`. Chủ hộ cũ chuyển sang `KHAC`.
4. Nếu nhận `CONFIRMATION_STALE`: chủ hộ đã đổi (người khác, hoặc hộ không còn chủ hộ); **không ghi gì**. Hiện `message()` (và `currentHead()` nếu có), để người dùng xem lại rồi gửi lại từ bước 1. Không dùng lại id cũ.
5. `confirmReplaceHeadId` bị bỏ qua nếu quan hệ trong form không phải `CHU_HO`.

## 5. Quy tắc nghiệp vụ (đã duyệt)
- Hộ chưa có nhân khẩu: người đầu tiên bắt buộc `CHU_HO` (lỗi ở `quan_he_chu_ho`).
- Căn hộ `Căn hộ trống`: không thêm nhân khẩu (lỗi ở `ma_ho`); trạng thái căn hộ không bị thay đổi.
- Tuổi ≥ 14 tính tại ngày nhập: bắt buộc CCCD. Dưới 14 có thể để trống; nếu có nhập thì kiểm định dạng và trùng.
- CCCD: đúng 9 hoặc 12 chữ số, duy nhất toàn hệ thống.
- Người không có CCCD: trùng nếu cùng hộ + `ho_ten` (chuẩn hóa, không phân biệt hoa/thường, phân biệt dấu) + `ngay_sinh` + `gioi_tinh` → `DUPLICATE` ở `ho_ten`.
- `ngay_sinh` < hôm nay; `ngay_chuyen_den` ≤ hôm nay và ≥ `ngay_sinh`; không ràng buộc với ngày nhận nhà.
- Họ tên 2–50 ký tự sau chuẩn hóa (NFC, gộp khoảng trắng), chỉ chữ cái và khoảng trắng.
- Chỉ thêm và xem nhân khẩu. Không có xóa/chuyển đi/sửa.
- Thông báo ngoài SRS (do Dev-3 đề xuất, chờ duyệt wording): căn hộ trống, người đầu tiên phải là chủ hộ, trùng người không CCCD, xác nhận cũ.

## 6. Dữ liệu và schema
- `database/migrations/us002_residents.sql` (áp sau `us001_household_apartment.sql`, chạy lặp an toàn), `us002_seed.sql`. Không sửa `schema.sql` hay bảng US-001.
- Seed: HK-1002 (có chủ hộ + vợ + con < 14), HK-0101 (chủ hộ), HK-0508 (người thuê), **HK-1502 trống** (demo người đầu tiên = chủ hộ), HK-1204/HK-3015 (căn hộ trống → bị từ chối).
- `ResidentRepository.countByMaHo(maHo)` cho US-007 (BR-05): Sprint 1 đếm mọi bản ghi của hộ vì chưa có chuyển đi.

## 7. Giới hạn / việc tích hợp riêng
- **Q17 (ngoài phạm vi US-002):** US-001 `updateHousehold` có thể đổi trạng thái sang `Căn hộ trống` khi hộ đã có nhân khẩu. Chưa sửa US-001. Cần task tích hợp riêng cho Lead Dev/PO, dùng `ResidentRepository.countByMaHo`.
- `HouseholdMySqlIT` được thêm một dòng `DROP TABLE IF EXISTS residents` vì khóa ngoại mới chặn việc xóa `households` (chỉ test, không đổi hành vi US-001).
