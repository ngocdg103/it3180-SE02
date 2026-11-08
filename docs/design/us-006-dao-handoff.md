# US-006 — Dev-2 Schema/DAO handoff (Mốc 2)

Ngày 08/11/2026. Branch `feature/us006-fee-rate-backend`, base `162d9ea4db222d3bb27d4a603a32f5496c983204` (origin/main sau fetch). Lead đã cho phép implementation, commit/push và mở PR; dừng trước merge. Contract Mốc 1 được giữ nguyên như tài liệu lịch sử; chỉ đạo Mốc 2 có ưu tiên cho các quyết định dưới đây.

## Files và API cuối cùng

- `database/migrations/us006_fee_rates.sql`: bảng InnoDB/utf8mb4, id BIGINT auto increment; mã VARCHAR(20) ascii/ascii_bin khớp parent US-005; don_gia DECIMAL(19,2) NOT NULL CHECK > 0; ngày DATE NOT NULL; ghi chú VARCHAR(255) nullable; UNIQUE `uq_fee_rates_type_date`; FK RESTRICT/RESTRICT. Không sửa schema.sql, migration US-005 hoặc seed BR-08.
- `src/main/java/com/bluemoon/models/FeeRate.java`: record immutable `(Long id, String maLoaiThu, BigDecimal donGia, LocalDate ngayApDung, String ghiChu)`. Null id cho input mới; id khác null phải dương. Giá chuẩn hóa scale 2 chính xác, không round; giới hạn 0.01..99999999999999999.99. Ngày 1000-01-01..9999-12-31; mã `[A-Z0-9_]{1,20}`; ghi chú tối đa 255 Unicode code points. Null bắt buộc → NPE, giá trị sai → IllegalArgumentException.
- `src/main/java/com/bluemoon/dao/FeeRateRepository.java`, `FeeRateDAO.java`: `FeeRate insert(FeeRate)`, `List<FeeRate> findHistory(String)`, `Optional<FeeRate> findEffectiveRate(String, LocalDate)`, tất cả throws SQLException. Constructor public nhận DatabaseConnection, seam Connections package-private dành cho test. History bất biến giảm dần ngày/id; lookup lấy MAX ngày ≤ target, không có thì empty. Không lọc lịch sử theo trạng thái hiện tại, không fallback so_tien_mac_dinh, không update/delete/upsert.
- Tests: `FeeRateTest`, `FeeRateDAOTest`, `FeeRateMySqlIT`. Tài liệu contract và handoff là hai file docs duy nhất thêm vào.

## Transaction và lỗi

Insert từ chối input có id trước I/O. Mỗi thao tác standalone mở/đóng connection riêng. Insert bắt đầu transaction, khóa parent `fee_types WHERE ma_loai_thu=? FOR UPDATE`, đọc `loai_thu/trang_thai` bằng locking current read rồi insert child và kiểm tra generated key. Parent phải BAT_BUOC và DANG_AP_DUNG tại thời điểm transaction ghi giá; khóa được giữ đến commit/rollback. PreparedStatement cho mọi SQL. Read/statement/generated keys đóng trước commit; connection đóng cả khi lỗi. Lỗi SQL/runtime trước khi commit thành công → rollback, lỗi rollback được suppressed; không retry khi commit không chắc chắn.

Đã đối chiếu US-005 FeeTypeDAO: update/deactivate UPDATE đúng một dòng parent theo PK rồi SELECT cùng transaction, không khóa child trước parent. US-006 cũng parent → child, nên không cần sửa US-005; thay trạng thái phải đợi parent lock. Nếu trạng thái đổi sau khi insert commit, bản ghi lịch sử vẫn hợp lệ và được giữ nguyên. Không hứa parent sẽ tiếp tục active vĩnh viễn. Deadlock ngoài luồng này vẫn có thể xảy ra, không retry mù.

SQLException JDBC được giữ nguyên vendor code/SQLState (1062 duplicate, 1452 FK, 3819 CHECK, 1213 deadlock, 1205 timeout). Không gán mã MySQL giả cho precheck: parent không tồn tại → SQLState **02000**, vendor 0; parent không đủ điều kiện → **45000**, vendor 0. Dev-1 cần map hai SQLState này riêng. Dữ liệu DB sai khi map → SQLException có cause. Không nuốt DB failure thành Optional.empty.

