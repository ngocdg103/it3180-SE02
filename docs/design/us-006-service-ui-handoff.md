# US-006 — Service/UI handoff (Dev-1)

Ngày: 08/11/2026. Phạm vi: Service, RBAC, adapter standalone, UI xem lịch sử/thêm giá và tests độc lập. Không triển khai BillingServiceImpl hoặc thay DAO/schema.

## 1. Branch/base và dependency

- Branch: `feature/us006-service-ui`, khởi tạo từ `origin/main` tại `ba9080c5413af86088e68e15aa0f51fb932e9c72`.
- PR #13 chưa merge khi bắt đầu. Branch tích hợp này chứa nguyên commit `3adaf4cb1136bded4d642c0848d68b3d30a02333`, đưa vào bằng merge commit `405b36b`, không copy/chỉnh sửa code DAO. Chín file của PR #13 là dependency Dev-2, không phải implementation mới của Dev-1.
- PR Service/UI là draft chờ PR #13 được QA xác minh và merge. Không merge branch này trước dependency. Sau đó cập nhật branch với main mới, giải quyết dependency và chạy lại regression; không ép push hoặc sửa branch Dev-2.
- US-007 shared contract và Q17 đã có trong base main. Hai tài liệu local US-007 sơ bộ và QA environment chưa commit được giữ nguyên, không đưa vào PR này.

## 2. Public contract

Package `com.bluemoon.services`:

```java
FeeRateResult<List<FeeType>> listFeeTypes();
FeeRateResult<List<FeeRate>> listHistory(String maKhoanThu);
FeeRateResult<Optional<FeeRate>> getEffectiveRate(String maKhoanThu, LocalDate atDate);
FeeRateResult<FeeRate> createRate(FeeRateForm form);
```

`FeeRateForm` ở `com.bluemoon.models`: `(String maKhoanThu, String donGia, LocalDate ngayApDung, String ghiChu)`. Đây là input chưa kiểm tra; id do DAO sinh. Không có update/delete/upsert.

`FeeRateResult<T>`: `status()`, `isSuccess()`, `message()`, `data(): Optional<T>`, `fieldErrors(): Map<String,String>` bất biến. Status: `SUCCESS`, `VALIDATION_ERROR`, `NOT_FOUND`, `DUPLICATE`, `BUSY`, `SYSTEM_ERROR`. Với lookup thành công nhưng chưa có giá, `data()` chứa `Optional.empty()`; không đồng nhất lỗi DB với thiếu giá. History rỗng là SUCCESS.

Constructor: `new FeeRateServiceImpl(FeeRateRepository, FeeTypeRepository, AuthorizationService)`. Repository US-005 chỉ phục vụ dropdown khoản bắt buộc, không xây lại FeeTypeService/DAO. `listFeeTypes` trả mọi khoản BAT_BUOC, gồm khoản ngừng áp dụng để xem lịch sử. Không cần quyền FEE_CATEGORY_VIEW cho thao tác US-006.

## 3. Validation, tiền và lịch sử

- Mã `[A-Z0-9_]{1,20}`; không tự đổi mã khi lưu.
- Input giá dạng thập phân thường, tối đa 17 chữ số nguyên và 2 chữ số lẻ, giá > 0; cho phép khoảng trắng hai đầu. Ví dụ `7000.50`, `0.01`, max `99999999999999999.99`. Không nhận exponent, dấu phân nhóm, dấu phẩy, NaN/Infinity, số âm hoặc phần lẻ quá 2 chữ số (kể cả `1.230`). UI ghi rõ dấu chấm thập phân. Parse trực tiếp BigDecimal, không qua double. Không dùng HALF_UP để sửa giá nhập sai: HALF_UP từng dòng hóa đơn thuộc US-007.
- Ngày LocalDate trong phạm vi MySQL DATE 1000-01-01..9999-12-31; DatePicker không nhận text chưa commit. Không tự cấm backdate/future date khi SRS không quy định. PO còn cần xác nhận ngày seed BR-08 trước khi tạo seed; branch này không seed giá.
- Ghi chú tối đa 255 Unicode code points; giữ null/chuỗi theo input, không tự cắt.
- Field errors: `ma_khoan_thu`, `don_gia`, `ngay_ap_dung`, `ghi_chu`. Thông báo ba trường đầu theo SRS EP-03 §2.1.
- DAO giữ unique mã/ngày làm quyết định cuối cùng. Service không làm SELECT rồi INSERT bằng hai connection để giả lập transaction. Eligibility BAT_BUOC/DANG_AP_DUNG được DAO kiểm lại dưới khóa parent trong transaction insert, kể cả dropdown đã cũ.
- Thêm giá mới luôn là bản ghi mới. Không sửa lịch sử; không dùng so_tien_mac_dinh làm fallback.

