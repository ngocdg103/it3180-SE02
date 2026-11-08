# US-006 — Service/UI handoff (Dev-1)

Ngày: 08/11/2026. Phạm vi: Service, RBAC, adapter standalone, UI xem lịch sử/thêm giá và tests độc lập. Không triển khai BillingServiceImpl hoặc thay DAO/schema.

## 1. Branch/base và dependency

- Branch: `feature/us006-service-ui`, khởi tạo từ `origin/main` tại `ba9080c5413af86088e68e15aa0f51fb932e9c72`.
- PR #13 chưa merge khi bắt đầu. Branch tích hợp này chứa nguyên commit `3adaf4cb1136bded4d642c0848d68b3d30a02333`, đưa vào bằng merge commit `405b36b`, không copy/chỉnh sửa code DAO. Chín file của PR #13 là dependency Dev-2, không phải implementation mới của Dev-1.
- PR #13 đã merge tại `ac43254bf76da329c6f4eab235325bc2260a49d9` (QA DAO 5/5 PASS theo bàn giao Lead). Đã merge main mới vào feature branch bằng `76a19ab3a53967fa03940071675da10f6d3955e0`, không conflict, không rebase/cherry-pick/force-push. Main là ancestor của HEAD; diff vào main không còn DAO/model FeeRate hoặc migration US-006.
- PR #15 vẫn Draft, chưa được phép merge. Đã gỡ dependency chờ PR #13; còn peer review và nghiệm thu integration gate của Lead/PO. Không sửa branch Dev-2 hoặc push main.
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
- `FeeRateServiceTest`, `FeeRateControllerTest`, `FeeRateWiringTest`, opt-in `FeeRateServiceMySqlIT` và tài liệu này.

Tests dùng repository kiểm soát **chỉ trong test sources**. Bao gồm role matrix + logout/no session, validation/precision/ngày/Unicode, SQLState/vendor mapping, missing-vs-error, adapter, immutable result, real FXML/Service/RBAC, lịch sử thành công/duplicate, các lỗi lưu, UI không chặn FX thread, late/stale callbacks, lifecycle module/Scene và wiring JDBC không vượt RBAC. Không cần MySQL cho các tests này.

Lệnh JDK17/Maven3.9.9, Enforcer bật:

```text
mvn clean verify
mvn -Pjavafx-smoke clean verify
git diff --check
```

Kết quả sau sync main: normal verify **482 tests**, JavaFX smoke **483 tests**, đều BUILD SUCCESS, 0 failures/errors/skips. Có **53 tests mới** thuộc normal: 35 Service/adapter, 17 controller/module, 1 wiring. Enforcer bật, JDK Temurin 17.0.20.1 và Apache Maven 3.9.9. Fixture FXML tạo Scene/CSS/skin cho ScrollPane. MySQL verification bổ sung được ghi ở mục 9; không tuyên bố UI E2E. Không sửa migration/DAO Dev-2, không App Shell, không BillingServiceImpl, không merge main.

## 8. Readiness / việc còn lại

- Service/UI/adapter đã sync main có DAO PR #13, sẵn sàng peer review sau checks nêu trong báo cáo cuối.
- **Integration gate:** peer review PR #15 và Lead/PO xác nhận; vẫn Draft, không tự merge. Dependency PR #13 và sync main đã hoàn tất.
- **Dependency:** host App Shell gắn module và đóng đúng lifecycle; DB config/migrations US-005 rồi US-006 trên môi trường test; PO chốt ngày seed BR-08 nếu cần. Không hardcode credentials hoặc ngày seed.
- **Giới hạn:** chưa có UI E2E/App Shell; cross-story billing snapshot IT thuộc Dev-3. Direct SQL/admin vẫn có thể sửa dữ liệu ngoài append-only application API như handoff DAO đã nêu.

## 9. Integration verification sau main ac43254 (08/11/2026)

