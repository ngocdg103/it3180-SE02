# US-002 — Thêm nhân khẩu vào hộ: Contract Review — Revision 01 (Dev-3 → Lead Dev)

Trạng thái: **chỉ báo cáo**. Chưa có production code, chưa tạo branch triển khai, chưa commit/push.
Quy ước: **[SRS]** = có trong tài liệu; **[LEAD]** = quyết định đã chốt; **[ĐỀ XUẤT]** = Dev-3 đề xuất, chờ duyệt; **[PO?]** = chờ PO.
Nguồn: `docs/srs/EP01` §2 (+BR-03 US-001), `EP03` §3.2 (BR-10), `EP06` §2.1 (RBAC), User Story US-002 (FE-012), WBS 1.1.2.x, code trên `origin/main` (đã gồm US-001 backend, US-018 `AuthorizationService`/`Permission`/`RoleBasedAuthorizationService`).

---
## A. Approved decisions [LEAD]
1. Bảng `residents`, FK `ma_ho → households(ma_ho)` cho phạm vi US-002 (không có `household_residents`).
2. Khóa chính `ma_nhan_khau`.
3. DB bảo đảm tối đa một chủ hộ mỗi hộ.
4. Tái sử dụng `HouseholdRepository` và `AuthorizationService` hiện có.
5. Không tạo lại Foundation, AuthService, RBAC. Không dùng `Supplier<Role>` cho Service mới.
6. Migration độc lập, không sửa `schema.sql` nếu chưa có lý do được duyệt.
Hệ quả: branch triển khai (sau khi duyệt Mốc 2) lấy nền từ **`origin/main`** — đã có US-001 + US-018. Câu hỏi 9 và 11 của bản trước (RBAC, tên bảng) được coi là đã trả lời.

**Final technical corrections (Lead, sau Revision 01) [LEAD]** — đã áp dụng ở B.3, B.4, B.7, C.1, D, E, F, G:
7. Dùng `ResidentDuplicateException` riêng; **không sửa** `DuplicateRecordException` của US-001.
8. Lock ordering nhất quán với các transaction US-001 (B.4a); nguy cơ deadlock và cách xử lý.
9. `confirmReplaceHeadId` cũ nhưng hộ hiện **không còn chủ hộ** → không tự chấp nhận xác nhận cũ (B.4b).
10. Bổ sung MySQL IT cho migration, generated column, unique index, FK, rollback, chạy migration lặp (F, mục 23–27).
11. `ResidentService` giữ nguyên API đã đề xuất (chữ ký, quyền). Chỉ `ResidentRepository`/`ResidentResult` (lớp mới, chưa tồn tại) đổi do các điểm trên.
12. Câu 9: PR #6 (Household–RBAC Integration) đã có → không tạo task trùng. Câu 11: chủ hộ cũ → `KHAC`. Câu 12: giữ `getHousehold`. Câu 16: chủ hộ đổi sau xác nhận → yêu cầu xác nhận lại.

---
## B. Technical design

### B.1 Model / DTO (package `com.bluemoon.models`, immutable như US-001) [ĐỀ XUẤT]
- `Resident`: `maNhanKhau` (long), `maHo`, `hoTen`, `ngaySinh`, `gioiTinh`, `soCccd` (nullable), `quanHeChuHo`, `ngayChuyenDen`.
- `Gender { NAM, NU }`; `RelationToHead { CHU_HO, VO, CHONG, CON, BO_ME, NGUOI_THUE, KHAC }`.
- `ResidentForm` (giá trị thô, Service parse/validate, như `HouseholdForm`): `maHo, hoTen, ngaySinh, gioiTinh, soCccd, quanHeChuHo, ngayChuyenDen`.
- `HouseholdMembers`: `maHo`, `List<Resident> members` (chủ hộ trước), `memberCount`, `Optional<Resident> head`. Không chứa `HouseholdProfile` để `getHousehold` chỉ cần một permission (xem B.5); UI lấy thông tin hộ từ `HouseholdService` có sẵn.

### B.2 Schema — `database/migrations/us002_residents.sql` (độc lập) [ĐỀ XUẤT]
```sql
CREATE TABLE IF NOT EXISTS residents (
  ma_nhan_khau    BIGINT NOT NULL AUTO_INCREMENT,
  ma_ho           VARCHAR(20) NOT NULL,
  ho_ten          VARCHAR(50) NOT NULL,
  ngay_sinh       DATE NOT NULL,
  gioi_tinh       ENUM('NAM','NU') NOT NULL,
  so_cccd         VARCHAR(12) NULL,
  quan_he_chu_ho  ENUM('CHU_HO','VO','CHONG','CON','BO_ME','NGUOI_THUE','KHAC') NOT NULL,
  ngay_chuyen_den DATE NOT NULL,
  head_ma_ho      VARCHAR(20) GENERATED ALWAYS AS (IF(quan_he_chu_ho = 'CHU_HO', ma_ho, NULL)) STORED,
  PRIMARY KEY (ma_nhan_khau),
  CONSTRAINT uq_residents_cccd     UNIQUE (so_cccd),     -- nhiều NULL vẫn hợp lệ
  CONSTRAINT uq_residents_one_head UNIQUE (head_ma_ho),  -- tối đa 1 chủ hộ/hộ, kể cả khi chạy đồng thời
  CONSTRAINT fk_residents_household FOREIGN KEY (ma_ho) REFERENCES households (ma_ho)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT chk_residents_cccd CHECK (so_cccd IS NULL OR REGEXP_LIKE(so_cccd, '^([0-9]{9}|[0-9]{12})$')),
  INDEX idx_residents_ho (ma_ho)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
- Cần MySQL ≥ 8.0.16 (đã là điều kiện US-001). MySQL không cho FK có `CASCADE/SET NULL` trên cột nền của cột generated STORED; ở đây dùng `RESTRICT` nên hợp lệ — **sẽ kiểm bằng MySQL IT trước khi nộp** (nếu máy chủ từ chối, phương án dự phòng: trigger hoặc cột `is_head TINYINT` + unique `(ma_ho, is_head_flag)`).
- Check `chk_residents_cccd` chấp nhận 9 hoặc 12 số vì SRS cho phép "12 chữ số (hoặc 9 số cũ)"; nếu PO bỏ 9 số (Q5) thì sửa regex, không ảnh hưởng thiết kế.
- Không hard delete (SRS không có). `utf8mb4` để lưu tên có dấu.
- `database/migrations/us002_seed.sql`: nhân khẩu cho các hộ trong `us001_seed.sql` (kiểm `ma_ho` thật ở Mốc 2).
- Báo Dev-1: bảng mới + FK tới `households`; thời điểm gộp vào `schema.sql` do Dev-1 quyết định.

### B.3 Repository / DAO [ĐỀ XUẤT – điều chỉnh so với bản trước]
Quyết định "có chủ hộ chưa / thay hay không" phải nằm **trong transaction đã khóa hộ**, không kiểm tra trước ở Service (tránh check-then-act). Do đó gộp `insert` và `insertReplacingHead` thành một hàm:
```java
public interface ResidentRepository {
    enum HeadPolicy { REJECT_IF_HEAD_EXISTS, REPLACE_HEAD_IF_UNCHANGED }