## 4. RBAC và lỗi

Mỗi lời gọi Service/adapter sử dụng AuthorizationService hiện có, đọc session hiện tại; không cache role. CREATE cho createRate, VIEW cho tất cả đọc. ADMIN/ACCOUNTANT được phép; RESIDENT/no session ném AccessDeniedException trước validation/I/O. Không thêm Permission hoặc thay AuthService/SessionContext.

| Lỗi từ insert | Result / UI |
|---|---|
| SQLState 02000, hoặc MySQL 1452 | NOT_FOUND, lỗi tại ma_khoan_thu |
| SQLState 45000 | VALIDATION_ERROR: khoản thu phải bắt buộc và đang áp dụng |
| MySQL 1062 | DUPLICATE, lỗi tại ngay_ap_dung; giữ nguyên input |
| MySQL 1213/1205 | BUSY; không retry tự động |
| Lỗi khác, gồm commit không chắc chắn | SYSTEM_ERROR chung; không lộ SQL/credentials và không retry tự động |

Các lỗi đọc 1213/1205 trả BUSY; lỗi đọc khác trả SYSTEM_ERROR, không biến thành empty success. Không coi mọi SQLState 23000 là duplicate. DAO sở hữu commit/rollback/resource cleanup; Service không mở thêm transaction. Với lỗi commit/close không chắc chắn, người dùng nên tải lại lịch sử trước khi thử lưu lại; UNIQUE chặn trùng, nhưng Service không hứa chưa có dữ liệu khi báo SYSTEM_ERROR.

## 5. Adapter US-007

`RepositoryFeeRateLookup implements com.bluemoon.dao.FeeRateLookup` với constructor `(FeeRateRepository, AuthorizationService)`.

```java
Optional<AppliedRate> getEffectiveRate(String maKhoanThu, LocalDate atDate);
```

Kiểm FEE_RATE_VIEW trước validate/I/O, gọi repository và map `maLoaiThu -> maKhoanThu`, giữ BigDecimal và **ngày hiệu lực thật của giá**, không dùng atDate làm ngayApDung. SQLException thành IllegalStateException với thông báo chung và giữ cause cho chẩn đoán nội bộ; UI phải hiển thị lỗi chung, không render cause. Empty chỉ khi thật sự không có rate.

Adapter là standalone, mỗi call có connection riêng. **BillingTx không được gọi adapter hoặc DAO standalone khi generate**: tự đọc fee_rates bằng cùng connection/snapshot của transaction billing, target là ngày đầu kỳ. Không tạo AppliedRate/FeeRateLookup thứ hai. Chứng minh snapshot toàn tuyến và dữ liệu đầu vào đổi đồng thời thuộc MySQL IT US-007, chưa được xác nhận ở đây.

## 6. UI và wiring

`FeeRateView.fxml` + `FeeRateController`: dropdown mã/tên khoản bắt buộc, lịch sử ngày giảm dần theo repository, giá hiệu lực tại ngày chọn, form đơn giá/ngày/ghi chú và lỗi từng trường. Khoản ngừng áp dụng chỉ xem lịch sử. Save chỉ hiện khi canAccess(FEE_RATE_CREATE); không có VIEW thì khóa màn hình và không truy vấn. Service vẫn bảo vệ mỗi thao tác. Form có vùng cuộn để dùng ở cửa sổ nhỏ.

