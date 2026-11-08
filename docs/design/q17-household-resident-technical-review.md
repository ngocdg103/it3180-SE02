# Q17 — Household–Resident integration / Mốc 1 Technical Review

Ngày 08/11/2026. **Chưa sửa code hoặc triển khai.** Chỉ tài liệu này được viết
mới; giữ các báo cáo US-007/QA local trước đó. Không tạo branch, merge/rebase,
cherry-pick hoặc commit/push trong mốc review.

## 1. Git và dependency PR #9

- Checkout hiện tại: feature/us018-household-integration, HEAD 3233962; không
  chuyển branch. Hai tài liệu untracked có trước được bảo toàn.
- Đã git fetch origin; đọc source từ **origin/main
  162d9ea4db222d3bb27d4a603a32f5496c983204**, không từ checkout cũ thiếu US-002.
- GitHub xác nhận [PR #9](https://github.com/ngocdg103/it3180-SE02/pull/9)
  state closed, merged=true, merge commit **0385182c0b5ca7926dbd35658cbcfb1604c767a6**;
  backend head 1b126566cb119ec33bb64670a813ba39e7685801 đã vào main.
  Đã kiểm tra merge commit là ancestor của origin/main.
- Blocker “US-002 chưa merge” đã được gỡ. Vẫn cần Lead Dev duyệt chiến lược Q17,
  contract nội bộ và branch mới từ main mới nhất trước Mốc 2.

## 2. Source/contract đã đối chiếu

Đọc HouseholdServiceImpl, HouseholdDAO/Repository, InMemoryHouseholdRepository,
ResidentRepository/DAO, ResidentServiceImpl, HouseholdResult, US-001 API contract,
US-002 contract review revision 01 và API contract, migrations US-001/US-002,
các MySQL tests liên quan. Các nhận xét dưới đây bám mã đã merge.

| Thành phần | Hiện trạng |
| --- | --- |
| HouseholdServiceImpl.updateHousehold | RBAC HOUSEHOLD_UPDATE trước validation; findByMaHo, BR-02 charge check, repository.update, rồi đọc lại; không count resident |
| HouseholdDAO.update | Một multi-table UPDATE apartments JOIN households, Connection mới autocommit; atomic cho hai bảng nhưng chưa atomic với quyết định Q17 |
| HouseholdRepository.update | boolean + throws SQLException; false được Service map NOT_FOUND |
| ResidentDAO.insert | Connection mới, autocommit=false; khóa households theo PK FOR UPDATE trước mọi kiểm tra/ghi resident, commit chỉ Added |
| ResidentDAO apartment check | SELECT thường sau khi đã giữ household lock; không khóa apartments; CAN_HO_TRONG → ApartmentVacant, rollback |
| ResidentRepository.countByMaHo | Đã có; đếm mọi resident record Sprint 1 vì chưa có chuyển đi |
| ResidentDAO.countByMaHo | Tự mở Connection riêng rồi SELECT COUNT(*), không nhận Connection/transaction của caller |
| Schema | households.ma_ho PK, so_can_ho unique; residents có FK/idx theo ma_ho; không có ràng buộc cross-table trạng thái ↔ số người |

US-002 có private count(Connection,...) nhưng đang là SELECT thường và không
public transaction port. Không giả định countByMaHo tự tham gia transaction khác.

### Khoảng trống trong test hiện có

ResidentMySqlIT.residentInsertsAndHouseholdUpdatesOnTheSameHouseholdNeverDeadlock
chạy insert đồng thời update nhưng chỉ đổi DANG_O/CHO_THUE, **không thử CAN_HO_TRONG**.
Nó kiểm head/CCCD/concurrency US-002, chưa chứng minh Q17. Các test hiện có không
được dùng làm bằng chứng Q17 đã PASS.

## 3. Guard đặt ở đâu

Quyết định cuối cùng bắt buộc nằm trong transaction của **HouseholdDAO.update**:
nếu trạng thái đích CAN_HO_TRONG và có ít nhất một resident của maHo → từ chối,
không cập nhật bất kỳ field nào của apartment hoặc household.

HouseholdServiceImpl giữ nguyên RBAC, field validation và BR-02. Nó chỉ map kết
quả bị chặn của persistence sang HouseholdResult.VALIDATION_ERROR với
fieldErrors["trang_thai"]. Không chỉ dựa vào UI hoặc một count sớm ở Service.
Đề xuất wording chờ duyệt: “Không thể chuyển căn hộ sang trạng thái Căn hộ trống
khi hộ vẫn còn nhân khẩu.” Không thêm enum status/result hoặc phương thức Service.

Không dùng false để báo Q17 vì false hiện có nghĩa NOT_FOUND. Đề xuất một domain
exception nội bộ DAO `HouseholdHasResidentsException extends RuntimeException`,
không kèm thông tin resident/SQL; DAO rollback trước khi rethrow, Service catch
riêng trước catch SQLException. Chữ ký HouseholdRepository/HouseholdService và
DTO/status hiện tại giữ nguyên. Lead Dev cần duyệt loại lỗi nội bộ mới này.

BR-02 hiện có thể yêu cầu xác nhận đổi diện tích trước khi gọi DAO; giữ nguyên
ưu tiên/luồng đó. Dù confirmAreaChange=true, lần ghi vẫn phải qua Q17. Không
thay chính sách phí/confirmation trong nhiệm vụ này.

## 4. Vì sao count trước update không đủ

Sai: Service countByMaHo(H)=0 → đóng Connection → request Resident thêm người
và commit → HouseholdDAO UPDATE CAN_HO_TRONG. Kết quả trống nhưng vẫn có người.

Sai: HouseholdDAO giữ Connection A nhưng gọi ResidentDAO.countByMaHo chuẩn,
vì method đó mở Connection B; đây không phải check/write cùng transaction như
contract yêu cầu. Không gắn boolean/counter cache ở JVM để thay khóa InnoDB,
vì hai instance/process khác nhau vẫn có thể race.

Đúng: hai đường ghi US-001/US-002 cùng tuần tự hóa qua đúng dòng households,
giữ khóa suốt kiểm tra và commit. Kiểm tra resident phải dùng chính Connection
của transaction cập nhật, không bật autocommit hoặc commit sớm giữa các bước.

## 5. Transaction và thứ tự khóa đề xuất

Pseudocode cho **mọi** HouseholdDAO.update trên hộ đang tồn tại:

```text
new Connection; setAutoCommit(false)
SELECT ma_ho, so_can_ho FROM households WHERE ma_ho = ? FOR UPDATE
  missing -> rollback, return false
if targetStatus == CAN_HO_TRONG:
  SELECT ma_nhan_khau FROM residents WHERE ma_ho = ? LIMIT 1 FOR UPDATE
    found -> rollback, throw HouseholdHasResidentsException
execute existing parameterized apartments JOIN households UPDATE
commit
return found/updated result compatible with current contract
SQLException/RuntimeException -> rollback entire transaction, rethrow
close Connection; suppress rollback failure on original exception
```

- Khóa đầu tiên là households PK, không dùng JOIN locking query để optimizer
  tự chọn bảng cần khóa trước. UPDATE hiện có chỉ chạy sau khi household đã được khóa.
- Thứ tự khóa cục bộ Q17: **household → resident presence (nếu cần) → apartment
  write**; các update trạng thái khác cũng khóa household trước apartment.
- ResidentDAO tiếp tục household → resident writes; trạng thái apartment vẫn
  đọc thường sau khóa hộ, không thêm FOR SHARE/FOR UPDATE apartments vào US-002.
- Với một hộ, khóa household đã loại trừ việc hai transaction cùng đi tới
  bước con. Căn hộ có một household theo unique hiện có; không đổi PK/link trong Q17.
- SELECT resident FOR UPDATE là current read; không dựa vào snapshot SELECT
  thường được tạo từ trước khi chờ khóa ở REPEATABLE READ. Không dùng SKIP LOCKED:
  một resident đang bị khóa không được hiểu thành “không có người”.
- Có thể dùng EXISTS/current-read tương đương sau khi chứng minh trên MySQL;
  không COUNT aggregate + FOR UPDATE một cách máy móc rồi cho rằng đã khóa row.

MySQL xác định locking read lấy dữ liệu hiện thời và giữ lock đến commit/rollback;
consistent read ở REPEATABLE READ có thể giữ snapshot cũ. Xem
[Locking Reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) và
[Consistent Reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html).

### Hai interleaving phải an toàn

| Thứ tự | Điều xảy ra | Trạng thái hợp lệ sau cùng |
| --- | --- | --- |
| Resident giữ household trước | Insert commit; update chờ rồi current-read thấy người | Q17 từ chối; apartment không đổi, resident được giữ |
| Household update giữ trước | Count/presence rỗng; Resident chờ; trạng thái trống commit | Resident lấy lock sau đó, lần SELECT thường đầu tiên của insert transaction thấy trạng thái trống và trả ApartmentVacant; không resident mới |

Nếu một bên rollback thì bên chờ phải đọc lại dưới lock và có thể thực hiện
theo trạng thái đã commit thật. Không được có hai SUCCESS dẫn tới
CAN_HO_TRONG + count>0 từ trạng thái ban đầu hợp lệ.

### Ranh giới chứng minh / deadlock

Contract US-002 B.4a có đề xuất tổng quát apartments→households→residents, nhưng
implementation thực tế cố ý khóa household trước và không lock apartment để
tránh cycle với UPDATE cũ. B.4a cũng đã đề xuất riêng HouseholdDAO khóa household
trước UPDATE. Q17 theo điểm tuần tự **household-first** này; cần cập nhật/chốt
tài liệu thứ tự khóa, không đồng thời tuyên bố mọi đường đều apartment-first.

Không chuyển riêng một phía sang khóa apartment trước household. Không tự sửa
ResidentDAO.insert hoặc HouseholdDAO.insert; insert household tạo dòng mới và
không phải transaction cập nhật hộ đã tồn tại. Không khẳng định hết mọi deadlock
ở mọi đường ghi: unique CCCD khác hộ hoặc các transaction mới vẫn cần review.

1213/1205: rollback đầy đủ, trả SYSTEM_ERROR chung theo contract hiện tại;
không lộ SQL. ResidentDAO đã có retry một lần cho 1213; giữ nguyên. Nếu muốn
HouseholdDAO retry, Lead Dev cần duyệt riêng bounded retry toàn transaction trên
Connection mới và chạy lại mọi guard; không retry riêng UPDATE bằng count cũ,
không retry lỗi commit có trạng thái kết quả không chắc chắn.

Đảm bảo ở đây áp dụng cho các đường ghi US-001/US-002 cùng protocol. Không nói
đã có database constraint ngăn mọi direct SQL/admin sửa trạng thái bỏ qua guard.

## 6. Cách dùng ResidentRepository phù hợp kiến trúc

**Không inject ResidentRepository.countByMaHo thông thường vào Service để quyết
định cuối Q17**, vì DAO implementation hiện tự mở Connection.

Phương án ưu tiên chờ Dev-3/Lead duyệt:

- Giữ public ResidentRepository/countByMaHo nguyên trạng, vẫn dùng cho read/US-007.
- Tái sử dụng ResidentDAO làm nơi quản lý truy vấn resident: thêm helper
  package-private `hasAnyForUpdate(Connection connection, String maHo)` hoặc
  transaction-bound read collaborator cùng package DAO. Helper chỉ đọc bằng
  Connection caller đang giữ lock; không mở/đóng/commit Connection đó.
- HouseholdDAO dùng helper/collaborator bên trong transaction. Constructor công
  khai HouseholdDAO(DatabaseConnection) và các constructor HouseholdServiceImpl
  giữ nguyên; nếu cần test seam thì package-private, không đổi public business API.
- Không tạo ResidentDAO thứ hai, không sửa policy/head/CCCD của US-002, không
  dùng adapter giả trả 0 trong production khi thiếu collaborator hoặc migration.
- Thiếu bảng residents/lỗi đọc → rollback + SYSTEM_ERROR, không cho cập nhật
  trống theo kiểu fail-open. PR #9 đã merge nên có migration chuẩn để chạy IT.

Nếu Lead Dev yêu cầu dùng chính interface ResidentRepository trong transaction,
cần duyệt transaction-bound extension/UnitOfWork và owner trước; API hiện có
không đủ để tự quảng bá rằng count đã dùng chung Connection. Không chọn phương
án mở API mới trong Mốc 1 chỉ để tiện inject.

## 7. File dự kiến ở Mốc 2, chưa sửa

1. HouseholdDAO.java: explicit transaction, household lock, Q17 check, rollback.
2. HouseholdServiceImpl.java: map domain refusal vào VALIDATION_ERROR/trang_thai;
   không đổi RBAC, validation/BR-02 hoặc public methods.
3. ResidentDAO.java: chỉ helper đọc resident cùng Connection nếu được owner duyệt;
   không đổi transaction insert/countByMaHo public.
4. HouseholdHasResidentsException.java (đề xuất nội bộ, chờ duyệt).
5. Tests Household DAO/service, integration guard và MySQL concurrency Q17.
6. Tài liệu US-001/US-002: invariant Q17, thống nhất lock-order, handoff/test evidence.

Không sửa schema/migration/RBAC/Auth/DTO API. Không thêm bước đếm qua Connection
riêng ở Service. Test fixtures/in-memory phải phản ánh refusal hoặc dùng fake
transaction boundary; synchronized hai repository object khác nhau không tự
tạo atomicity và không được coi là bằng chứng concurrency MySQL.

## 8. Test cases phải bổ sung

### Unit / regression

- Có >=1 resident, target CAN_HO_TRONG: VALIDATION_ERROR/trang_thai, không đổi
  tầng/diện tích/ngày nhận nhà/status, không sửa resident/head.
- 0 resident: chuyển CAN_HO_TRONG thành công; target DANG_O/CHO_THUE tiếp tục
  đúng US-001 dù có người. Không tự thêm rule buộc DANG_O phải có nhân khẩu.
- Hộ không tồn tại: NOT_FOUND; SQL/helper failure: SYSTEM_ERROR, không thành 0.
- Đã trống nhưng dữ liệu lỗi vẫn có resident: request ghi trống bị chặn; sửa về
  DANG_O/CHO_THUE vẫn được theo rule cũ (không tự xóa resident để “sửa” dữ liệu).
- ACCOUNTANT/RESIDENT/no session bị guard trước DAO như hiện tại; ADMIN cũng
  không bypass Q17. Authentication/RBAC matrix không đổi.
- BR-02 confirmation vẫn hoạt động; confirmAreaChange=true không bypass Q17.
- DAO fixture xác nhận autocommit=false, SELECT household lock trước presence
  và UPDATE, cùng Connection, commit chỉ khi đủ điều kiện; rollback khi refusal,
  SQL failure/commit failure, giữ original failure khi rollback lỗi.
- Giữ regression unchanged-update/found boolean của HouseholdRepository.

### MySQL IT / concurrency (không thay bằng unit mocks)

- Áp migrations US-001 rồi US-002 trong schema disposable *_test; user chỉ có
  quyền schema test. Giữ nguyên mọi dữ liệu thật/production.
- Có người → real HouseholdDAO update trống bị chặn và toàn bộ fields không đổi.
- Hộ đang ở nhưng chưa có người (fixture HK-1502) → cập nhật trống được; insert
  ResidentDAO sau đó trả ApartmentVacant. Person đầu tiên khi chạy race phải
  là CHU_HO để tránh test pass vì FirstMemberMustBeHead không liên quan Q17.
- Cưỡng bức **R-first** bằng latch/barrier + transaction giữ household lock:
  update phải chờ, sau resident commit phải thấy người và từ chối.
- Cưỡng bức **U-first**: sau lock và kiểm tra rỗng, giữ transaction chưa commit;
  ResidentDAO phải chờ, chỉ tiếp tục sau commit và từ chối vì apartment vacant.
- Nếu cần điều khiển đúng pause point, dùng test-only JDBC harness/test seam
  package-private được duyệt. Không chỉ Thread.sleep để giả định đã lấy lock.
  Test raw holder SQL kiểm protocol phải được phân biệt với stress test gọi
  cả hai DAO thật; không trình bày SQL mô phỏng như đã chạy cả hai implementation.
- Stress hai DAO thật bằng barrier, lặp nhiều lượt: outcomes chỉ thuộc hai
  interleaving hợp lệ, tuyệt đối không COMMITTED empty+resident. Chạy khác hộ
  để bảo đảm không tạo application/global lock không cần thiết.
- Cả hai phía rollback/failure: trạng thái/nhân khẩu cuối đúng; wait timeout/deadlock
  không để partial data hoặc giữ lock; future/latch có timeout và connection cleanup.
- Giữ ResidentMySqlIT head uniqueness, duplicate CCCD, xác nhận cũ, thay chủ hộ
  rollback, count semantics và concurrency DANG_O/CHO_THUE hiện có.
- Verification sau implementation: mvn clean verify, javafx-smoke, targeted Q17
  MySQL IT và git diff --check. Chưa chạy/chưa báo PASS Q17 trong Mốc 1.

## 9. Các quyết định chờ Lead Dev

1. Duyệt household-first lock protocol cho mọi update hộ hiện có và helper đọc
   resident cùng Connection; thống nhất với Dev-3 phần comment/contract B.4a.
2. Duyệt domain exception nội bộ và mapping VALIDATION_ERROR/trang_thai, wording.
   Không đổi public HouseholdService/DTO hoặc enum trạng thái trả về.
3. Duyệt phạm vi helper ResidentDAO/test seam, thay vì inject countByMaHo mở
   Connection khác; không thêm public API hoặc đổi US-002 trước khi được duyệt.
4. Chốt retry policy nếu muốn bổ sung cho HouseholdDAO; mặc định lỗi DB rollback
   theo contract hiện tại, không tự áp retry mới.
5. Xác nhận branch implementation mới và base origin/main mới nhất sau approval.
   Dependency PR #9 đã merge, không cần cherry-pick hay merge thủ công US-002.

**Dừng tại Mốc 1.** Chưa sửa production code/tests, chưa tạo branch mới, chưa
chạy test Q17, chưa commit/push hoặc merge. Các tài liệu local cũ được giữ nguyên.