## Migration và test DB

Apply US-005 trước US-006, MySQL >= 8.0.16, strict SQL mode. CREATE IF NOT EXISTS chạy lại không xóa dữ liệu nhưng không sửa schema drift; đối chiếu SHOW CREATE TABLE/metadata trước deploy. DDL implicit commit, không có rollback migration tự động.

MySQL IT opt-in theo repo: `BLUEMOON_MYSQL_TEST=1`, DatabaseConfig đọc BLUEMOON_DB_* hoặc config/db.properties. Chỉ trỏ đến schema disposable chuyên dụng có suffix `_test`; fixture kiểm tra catalog thực tế trước DDL/DML và không DROP bảng, chỉ cleanup mã `US006_IT`. Dành riêng mã này cho fixture. Không chạy đồng thời với fixture US-005 (DROP fee_types). Chạy tuần tự theo profile mặc định; không bật JUnit parallel execution cho fixture này.

IT bao gồm migration hai lần giữ dữ liệu, metadata/collation/FK RESTRICT, CHECK/UNIQUE/NOT NULL, lịch sử/backdate/biên ngày, decimal max/0.01, hai writer duplicate, rollback sau insert thật bằng lỗi pre-commit, parent ngừng áp dụng/tự nguyện, và parent status update giữ khóa khiến insert chờ rồi đọc trạng thái mới.

## Verification

Đã chạy với JDK portable 17.0.17 và Maven 3.9.9 trong .tools ignored, Enforcer giữ bật và pass:

- `mvn clean verify`: BUILD SUCCESS, 352 tests, 0 failure/error/skip.
- `mvn -Pjavafx-smoke clean verify`: BUILD SUCCESS, 353 tests, 0 failure/error/skip.
- `git diff --check` và staged diff check: pass.

Lần chạy normal đầu bị sandbox chặn dependency network; chạy lại với network approval đã pass. Không skip Enforcer hoặc sửa pom.xml.

MySQL IT **chưa chạy**: không có config/db.properties hay environment BLUEMOON_DB_* / BLUEMOON_MYSQL_TEST được cấu hình. Không tự tạo database hoặc bật opt-in. Code IT được compile cùng test sources; chưa có bằng chứng chạy MySQL thật hoặc kiểm chứng locking trên server. QA/Lead phải chạy trên schema an toàn trước nghiệm thu DB.

## Handoff cho Dev-1 và Dev-3

Dev-1 dùng repository cho Service/UI, kiểm RBAC trước validation/I/O qua FEE_RATE_VIEW/CREATE; ADMIN/ACCOUNTANT được phép, RESIDENT/no session bị chặn. DAO đã kiểm eligibility trong transaction, Service có thể precheck để UX nhưng không thay thế DAO check. Backdate được phép theo contract; thêm giá không sửa lịch sử. Ghi chú null được giữ nguyên. Cần PO chốt ngày seed BR-08 trước khi thêm dữ liệu chuẩn.

AppliedRate và FeeRateLookup thuộc Dev-3; không tạo/sửa hai type này. Dev-1 cung cấp adapter standalone từ FeeRate sang Optional<AppliedRate>, map maLoaiThu → maKhoanThu, giữ ngày hiệu lực thật và chuyển SQLException thành unchecked failure phù hợp contract. Không trả empty khi DB lỗi.

Dev-3 BillingTx.loadInputs phải tự query fee_rates với cùng connection/snapshot của BillingDAO; tuyệt đối không gọi FeeRateDAO standalone trong transaction billing. SQL chuẩn: WHERE ma_loai_thu=? AND ngay_ap_dung<=? ORDER BY ngay_ap_dung DESC LIMIT 1. Ngày target là ngày đầu kỳ. Snapshot invoice_lines do US-007 sở hữu, hóa đơn đã chốt không được tính lại từ fee_rates. Test snapshot và tính nhất quán toàn tuyến là trách nhiệm phối hợp US-007, chưa được kiểm chứng ở PR này. Không sửa Service/UI, Billing Backend hoặc App Shell.

Commit/full SHA và URL PR được báo riêng cùng bàn giao để tránh tự tham chiếu commit chứa chính tài liệu này. Không merge PR.