JDBC/Service calls chạy trên worker daemon, cập nhật controls trên FX thread. Chống double-submit, vô hiệu hóa đổi khoản thu khi lưu; lỗi giữ input; không tự retry. Request token bỏ response history đã cũ khi đổi lựa chọn; callback sau logout/đóng màn hình không đưa dữ liệu vào màn hình cũ. `close()` không hủy transaction đã bắt đầu: write vẫn có thể commit.

Composition (không I/O lúc construction):

```java
// Load DatabaseConfig away from FX thread; reuse the existing configured DatabaseConnection.
var authorization = new RoleBasedAuthorizationService(null);
FeeRateService service = FeeRateModule.createService(connections, authorization);
FeeRateLookup lookup = FeeRateModule.createLookup(connections, authorization);

// FX thread: host owns Stage/Scene/navigation.
FeeRateModule module = FeeRateModule.open(connections);
hostPane.getChildren().setAll(module.view());
// Before screen switch/logout/window close, on FX thread:
module.close();
```

`open` dùng FeeRateDAO + FeeTypeDAO thật và RBAC session hiện có, không production fake. Module tạo Parent, không tự tạo Stage hoặc thay Login UI/App Shell. Host chưa gắn module này vào menu; không tuyên bố ứng dụng end-to-end đã tích hợp.

## 7. Files và verification

Dev-1 thêm:

- `models/FeeRateForm.java`.
- `services/FeeRateService.java`, `FeeRateServiceImpl.java`, `FeeRateResult.java`, `RepositoryFeeRateLookup.java`.
- `controllers/FeeRateController.java`, `FeeRateModule.java`, resources `views/FeeRateView.fxml` (tái sử dụng CSS US-005).
- `FeeRateServiceTest`, `FeeRateControllerTest`, `FeeRateWiringTest` và tài liệu này.

Tests dùng repository kiểm soát **chỉ trong test sources**. Bao gồm role matrix + logout/no session, validation/precision/ngày/Unicode, SQLState/vendor mapping, missing-vs-error, adapter, immutable result, real FXML/Service/RBAC, lịch sử thành công/duplicate, các lỗi lưu, UI không chặn FX thread, late/stale callbacks, lifecycle module/Scene và wiring JDBC không vượt RBAC. Không cần MySQL cho các tests này.

Lệnh JDK17/Maven3.9.9, Enforcer bật:

```text
mvn clean verify
mvn -Pjavafx-smoke clean verify
git diff --check
```

Kết quả bản cuối: normal verify **482 tests**, JavaFX smoke **483 tests**, đều BUILD SUCCESS, 0 failures/errors/skips. Có **53 tests mới**: 35 Service/adapter, 17 controller/module, 1 wiring. Enforcer bật, JDK Temurin 17.0.20.1 và Apache Maven 3.9.9. Staged diff check PASS. Fixture FXML đã tạo Scene/CSS/skin cho ScrollPane; một lần chạy trước khi sửa fixture bị lỗi lookup controls, không tính là PASS; kết quả trên là lần chạy lại bản cuối. Không chạy FeeRateMySqlIT/E2E DB trong nhiệm vụ này; QA đang phụ trách xác minh PR #13. Không sửa migration/DAO Dev-2, không App Shell, không BillingServiceImpl, không merge main.

## 8. Readiness / việc còn lại

- Phần độc lập Service/UI/adapter sẵn sàng review khi checks cuối PASS.
- **Integration gate:** QA PR #13, merge DAO vào main, cập nhật base và chạy regression trước merge PR này.
- **Dependency:** host App Shell gắn module và đóng đúng lifecycle; DB config/migrations US-005 rồi US-006 trên môi trường test; PO chốt ngày seed BR-08 nếu cần. Không hardcode credentials hoặc ngày seed.
- **Giới hạn:** chưa có E2E DB/App Shell; cross-story billing snapshot IT thuộc Dev-3. Direct SQL/admin vẫn có thể sửa dữ liệu ngoài append-only application API như handoff DAO đã nêu.
