# US-007 — Lập kỳ thu và tính phí tự động: Contract Review & Technical Design (Mốc 1) — Dev-3 → Lead Dev

Trạng thái: **chỉ thiết kế**. Chưa có production code, chưa có migration thật (SQL bên dưới là *đề xuất*), chưa sửa API có sẵn. **Revision 03 (peer review PR #11):** chốt `FeeRateLookup` là `Optional<AppliedRate> getEffectiveRate(String, LocalDate)` cho khớp code; bổ sung invariant `ChargeLine` (số nhân khẩu nguyên) và `MonthlyCharge` (trạng thái khớp số tiền) ở mục E.2. Không đổi Q1–Q18. **Revision 02 (sau PO phê duyệt Q1–Q18; main 162d9ea):** Q11 đổi thành *chỉ cho lập kỳ = tháng hiện tại* (không quá khứ, không tương lai), inject `Clock`; Q6 lưu `DECIMAL(19,2)` nhưng làm tròn HALF_UP về 1 VNĐ trước khi lưu; tách mã MySQL 1062 / 1213 / 1205; tách `FeeRateLookup` (contract công khai) khỏi việc load đơn giá cùng connection trong `BillingTx`; phụ thuộc US-006: **Dev-2 Schema/DAO, Dev-1 Service/UI**; snapshot bất biến ở application layer, DB chưa khóa UPDATE trực tiếp.
Quy ước: **[SRS]** = có trong tài liệu; **[WBS]** = Sprint 1 WBS; **[CODE]** = đã có trên `origin/main` (0385182); **[ĐỀ XUẤT]** = Dev-3 đề xuất, chờ duyệt; **[PO?]** = cần PO quyết định (mục G, có mặc định).
Nguồn đã đọc: `docs/srs/EP03` §1–3, `EP01` §1–2 (BR-01..05), `EP06` §2 (RBAC), `docs/design/us-005-backend-handoff.md`, `us-002-contract-review.md`, `FeeTypeService/FeeTypeDAO`, `ResidentRepository.countByMaHo`, `HouseholdRepository`, `ApartmentChargeLookup`, `RoleBasedAuthorizationService`, `SessionContext`, migrations us001/us002/us005, và các chuỗi mô tả US-006/US-007 trong `SE_02_Implementation_WBS.xlsx` (chỉ trích được text; không đọc được cấu trúc hàng/cột/assignee).

---
## A. Tóm tắt thiết kế

1. **Fee Type** (US-005, bảng `fee_types`) chỉ là *danh mục*. **Monthly Charge** là *kết quả tính* cho **một hộ trong một kỳ**, lưu thành snapshot bất biến gồm: đơn giá + ngày hiệu lực của giá, diện tích, số nhân khẩu, tên/hình thức của khoản thu tại thời điểm chốt.
2. Ba bảng mới: `billing_periods` (1 dòng/kỳ, khóa chống trùng), `invoices` (= `hoa_don_ky` [SRS], 1 dòng/hộ/kỳ), `invoice_lines` (= `chi_tiet_hoa_don` [SRS], 1 dòng/khoản thu/hóa đơn).
3. Kỳ đã chốt **không bao giờ được tính lại** từ dữ liệu hiện tại: không có method update/delete cho `billing_periods`/`invoice_lines`; `invoices` chỉ cho US-008 cập nhật các cột thanh toán.
4. `previewPeriod` chỉ đọc, không ghi. `generatePeriod` chạy **một transaction duy nhất** (all-or-nothing); chống tạo trùng bằng `UNIQUE/PRIMARY KEY (ky_thu)` của DB, không bằng kiểm tra "SELECT rồi INSERT".
5. Engine **data-driven**: tính theo mọi `fee_types` loại `BAT_BUOC`, `DANG_AP_DUNG`, theo `phuong_thuc_tinh` (diện tích / nhân khẩu / cố định) với đơn giá hiệu lực từ US-006. AC-01 (P1204 → 1.384.000) là một trường hợp của engine này.
6. Tiền dùng `BigDecimal`; làm tròn HALF_UP về 1 VNĐ **theo từng dòng** *trước khi lưu*, tổng = tổng các dòng đã làm tròn; cột lưu `DECIMAL(19,2)` (phần thập phân luôn `.00`, DB có CHECK).
6a. **Kỳ thu chỉ được lập cho tháng hiện tại** (Q11). `Clock` được inject vào `BillingServiceImpl` để test xác định (deterministic); production dùng `Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"))`.
7. **Nút thắt lớn nhất: US-006 (bảng đơn giá theo thời điểm) chưa có trên main** — xem D-1 và mục I.

## B. Mapping SRS → business rule → service API

| SRS | Quy tắc thiết kế [ĐỀ XUẤT] | API |
|---|---|---|
| EP03 §3.2 công thức DV/QL/VS | `thanh_tien = round(so_luong × don_gia)`; `so_luong` = diện tích (THEO_DIEN_TICH) / số nhân khẩu (THEO_NHAN_KHAU) / 1 (THEO_CAN_HO) | `previewPeriod`, `generatePeriod` |
| AC-01 P1204 | 80 m², 4 người, 10.000/7.000/6.000 → 800.000 + 560.000 + 24.000 = **1.384.000**, `CHUA_DONG`, `da_thanh_toan = 0`, `con_thieu = 1.384.000` | test bắt buộc (E.1) |
| AC-02 chặn trùng | `ky_thu` là khóa duy nhất; lần 2 → `DUPLICATE`, `fieldErrors["ky_thu"] = "Kỳ thu 2026-11 đã tồn tại trên hệ thống!"`, không ghi gì | `generatePeriod` |
| BR-10 căn hộ trống | số nhân khẩu = 0 → dòng VS có `so_luong = 0`, `thanh_tien = 0` (vẫn lưu dòng để truy vết); QL/DV vẫn tính đủ | calculator |
| BR-11 thiếu diện tích | bỏ qua riêng hộ đó, ghi vào `skipped` kèm thông báo "cập nhật hồ sơ căn hộ"; các hộ khác vẫn lập | calculator |
| EP03 US-006 BR-09 | đơn giá = bản ghi có `ngay_ap_dung` lớn nhất ≤ **ngày đầu kỳ** | `FeeRateLookup` |
| EP03 §3.1 `han_nop` | mặc định ngày 15 tháng kế tiếp (kỳ 2026-12 → 2027-01-15) | `generatePeriod` |
| EP03 §3.1 `ma_hoa_don` | `HD-` + `YYYYMM` + `so_can_ho` (VD `HD-202611-P1204`, ≤ 30 ký tự) | `generatePeriod` |
| EP01 BR-02 | `ApartmentChargeLookup.hasCharges(soCanHo)` = `EXISTS invoices` (hook đã có, hiện mặc định `NONE`) | wiring Mốc 2 (cần Lead duyệt, F.4) |
| EP01 BR-05 | số nhân khẩu = số bản ghi `residents` của hộ có `ngay_chuyen_den ≤ ngày cuối kỳ` | `BillingTx.loadInputs` |
| EP06 §2.1 | ADMIN + ACCOUNTANT "toàn quyền" lập kỳ; RESIDENT không có quyền | RBAC (mục F) |
| EP04 BR-16 | thông báo chỉ phát hành khi kỳ đã tính xong → EP04 dùng `getPeriod(kyThu).isPresent` | `getPeriod` |
| WBS "không gộp khoản tự nguyện" | `DONG_GOP` **không** nằm trong kỳ thu US-007 | calculator |

## C. Phân tích nghiệp vụ tính phí

### C.1 Fee Type vs Monthly Charge
| | Fee Type (`fee_types`) | Monthly Charge (`invoices` + `invoice_lines`) |
|---|---|---|
| Ý nghĩa | danh mục khoản thu, đổi được trạng thái | khoản phải thu của **một hộ, một kỳ** |
| Vòng đời | sống lâu, `NGUNG_AP_DUNG` chứ không xóa (BR-07) | bất biến sau khi chốt (trừ cột thanh toán của US-008) |
| `so_tien_mac_dinh` | **không phải đơn giá chính thức** [CODE: comment `FeeType`] → US-007 *không* đọc cột này | không có |
| Đơn giá | thuộc US-006 (`fee_rates`) | snapshot `don_gia` + `ngay_ap_dung_gia` trong từng dòng |

### C.2 Quy tắc từng trường hợp
1. **Cố định theo hộ (`THEO_CAN_HO`)**: `so_luong = 1`, `thanh_tien = round(don_gia)`. Đơn giá lấy từ US-006 như mọi khoản bắt buộc (vì US-006 chấp nhận "danh mục khoản thu bắt buộc còn hiệu lực"). *SRS không nêu khoản cố định cụ thể nào; BR-08 chỉ có 3 giá chuẩn → xem C.1/Q12.*
2. **Theo diện tích**: `so_luong = apartments.dien_tich` (snapshot DECIMAL(8,2)); `thanh_tien = round(dien_tich × don_gia)`.
3. **Theo nhân khẩu**: `so_luong` = số nhân khẩu như B/BR-05; `0` hợp lệ (BR-10).
4. **Khoản ngừng áp dụng**: chỉ khoản `DANG_AP_DUNG` tại thời điểm chốt được đưa vào kỳ **mới**. Kỳ đã chốt giữ nguyên dòng cũ (snapshot có tên/hình thức/giá nên không phụ thuộc trạng thái hiện tại). Vì chỉ lập kỳ tháng hiện tại (Q11), trạng thái `fee_types` tại lúc bấm "Lập kỳ" chính là trạng thái của kỳ; `fee_types` không có ngày ngừng áp dụng nên **không** hỗ trợ dựng lại kỳ lịch sử.
5. **Thiếu diện tích / thiếu dữ liệu hộ**: schema hiện tại **không cho phép** (`dien_tich NOT NULL CHECK > 0`; `households.so_can_ho` FK). BR-11 là *phòng thủ*: nếu gặp (dữ liệu hỏng/nhập tay) → bỏ qua hộ đó + cảnh báo trong kết quả, không làm hỏng cả kỳ (đúng SRS). **Thiếu đơn giá hiệu lực của khoản đang áp dụng** là lỗi *cấu hình toàn kỳ* → `MISSING_RATE`, không ghi gì (bỏ khoản đó âm thầm sẽ thu thiếu cho cả tòa nhà) [Q7].
6. **Số tiền = 0**: dòng 0 đồng (VS của hộ trống) vẫn lưu. Hóa đơn có `tong_tien = 0` (khi mọi dòng bằng 0) không lập, đưa vào `skipped` với lý do `ZERO_TOTAL`. Thực tế gần như không xảy ra vì `don_gia > 0` và `dien_tich > 0`.
7. **Khoản tự nguyện (`DONG_GOP`)**: không tự sinh dòng nào trong US-007. SRS có câu "+ Khoản đóng góp (nếu đã đăng ký)" nhưng không có cơ chế "đăng ký" nào trong schema/US, và US-005 BR-06 nói "không ép buộc, chỉ ghi nhận theo thực tế hộ đóng góp" → mâu thuẫn, chọn theo WBS (không gộp). Tiền đóng góp thực tế thuộc US-008.
8. **Hộ được tính**: hộ có `households.ngay_nhan_nha ≤ ngày cuối kỳ`, mọi trạng thái căn hộ (`DANG_O`, `CHO_THUE`, `CAN_HO_TRONG`) vì BR-10/BR-03 vẫn thu QL+DV. Nếu căn hộ `CAN_HO_TRONG` nhưng vẫn có nhân khẩu (dữ liệu chưa nhất quán, xem Q17 của US-002) → **đếm theo dữ liệu thực** và thêm cảnh báo `VACANT_WITH_RESIDENTS`.
9. **Làm tròn (Q6)**: `BigDecimal.setScale(0, HALF_UP)` mỗi dòng **trước khi lưu**, rồi lưu vào cột `DECIMAL(19,2)` (giá trị dạng `1384000.00`); `tong_tien = Σ thanh_tien`. Không bao giờ dùng `double`.

### C.3 Quy tắc snapshot (không tính lại kỳ đã chốt)
- Lưu trong `invoices`: `dien_tich`, `so_nhan_khau`, `ma_ho`, `so_can_ho`, `han_nop`.
- Lưu trong `invoice_lines`: `ma_loai_thu`, `ten_loai_thu`, `phuong_thuc_tinh`, `so_luong`, `don_gia`, `ngay_ap_dung_gia`, `thanh_tien`.
- Đổi diện tích (US-001), thêm nhân khẩu (US-002), đổi giá (US-006) **sau** khi chốt → không đổi hóa đơn cũ. `getPeriod/getInvoice` chỉ đọc bảng snapshot, không join `apartments/residents/fee_rates` để lấy số liệu tiền.
- **Bất biến ở application layer**: chỉ bảo đảm vì repository không có update/delete cho `billing_periods`/`invoice_lines`. **DB chưa khóa UPDATE trực tiếp** (không trigger/không tách quyền); ai ghi được vào DB vẫn sửa được snapshot (R2).
- `previewPeriod` cho kỳ **đã tồn tại** trả snapshot đã lưu (`alreadyGenerated = true`), không tính lại từ dữ liệu sống.

## D. Dependencies

| # | Phụ thuộc | Trạng thái | Việc cần |
|---|---|---|---|
| D-1 | **US-006** `fee_rates` + `FeeRateLookup` | **Chưa có trên main.** Phân công: **Dev-2 làm Schema/DAO** (`fee_rates` + truy vấn đơn giá hiệu lực); **Dev-1 làm Service/UI** (service công khai + `FeeRateLookup`) | Chốt schema tối thiểu: `fee_rates(ma_loai_thu, don_gia DECIMAL(19,2) > 0, ngay_ap_dung DATE, UNIQUE(ma_loai_thu, ngay_ap_dung))`; Dev-3 (US-007) *không* sửa US-006. Mốc 2a chỉ cần interface `FeeRateLookup`; Mốc 2b cần schema+DAO của Dev-2 để load đơn giá cùng connection (xem F.2) |
| D-2 | US-005 `fee_types` | Có (`FeeTypeDAO`) | US-007 đọc `fee_types` trực tiếp trong transaction (không gọi `FeeTypeService` vì cần cùng connection/snapshot); FK từ `invoice_lines` |
| D-3 | US-001 `apartments/households` | Có | đọc `dien_tich`, `ngay_nhan_nha`; FK; triển khai `ApartmentChargeLookup` |
| D-4 | US-002 `residents` | Có | `countByMaHo` hiện **không lọc ngày** và mở connection riêng → US-007 dùng SQL đếm riêng trong transaction (có test đối chiếu với `countByMaHo`). Không sửa `ResidentRepository` |
| D-5 | US-018 RBAC | Có | dùng `BILLING_PERIOD_CREATE`, `DEBT_REPORT_VIEW` có sẵn; không thêm Permission |
| D-6 | `users.id` | Có | `billing_periods.created_by` FK |
| D-7 | US-008/US-009/EP04 (hạ nguồn) | Chưa | US-008 ghi `da_thanh_toan`, đổi `trang_thai`; US-009 đọc `invoices` |

## E. Data model

```
fee_types(ma_loai_thu PK) <-- FK --+
households(ma_ho PK)  <-- FK --+   |
apartments(so_can_ho PK) <-FK-+|   |
users(id PK) <--created_by-+  ||   |
billing_periods(ky_thu PK) 1 --- n invoices(ma_hoa_don PK; UNIQUE(ky_thu,so_can_ho)) 1 --- n invoice_lines(PK ma_hoa_don+ma_loai_thu)
```

### E.1 Proposed migration `database/migrations/us007_billing.sql` (CHƯA tạo file) [ĐỀ XUẤT]
Độc lập như us001/us002/us005; MySQL 8.0.16+; áp dụng sau us001, us002, us005 và schema users. Số tiền VNĐ là `DECIMAL(19,2)` (Q6); service làm tròn HALF_UP về 1 VNĐ trước khi lưu và DB có CHECK `col = ROUND(col, 0)` để chặn giá trị lẻ.

```sql
CREATE TABLE IF NOT EXISTS billing_periods (
    ky_thu       CHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    han_nop      DATE NOT NULL,
    trang_thai   ENUM('DA_PHAT_HANH') NOT NULL DEFAULT 'DA_PHAT_HANH',
    so_hoa_don   INT NOT NULL DEFAULT 0,
    so_ho_bo_qua INT NOT NULL DEFAULT 0,
    tong_tien    DECIMAL(19,2) NOT NULL DEFAULT 0,
    created_by   BIGINT NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (ky_thu),
    CONSTRAINT ck_bp_ky CHECK (REGEXP_LIKE(ky_thu, '^[0-9]{4}-(0[1-9]|1[0-2])$', 'c')),
    CONSTRAINT ck_bp_counts CHECK (so_hoa_don >= 0 AND so_ho_bo_qua >= 0 AND tong_tien >= 0 AND tong_tien = ROUND(tong_tien, 0)),
    CONSTRAINT fk_bp_user FOREIGN KEY (created_by) REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invoices (
    ma_hoa_don    VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ky_thu        CHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ma_ho         VARCHAR(20) NOT NULL,
    so_can_ho     VARCHAR(10) NOT NULL,
    dien_tich     DECIMAL(8,2) NOT NULL,      -- snapshot
    so_nhan_khau  INT NOT NULL,               -- snapshot
    tong_tien     DECIMAL(19,2) NOT NULL,
    da_thanh_toan DECIMAL(19,2) NOT NULL DEFAULT 0,   -- US-008 owns writes
    con_thieu     DECIMAL(19,2) GENERATED ALWAYS AS (tong_tien - da_thanh_toan) STORED,
    trang_thai    ENUM('CHUA_DONG','DONG_THIEU','DA_DONG_DU','DONG_THUA') NOT NULL DEFAULT 'CHUA_DONG',
    han_nop       DATE NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (ma_hoa_don),
    CONSTRAINT uq_invoice_period_apartment UNIQUE (ky_thu, so_can_ho),
    CONSTRAINT ck_inv_amounts CHECK (tong_tien >= 0 AND da_thanh_toan >= 0 AND dien_tich > 0 AND so_nhan_khau >= 0
        AND tong_tien = ROUND(tong_tien, 0)),   -- da_thanh_toan: US-008 tự quyết quy tắc làm tròn của nó
    CONSTRAINT fk_inv_period FOREIGN KEY (ky_thu) REFERENCES billing_periods (ky_thu) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_inv_household FOREIGN KEY (ma_ho) REFERENCES households (ma_ho) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_inv_apartment FOREIGN KEY (so_can_ho) REFERENCES apartments (so_can_ho) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_inv_period_status (ky_thu, trang_thai)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invoice_lines (
    ma_hoa_don       VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ma_loai_thu      VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ten_loai_thu     VARCHAR(100) NOT NULL,   -- snapshot
    phuong_thuc_tinh ENUM('THEO_DIEN_TICH','THEO_NHAN_KHAU','THEO_CAN_HO') NOT NULL,  -- snapshot
    so_luong         DECIMAL(10,2) NOT NULL,  -- m2 / nguoi / 1
    don_gia          DECIMAL(19,2) NOT NULL,  -- snapshot
    ngay_ap_dung_gia DATE NOT NULL,           -- hieu luc cua don gia da dung
    thanh_tien       DECIMAL(19,2) NOT NULL,  -- đã làm tròn HALF_UP về 1 VNĐ trước khi lưu
    PRIMARY KEY (ma_hoa_don, ma_loai_thu),
    CONSTRAINT ck_line_amounts CHECK (so_luong >= 0 AND don_gia > 0 AND thanh_tien >= 0 AND thanh_tien = ROUND(thanh_tien, 0)),
    CONSTRAINT fk_line_invoice FOREIGN KEY (ma_hoa_don) REFERENCES invoices (ma_hoa_don) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_line_fee FOREIGN KEY (ma_loai_thu) REFERENCES fee_types (ma_loai_thu) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
Ghi chú: FK `ON DELETE RESTRICT` ở mọi nơi → bảo đảm BR-07 (khoản thu đã có hóa đơn không xóa cứng) và BR-02. Bảng/cột chữ Anh–Việt theo quy ước `fee_types`; **tên bảng khác SRS** (`hoa_don_ky`, `chi_tiet_hoa_don`) và khác WBS (`BillingPeriod`, `Charge`) — mapping nằm ở đây, Q13. Enum/FK collation `ascii_bin` khớp `fee_types`. Chưa có trigger chống UPDATE `invoice_lines` (hoãn, mục H); Sprint 1 bảo vệ bằng API repository không có update/delete.

### E.2 Java model (package `com.bluemoon.models`, record bất biến như US-001/002) [ĐỀ XUẤT]
- `BillingPeriod(kyThu, hanNop, soHoaDon, soHoBoQua, tongTien, createdAt)`.
- `MonthlyCharge(maHoaDon, kyThu, maHo, soCanHo, dienTich, soNhanKhau, tongTien, daThanhToan, conThieu, trangThai, hanNop, List<ChargeLine> lines)`.
- `ChargeLine(maKhoanThu, tenKhoanThu, hinhThuc, soLuong, donGia, ngayApDungGia, thanhTien)` (= BillingDetail).
- `ChargeStatus { CHUA_DONG, DONG_THIEU, DA_DONG_DU, DONG_THUA }`.
- **Invariant của model (Revision 03, theo peer review):**
  - `ChargeLine`: `THEO_NHAN_KHAU` bắt buộc `soLuong` là số nguyên không âm (0, 1, 2, 2.00 hợp lệ; 0.5, 1.5, số âm bị từ chối). `THEO_DIEN_TICH` vẫn cho phép số thập phân; `THEO_CAN_HO` bắt buộc `soLuong = 1`.
  - `MonthlyCharge` (theo BR-13 và định nghĩa enum): `CHUA_DONG` ⇒ `daThanhToan = 0`; `DONG_THIEU` ⇒ `daThanhToan > 0` và `conThieu > 0`; `DA_DONG_DU` ⇒ `conThieu = 0`; `DONG_THUA` ⇒ `conThieu < 0`. Áp dụng cho cả header-only và hóa đơn đầy đủ. Không thêm trạng thái mới.
- `BillingPreview(kyThu, hanNop, alreadyGenerated, List<MonthlyCharge> charges, List<SkippedHousehold> skipped, List<AppliedRate> rates, tongTien)`; `SkippedHousehold(soCanHo, reason, message)` với `reason ∈ {MISSING_AREA, ZERO_TOTAL}`; `AppliedRate(maKhoanThu, donGia, ngayApDung)`; `Warning` (VD `VACANT_WITH_RESIDENTS`).

## F. Service / Repository contract [ĐỀ XUẤT]

### F.1 `BillingService` (package `com.bluemoon.services`; khớp tên WBS `previewPeriod/generatePeriod`)
| Method | Input | Output | Quyền |
|---|---|---|---|
| `BillingResult<BillingPreview> previewPeriod(String kyThu)` | `YYYY-MM` | SUCCESS + preview (không ghi DB) | `BILLING_PERIOD_CREATE` |
| `BillingResult<BillingPeriod> generatePeriod(String kyThu)` | `YYYY-MM`; `han_nop` luôn mặc định ngày 15 tháng sau (Q17) | SUCCESS + kỳ vừa chốt | `BILLING_PERIOD_CREATE` |
| `BillingResult<List<BillingPeriod>> listPeriods()` | — | mới nhất trước | `DEBT_REPORT_VIEW` |
| `BillingResult<BillingPeriodDetail> getPeriod(String kyThu)` | `YYYY-MM` | kỳ + danh sách `MonthlyCharge` (không kèm dòng) | `DEBT_REPORT_VIEW` |
| `BillingResult<MonthlyCharge> getInvoice(String maHoaDon)` | mã hóa đơn | hóa đơn + `lines` | `DEBT_REPORT_VIEW` |

Quy ước giống US-005: `AccessDeniedException` (không phải Result) khi thiếu quyền/không session; **kiểm tra quyền trước validate và trước repository**; Result bất biến với `status/message/data/fieldErrors`; thông báo SYSTEM_ERROR chung, không lộ chi tiết SQL. Gọi JDBC → không chạy trên JavaFX thread.

`BillingResult.Status`: `SUCCESS`, `VALIDATION_ERROR` (`ky_thu` null/sai định dạng/tháng không tồn tại, **hoặc khác tháng hiện tại theo `Clock`** — cả quá khứ lẫn tương lai đều bị từ chối [Q11]; áp dụng cho **cả** `previewPeriod` và `generatePeriod`; xem kỳ cũ bằng `getPeriod/getInvoice`), `DUPLICATE` (kỳ đã tồn tại), `MISSING_RATE` (liệt kê mã khoản thu thiếu giá), `NOTHING_TO_BILL` (không có khoản `BAT_BUOC` đang áp dụng hoặc không có hộ nào), `NOT_FOUND`, `BUSY` (lock wait timeout/deadlock, thử lại), `SYSTEM_ERROR`.
Thông báo tiếng Việt, AC-02: `"Kỳ thu 2026-11 đã tồn tại trên hệ thống!"`.

### F.2 Repository (package `com.bluemoon.dao`) — Unit-of-Work để preview và generate dùng **cùng** logic đọc
```java
public interface BillingRepository {
    <T> T inTransaction(TxWork<T> work) throws SQLException;      // commit/rollback; fresh connection
    Optional<BillingPeriod> findPeriod(String kyThu) throws SQLException;
    List<BillingPeriod> findAllPeriods() throws SQLException;
    List<MonthlyCharge> findCharges(String kyThu) throws SQLException;          // header only
    Optional<MonthlyCharge> findInvoice(String maHoaDon) throws SQLException;   // + lines
    boolean hasCharges(String soCanHo) throws SQLException;                     // ApartmentChargeLookup
    interface TxWork<T> { T run(BillingTx tx) throws SQLException; }
}
interface BillingTx {        // cùng 1 connection
    void insertPeriod(BillingPeriod p) throws SQLException;       // 1062 -> DuplicateRecordException
    BillingInputs loadInputs(YearMonth period) throws SQLException; // fees active BAT_BUOC + effective rates + households + resident counts
    void insertInvoices(List<MonthlyCharge> charges) throws SQLException;   // JDBC batch, header + lines
    void finalizePeriod(String kyThu, int invoices, int skipped, BigDecimal total) throws SQLException;
}
```
**`FeeRateLookup` ≠ load đơn giá trong `BillingTx`** (hai thứ khác nhau):
- `FeeRateLookup` là *contract công khai* (Mốc 2a): `Optional<AppliedRate> getEffectiveRate(String maKhoanThu, LocalDate atDate)` (Lead Dev chốt; **không** trả `Optional<BigDecimal>`; `AppliedRate(maKhoanThu, donGia, ngayApDung)` là kiểu dùng chung duy nhất, US-006 không tạo `AppliedRate` hay `FeeRateRepository` thứ hai; `ngayApDung` là ngày hiệu lực của bản ghi giá, không phải `atDate`), mỗi lần gọi tự mở connection riêng. Dùng cho preview/hiển thị, test double và các module khác; **không** tham gia transaction của `generatePeriod`, nên không dùng nó để tính kỳ chốt.
- `BillingTx.loadInputs` (Mốc 2b) tự đọc `fee_rates` bằng **cùng connection/transaction** của generate để có snapshot nhất quán. Việc này phụ thuộc schema `fee_rates` của US-006 (Dev-2), không đi qua `FeeRateLookup`. Test đối chiếu: với cùng dữ liệu, kết quả `loadInputs` và `FeeRateLookup` phải trùng.

DAO chỉ chứa SQL; công thức nằm ở `BillingCalculator` (hàm thuần, test không cần DB). Prepared statement 100%, connection mới từ `DatabaseConnection`, như `FeeTypeDAO`. `DuplicateRecordException` của US-001 tái sử dụng *không sửa* (hoặc exception riêng như `ResidentDuplicateException` — để Lead chọn).

### F.3 Transaction & locking (`generatePeriod`)
```
authz -> validate(kyThu == YearMonth.now(clock), định dạng) -> inTransaction (REPEATABLE READ, mặc định MySQL):
  1. INSERT billing_periods(ky_thu, ... totals=0)      -- lấy khóa duy nhất NGAY, tuần tự hóa các request cùng kỳ
       MySQL 1062 (duplicate key) -> rollback -> DUPLICATE (kỳ đã tồn tại)
       MySQL 1213 (deadlock)      -> rollback -> đọc lại kỳ ở transaction mới: có -> DUPLICATE; chưa có -> thử lại tối đa 1 lần,
                                     vẫn lỗi -> BUSY. KHÔNG coi 1213 là DUPLICATE nếu kỳ không tồn tại.
       MySQL 1205 (lock timeout)  -> rollback -> BUSY
  2. loadInputs(period)    -- đọc nhất quán (MVCC snapshot) fees, rates, households, residents
       thiếu rate -> rollback -> MISSING_RATE ; không hộ/khoản -> rollback -> NOTHING_TO_BILL
  3. BillingCalculator.compute(...)   -- thuần, không I/O
  4. insertInvoices(batch) + finalizePeriod(totals)
  COMMIT                       -- lỗi bất kỳ -> ROLLBACK toàn bộ, không có kỳ nửa vời
```
- **Idempotency [ĐỀ XUẤT]**: khóa tự nhiên `ky_thu`. Gọi lại không bao giờ sinh dữ liệu mới; trả `DUPLICATE` (AC-02) chứ không "SUCCESS giả", để UI báo đúng thông điệp. UI lấy kỳ hiện có bằng `getPeriod`.
- **Đồng thời**: request thứ hai chờ ở khóa của bước 1 cho tới khi request đầu commit (→ 1062 → `DUPLICATE`) hoặc rollback (→ nó tự chạy được). Mã lỗi phân biệt rõ: **1062 → `DUPLICATE`** (chắc chắn đã có dòng trùng); **1213 (deadlock) → không suy ra trùng**, phải đọc lại kỳ rồi quyết định (xem F.3); **1205 (`innodb_lock_wait_timeout`) → `BUSY`**. Không dùng `synchronized`/lock trong JVM (nhiều tiến trình client).
- **Không khóa dòng hộ/căn hộ/nhân khẩu**: đọc snapshot nhất quán là đủ; không chặn US-001/US-002 sửa dữ liệu khi đang chốt. Thay đổi commit sau điểm đọc đầu tiên thuộc về kỳ sau.
- Preview: đọc-only, transaction `READ ONLY` ngắn; **không** là bảo đảm kết quả generate (dữ liệu có thể đổi giữa hai lần bấm) → generate luôn tính lại trong transaction, không tin dữ liệu preview từ client.
- Kích thước: vài trăm hộ × vài khoản → batch insert một transaction là phù hợp; không chia nhỏ để giữ tính nguyên tử.

### F.4 RBAC
| Hành động | Permission (đã có) | ADMIN | ACCOUNTANT | RESIDENT/no session |
|---|---|---|---|---|
| preview, generate | `BILLING_PERIOD_CREATE` | ✔ | ✔ | ✘ `AccessDeniedException` |
| list/get kỳ, xem hóa đơn | `DEBT_REPORT_VIEW` | ✔ | ✔ | ✘ |
Khớp EP06 ("Lập kỳ thu: ADMIN toàn quyền, ACCOUNTANT toàn quyền, RESIDENT không"). Cư dân xem hóa đơn của mình (`PAYMENT_HISTORY_VIEW_OWN`) thuộc story khác, không làm ở US-007. `created_by` lấy từ `SessionContext.getCurrentUser()`; hoàn toàn không dùng `Supplier<Role>`. Không sửa `RoleBasedAuthorizationService`.

## G. Câu hỏi cần PO quyết định (mỗi câu có mặc định)

| # | Câu hỏi | Mặc định đề xuất |
|---|---|---|
| Q1 | Khoản tự nguyện (`DONG_GOP`) có nằm trong kỳ thu US-007 không? | **Không**. SRS ("nếu đã đăng ký") mâu thuẫn US-005 BR-06 + WBS ("không gộp khoản tự nguyện"). Ghi nhận đóng góp thực tế ở US-008 |
| Q2 | Kỳ thu theo tháng/năm thế nào? | Kỳ = **tháng dương lịch** `YYYY-MM`; ngày đầu kỳ = ngày 1, ngày cuối = ngày cuối tháng; `han_nop` = ngày 15 tháng sau (Dec → Jan năm sau); không tính theo ngày/nửa tháng |
| Q3 | Chốt số nhân khẩu vào thời điểm nào? | Lúc bấm "Lập kỳ", đếm nhân khẩu có `ngay_chuyen_den ≤ ngày cuối kỳ`; lưu `so_nhan_khau` vào snapshot. Không tính theo ngày lẻ (không prorate); Sprint 1 chưa có chuyển đi |
| Q4 | Giá đổi giữa kỳ dùng giá nào? | Giá có `ngay_ap_dung` lớn nhất **≤ ngày đầu kỳ** (đúng US-006 BR-09); giá hiệu lực giữa tháng áp dụng từ kỳ sau |
| Q5 | Diện tích đổi thì kỳ đã chốt xử lý sao? | **Không đổi** kỳ đã chốt; chỉ ảnh hưởng kỳ mới. Điều chỉnh sai sót do hủy/lập lại → hoãn (Q9) |
| Q6 | Quy tắc làm tròn? | HALF_UP về 1 VNĐ **từng dòng** trước khi lưu, tổng = tổng dòng; cột tiền `DECIMAL(19,2)` (luôn `.00`, có CHECK) — **PO duyệt** |
| Q7 | Hộ thiếu dữ liệu hợp lệ: bỏ qua hay lỗi cả kỳ? | **Hộ thiếu diện tích/dữ liệu hộ → bỏ qua riêng hộ đó + cảnh báo** (đúng BR-11). **Khoản đang áp dụng thiếu đơn giá → lỗi cả kỳ `MISSING_RATE`** (không thu thiếu âm thầm). Hộ bị bỏ qua sẽ không có hóa đơn bù trong Sprint 1 (xem Q9) |
| Q8 | Generate lần hai: trả kỳ hiện có hay báo trùng? | **Báo trùng** `DUPLICATE` với đúng thông điệp AC-02, không ghi gì; xem kỳ cũ bằng `getPeriod` |
| Q9 | Có hỗ trợ sửa/hủy/lập lại kỳ đã phát hành trong Sprint 1? | **Không**. Kỳ đã chốt bất biến; sai sót xử lý bằng bút toán điều chỉnh ở sprint sau. Cũng hoãn "lập bù hóa đơn cho hộ bị bỏ qua" |
| Q10 | Kết quả lưu thành snapshot nào để truy vết? | Snapshot hai cấp đã nêu ở C.3: header (diện tích, nhân khẩu, hạn nộp, người tạo, thời điểm) + dòng (mã/tên/hình thức khoản thu, số lượng, đơn giá, ngày hiệu lực giá, thành tiền) |
| Q11 | Cho lập kỳ **tương lai** / **quá khứ** không? | **PO quyết định (thay đổi so với đề xuất ban đầu): chỉ cho phép `ky_thu` = tháng hiện tại.** Quá khứ và tương lai đều bị từ chối (`VALIDATION_ERROR`). "Tháng hiện tại" tính bằng `Clock` được inject (production: `Asia/Ho_Chi_Minh`) |
| Q12 | Chỉ 3 khoản BR-08 hay mọi khoản `BAT_BUOC` đang áp dụng? | **Mọi khoản `BAT_BUOC` + `DANG_AP_DUNG`** (data-driven, gồm cả `THEO_CAN_HO`). Lưu ý: khoản bắt buộc nào được thêm mà chưa có đơn giá sẽ làm kỳ lỗi `MISSING_RATE` |
| Q13 | Tên bảng: SRS `hoa_don_ky`/`chi_tiet_hoa_don` hay WBS `BillingPeriod/Charge` hay `invoices/invoice_lines`? | `billing_periods`, `invoices`, `invoice_lines` (đồng bộ `fee_types`); mapping ghi trong tài liệu |
| Q14 | Hộ được tính từ khi nào? | `ngay_nhan_nha ≤ ngày cuối kỳ`, mọi trạng thái căn hộ (kể cả `CAN_HO_TRONG`/`CHO_THUE`) |
| Q15 | Căn hộ `CAN_HO_TRONG` nhưng đang có nhân khẩu? | Đếm theo dữ liệu thực + cảnh báo `VACANT_WITH_RESIDENTS` (không tự sửa trạng thái; Q17 US-002 vẫn là task tích hợp riêng) |
| Q16 | Tổng tiền = 0 thì có lập hóa đơn? | Không lập, đưa vào `skipped` (`ZERO_TOTAL`) |
| Q17 | `han_nop` có cho người dùng chọn không? | Không; luôn mặc định ngày 15 tháng kế tiếp |
| Q18 | Cần lưu tên chủ hộ vào snapshot? | Không (US-009 join `residents`); nếu PO cần báo cáo lịch sử đúng tên → thêm cột sau |

## H. Phạm vi Sprint 1 và hoãn lại

**Sprint 1 (Mốc 2/3):** migration độc lập; `BillingCalculator`; `BillingRepository/BillingDAO`; `BillingService` (preview, generate, list, get, getInvoice); chống trùng bằng DB; snapshot; RBAC; wiring `ApartmentChargeLookup` (nếu Lead duyệt); test unit + MySQL IT + concurrency.
**Hoãn:** sửa/hủy/lập lại kỳ; lập bù hóa đơn cho hộ bị bỏ qua; khoản tự nguyện/ghi nhận đóng góp (US-008); thanh toán, số dư thừa (BR-13, US-008); công nợ/lọc/KPI (US-009); thông báo (EP04); phí bảo trì căn hộ trống (EP01 BR-03); tính theo ngày lẻ (prorate)/phí chậm nộp; trigger DB chống UPDATE `invoice_lines`/`billing_periods`; bảng audit log; UI/Controller (dùng contract này để Dev-2 làm UI với mock — như US-005).

## I. Mâu thuẫn / vấn đề chưa rõ trong tài liệu

1. **Voluntary**: EP03 §3.2 công thức có "+ Khoản đóng góp (nếu đã đăng ký)" ↔ US-005 BR-06 ↔ WBS "không gộp" → đề xuất loại (Q1).
2. **US-006 chưa merge**: không có bảng đơn giá; `fee_types.so_tien_mac_dinh` bị `FeeType` ghi chú "không phải đơn giá chính thức". Em *không* dùng cột này trừ khi PO duyệt rõ ràng.
3. **SRS chỉ liệt kê 3 khoản** (DV, QL, VS) còn US-005 cho phép `THEO_CAN_HO`; SRS không nêu khoản cố định cụ thể → cần Q12.
4. **Khóa**: SRS hóa đơn khóa theo `so_can_ho`, hộ khóa theo `ma_ho`; tương đương nhờ `uq_households_apartment` → lưu cả hai.
5. **Kiểu số**: SRS ghi `Double` ↔ yêu cầu làm tròn VNĐ + US-001 đã dùng DECIMAL → dùng DECIMAL/`BigDecimal`.
6. **BR-11 không thể xảy ra** với schema hiện tại (diện tích `NOT NULL > 0`) → chỉ làm phòng thủ.
7. **EP01 BR-05** "thường trú/tạm trú đang ở" nhưng `residents` không có loại cư trú hay ngày chuyển đi → đếm toàn bộ bản ghi theo `ngay_chuyen_den`.
8. **`ApartmentChargeLookup`** hiện là hook mặc định `NONE` → US-007 phải cung cấp implementation để BR-02 hoạt động; cần xác nhận phạm vi sửa `HouseholdServiceImpl` composition (không ở trong US-007 gốc).
9. **`fee_types` không có ngày ngừng áp dụng** và `residents` không có ngày chuyển đi → không dựng lại được kỳ lịch sử; vấn đề này được tránh bằng Q11 (chỉ tháng hiện tại), không phải được giải quyết.
10. Em chưa đọc được cấu trúc WBS (xlsx) để xác nhận assignee/ngày; chỉ dùng text mô tả. Cần Lead xác nhận US-006 ai làm.

### I.1 Rủi ro cần PO quyết định (Lead Dev yêu cầu bổ sung)
Các mặc định Q1–Q18 giữ nguyên; mục này chỉ nêu rủi ro, không đổi chính sách.
- **R1 — Lập kỳ quá khứ (Q11) — ĐÃ GIẢM THIỂU bằng quyết định PO:** tính kỳ quá khứ từ dữ liệu *hiện tại* sẽ sai trạng thái khoản thu (`fee_types` không có ngày ngừng), nhân khẩu lịch sử (`residents` không có ngày chuyển đi) và diện tích. PO đã chọn chỉ cho phép tháng hiện tại, nên rủi ro này bị chặn ở service. Rủi ro còn lại: kỳ lập **muộn trong tháng** vẫn dùng dữ liệu tại lúc bấm; và nếu sau này PO muốn lập bù kỳ quá khứ thì cần thêm ngày hiệu lực cho khoản thu/nhân khẩu trước.
- **R2 — Bất biến snapshot chỉ ở mức API (liên quan Q5, Q9):** `invoice_lines` (và `billing_periods`) chưa được DB bảo vệ khỏi `UPDATE` trực tiếp; Sprint 1 chỉ bảo đảm vì repository không có method update/delete. Ai có quyền ghi DB vẫn sửa được. Trigger/quyền DB chống UPDATE đang được hoãn (mục H); PO/Lead cần quyết định có đưa vào Sprint 1 không.
- **R3 — Nhất quán khi dữ liệu đầu vào đổi đồng thời:** thiết kế dựa vào đọc snapshot nhất quán của InnoDB (REPEATABLE READ) trong một transaction. Điều này **chưa được chứng minh** cho tới khi có MySQL IT thật (J.3-4, J.4-3) với thay đổi diện tích/nhân khẩu/đơn giá/trạng thái khoản thu đồng thời lúc generate. Đây là điều kiện nghiệm thu của Mốc 3, không phải giả định.

## J. Test matrix

### J.1 Unit (không DB) — `BillingCalculatorTest`, `BillingServiceTest`
| # | Case | Kỳ vọng |
|---|---|---|
| U1 | AC-01 P1204: 80 m², 4 người, 10.000/7.000/6.000 | 800.000 / 560.000 / 24.000 = 1.384.000, `CHUA_DONG`, `con_thieu = tong_tien` |
| U2 | Căn hộ trống 0 nhân khẩu | VS = 0 (dòng vẫn có), QL/DV đủ |
| U3 | Khoản cố định theo hộ | `so_luong=1`, `thanh_tien = don_gia` |
| U4 | Làm tròn: 75.55 m² × 7.000,50 và 3 người × 6.000,50 (= 18.001,5) | HALF_UP từng dòng (→ 18.002); tổng = Σ dòng đã làm tròn |
| U5 | Đơn giá hiệu lực: nhiều bản ghi, `ngay_ap_dung = ngày đầu kỳ`, trước, sau (US-006 AC-01: 8.000 từ 2027-01-01 ⇒ 2026-12 dùng 7.000) | đúng bản ghi; giá sau kỳ bị bỏ qua |
| U6 | Khoản `NGUNG_AP_DUNG` / `DONG_GOP` | không có dòng |
| U7 | Thiếu rate cho khoản đang áp dụng | `MISSING_RATE`, liệt kê mã, không ghi |
| U8 | Hộ thiếu diện tích (mô phỏng) | bỏ qua + cảnh báo; hộ khác vẫn lập |
| U9 | `tong_tien = 0` | `ZERO_TOTAL` |
| U10 | `ky_thu` null/rỗng/`2026-13`/`2026-1`/`2026/11`/có khoảng trắng | `VALIDATION_ERROR` + `fieldErrors["ky_thu"]` |
| U10a | `Clock` cố định = 2026-11-15: `ky_thu` = `2026-10` (quá khứ), `2025-11`, `2026-12` (tương lai), `2027-11` — cho cả `previewPeriod` và `generatePeriod` | `VALIDATION_ERROR`; repository **không** được gọi, không ghi |
| U10b | `Clock` cố định = 2026-11-15: `ky_thu = 2026-11` | hợp lệ (qua bước validate) |
| U10c | Biên tháng với `Clock` + `ZoneId` `Asia/Ho_Chi_Minh`: `2026-11-30T23:59:59+07:00` → `2026-11` hợp lệ/`2026-12` bị từ chối; `2026-12-01T00:00:00+07:00` → ngược lại; thời điểm UTC `2026-11-30T17:30Z` (đã sang 12/01 giờ VN) → tính theo giờ VN | đạt |
| U10d | Biên năm: `Clock` = 2026-12-31T23:59 và 2027-01-01T00:00 (giờ VN) | `2026-12` rồi `2027-01`; `han_nop` = 2027-01-15 / 2027-02-15 |
| U11 | `han_nop` Dec → Jan năm sau; Feb | đúng |
| U12 | `ma_hoa_don` | `HD-202611-P1204` |
| U13 | RBAC: ADMIN, ACCOUNTANT ok; RESIDENT/no session → `AccessDeniedException`, repository **không** được gọi, kiểm tra trước validate | đạt |
| U14 | Lỗi SQL (mock) → rollback, `SYSTEM_ERROR` chung; SQLException có `getErrorCode()` = **1062 → `DUPLICATE`**; **1213 → đọc lại kỳ (mock): có → `DUPLICATE`, chưa có → thử lại 1 lần rồi `BUSY`, không bao giờ `DUPLICATE` khi kỳ không tồn tại**; **1205 → `BUSY`**; mã khác → `SYSTEM_ERROR` | đạt |
| U17 | Làm tròn lưu `DECIMAL(19,2)`: mọi `thanh_tien`/`tong_tien` có `scale` 2 và phần thập phân `.00`; không có giá trị lẻ trước khi lưu | đạt |
| U15 | Preview không gọi bất kỳ method ghi nào; kỳ đã có → `alreadyGenerated=true` + snapshot cũ | đạt |
| U16 | `DONG_GOP`/`THEO_*` không hợp lệ không xuất hiện; `VACANT_WITH_RESIDENTS` cảnh báo | đạt |

### J.2 JDBC (fake `Connections`, như `FeeTypeDAOTest`)
commit khi OK; rollback + suppressed khi lỗi giữa batch; prepared statement (không nối chuỗi); đóng connection; 1062 → `DuplicateRecordException`.

### J.3 MySQL IT (`-Pmysql-it`, schema `_test`, như US-001/002/005) — `BillingMySqlIT`
1. Migration chạy 2 lần (idempotent); FK/CHECK/UNIQUE tồn tại; bảng tạo đủ.
2. Generate đầy đủ → số hóa đơn = số hộ; số dòng đúng; tổng khớp tay; AC-01 trên MySQL thật.
3. Generate lần 2 → `DUPLICATE`; số dòng không đổi.
4. **Snapshot bất biến**: sau generate, đổi diện tích (US-001), thêm nhân khẩu (US-002), thêm rate mới → `getInvoice` cũ không đổi; kỳ mới dùng dữ liệu mới.
5. Rollback toàn kỳ khi lỗi giữa chừng (vd ép vi phạm CHECK ở hộ cuối) → 0 dòng `billing_periods/invoices/invoice_lines`.
6. `ON DELETE RESTRICT`: không xóa được `fee_types/apartments/households` đã có hóa đơn.
7. CHECK: `ky_thu` sai định dạng, `tong_tien < 0`, `don_gia <= 0` bị DB từ chối; `con_thieu` generated đúng.
8. DECIMAL round-trip (không lệch dấu phẩy động); collation `ascii_bin` FK với `fee_types` hoạt động.
9. Đối chiếu số nhân khẩu `loadInputs` với `ResidentRepository.countByMaHo` (không lọc ngày) khi mọi `ngay_chuyen_den` ≤ ngày cuối kỳ.
10. `ApartmentChargeLookup.hasCharges` đúng trước/sau generate.
11. SQL-injection literals trong `ky_thu`/`ma_hoa_don`.

### J.4 Concurrency IT (cùng MySQL) — `BillingConcurrencyIT`
1. **8–16 thread** cùng `generatePeriod("2026-11")` (CountDownLatch phát lệnh cùng lúc) → **đúng 1 SUCCESS, còn lại DUPLICATE**, 1 dòng `billing_periods`, số hóa đơn = số hộ, không `SYSTEM_ERROR`.
2. **Không** kỳ vọng hai kỳ khác tháng đều `SUCCESS` qua public API (Q11 chỉ cho tháng hiện tại). Thay vào đó: (a) với `Clock` cố định, request cho tháng khác tháng hiện tại → `VALIDATION_ERROR`, không khóa/ghi gì; (b) tính độc lập của khóa `ky_thu` được kiểm ở **tầng repository** (gọi trực tiếp `BillingRepository.inTransaction` với hai `ky_thu` khác nhau song song → cả hai commit, không deadlock) — đây là test hạ tầng, không phải hành vi service.
3. Generate đồng thời với thêm nhân khẩu / sửa diện tích → kỳ nhất quán (mỗi hóa đơn khớp snapshot một thời điểm), không treo.
4. Mô phỏng **1205** (giữ khóa dài, hạ `innodb_lock_wait_timeout`) → `BUSY`; sau đó thử lại thành công/`DUPLICATE`. Kiểm riêng **1062** (→ `DUPLICATE`) và **1213** (gây deadlock có chủ đích ở tầng repository nếu tái hiện được; nếu không tái hiện được trên MySQL thật thì ghi rõ và chỉ dựa vào unit test U14 — không khẳng định đã kiểm chứng).
5. Rollback của request thắng khóa → request chờ chạy được (không kẹt).
6. Preview song song với generate → không lỗi, không ghi.

## K. Đề xuất file cho Mốc 2 (chưa tạo)
Branch mới từ `origin/main` (không cherry-pick), tách hai bước như US-005: **2a** contract (interface + models + Result, chưa implementation, để Dev-2 làm UI/mock) → **2b** implementation.
- `database/migrations/us007_billing.sql`
- `models/`: `BillingPeriod`, `MonthlyCharge`, `ChargeLine`, `ChargeStatus`, `BillingPreview`, `BillingPeriodDetail`, `SkippedHousehold`, `AppliedRate`
- `services/`: `BillingService`, `BillingResult`, `BillingServiceImpl` (nhận `Clock` qua constructor), `BillingCalculator`
- `dao/`: `BillingRepository`, `BillingDAO` (+ `BillingTx`), `FeeRateLookup` (phối hợp US-006)
- `src/test/.../`: `BillingCalculatorTest`, `BillingServiceTest`, `BillingDAOTest`, `BillingMySqlIT`, `BillingConcurrencyIT`
- `docs/design/us-007-backend-handoff.md`
- *Tùy chọn, cần Lead duyệt riêng:* wiring `ApartmentChargeLookup` vào composition `HouseholdServiceImpl`.
**Không đụng**: `schema.sql`, `FeeTypeService/DAO`, `ResidentRepository`, `HouseholdService`, `RoleBasedAuthorizationService`, `AuthService`.

## L. Đề xuất READY / BLOCKED
- **Contract review: READY** để Lead/PO duyệt.
- **Implementation: BLOCKED**, vì cần:
  1. ~~PO duyệt Q1–Q18~~ — **đã xong** (Q11 thay đổi: chỉ tháng hiện tại; xem bảng G).
  2. **US-006**: đã phân công Dev-2 (Schema/DAO), Dev-1 (Service/UI); còn thiếu schema `fee_rates` tối thiểu được chốt và merge (D-1). Mốc 2a (contract) không bị chặn bởi việc này; Mốc 2b bị chặn. Nếu US-006 chưa kịp: có thể làm calculator/service/DAO với `FeeRateLookup` giả, nhưng tích hợp thật và MySQL IT toàn tuyến bị chặn; **không** dùng `so_tien_mac_dinh` làm giá thay thế nếu chưa có quyết định bằng văn bản của PO.
  3. Lead cho phép tạo branch Mốc 2 từ `origin/main`.
