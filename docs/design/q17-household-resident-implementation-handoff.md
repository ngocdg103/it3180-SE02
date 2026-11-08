# Q17 — Household–Resident Guard / Mốc 2 handoff

Ngày 08/11/2026. Scope: không ghi CAN_HO_TRONG cho hộ còn nhân khẩu, kể cả
update cạnh tranh với insert US-002. Chưa merge và không chuyển sang US khác.

## 1. Git và bảo toàn dữ liệu

Branch `feature/q17-household-resident-guard` được tạo từ origin/main
**162d9ea4db222d3bb27d4a603a32f5496c983204** sau fetch và kiểm tra ancestry.
Main đã có PR #9/US-002 (`0385182`, head `1b12656`), không cherry-pick/merge lại.

Ba tài liệu local untracked được kiểm tra hash và giữ nguyên khi chuyển branch:
Q17 Technical Review, US-007 Contract Review, QA MySQL handoff. Chỉ Q17 Technical
Review được đưa vào commit Q17; hai tài liệu ngoài scope không stage/commit.
Không sửa main hoặc App Shell/US-006/US-007, không reset/force-push/rebase.

## 2. Production changes và public API

- HouseholdDAO.update mở transaction, khóa household theo PK trước mọi read
  quyết định, kiểm tra resident khi target CAN_HO_TRONG bằng cùng Connection.
  Nếu có người: ném HouseholdHasResidentsException và rollback toàn update.
- Nếu không có người: thực hiện multi-table UPDATE hiện có rồi commit. Hộ
  không tồn tại vẫn trả false; update không đổi giá trị vẫn giữ semantics found.
- HouseholdServiceImpl map lỗi Q17 vào VALIDATION_ERROR, fieldErrors.trang_thai;
  không data trong result lỗi. RBAC và BR-02 confirmation giữ nguyên. Admin
  cũng không bypass; confirmAreaChange=true không bỏ guard Q17.
- ResidentDAO thêm helper package-private hasAnyForUpdate(Connection,String).
  Helper không mở/đóng/commit Connection caller. countByMaHo, findHead/findByMaHo,
  ResidentRepository và logic insert/head/CCCD/retry hiện tại giữ nguyên.
- Có connection-factory constructor package-private làm test seam cho JDBC
  proxies. Constructor public của hai DAO không đổi. Resident test seam chỉ ở
  đường insert; public read methods, đặc biệt countByMaHo, giữ nguyên code đọc.

Không thay public interface HouseholdService/HouseholdRepository/ResidentService/
ResidentRepository, DTO/status enum, RBAC/AuthorizationService, AuthService,
SessionContext, schema/migrations hoặc dữ liệu production.

## 3. Lock order và current read

```text
Household update:
  BEGIN
  SELECT ma_ho FROM households WHERE ma_ho=? FOR UPDATE
  if missing: ROLLBACK, return false
  if target CAN_HO_TRONG:
    SELECT ma_nhan_khau FROM residents WHERE ma_ho=? LIMIT 1 FOR UPDATE
    if found: throw domain refusal → ROLLBACK
  UPDATE apartments JOIN households ...
  COMMIT

Resident insert (logic không đổi):
  BEGIN → households FOR UPDATE → apartment status plain read
        → resident/head checks/writes → COMMIT/ROLLBACK
```

Điểm tuần tự chung là **households**, luôn được khóa trước resident presence
và trước apartment UPDATE. UPDATE JOIN không còn được dùng như bước khóa đầu
tiên phụ thuộc optimizer. Không có pre-lock apartments → households trong update.
ResidentDAO không thêm apartment lock; SELECT trạng thái đầu tiên của insert
transaction xảy ra sau household lock như implementation US-002 hiện có.

Q17 dùng current/locking read theo resident index, không COUNT snapshot cũ,
không SKIP LOCKED và không kết nối riêng giữa check/write. Lock giữ tới commit/
rollback. Do đó, dưới REPEATABLE READ, resident vừa commit trước khi lấy household
lock vẫn được nhìn thấy ngay cả khi Connection đã có một consistent snapshot cũ.

Giới hạn: insert **household mới** của US-001 vẫn là apartments rồi households
như trước; Q17 không sửa flow tạo mới, chỉ protocol update hộ đã tồn tại. Đề
xuất global apartment-first trong bản review US-002 cũ không phải thứ tự thực
thi của các transaction resident hiện tại. Không tuyên bố toàn hệ thống không
bao giờ deadlock; invariant được bảo đảm cho hai đường ghi cùng household protocol.

## 4. Error và rollback

HouseholdHasResidentsException là domain exception, không giả làm SQLException,
không dùng false (NOT_FOUND), không tạo AccessDeniedException thứ hai. DAO bắt
RuntimeException/SQLException để rollback, rethrow lỗi gốc; rollback failure là
suppressed exception. Q17 bị chặn trước mọi ghi nên toàn bộ field cũ được giữ.

Lỗi SQL/read/missing residents migration không được hiểu thành count=0: rollback
và SYSTEM_ERROR chung ở Service. Không thêm retry policy mới. ResidentDAO vẫn
retry 1213 một lần như cũ; Household DAO lỗi 1213/1205 vẫn fail/rollback theo
contract, không dùng lại quyết định từ transaction cũ. Commit mạng lỗi có thể
không xác định được kết quả server; không tự retry update trong trường hợp đó.

## 5. Verification thực tế