    sealed interface InsertOutcome permits Added, HeadExists, HeadChanged, HouseholdMissing { }
    record Added(Resident resident) implements InsertOutcome { }
    record HeadExists(Resident currentHead) implements InsertOutcome { }   // chưa xác nhận; không ghi gì
    /** Xác nhận cũ: expectedHeadId không còn là chủ hộ hiện tại (đổi người, hoặc hộ không còn chủ hộ). Không ghi gì. */
    record HeadChanged(Optional<Resident> currentHead) implements InsertOutcome { }
    record HouseholdMissing() implements InsertOutcome { }

    /** expectedHeadId: ma_nhan_khau của chủ hộ mà người dùng đã thấy và xác nhận thay; null nếu chưa xác nhận. */
    InsertOutcome insert(Resident resident, HeadPolicy policy, Long expectedHeadId)
            throws SQLException, ResidentDuplicateException;
    List<Resident> findByMaHo(String maHo) throws SQLException;
    Optional<Resident> findHead(String maHo) throws SQLException;
    int countByMaHo(String maHo) throws SQLException;              // cho US-007 (BR-05)
}
```
`ResidentDuplicateException` (mới, xem B.7) thay cho `DuplicateRecordException` trong mọi signature của `ResidentRepository`/`ResidentDAO`/`InMemoryResidentRepository`; `DuplicateRecordException` của US-001 giữ nguyên.
`ResidentDAO` (JDBC trên `DatabaseConnection` của Dev-1, PreparedStatement) + `InMemoryResidentRepository` (test và mock cho Dev-2). Existence hộ ở Service dùng `HouseholdRepository.findByMaHo` để trả lỗi sớm; DAO vẫn tự khóa và kiểm tra lại (nguồn sự thật).

### B.4 Transaction design — thay thế chủ hộ (yêu cầu chỉnh 1)
Một `Connection`, `setAutoCommit(false)`, isolation mặc định InnoDB (REPEATABLE READ; mọi đọc quyết định dùng *locking read* nên luôn thấy bản mới nhất đã commit).
```
BEGIN
 1. SELECT ma_ho FROM households WHERE ma_ho = ? FOR UPDATE
      0 dòng            -> ROLLBACK, trả HouseholdMissing
 2. [nếu quan_he = CHU_HO]
    SELECT ma_nhan_khau, ... FROM residents WHERE ma_ho = ? AND quan_he_chu_ho = 'CHU_HO' FOR UPDATE   -- H hoặc rỗng
      H có, expectedHeadId = null                              -> ROLLBACK, trả HeadExists(H)           (AC-03, chưa ghi gì)
      H có, expectedHeadId != H.id                             -> ROLLBACK, trả HeadChanged(H)          (xác nhận cũ)
      H có, expectedHeadId == H.id                             -> UPDATE residents SET quan_he_chu_ho = 'KHAC' WHERE ma_nhan_khau = H.id
      rỗng, expectedHeadId != null                             -> ROLLBACK, trả HeadChanged(empty)      (xác nhận cũ, B.4b)
      rỗng, expectedHeadId = null                              -> tiếp tục (thêm chủ hộ đầu tiên)
    [nếu quan_he != CHU_HO] expectedHeadId bị bỏ qua: không bao giờ demote ai.
 3. INSERT INTO residents (...)      -- lỗi 1062 -> ROLLBACK -> ResidentDuplicateException(SO_CCCD | CHU_HO)
 4. COMMIT                           -- lỗi bất kỳ ở bước 2-4 -> ROLLBACK, chủ hộ cũ nguyên vẹn