- HEAD trước sync: `0b98922a925857dca41f299a9fa68800b4a02a06`; merge main: `76a19ab3a53967fa03940071675da10f6d3955e0`. Commit verification tiếp theo chỉ thêm test và cập nhật tài liệu này; không sửa production code.
- `3adaf4c` là ancestor của cả branch và main (branch đưa vào bằng 405b36b; main đưa vào bằng ac43254). Merge main giữ nguyên hai lịch sử, không cherry-pick lại DAO. `git merge-base --is-ancestor origin/main HEAD` PASS. Diff PR sau sync chỉ gồm 13 file Service/UI/docs/tests, không đưa lại 9 file dependency của PR #13. So sánh toàn bộ DAO, migrations và FeeRate model với main: không có diff.
- MySQL Community **8.4.13**, chỉ bind `127.0.0.1:33317`, REPEATABLE READ, InnoDB + strict mode. Schema mới độc lập `bluemoon_us006_sync_9ff36e98_test`, user riêng có quyền DDL/DML chỉ trên tên schema đã escape `_` trong GRANT; không có quyền global. Đã xác nhận SELECT sang schema QA khác bị từ chối. Không dùng production/shared DB hoặc root để chạy tests.
- Áp dụng hai migration **chính thức** `us005_fee_types.sql`, `us006_fee_rates.sql`. Mỗi fixture chỉ xóa mã riêng, child trước parent; không DROP bảng, không tắt FK checks. Schema/account/config chỉ dành cho lần verification này, không đưa credentials vào Git. MySQL portable được khởi động cho verification và dừng sau khi kiểm tra; data/config giữ trong `.tools/` ignored.
- `mvn -Pmysql-it "-Dtest=FeeRateMySqlIT,FeeRateServiceMySqlIT" verify`: **9/9 PASS**, 0 failures/errors/skips, BUILD SUCCESS. Trong đó 5 DAO IT gốc của Dev-2 (migration/constraints/history/concurrency/rollback/locking) và 4 Service IT mới dùng composition production FeeRateModule -> FeeRateServiceImpl -> FeeRateDAO/FeeTypeDAO thật.
- Service IT kiểm bốn API; ADMIN/ACCOUNTANT đọc/thêm; RESIDENT/no session bị chặn; BigDecimal round-trip + lịch sử bất biến; missing rate trả empty; adapter giữ đúng AppliedRate/ngày hiệu lực; duplicate thật 1062; precheck thật 02000/45000; validation trước ghi; trạng thái parent đổi sau lúc chọn vẫn bị từ chối và lịch sử cũ giữ nguyên.
- Mapping 1213/1205 và lỗi hệ thống được regression unit kiểm bằng SQLException kiểm soát; không tuyên bố đã gây deadlock/timeout thật trong Service IT mới. Background Task/stale/late callbacks/cleanup được kiểm qua controller/module tests trong normal/smoke. Controller/FXML không import java.sql hoặc dùng Connection/DAO trực tiếp.
- Kết quả regression sau merge: **482 normal / 483 JavaFX smoke PASS**, JDK17/Maven3.9.9, Enforcer bật. `git diff --check` PASS. Logs cục bộ ignored: `.tools/us006-sync/{verify,javafx,mysql}.log`.

Tái hiện MySQL IT: cấp schema disposable `*_test` và user chỉ có quyền trên schema đó, cấu hình ngoài source theo Foundation; không dùng credentials từ máy khác. Ví dụ PowerShell:

```powershell
$env:BLUEMOON_MYSQL_TEST = '1'
$env:BLUEMOON_DB_CONFIG = '<absolute path to private test db.properties>'
# BLUEMOON_DB_URL/USERNAME/PASSWORD override the properties file: unset stale values first.
mvn -Pmysql-it '-Dtest=FeeRateMySqlIT,FeeRateServiceMySqlIT' verify
```

Không chạy toàn profile cùng schema với FeeTypeMySqlIT có DROP fee_types. Kết quả trên xác nhận backend Service/DAO integration; **không** xác nhận UI E2E/App Shell hoặc billing snapshot toàn tuyến. Không có blocker kỹ thuật phát hiện trong phạm vi sync; PR #15 vẫn Draft chờ peer review/Lead gate.
