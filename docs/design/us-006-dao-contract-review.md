# US-006 (FE-032) — Mốc 1: Schema/DAO contract review

Ngày: 08/11/2026. Owner: Dev-2 (Nguyễn Hữu Gia Vinh). Trạng thái: **READY để Lead review; BLOCKED Mốc 2 đến khi Lead duyệt**. Đây là đề xuất, chưa tạo production model/interface/DAO/migration, chưa thực thi SQL, chưa commit/push.

## 1. Preflight và căn cứ

- `git fetch origin` thành công. HEAD trước thao tác: `d03c72be406577091e366299552b9112f34db52d`, branch `feature/us005-ui-backend-integration`; working tree sạch (không có tracked change hoặc untracked file cần di chuyển).
- `origin/main` mới nhất tại lần fetch: `162d9ea4db222d3bb27d4a603a32f5496c983204`, đúng SHA Lead xác nhận. Branch riêng `docs/us006-dao-contract` được tạo từ đó. Không discard, reset, cherry-pick hoặc nhập code US-007.
- Đã đọc EP-03 §2: AC-01, BR-08 (QL 7.000/m², DV 10.000/m², VS 6.000/người), BR-09 (thêm lịch sử, không ghi đè; ngày tra cứu kỳ là ngày đầu tháng).
- WBS `SE_02_Implementation_WBS.xlsx`, sheet `01 TASK THUC THI`, dòng 28: `1.2.2.2.1`, US-006/FE-032, Dev-2, đầu ra schema/entity/DAO/test; dependencies `1.2.2.1.1, 1.2.1.2.1, 1.4.1.2.1`. Dòng 30 giao Service/UI integration cho Dev-1; dòng 35 xác nhận US-007 phụ thuộc task DAO này. WBS nói CRUD nhưng BR-09 giới hạn API ở insert/read, không update/delete lịch sử.
- Đã đọc `FeeType`, `FeeTypeRepository`, `FeeTypeDAO`, migration US-005, `DatabaseConnection`, `DatabaseConfig`, `RoleBasedAuthorizationService`, permission matrix và `FeeTypeMySqlIT` trên base.
- Review US-007 Revision 02 được đọc bằng `git show` từ `origin/docs/us007-contract-review` tại `b676211e49114982aabeabf1b44a2d2032c3ba73`; file chưa nằm trên main.
- Source `AppliedRate` và `FeeRateLookup` được đọc từ `origin/feature/us007-billing-contract` tại `4a8061d0bd95f4dc813704367e4604329fbcb035`, chỉ đọc, không checkout/cherry-pick. Không xác minh được metadata PR #11 bằng CLI vì máy không có `gh`; cần Lead đối chiếu head PR với SHA này.

## 2. Schema và migration đề xuất

Đã đối chiếu migration US-005 thực tế: `fee_types.ma_loai_thu` là `VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY`; bảng InnoDB, default charset utf8mb4. Cột con phải khai báo ascii/ascii_bin tường minh, không kế thừa utf8mb4 của bảng. `id` là BIGINT signed theo schema PO duyệt.

Nội dung dự kiến cho `database/migrations/us006_fee_rates.sql` (chưa tạo file SQL):