```
Bước đọc trạng thái căn hộ cho P1 (C.1) là **đọc thường (không khóa)** sau bước 1 — xem B.4a.
- Khóa hộ ở bước 1 là điểm tuần tự duy nhất cho **mọi** ghi nhân khẩu của hộ đó → trong các transaction US-002, thứ tự khóa luôn "hộ → nhân khẩu". Phạm vi chứng minh "không deadlock" chỉ gồm các transaction US-002; tương tác với US-001 xem B.4a (đã kiểm code thực tế).
- Demote trước, insert sau, cùng transaction: unique `uq_residents_one_head` không bị vi phạm giữa chừng; nếu insert lỗi thì demote cũng bị rollback (không có hộ "mất" chủ hộ).
- Unique index là **hàng rào cuối** nếu có đường ghi nào đó bỏ qua bước 1 (script, tool khác).

**Hai request thay chủ hộ gần như đồng thời** (hộ có chủ hộ H0; A muốn thêm "Trần B", B muốn thêm "Lê C", cả hai đã thấy hộp thoại về H0 và xác nhận với `expectedHeadId = H0`):
| Bước | Request A | Request B | Kết quả |
|---|---|---|---|
| 1 | giữ khóa hộ, demote H0 → KHAC, insert B, commit | chờ khóa hộ (InnoDB lock wait) | A: `SUCCESS`, B trở thành chủ hộ |
| 2 | — | được khóa; thấy chủ hộ hiện là Trần B ≠ H0 | B: `HeadChanged(Trần B)` → `CONFIRMATION_REQUIRED` (lý do `CONFIRMATION_STALE`), nói rõ "Hộ này đã có chủ hộ Trần B…", **không ghi gì** |
| 3 | — | người dùng B xác nhận lại với `expectedHeadId = Trần B` | thay Trần B → KHAC, thêm Lê C |
Kết quả cuối luôn đúng một chủ hộ, không ai bị thay mà chưa được người dùng xác nhận đúng người ([LEAD] Q16: chủ hộ đổi sau xác nhận → **yêu cầu xác nhận lại**, không thay luôn chủ hộ mới nhất). Chờ khóa quá `innodb_lock_wait_timeout` (1205): rollback, `SYSTEM_ERROR` chung, gửi lại được. Deadlock 1213: xem B.4a.
Hai request thêm **Chủ hộ vào hộ chưa có chủ hộ**: tuần tự hóa như trên, người thứ hai nhận `CONFIRMATION_REQUIRED`. Hai request cùng **CCCD**: người thứ hai nhận `DUPLICATE(so_cccd)`, rollback.
→ Chữ ký Service dùng `Long confirmReplaceHeadId` (đã nêu ở Revision 01, giữ nguyên, B.5); phương án `boolean` bị loại vì không phát hiện được xác nhận cũ.

### B.4a Lock ordering, nguy cơ deadlock (đã kiểm `HouseholdDAO` trên `origin/main`)
**Thực tế code US-001**
| Thao tác | Câu lệnh | Khóa / thứ tự |
|---|---|---|
| `HouseholdDAO.insert` | transaction: `INSERT apartments` → `INSERT households` | tạo dòng mới, thứ tự **apartments → households**; kiểm FK khi insert `households` lấy khóa S trên dòng cha `apartments` (của chính mình, vừa tạo) |
| `HouseholdDAO.update` | **một** câu `UPDATE apartments a JOIN households h ON h.so_can_ho = a.so_can_ho SET a.tang…, a.dien_tich…, a.trang_thai…, h.ngay_nhan_nha… WHERE h.ma_ho = ?` (autocommit, 1 câu) | khóa X trên **cả** dòng `apartments` và dòng `households`; **thứ tự khóa do optimizer quyết định** (`WHERE h.ma_ho` là PK nên thường quét `households` trước, nhưng không được code bảo đảm) |
| `findByMaHo/BySoCanHo/findAll` | `SELECT` thường (JOIN) | đọc nhất quán, không khóa |
| Không có transaction nào của US-001 sửa `households.so_can_ho` hay `ma_ho` (khóa bất biến, FK `ON UPDATE RESTRICT`). |

**Thứ tự khóa chuẩn đề xuất** — theo chiều FK cha → con: `apartments → households → residents`. Mọi transaction hiện tại và tương lai phải lấy khóa theo thứ tự này (US-001 insert đã tuân thủ; US-001 update chưa được bảo đảm).

**Nguy cơ deadlock cụ thể:** nếu transaction nhân khẩu lấy `households` X rồi xin khóa `apartments` (ví dụ `SELECT … FROM apartments FOR SHARE` để kiểm trạng thái P1), còn `UPDATE` của US-001 đã giữ khóa X `apartments` và đang chờ `households` → vòng chờ (R giữ households, chờ apartments; U giữ apartments, chờ households). InnoDB sẽ phát hiện và rollback một bên bằng lỗi 1213; không mất dữ liệu nhưng một thao tác thất bại ngẫu nhiên.

**Phương án (đề xuất):**
1. **Transaction US-002 chỉ khóa `households` (rồi `residents`), không bao giờ khóa `apartments`.** Trạng thái căn hộ cho P1 đọc bằng `SELECT` thường *sau khi* đã giữ khóa X `households` (cùng transaction, lần đọc đầu tiên không khóa nên snapshot được lập sau khi có khóa). Khi đó R chỉ giữ một khóa cha → R không thể là một phía của vòng chờ với U.
2. Vì U luôn gán `h.ngay_nhan_nha` nên luôn lấy X trên dòng `households`; do đó khi R đã giữ X `households`, U hoặc đã commit hoặc chưa sửa gì → R luôn thấy trạng thái căn hộ nhất quán với một thứ tự tuần tự (R trước U hoặc U trước R). Hệ quả cần ghi nhận: nếu U (trước hoặc sau) đổi trạng thái sang `Căn hộ trống` *sau* khi R thêm nhân khẩu thì bất biến BR-03 "trống ⇒ 0 nhân khẩu" **không thể được bảo đảm từ phía US-002** — US-001 update sẽ phải kiểm số nhân khẩu (liên quan Q3, [PO?]; chưa đề xuất sửa US-001 ở Mốc này).
3. Không sửa `HouseholdDAO`. Tùy chọn gia cố (cần Lead duyệt riêng, ngoài phạm vi US-002): cho `HouseholdDAO.update` lấy `SELECT … FROM households WHERE ma_ho=? FOR UPDATE` trước rồi mới `UPDATE`, để thứ tự là cố định thay vì phụ thuộc optimizer.
4. Quy tắc cho các transaction tương lai (US-007 billing, US-003…): nếu cần khóa nhiều bảng thì theo `apartments → households → residents`; đọc số liệu thì dùng `SELECT` thường (không khóa), vì `countByMaHo` không cần khóa.
5. Deadlock 1213 vẫn có thể xảy ra ngoài các chứng minh trên (ví dụ chồng chéo trên unique `uq_residents_cccd` giữa các hộ khác nhau khi có xung đột khóa trùng key). Xử lý: DAO rollback; **tự thử lại đúng một lần chỉ với 1213** (an toàn vì chưa commit gì và toàn bộ kiểm tra BR-04/xác nhận được chạy lại dưới khóa); lần hai vẫn 1213 hoặc 1205 → `SYSTEM_ERROR` thông báo chung. Không thử lại với lỗi khác.
6. Kiểm chứng ở MySQL IT (F, mục 25–27): chạy đồng thời US-002 insert với `HouseholdDAO.update` nhiều vòng, khẳng định không có 1213 thoát ra ngoài, và ghi `EXPLAIN` của câu UPDATE US-001 làm bằng chứng về thứ tự thực tế trên MySQL 8.4 (không dùng để giả định, chỉ để báo Lead).

### B.4b Xác nhận cũ khi hộ hiện không còn chủ hộ
Tình huống: người dùng thấy hộp thoại về chủ hộ H và gửi `confirmReplaceHeadId = H.id`, nhưng lúc xử lý hộ **không còn chủ hộ** (H bị đổi quan hệ hoặc bị gỡ bằng đường khác, ví dụ US-003 sau này hoặc chỉnh trực tiếp DB).
- Quy tắc: `confirmReplaceHeadId != null` chỉ hợp lệ khi **đúng** `H.id` đang là chủ hộ ở thời điểm giữ khóa. Hộ rỗng chủ hộ + xác nhận khác null ⇒ `HeadChanged(Optional.empty())`, **không ghi gì**, không tự chấp nhận xác nhận cũ và không tự coi như "thêm chủ hộ đầu tiên".
- Service trả `CONFIRMATION_REQUIRED` (lý do `CONFIRMATION_STALE`), thông báo [ĐỀ XUẤT]: "Thông tin chủ hộ của hộ này đã thay đổi. Hộ hiện chưa có chủ hộ. Vui lòng kiểm tra lại và lưu lại." UI hiển thị rồi để người dùng bấm Lưu lại; lần gửi lại có `confirmReplaceHeadId = null` và đi tiếp theo nhánh "hộ chưa có chủ hộ".
- `confirmReplaceHeadId != null` nhưng quan hệ của form khác `CHU_HO` → bị bỏ qua, không bao giờ demote ai (và không phải lỗi).
- Cùng nguyên tắc cho `HeadChanged(chủ hộ khác)`: mục Q16.

### B.5 Service API [ĐỀ XUẤT]
```java
public interface ResidentService {
    /**
     * Requires Permission.RESIDENT_PROFILE_CREATE. confirmReplaceHeadId == null: chưa xác nhận.
     * Nếu form có quan hệ CHU_HO và hộ đã có chủ hộ -> CONFIRMATION_REQUIRED (data: chủ hộ hiện tại);
     * gọi lại với confirmReplaceHeadId = ma_nhan_khau của chủ hộ đó để thay (BR-04, AC-03).
     */
    ResidentResult<Resident> createForHousehold(ResidentForm form, Long confirmReplaceHeadId);