JDK **Temurin 17.0.20.1**, **Apache Maven 3.9.9**, Enforcer bật và passed.

| Lệnh | Kết quả |
| --- | --- |
| mvn clean verify | BUILD SUCCESS; **352 tests**, 0 failures/errors/skips |
| mvn -Pjavafx-smoke clean verify | BUILD SUCCESS; **353 tests**, 0 failures/errors/skips |
| mvn -Pmysql-it -Dtest=HouseholdQ17MySqlIT,HouseholdMySqlIT,ResidentMySqlIT verify | BUILD SUCCESS; **40 tests**, 0 failures/errors/skips |
| git diff --check / staged check | PASS |

Các lệnh thêm batch/no-transfer-progress, cache và log path ignored; không tắt
Enforcer hoặc skip test. Log local: .tools/q17-verify.log, q17-javafx.log,
q17-mysql.log. JavaFX warning unnamed-module như các mốc Foundation, tests PASS.

### Unit/regression

7 DAO tests kiểm check/write cùng Connection, thứ tự BEGIN/household/presence/
UPDATE/commit, domain refusal, missing household, non-vacant states, lỗi read/
write/commit/rollback. 4 Service tests kiểm mapping trang_thai, dữ liệu không đổi,
empty allowed, trạng thái khác, BR-02 và system error. Existing 60 Household,
29 Household-RBAC, 57 ResidentService, 97 RBAC, Authentication và UI regressions PASS.

### MySQL thực tế

MySQL Community **8.4.13**, REPEATABLE-READ, instance riêng loopback 127.0.0.1:33317.
Schema **bluemoon_q17_test**, user **bluemoon_q17@localhost**, chỉ quyền DDL/DML
trong schema đó; không chạy bằng root và không dùng schema QA US-005 hoặc dữ liệu
thật. Password sinh local trong .tools ignored, không đưa vào Git.

Q17 IT rebuild residents → households → apartments trong schema riêng, áp đúng
US-001/US-002 migrations không sửa. HouseholdMySqlIT tạo thêm bảng residents từ
migration US-002 để guard Q17 không giả định bảng thiếu nghĩa là hộ rỗng.

7 Q17 MySQL cases:

1. Có resident: từ chối toàn update, giữ fields và resident; trạng thái khác vẫn cập nhật.
2. Không resident: cập nhật trống được, unchanged update vẫn found; insert sau đó ApartmentVacant.
3. R-first: actual ResidentDAO giữ household lock; actual HouseholdDAO chờ rồi từ chối sau insert commit.
4. U-first: actual HouseholdDAO đã current-read hộ rỗng, giữ lock trước UPDATE;
   actual ResidentDAO phải chờ, sau commit thấy apartment vacant và từ chối.
5. Một Connection đã có snapshot resident_count=0 ở RR, Connection khác insert
   commit; Q17 vẫn thấy resident mới và từ chối (kiểm trực tiếp chống stale snapshot).
6. Inject SQLException **sau UPDATE thật** nhưng trước commit: cả apartment và
   household fields rollback; insert sau đó vẫn được, chứng minh lock được giải phóng.
7. 40 lượt hai DAO thật race: chỉ một trong hai serial outcome hợp lệ, không
   commit CAN_HO_TRONG với count>0; không nuốt SQL/deadlock hoặc lost insert.

Gate dùng JDBC proxy test-only, delegate SQL thật và pause sau executeQuery
locking read thật, không mock dữ liệu hoặc dựa vào Thread.sleep để chọn thứ tự.
Futures/latches có timeout và executor cleanup. Cả hai deterministic interleaving
đều đã chạy, không suy ra coverage thứ tự chỉ từ stress ngẫu nhiên.

Regression MySQL: HouseholdMySqlIT **12 PASS**, ResidentMySqlIT **21 PASS**, Q17
**7 PASS**. Resident suite còn giữ 60-round insert/update non-vacant concurrency,
head/CCCD uniqueness và head replacement rollback. Không deadlock/SQL failure
thoát ra trong suite đã chạy; đây không phải chứng minh không deadlock với mọi
transaction tương lai hoặc raw SQL ngoài protocol.

## 6. File thay đổi và dependency

- HouseholdDAO.java: transaction Q17 và package-private connection seam.
- ResidentDAO.java: current-read helper, seam cho insert IT; public API/read policy không đổi.
- HouseholdHasResidentsException.java: domain refusal.
- HouseholdServiceImpl.java: map domain error.
- HouseholdQ17DAOTest.java, HouseholdQ17ServiceTest.java, HouseholdQ17MySqlIT.java.
- HouseholdMySqlIT.java: khởi tạo dependency residents migration trong test.
- Q17 Technical Review gốc và handoff này; docs/test/README.md ghi evidence.

Không cần PR #9 merge thêm (đã vào base). Deployment phải áp US-002 migration
trước khi dùng Q17; thiếu bảng là fail-closed SYSTEM_ERROR, không silent ALLOW.
InMemoryHouseholdRepository độc lập không có resident store: Service unit test
dùng fixture domain refusal, không coi mock là bằng chứng concurrency production.
Không thêm/xóa/chuyển nhân khẩu, không sửa App Shell hoặc US-006/US-007.

PO/Lead Dev review và merge theo quy trình sau handoff. Chưa tự merge và chưa
chuyển US khác; metadata commit/PR được báo ngoài file để tránh self-reference.