```sql
-- MySQL 8.0.16+; run us005_fee_types.sql first.
CREATE TABLE IF NOT EXISTS fee_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ma_loai_thu VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    don_gia DECIMAL(19,2) NOT NULL,
    ngay_ap_dung DATE NOT NULL,
    ghi_chu VARCHAR(255) NULL,
    CONSTRAINT ck_fee_rates_amount CHECK (don_gia > 0),
    CONSTRAINT uq_fee_rates_code_date UNIQUE (ma_loai_thu, ngay_ap_dung),
    CONSTRAINT fk_fee_rates_fee_type FOREIGN KEY (ma_loai_thu)
        REFERENCES fee_types (ma_loai_thu)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- Unique index đồng thời phục vụ truy vấn theo mã + khoảng ngày và scan ngược; chưa thêm index trùng lặp. Kiểm tra EXPLAIN ở Mốc 2.
- Chạy lặp trên schema đúng phải giữ nguyên cấu trúc và dữ liệu. `IF NOT EXISTS` không sửa bảng có sẵn bị drift: triển khai phải đối chiếu `SHOW CREATE TABLE fee_types/fee_rates` hoặc information_schema, dừng nếu sai. Chưa có bằng chứng kiểm tra DB thật trong Mốc 1.
- MySQL DDL có implicit commit: không hứa rollback migration như rollback DML. Không DROP bảng hoặc đổi `schema.sql`, seed, migration US-005 trong phạm vi này.
- FK chỉ bảo đảm khoản thu tồn tại; không bảo đảm `BAT_BUOC`/`DANG_AP_DUNG`. Service chịu trách nhiệm rule này. Không thêm trigger hoặc cột ngoài schema tối thiểu đã duyệt.
- BR-08 là dữ liệu nghiệp vụ, không hardcode vào DAO hoặc tự seed ngày hiệu lực chưa được PO duyệt. Không dùng `fee_types.so_tien_mac_dinh` làm fallback.

## 3. Model và repository API đề xuất

Package và chữ ký chờ Lead duyệt, chưa tạo production files:

```java
// com.bluemoon.models
public record FeeRate(Long id, String maLoaiThu, BigDecimal donGia,
                      LocalDate ngayApDung, String ghiChu) { }

// com.bluemoon.dao
public interface FeeRateRepository {
    FeeRate insert(FeeRate newRate) throws SQLException;
    List<FeeRate> findHistory(String maLoaiThu) throws SQLException;
    Optional<FeeRate> findEffectiveRate(String maLoaiThu, LocalDate targetDate)
        throws SQLException;
}