    /** Requires Permission.RESIDENT_PROFILE_VIEW. NOT_FOUND nếu hộ không tồn tại. */
    ResidentResult<HouseholdMembers> getHousehold(String maHo);
}
```
- `ResidentResult<T>`: `status()`, `message()`, `data()`, `fieldErrors()` (khóa là tên trường SRS). Status: `SUCCESS, VALIDATION_ERROR, DUPLICATE, NOT_FOUND, CONFIRMATION_REQUIRED, SYSTEM_ERROR`. Class riêng, không đụng `HouseholdResult`. Với `CONFIRMATION_REQUIRED`, thêm `Optional<Resident> currentHead()` (chủ hộ UI cần hiện và gửi lại `id`) và `confirmationReason()` ∈ {`HEAD_EXISTS`, `CONFIRMATION_STALE`}. Đây là lớp mới nên không phải thay đổi API đã duyệt; chữ ký `ResidentService` không đổi.
- Map lỗi DAO → Service: `Added` → `SUCCESS`; `HeadExists` → `CONFIRMATION_REQUIRED/HEAD_EXISTS`; `HeadChanged` → `CONFIRMATION_REQUIRED/CONFIRMATION_STALE`; `HouseholdMissing` → `NOT_FOUND`; `ResidentDuplicateException(SO_CCCD)` → `DUPLICATE` (`so_cccd`); `ResidentDuplicateException(CHU_HO)` (chỉ xảy ra khi có ghi bỏ qua khóa) → đọc lại chủ hộ, có thì `CONFIRMATION_REQUIRED/CONFIRMATION_STALE`, không có thì `SYSTEM_ERROR`; `SQLException` khác → `SYSTEM_ERROR`.
- Constructor: `ResidentServiceImpl(ResidentRepository, HouseholdRepository, AuthorizationService, Clock)`. Không `SessionContext`/`Supplier<Role>` trực tiếp; Service gọi `authorization.requirePermission(...)` **trước** mọi truy vấn (kể cả kiểm tra tồn tại hộ, để không lộ sự tồn tại của hộ cho người không có quyền).
- Không tạo/sửa `HouseholdService`, `AuthorizationService`, `RoleBasedAuthorizationService`.
- Gọi chặn trên JDBC; UI chạy ngoài JavaFX thread.

### B.6 Authorization (yêu cầu chỉnh 5) — đối chiếu `Permission` + ma trận RBAC trên `origin/main`
Ma trận SRS EP06 §2.1 "Hồ sơ nhân khẩu": ADMIN toàn quyền, ACCOUNTANT chỉ xem, RESIDENT không có quyền. `RoleBasedAuthorizationService` hiện: `RESIDENT_PROFILE_VIEW → {ADMIN, ACCOUNTANT}`, `RESIDENT_PROFILE_CREATE → {ADMIN}` (đúng khớp, **không cần thêm Permission**).
| Method | Permission | ADMIN | ACCOUNTANT | RESIDENT | Chưa đăng nhập |
|---|---|---|---|---|---|
| `createForHousehold` | `RESIDENT_PROFILE_CREATE` | Cho phép | `AccessDeniedException` | `AccessDeniedException` | `AccessDeniedException` |
| `getHousehold` | `RESIDENT_PROFILE_VIEW` | Cho phép | Cho phép | `AccessDeniedException` | `AccessDeniedException` |
- Chưa đăng nhập: `canAccess` trả false (không có account) → `requirePermission` ném `AccessDeniedException("Bạn không có quyền thực hiện chức năng này.")`; không phân biệt với thiếu quyền, và không có đường rò rỉ qua `NOT_FOUND`.
- `RESIDENT_PROFILE_UPDATE` thuộc US-003, ngoài phạm vi.
- Lưu ý: `HouseholdServiceImpl` (US-001) vẫn dùng `Supplier<Role>` riêng; US-002 không sửa. Đề nghị Lead xếp việc chuyển US-001 sang `AuthorizationService` thành task riêng.

### B.7 `ResidentDuplicateException` thay cho sửa `DuplicateRecordException` — chưa sửa gì
API hiện tại của US-001 trên `origin/main` (giữ nguyên, chỉ để đối chiếu):
```java
public final class DuplicateRecordException extends Exception {
    public enum Field { SO_CAN_HO, MA_HO }
    public DuplicateRecordException(Field field, Throwable cause) { super("Duplicate " + field, cause); this.field = field; }
    public Field field() { return field; }
}
```
Nơi dùng: `HouseholdDAO` (dòng 52, 118–121), `InMemoryHouseholdRepository`, `HouseholdServiceImpl` (dòng 82: `== Field.MA_HO ? … : so_can_ho`), test `HouseholdMySqlIT`. Không có `switch` trên enum này.
**[LEAD] Quyết định: dùng `ResidentDuplicateException` riêng; `DuplicateRecordException` của US-001 không bị sửa** (phương án "thêm 2 hằng enum" ở Revision 01 bị loại).
```java
package com.bluemoon.dao;