public final class FeeRateDAO implements FeeRateRepository {
    public FeeRateDAO(DatabaseConnection connections);
    // Implements the three methods above.
}
```

- `id == null` chỉ cho input mới; insert từ chối id được caller cấp, trả bản ghi có generated id dương sau commit. Bản ghi đọc từ DB luôn có id dương. Không dùng `0` làm id giả. Model immutable, không phụ thuộc US-007.
- `maLoaiThu` ánh xạ `fee_types.ma_loai_thu`; khác tên accessor `FeeType.maKhoanThu()` là có chủ ý và phải ghi rõ trong adapter.
- Giá dùng `BigDecimal`, JDBC `setBigDecimal/getBigDecimal`, ngày dùng `LocalDate` và JDBC DATE; không qua double, timestamp hoặc timezone.
- Null bắt buộc bị từ chối bằng `NullPointerException`; mã không khớp `[A-Z0-9_]{1,20}`, id không hợp lệ, giá không biểu diễn chính xác ở scale 2, giá ≤ 0 hoặc > `99999999999999999.99`, ngày ngoài MySQL DATE `1000-01-01..9999-12-31`, ghi chú quá 255 Unicode code points: `IllegalArgumentException` trước mở connection. Chuẩn hóa giá bằng `setScale(2, UNNECESSARY)`; `1.230` được chấp nhận thành `1.23`, `1.234` bị từ chối, không âm thầm làm tròn. Ghi chú null được giữ null; không tự cắt hoặc trim dữ liệu.
- `findHistory`: danh sách bất biến, sắp `ngay_ap_dung DESC, id DESC`; mã hợp lệ chưa tồn tại hoặc chưa có giá trả list rỗng. Không join trạng thái hiện tại để xóa lịch sử khỏi kết quả.
- `findEffectiveRate`: trả empty khi chưa có giá hợp lệ (kể cả mã chưa tồn tại); không trả giá tương lai, không fallback giá chuẩn. Caller dùng FeeTypeRepository khi cần phân biệt mã không tồn tại với thiếu giá.
- Không có update/delete/upsert: cùng mã/ngày là duplicate ngay cả khi giá giống nhau. Không sửa bản ghi cũ hoặc tự thay ngày để né xung đột.

SQL lookup chuẩn (PreparedStatement, dấu `?` bind mã và `java.sql.Date.valueOf(targetDate)`):

```sql
SELECT id, ma_loai_thu, don_gia, ngay_ap_dung, ghi_chu
FROM fee_rates
WHERE ma_loai_thu = ? AND ngay_ap_dung <= ?
ORDER BY ngay_ap_dung DESC
LIMIT 1;
```

Unique mã/ngày loại bỏ tie; không cần dùng id làm tiêu chí lựa chọn giá hiệu lực. Ngày billing truyền vào là `YearMonth.atDay(1)`, DAO không tự thay targetDate.

## 4. Lỗi, transaction và lifecycle

- DAO giữ `SQLException` gốc, vendor code, SQLState và cause; không tạo thêm exception type trong Mốc này. MySQL **1062** = duplicate mã/ngày; **1452** = mã cha không tồn tại khi insert; **1451** = ngăn sửa/xóa khóa cha đang được tham chiếu; **3819** = CHECK; **1048** = NOT NULL. Không gom mọi SQLState 23000 thành duplicate. **1213/1205** là deadlock/timeout, không phải duplicate, không tự retry trong DAO.
- Dev-1 ánh xạ SQL thành kết quả nghiệp vụ/thông báo chung, không lộ SQL/credential ra UI. Kiểm tra trùng trước insert chỉ để UX; UNIQUE là cơ chế quyết định khi có race. Trạng thái/loại khoản thu phải được Dev-1 xác nhận lại khi lưu; cần duyệt cách khóa/transaction nếu yêu cầu chống thay trạng thái đồng thời (mục 7).
- Mỗi public DAO call mở connection mới qua `DatabaseConnection` (đã xác nhận fresh connection per call), đóng bằng try-with-resources. Read đóng ResultSet, PreparedStatement, Connection; không commit/rollback connection do nơi khác sở hữu.
- Insert sở hữu transaction: setAutoCommit(false), PreparedStatement `INSERT` cột tường minh bỏ id, `RETURN_GENERATED_KEYS`, kiểm tra affected row = 1 và key hợp lệ, commit rồi trả FeeRate. SQLException/RuntimeException trước khi commit thành công → rollback; lỗi rollback gắn suppressed, giữ nguyên lỗi gốc; đóng toàn bộ resources cả khi lỗi bind/map/key/commit. Không trả thành công nếu commit/close báo lỗi; lỗi mất kết nối khi commit có thể chưa biết kết quả, không retry mù.
- Seam test package-private `Connections.open() throws SQLException` tương tự FeeTypeDAO, không thêm public factory. Chưa có overload nhận Connection công khai: US-007 tự thực hiện SQL trên BillingTx connection của mình. DAO standalone không cung cấp atomic multi-call transaction.

## 5. Ownership, RBAC và US-007

| Owner | Sở hữu / dependency |
| --- | --- |
| Dev-2 | FeeRate, FeeRateRepository, FeeRateDAO, migration us006, DAO/model tests và MySQL IT |
| Dev-1 | FeeRateService/UI/validation nghiệp vụ, RBAC, adapter standalone FeeRate → AppliedRate và mapping lỗi |
| Dev-3 (PR #11) | AppliedRate, FeeRateLookup, BillingTx.loadInputs, snapshot billing và test tích hợp US-007 |

Source US-007 đã đọc định nghĩa `AppliedRate(String maKhoanThu, BigDecimal donGia, LocalDate ngayApDung)` và **`Optional<AppliedRate> FeeRateLookup.getEffectiveRate(String maKhoanThu, LocalDate atDate)`**, không checked SQLException. Revision 02 mục F.2 còn mô tả cũ `Optional<BigDecimal> findEffectiveRate(...)`: đề nghị Lead/Dev-3 cập nhật tài liệu cho khớp PR, không nhân bản interface để né khác biệt.

Adapter Dev-1 gọi repository standalone, map `maLoaiThu → maKhoanThu`, giữ nguyên giá và ngày hiệu lực của bản ghi, không thay bằng ngày lookup; chuyển SQLException thành unchecked lỗi hạ tầng theo contract PR. Không nuốt lỗi DB thành Optional.empty. US-006 không tạo AppliedRate/FeeRateLookup hoặc phụ thuộc chúng ở tầng DAO/model.

`BillingTx.loadInputs()` phải dùng **cùng JDBC connection/transaction** với toàn bộ generate; tự query fee_rates theo SQL trên cho từng khoản hoặc query set tương đương, không gọi DAO/adapter standalone mở connection mới. Dev-3 chịu trách nhiệm REPEATABLE READ/snapshot và kiểm chứng concurrent writes. Với cùng dữ liệu/targetDate, lookup standalone và loadInputs phải khớp. Thiếu giá phải đi theo MISSING_RATE của US-007, không dùng giá mặc định.

RBAC hiện tại cho ADMIN/ACCOUNTANT quyền `FEE_RATE_VIEW`, `FEE_RATE_CREATE`, `FEE_RATE_UPDATE`; RESIDENT/chưa đăng nhập bị từ chối. Dev-1 gọi AuthorizationService trước validation/truy vấn, không nhận role từ UI. Lưu giá mới dùng CREATE; quyền UPDATE hiện có không cho phép DAO ghi đè lịch sử. Chưa mở luồng UPDATE. DAO không tự đọc SessionContext; không đổi permission/role.

Hóa đơn đã chốt đọc snapshot giá/ngày hiệu lực từ invoice_lines do US-007 sở hữu. Thêm giá kể cả backdate không được tái tính hóa đơn cũ. Không có trigger DB chống sửa lịch sử/snapshot trong phạm vi này; API append-only không bảo vệ trước người có quyền ghi DB trực tiếp.

## 6. File dự kiến và test matrix Mốc 2

Production chỉ bốn file do Dev-2 sở hữu: `src/main/java/com/bluemoon/models/FeeRate.java`, `src/main/java/com/bluemoon/dao/FeeRateRepository.java`, `src/main/java/com/bluemoon/dao/FeeRateDAO.java`, `database/migrations/us006_fee_rates.sql`.

Test dự kiến: `src/test/java/com/bluemoon/models/FeeRateTest.java`, `src/test/java/com/bluemoon/dao/FeeRateDAOTest.java`, `src/test/java/com/bluemoon/dao/FeeRateMySqlIT.java`; handoff `docs/design/us-006-dao-handoff.md`. Không sửa US khác nếu chưa có chỉ đạo mới.

| Tầng | Trường hợp | Kỳ vọng |
| --- | --- | --- |
| Unit model/DAO | null, mã sai, ghi chú 255/256 code points, ngày ngoài range; 0/âm/null, max/overflow, 1.230/1.234 | reject trước I/O theo hợp đồng; giá hợp lệ scale 2 |
| Unit JDBC | bind mã chứa SQL literal; DATE/BigDecimal/null note; map hàng và generated key | không nối input vào SQL; đủ cột/đúng type; key hợp lệ |
| Unit JDBC | insert OK; lỗi execute/key/map/commit; rollback cũng lỗi | commit một lần khi OK; rollback khi lỗi; suppressed; đóng tài nguyên mọi nhánh |
| Unit JDBC | lỗi 1062/1452/3819/1213/1205 | giữ nguyên SQLException; không đổi thành empty hoặc duplicate sai |
| MySQL IT | US-005 trước US-006, chạy US-006 hai lần với dữ liệu có sẵn | dữ liệu không mất; PK/FK/CHECK/UNIQUE/charset/collation đúng; drift được phát hiện bằng đối chiếu metadata |
| MySQL IT | FK mã không tồn tại; sửa/xóa khóa cha đã tham chiếu; null và giá ≤ 0 | DB từ chối; parent/child không đổi; không tắt FOREIGN_KEY_CHECKS |
| MySQL IT | insert, history nhiều mã, note null/Unicode | id sinh đúng; history giảm dần; không lẫn mã, list rỗng khi chưa có giá |
| MySQL IT | target trước giá đầu / đúng ngày / giữa hai giá / sau tất cả; giá tương lai | empty / đúng giá tại ngày / mới nhất ≤ target; không chọn tương lai |
| MySQL IT | QL 7.000 từ 2026-01-01, 8.000 từ 2027-01-01 | 2026-11-01 và 2026-12-01 → 7.000; 2027-01-01 → 8.000 (AC-01) |
| MySQL IT | trùng mã/ngày, hai writer đồng thời; cùng ngày khác mã | 1062 cho duplicate, chỉ một bản ghi; khác mã thành công |
| MySQL IT | insert trong transaction thử nghiệm rồi lỗi/rollback; duplicate qua DAO | không để bản ghi mới dở dang; bản ghi cũ giữ nguyên; xác nhận rollback bằng connection độc lập |
| MySQL IT | DECIMAL 0.01, 7000.50, 99999999999999999.99 | round-trip chính xác BigDecimal, scale 2; không double; service/DAO chặn phần lẻ > 2 trước MySQL làm tròn |
| Dev-1 unit/integration | ADMIN/ACCOUNTANT, RESIDENT/no session; khoản DONG_GOP/ngừng áp dụng; duplicate/FK mapping | quyền kiểm trước truy vấn; rule nghiệp vụ đúng; UI không lộ SQL |
| Dev-3 phối hợp IT | chốt hóa đơn, thêm giá mới/backdate, đọc lại snapshot; loadInputs so với standalone; thêm giá đồng thời generate | hóa đơn cũ không đổi; lookup tương đương trên cùng dataset; generate dùng một snapshot/cùng connection |

MySQL IT opt-in `BLUEMOON_MYSQL_TEST=1`, profile `mysql-it`; dùng config/environment riêng, tuyệt đối không ghi credential vào repo. Trước bất kỳ DDL/DML cleanup nào phải kiểm tra catalog thực tế không null và kết thúc `_test`, MySQL >= 8.0.16, InnoDB và strict SQL mode; sai thì fail/stop trước ghi. Schema `_test` chuyên dụng, không chạy chung song song với US-005 IT hiện có (test đó DROP fee_types, sẽ xung đột FK mới). Chỉ cleanup bảng do fixture sở hữu theo thứ tự child trước parent; không DROP bảng billing của US-007. Test snapshot dùng fixture/schema riêng phối hợp Dev-3. Mốc 1 chưa chạy test production/MySQL và không tuyên bố các case trên đã pass.

## 7. Quyết định cần Lead duyệt và bàn giao

1. Duyệt API/model: Long id nullable chỉ cho input mới, insert trả persisted FeeRate, history DESC, Optional.empty cho mã chưa có dữ liệu, policy validation scale/date/note và giữ SQLException gốc.
2. Xác nhận head PR #11 và chữ ký source `getEffectiveRate → Optional<AppliedRate>` làm chuẩn; Dev-3 cập nhật phần mô tả cũ trong Revision 02. Dev-1 xác nhận adapter và loại unchecked lỗi hạ tầng (không thêm class US-007 vào US-006).
3. PO/Dev-1 chốt chính sách backdate và ngày seed BR-08. Đề xuất cho phép mọi ngày hợp lệ (SRS chưa cấm), append-only; chưa tự seed. Nếu cần cấm backdate thì chặn ở Service, không đổi schema tối thiểu.
4. Chốt consistency rule khi loại/trạng thái fee_types đổi đồng thời lúc save. Đề xuất Mốc 2 DAO insert kiểm tra/khóa hàng cha trong cùng transaction nếu Lead yêu cầu bảo đảm eligibility tại commit; khi đó cần duyệt rõ semantics lỗi nghiệp vụ/API trước code. Không khẳng định FK giải quyết được rule này. Nếu chỉ Service precheck, phải chấp nhận race và ghi giới hạn trong handoff.
5. Lead duyệt SQL và cho phép Mốc 2; xác nhận test DB riêng và kế hoạch phối hợp Dev-1/Dev-3. Không cần sửa FeeType hoặc production US-007 để review contract này.

**READY:** bản thiết kế schema/DAO và test matrix để Lead review. **BLOCKED Mốc 2:** chưa có phê duyệt API/transaction policy và lệnh implementation. Schema tối thiểu đã được PO duyệt theo yêu cầu giao việc; không coi đó là tự động duyệt các lựa chọn API ở trên. Không commit/push contract trước khi Lead duyệt.