/** A US-002 unique constraint was violated. Distinct from US-001's DuplicateRecordException. */
public final class ResidentDuplicateException extends Exception {
    public enum Field { SO_CCCD, CHU_HO }
    private final Field field;
    public ResidentDuplicateException(Field field, Throwable cause) { super("Duplicate " + field, cause); this.field = field; }
    public Field field() { return field; }
}
```
**Exception mapping trong `ResidentDAO`** (dựa tên constraint trong thông báo MySQL, định dạng `'residents.<tên_key>'` ở 8.0.19+ gồm 8.4):
| Lỗi MySQL | Điều kiện | Kết quả |
|---|---|---|
| 1062 | message chứa `residents.uq_residents_cccd` | `ResidentDuplicateException(SO_CCCD)` |
| 1062 | message chứa `residents.uq_residents_one_head` | `ResidentDuplicateException(CHU_HO)` |
| 1062 | tên key khác/không nhận ra | ném lại `SQLException` (không mặc định về một field, khác `HouseholdDAO.duplicateField`) |
| 1452 (FK) | `fk_residents_household` | `HouseholdMissing` (chỉ còn là hàng rào cuối, vì bước 1 đã khóa/kiểm hộ) |
| 3819 (CHECK) | `chk_residents_cccd` | `SQLException` → Service `SYSTEM_ERROR` (Service đã validate, nên đây là lỗi lập trình/dữ liệu bên ngoài) |
| 1213 (deadlock) | — | rollback, thử lại 1 lần (B.4a #5), sau đó `SYSTEM_ERROR` |
| 1205 (lock wait timeout) | — | rollback, `SYSTEM_ERROR`, không thử lại |
| khác | — | rollback, `SQLException` → `SYSTEM_ERROR` |
Rollback luôn thực hiện trước khi ném (mẫu như `HouseholdDAO`: `rollback(connection, failure)` giữ suppressed exception).
Mọi chữ ký `throws ... DuplicateRecordException` ở B.3 đã được đổi thành `ResidentDuplicateException`. Câu 13 vì vậy chỉ còn phần migration/Dev-1.

### B.8 Tích hợp UI Sprint 1
- Dev-2: form "Thêm nhân khẩu" + danh sách thành viên trong `HouseholdDetailView`, chỉ phụ thuộc `ResidentService`; dùng `InMemoryResidentRepository` hoặc fake cho tới khi nối thật; Controller không SQL, không tự kiểm tra rule; luồng `CONFIRMATION_REQUIRED` giống `confirmAreaChange` của US-001 (lưu `Resident` chủ hộ hiện tại từ `data()` để gửi lại `confirmReplaceHeadId`).
- Bàn giao: `us-002-api-contract.md`, DTO/enum/result, fake service, seed. Cùng lifecycle `dispose()` đã chốt cho các module khác.

---
## C. Business rules — căn chỉnh với SRS

### C.1 Căn hộ trống (yêu cầu chỉnh 2)
- [SRS] US-001 BR-03: trạng thái `Căn hộ trống` → "danh sách thành viên nhân khẩu sẽ bằng 0". EP03 BR-10: căn hộ không có người ở → nhân khẩu = 0 → phí vệ sinh = 0.
- [SRS] US-002 không có quy tắc nào về trạng thái căn hộ khi thêm nhân khẩu; AC chỉ yêu cầu "hộ/căn hộ đã tồn tại".
- Hệ quả mâu thuẫn: thêm nhân khẩu vào hộ `CAN_HO_TRONG` làm BR-03/BR-10 mâu thuẫn dữ liệu (trạng thái "trống" nhưng có người).
- Phương án (chưa có quy tắc được duyệt, **không tự đổi trạng thái căn hộ**):
  - **P1 (khuyến nghị làm mặc định cho tới khi PO trả lời):** từ chối với `VALIDATION_ERROR` (field `ma_ho`), thông báo hướng dẫn "Căn hộ đang ở trạng thái Căn hộ trống. Hãy cập nhật trạng thái căn hộ trước khi thêm nhân khẩu." — người dùng ADMIN tự đổi trạng thái qua US-001 (AC-03 của US-001 đã có); giữ nguyên bất biến BR-03. Thông báo này là [ĐỀ XUẤT], không có trong SRS.
  - P2: cho thêm, không đổi trạng thái → dữ liệu mâu thuẫn BR-03; chỉ chọn nếu PO xác nhận "Căn hộ trống" chỉ là nhãn tham khảo.
  - P3: tự chuyển sang `Đang ở` → **bị loại** (Lead cấm khi chưa có quy tắc duyệt).
- Hiện thực P1: trạng thái căn hộ được đọc bằng `SELECT` thường (không khóa `apartments`) trong transaction của DAO, sau khi giữ khóa `households` (xem B.4a — khóa `apartments` có nguy cơ deadlock với `HouseholdDAO.update`; bản Revision 01 đề xuất `FOR SHARE` đã được thay). Nếu PO chọn P2 thì bỏ kiểm tra này, không ảnh hưởng schema. Giới hạn đã biết: US-001 update có thể đổi sang `Căn hộ trống` sau khi đã có nhân khẩu (B.4a #2), cần quyết định PO ở Q3.
- `Cho thuê` + quan hệ `Người thuê`: SRS không ràng buộc → không thêm rule (Q3).

### C.2 Tính phí theo nhân khẩu (yêu cầu chỉnh 3)
**Được SRS xác nhận**
- EP01 BR-05 / EP03 §3.2: `Tiền vệ sinh = Số nhân khẩu đang ở của căn hộ × Đơn giá VS` (6.000 đ/người/tháng); EP03 BR-10: căn hộ không người ở → 0.
- US-007 (Sprint 1) tính phí theo diện tích, đơn giá và nhân khẩu; thiếu dữ liệu → chỉ ra dữ liệu thiếu, không tính sai.
- Dữ liệu nhân khẩu **bắt buộc cho phép tính**: liên kết nhân khẩu → hộ (`ma_ho`, FK) và khả năng đếm số người của hộ. Các trường còn lại của US-002 (họ tên, ngày sinh, giới tính, CCCD, quan hệ, ngày chuyển đến) phục vụ quản lý, không đi vào công thức.
- US-006/008/009 (đơn giá, ghi nhận thu, công nợ) không dùng dữ liệu nhân khẩu.
**Không được SRS xác nhận (đề xuất / cần PO)**
- "Đang ở", "thường trú/tạm trú" (BR-05) không có trường, enum hay quy tắc trong US-002; US-003/US-004 (cập nhật trạng thái, biến động cư trú) không thuộc Sprint 1.
- [ĐỀ XUẤT] Trong Sprint 1 `countByMaHo` đếm **mọi** dòng của hộ (vì chưa có đường chuyển đi/xóa). Mở rộng (cờ cư trú, `ngay_chuyen_di`) chờ US-003/004 và PO — **không thêm cột trong migration này**.
- Chưa rõ: người chuyển đến giữa kỳ có tính trọn kỳ không (theo `ngay_chuyen_den`?) — Q14.
- US-007 (Dev-3 dự kiến) dùng `ResidentRepository.countByMaHo`; không cần API mới.

### C.3 Validation theo SRS (yêu cầu chỉnh 4)
| Trường | Quy tắc **chính xác theo SRS** | Thông báo SRS |
|---|---|---|
| `ho_ten` | bắt buộc; 2–50 ký tự; chỉ chữ cái và khoảng trắng | "Họ tên không hợp lệ (từ 2-50 ký tự, không chứa ký tự đặc biệt)!" |
| `ngay_sinh` | bắt buộc; nhỏ hơn ngày hiện tại | "Ngày sinh không hợp lệ!" |
| `gioi_tinh` | bắt buộc; `Nam` hoặc `Nữ` | "Vui lòng chọn giới tính!" |
| `so_cccd` | bắt buộc nếu tuổi ≥ 14 tính đến thời điểm nhập; đúng 12 chữ số (hoặc 9 số cũ); duy nhất toàn hệ thống | "CCCD phải đúng 12 chữ số và không được trùng lặp!"; thiếu CCCD ≥ 14 tuổi (AC-02): "Công dân từ 14 tuổi trở lên bắt buộc phải nhập số CCCD!" |
| `quan_he_chu_ho` | bắt buộc; một trong 7 giá trị | "Vui lòng chọn quan hệ với chủ hộ!" |
| `ngay_chuyen_den` | bắt buộc; không lớn hơn ngày hiện tại (hôm nay hợp lệ) | "Ngày chuyển đến không hợp lệ!" |
| `ma_ho` | bắt buộc; khóa ngoại; chọn từ danh sách hộ hợp lệ | "Vui lòng chọn hộ gia đình/căn hộ!" |
Cách đọc [ĐỀ XUẤT] để hiện thực được: tuổi tính theo sinh nhật đầy đủ so với ngày hôm nay (`Clock`), đúng 14 tuổi hôm nay vẫn bắt buộc; `ho_ten` dùng `\p{L}` để nhận chữ có dấu, trim và gộp khoảng trắng liên tiếp; mọi lỗi gom vào `fieldErrors`.
**Chưa rõ — ghi thành open question, không tự suy diễn** (chi tiết ở mục E): CCCD 9 số (Q5a), người dưới 14 tuổi (Q5b), không có CCCD (Q5c), quan hệ ngày tháng (Q7), ký tự trong họ tên (Q8), người trùng không CCCD (Q6).
Hiện thực Mốc 2 chỉ áp dụng phần SRS ghi rõ; với phần chưa rõ sẽ chọn cách đọc **bảo thủ nhất** nêu trong từng câu hỏi và có test khóa hành vi để đổi dễ.

### C.4 Ownership (yêu cầu chỉnh 7) — chờ PO xác nhận
- WBS 1.1.2.2.1: cột bàn giao ghi "Dev-1 → người làm Service: model/DAO methods + DB schema" (Dev-1 phụ trách phần model/DAO/schema rồi giao cho người làm Service); WBS 1.1.2.2.3 (Service) phụ thuộc 1.1.2.2.1, 1.1.2.2.2, 1.1.1.2.3. Cột Owner theo từng dòng của hai task này tôi chưa đọc ra được từ file xlsx (chuỗi trùng lặp dùng chung) nên **không khẳng định** ai là owner chính thức.
- SRS README: US-002 giao cho Dev-2, Dev-3, QA. Phân công dự kiến theo chỉ đạo Lead: Dev-3 backend (DAO + Service + migration), Dev-2 UI.
- **Trạng thái: [PO?]** — Mốc 2 chỉ bắt đầu theo phân công dự kiến sau khi Lead/PO xác nhận; nếu Dev-1 giữ DAO/schema thì Dev-3 chỉ làm Service + contract.

---
## D. Planned files (chờ duyệt Mốc 2)
Branch: `feature/us002-resident-backend` từ `origin/main` (chưa tạo).
**Mới:** `database/migrations/us002_residents.sql`, `database/migrations/us002_seed.sql`; `models/{Resident, Gender, RelationToHead, ResidentForm, HouseholdMembers}.java`; `dao/{ResidentRepository, ResidentDAO, InMemoryResidentRepository, ResidentDuplicateException}.java`; `services/{ResidentService, ResidentServiceImpl, ResidentResult}.java`; `docs/design/us-002-api-contract.md`; tests: `services/ResidentServiceTest.java`, `dao/ResidentMySqlIT.java`.
**Sửa file US-001:** không có. `DuplicateRecordException.java` **không được sửa** [LEAD].
**Không đụng:** `DuplicateRecordException`, `HouseholdService*`, `HouseholdDAO`, `HouseholdRepository`, `AuthorizationService`, `RoleBasedAuthorizationService`, `SessionContext`, `schema.sql`, `BlueMoonApplication`, UI của Dev-2, nhánh US-005.

---
## E. Business rules awaiting PO — đầy đủ 13 câu hỏi trước + câu mới
Câu 1 và 11 đã được Lead trả lời (A.1); câu 9 và 10 đã được xử lý một phần (A.4–A.5, C.4); vẫn liệt kê để lưu vết.
1. **Mô hình dữ liệu** — một bảng `residents` hay thêm `household_residents` theo tên WBS? *Đã chốt [LEAD]: bảng `residents`.* Vẫn cần PO xác nhận US-004 (lịch sử cư trú) sẽ mở rộng sau, không cần ngay Sprint 1.
2. **Chủ hộ** — BR-04 "bắt buộc đúng 1 chủ hộ": người đầu tiên thêm vào hộ trống có bắt buộc là Chủ hộ không? Có cho thêm Vợ/Con khi hộ chưa có Chủ hộ không? Hộ có 0 nhân khẩu có hợp lệ (BR-03 cho phép)? *Tạm thời: không ép, DB chỉ bảo đảm "tối đa 1" theo [LEAD].*
3. **Căn hộ trống / Cho thuê** — hộ `CAN_HO_TRONG` có được thêm nhân khẩu không (BR-03 nói danh sách = 0)? Nếu có, trạng thái căn hộ có tự đổi sang `Đang ở` không? `Người thuê` chỉ hợp lệ khi `Cho thuê`? *Tạm thời P1 (C.1); không tự đổi trạng thái.*
4. **Phí vệ sinh (BR-05)** — chỉ đếm người "thường trú/tạm trú đang ở" nhưng US-002 không có trường thường trú/tạm trú hay trạng thái cư trú. Có cần thêm trường không, hay đếm mọi nhân khẩu cho tới khi US-004 có trạng thái? *Tạm thời: đếm mọi dòng (C.2).*
5. **CCCD** — (a) 9 số cũ có chấp nhận cho mọi tuổi không, hay chỉ 12 số? (b) Người dưới 14 tuổi nhập CCCD có được không, và vẫn phải đúng định dạng/duy nhất? (c) Người dưới 14 tuổi không có CCCD: được bỏ trống? (d) CCCD 9 số và 12 số của cùng một người có tính là trùng không (không chuẩn hóa)? *Tạm thời: 9 hoặc 12 số hợp lệ; <14 tuổi để trống được; có nhập thì vẫn validate; so khớp chuỗi nguyên văn.*
6. **Trùng người ngoài CCCD** — trẻ em chưa có CCCD cùng họ tên + ngày sinh + hộ có bị coi là trùng không? *Tạm thời: không chặn.*
7. **Quan hệ ngày tháng** — `ngay_chuyen_den` có phải ≥ `ngay_sinh` không; có phải ≥ `ngay_nhan_nha` của hộ không? "Sinh năm 1990" ở AC-01: nhập năm hay ngày đầy đủ (bảng dữ liệu yêu cầu Date)? *Tạm thời: chỉ áp dụng ràng buộc SRS ghi rõ; ngày sinh đầy đủ.*
8. **Họ tên** — có chấp nhận dấu gạch nối/dấu nháy (VD "Ánh-Dương") không? SRS nói "chỉ chữ cái và khoảng trắng". *Tạm thời: chỉ chữ + khoảng trắng.*
9. **RBAC** — *Đã chốt [LEAD]: dùng `AuthorizationService`, không `Supplier<Role>`.* Việc chuyển `HouseholdServiceImpl` sang `AuthorizationService` **đã có PR #6 (Household–RBAC Integration)** → không tạo task trùng; US-002 không đụng `HouseholdServiceImpl`. *(Tôi chưa mở/kiểm PR #6 trong phiên này — không có `gh`.)*
10. **Phân công** — Dev-1/Dev-3 cho DAO/Service, Dev-2 cho UI: *[PO?] chờ xác nhận (C.4).*
11. **Xác nhận đổi chủ hộ** — *Đã chốt [LEAD]: chủ hộ cũ chuyển sang `KHAC`.*
12. **Tên `getHousehold`** — *Đã chốt [LEAD]: giữ `getHousehold`.*
13. **Schema/Exception** — *Exception đã chốt [LEAD]: `ResidentDuplicateException` riêng.* Còn lại: Dev-1 có đồng ý nhận migration `us002_residents.sql` (không sửa `schema.sql`) không? *[Dev-1/Lead]*
**Câu hỏi mới phát sinh từ Revision 01**
14. Người chuyển đến giữa kỳ thu phí có được tính vào số nhân khẩu của kỳ đó không, và tính theo ngày/tháng nào (liên quan `ngay_chuyen_den`, US-007)? *[PO?]*
15. Tuổi ≥ 14 tính tại thời điểm nhập hồ sơ (hôm nay) hay tại `ngay_chuyen_den`? SRS ghi "tính đến thời điểm nhập". *Tạm thời: hôm nay.*
16. *Đã chốt [LEAD]:* nếu chủ hộ thay đổi sau khi người dùng xác nhận → yêu cầu xác nhận lại (B.4, B.4b); không thay luôn chủ hộ mới nhất.
17. **Mới (từ B.4a #2)** — Khi US-001 cập nhật trạng thái sang `Căn hộ trống` cho hộ đang có nhân khẩu: chặn, cảnh báo, hay cho phép (BR-03 nói danh sách = 0)? Cần PO; liên quan Q3. Không sửa US-001 nếu chưa có quyết định. *[PO?]*

---
## F. Test matrix
| # | Nhóm | Kịch bản | Kỳ vọng | Lớp |
|---|---|---|---|---|
| 1 | AC-01 | ADMIN thêm nhân khẩu hợp lệ | `SUCCESS`, `getHousehold` thấy người đó, `memberCount` +1 | Service |
| 2 | AC-02 | Tuổi 26, CCCD trống | `VALIDATION_ERROR`, thông báo AC-02, không ghi | Service |
| 3 | Biên tuổi | 13 tuổi 364 ngày / đúng 14 tuổi hôm nay, không CCCD | được / bị chặn | Service (fake `Clock`) |
| 4 | CCCD | 9 số, 12 số, 11 số, có chữ, trùng | hợp lệ / hợp lệ / lỗi / lỗi / `DUPLICATE(so_cccd)` | Service + IT |
| 5 | CCCD null | nhiều nhân khẩu không CCCD (trẻ < 14) | đều lưu được | IT |
| 6 | Họ tên | 1, 2, 50, 51 ký tự; có số; có dấu tiếng Việt; khoảng trắng thừa | lỗi / ok / ok / lỗi / lỗi / ok / chuẩn hóa | Service |
| 7 | Ngày | `ngay_sinh` hôm nay/tương lai; `ngay_chuyen_den` hôm nay/tương lai; sai định dạng | lỗi / lỗi / ok / lỗi / lỗi | Service |
| 8 | Enum | thiếu/ sai `gioi_tinh`, `quan_he_chu_ho` | lỗi theo field | Service |
| 9 | Hộ | `ma_ho` trống; không tồn tại | `VALIDATION_ERROR`; `NOT_FOUND`, không ghi | Service + IT (FK 1452) |
| 10 | Căn hộ trống | hộ `CAN_HO_TRONG` (P1) | `VALIDATION_ERROR`, trạng thái căn hộ **không đổi** | Service |
| 11 | AC-03 | thêm Chủ hộ thứ hai, chưa xác nhận | `CONFIRMATION_REQUIRED` + chủ hộ hiện tại, không ghi | Service |
| 12 | Thay chủ hộ | xác nhận đúng `confirmReplaceHeadId` | chủ hộ cũ → `KHAC`, mới = `CHU_HO`, đúng 1 chủ hộ | Service + IT |
| 13 | Từ chối | UI không gọi lại | không có dữ liệu thay đổi | UI contract |
| 14 | Xác nhận cũ (đổi người) | `confirmReplaceHeadId` ≠ chủ hộ hiện tại | `CONFIRMATION_REQUIRED/CONFIRMATION_STALE` + chủ hộ hiện tại, không ghi, không demote | Service + IT |
| 14b | Xác nhận cũ (hộ không còn chủ hộ) | `confirmReplaceHeadId = H` nhưng hộ không có chủ hộ | `CONFIRMATION_REQUIRED/CONFIRMATION_STALE`, `currentHead` rỗng, **không ghi gì** (không tự thêm làm chủ hộ đầu tiên); gửi lại với `null` mới được thêm | Service + IT |
| 14c | Xác nhận thừa | `confirmReplaceHeadId != null` nhưng quan hệ ≠ `CHU_HO` | bỏ qua xác nhận, thêm bình thường, không ai bị demote | Service + IT |
| 15 | Rollback | insert lỗi sau khi demote (CCCD trùng) | chủ hộ cũ nguyên vẹn, không ghi gì | IT |
| 16 | Đồng thời | 2 thread thay chủ hộ; 2 thread thêm chủ hộ vào hộ rỗng; 2 thread cùng CCCD | luôn đúng ≤ 1 chủ hộ; kết quả như bảng B.4; không deadlock | IT |
| 17 | DB | `uq_residents_one_head` vi phạm trực tiếp (bỏ qua Service) | 1062, DB từ chối | IT |
| 18 | MySQL lỗi | repository ném `SQLException` / mất kết nối | `SYSTEM_ERROR` thông báo chung, không lộ SQL | Service |
| 19 | Quyền | từng method × ADMIN/ACCOUNTANT/RESIDENT/chưa đăng nhập (bảng B.6) | khớp bảng; deny xảy ra **trước** truy vấn (repo không bị gọi) | Service (fake `AuthorizationService`) |
| 20 | Lộ thông tin | RESIDENT gọi `getHousehold` với `ma_ho` không tồn tại | `AccessDeniedException`, không phải `NOT_FOUND` | Service |
| 21 | Schema | chạy migration 2 lần; FK RESTRICT; CHECK CCCD; round-trip tên có dấu; generated column + FK trên MySQL 8.4 | thành công | IT |
| 22 | Tích hợp phí | `countByMaHo` với hộ có 0 / n nhân khẩu | 0 / n | IT |
| 23 | **IT – migration** | (a) áp `us001_*.sql` rồi `us002_residents.sql` trên DB trống; (b) **chạy `us002_residents.sql` lần 2 và lần 3**; (c) chạy `us002_seed.sql` hai lần; (d) kiểm `information_schema` có đủ cột, `head_ma_ho` là `GENERATED STORED`, key `uq_residents_cccd`, `uq_residents_one_head`, FK `fk_residents_household` (RESTRICT), CHECK `chk_residents_cccd` | (a) thành công; (b) không lỗi, không đổi cấu trúc/dữ liệu; (c) seed lặp không nhân đôi (hoặc script có điều kiện rõ ràng); (d) đúng cấu trúc đề xuất | IT |
| 24 | **IT – generated column / unique / FK / CHECK** | 2 chủ hộ cùng hộ bằng `INSERT` trực tiếp; 1 chủ hộ ở 2 hộ khác nhau; đổi `quan_he` CHU_HO→KHAC rồi chèn chủ hộ mới; `head_ma_ho` tự đúng khi `UPDATE quan_he`; nhiều `so_cccd` NULL; CCCD trùng; CCCD sai định dạng (8 / 10 / có chữ); `ma_ho` không tồn tại; xóa/đổi `households` có nhân khẩu (RESTRICT) | 1062 `uq_residents_one_head` / hợp lệ / hợp lệ / `head_ma_ho` cập nhật / hợp lệ / 1062 `uq_residents_cccd` / 3819 / 1452 / bị từ chối. **Kiểm chứng điều kiện cốt lõi trên MySQL 8.4: FK RESTRICT cùng cột nền của generated STORED được chấp nhận**; nếu không, báo Lead và chuyển phương án dự phòng B.2 | IT |
| 25 | **IT – rollback** | `insert(REPLACE_HEAD)` với CCCD trùng sau khi đã demote; ép lỗi giữa hai câu lệnh; mất kết nối trước COMMIT | chủ hộ cũ vẫn `CHU_HO`, không có dòng mới; lần gọi sau vẫn đúng | IT |
| 26 | **IT – đồng thời US-002** | N vòng × (2 thread thay chủ hộ; 2 thread thêm chủ hộ vào hộ rỗng; 2 thread cùng CCCD khác hộ) | luôn ≤ 1 chủ hộ/hộ; kết quả đúng bảng B.4 (một `SUCCESS`, một `CONFIRMATION_STALE`/`DUPLICATE`); không lỗi 1213 thoát ra ngoài | IT |
| 27 | **IT – lock ordering với US-001** | N vòng đồng thời `ResidentDAO.insert` và `HouseholdDAO.update` trên cùng hộ (đổi `trang_thai`, `dien_tich`); ghi `EXPLAIN` của câu `UPDATE … JOIN` | không deadlock thoát ra ngoài (1213 nếu có chỉ được thử lại tối đa 1 lần); không dòng treo; dữ liệu cuối nhất quán; `EXPLAIN` đính kèm báo cáo (bằng chứng, không làm giả định) | IT |
| 28 | Exception mapping | `ResidentDAO` map đúng SO_CCCD / CHU_HO; key lạ → `SQLException` | như B.7 | IT |
IT chạy bằng Docker MySQL 8.4 như US-001 (`mvn -Pmysql-it`); chưa chạy gì trong Mốc 1.

---
## G. Dependencies
- **`origin/main`**: nền cho branch (US-001 + US-018 đã merge). Phải rebase/kiểm tra lại nếu `main` thay đổi trước khi bắt đầu.
- **US-001**: `HouseholdRepository.findByMaHo`, bảng `households`/`apartments`; migration `us001_*.sql` phải được áp dụng trước `us002_residents.sql`.
- **US-018 / US-012**: `AuthorizationService`, `Permission.RESIDENT_PROFILE_*`, `SessionContext` (phía UI/composition root) — do Dev-1; Service chỉ phụ thuộc interface.
- **Foundation (Dev-1)**: `DatabaseConnection`; `schema.sql` không sửa.
- **Dev-2**: UI US-001 (`HouseholdDetailView`, branch `feature/us001-household-ui` chưa merge) để gắn danh sách/form nhân khẩu.
- **PR #6 (Household–RBAC Integration)**: đã có theo Lead; US-002 không phụ thuộc vào việc merge nó và không đụng `HouseholdServiceImpl`. Nếu PR #6 đổi `HouseholdDAO`/`HouseholdRepository`, phải đối chiếu lại B.4a trước khi bắt đầu Mốc 2.
- **PO**: Q2–Q8, Q14, Q15, Q17; **Lead/Dev-1**: migration `us002_residents.sql` (Q13), tùy chọn gia cố `HouseholdDAO.update` (B.4a #3); **Lead/PO**: ownership (C.4).
- **Phía sau**: US-007 dùng `countByMaHo`; US-003/US-004 (Sprint 2) mở rộng trạng thái cư trú.
- **Môi trường**: máy hiện có JDK 17.0.21; Maven/Docker cho IT cần cài lại như US-001 (chưa xác minh trong phiên này).

---
## H. Ghi chú triển khai Mốc 2 (khác/bổ sung so với bản duyệt)
- **PO đã duyệt thêm** (Mốc 2): người đầu tiên của hộ bắt buộc `CHU_HO`; không thêm vào `Căn hộ trống`; CCCD 9 hoặc 12 số; < 14 tuổi được bỏ trống CCCD; `ngay_chuyen_den` ≥ `ngay_sinh`, không ràng buộc với ngày nhận nhà; **trùng người không CCCD** trong cùng hộ theo (`ho_ten` chuẩn hóa, `ngay_sinh`, `gioi_tinh`).
- `ResidentDuplicateException.Field` có **3 giá trị**: `SO_CCCD`, `CHU_HO`, `PERSON` (giá trị thứ ba do quy tắc trùng người không CCCD mới được duyệt). Constraint DB: `uq_residents_no_cccd_person` trên cột generated `no_cccd_key` (collation `utf8mb4_0900_as_ci`: không phân biệt hoa/thường, phân biệt dấu).
- `ResidentRepository.InsertOutcome` có thêm `ApartmentVacant`, `FirstMemberMustBeHead` (quyết định trong transaction đã khóa hộ); trạng thái căn hộ đọc không khóa sau khi giữ khóa `households` (B.4a #1). `ResidentDAO` thử lại đúng một lần khi deadlock 1213.
- Đã xác minh trên MySQL 8.4.13 (Docker): cột generated STORED + unique + FK `RESTRICT` trên cột nền được chấp nhận; `EXPLAIN` của `UPDATE` US-001 cho thấy `households` được truy cập trước `apartments` (cả hai `const`); 60 vòng `HouseholdDAO.update` đồng thời với `ResidentDAO.insert` không phát sinh deadlock.
- `HouseholdMySqlIT.freshSchema` thêm `DROP TABLE IF EXISTS residents` (FK mới chặn xóa `households`). Chỉ là test; không đổi code sản phẩm US-001.
- Q17 vẫn là task tích hợp riêng cho Lead Dev/PO (không sửa US-001).
