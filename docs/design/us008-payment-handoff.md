# US-008 — Payment handoff (Dev-1, emergency assignment 08/11/2026)

## Base / scope

- Worktree riêng `.tools/us008-payment`, branch `feature/us008-payment`, base main sau PR #16: `1123219ed4244853ba4593c7cab284998dae0043`.
- Billing PR #14 còn pending khi bắt đầu. Merge đúng dependency `7b4ac366242796575ddacdfc27ae7b6e2d9e36c5` bằng `abef76ba7cb6cbcbd1a848adabd9372a7294bba1`; không copy schema, không sửa Billing/DAO/shared DTO, không sửa ProductionModules.
- Trước bàn giao cuối, PR #14 sync main có hotfix fixture #17. Đã merge dependency mới `fb54296874ffa96fdbb58bca814bf4fe69084756` bằng `96e951fc996a4892086f718c6b375a639805e7e9`; main nền cập nhật `4c4833c4284ca666ba94237a7bf319bd26b5cc57`. Diff production/schema giữa hai phiên bản dependency rỗng; không đưa chỉnh sửa ngoài Billing/Payment. Regression/full MySQL sau sync ghi trong PR cuối.
- Root branch/PR #15 giữ nguyên `9d79536770ab3a0a54391c9f65e4c17ad65169a7`; hai tài liệu untracked giữ nguyên. Phân công emergency của Lead thay phạm vi owner ban đầu trong WBS.
- Đã đọc EP-03 US-008, UserStory.xlsx dòng US-008, Implementation WBS sheets task/API/DEC-10. Không có AC cấm vượt dư nợ; BR-13 và DEC-10 cho phép nộp thừa/trừ kỳ sau. Không tự áp dụng policy chặn overpayment.

## Schema / invariants

Áp dụng migration chính thức Foundation, US-001/002/005/006/007 rồi `database/migrations/us008_receipts.sql`.

- `phieu_thu`: mã phiếu PK, request UUID unique (idempotency), FK ma_hoa_don -> invoices VARCHAR(30) ascii/ascii_bin RESTRICT, FK actor -> users.id, payer/date/method/note/money/time. DECIMAL(19,2), CHECK tiền dương và nguyên VNĐ; không có API sửa/xóa phiếu.
- `apartment_credit`: số dư thừa theo căn hộ, FK đúng apartments; cộng credit trong cùng transaction thanh toán. Không sửa diện tích/nhân khẩu/tổng phải thu hoặc invoice_lines; chỉ update invoices.da_thanh_toan và trang_thai, con_thieu vẫn generated theo Billing.
- **Contract cần PO chốt trước merge:** SRS ghi mã phiếu dài 30, nhưng Timestamp + mã hộ đầy đủ + tag tránh trùng có thể vượt 30; implementation đề xuất VARCHAR(64), dạng PT-[epochMillis]-[requestTag]-[maHo]. Không tuyên bố đạt giới hạn 30 ký tự, không chỉnh SRS để khớp code.
- **Precision cần xác nhận:** payment dùng VNĐ nguyên, chấp nhận `1000.00`, từ chối `1000.50`, để tương thích invariant MonthlyCharge đã merge (paid là whole VND). Không tự HALF_UP hoặc làm mất phần lẻ.
- **BR-13 dependency còn lại:** DONG_THUA và credit được lưu thật; Billing PR #14 chưa consume credit khi tạo kỳ sau. Chưa sửa Billing để tự trừ credit. Cần Lead/Dev-3 chốt giao thức consume ledger trong cùng transaction kỳ mới, tránh double-use. Không tuyên bố hoàn thành phần tự trừ kỳ sau.

## Service / transaction

`ReceiptService`:

```java
ReceiptResult<List<PaymentInvoice>> listOutstanding();
ReceiptResult<List<Receipt>> listByHousehold(String maHo);
ReceiptResult<Optional<Receipt>> findByRequestId(String requestId);
ReceiptResult<ReceiptOutcome> recordPayment(ReceiptForm form);
```

Form giữ request UUID một lần cho một ý định, invoice/payer/amount/date/method/note. Result status: SUCCESS, DUPLICATE (exact replay, không ghi lần hai), VALIDATION_ERROR, NOT_FOUND, ALREADY_SETTLED, CONFLICT (request dùng cho payload khác), BUSY, SYSTEM_ERROR.

- Ghi PAYMENT_RECORD: chỉ ACCOUNTANT. ADMIN bị từ chối; ADMIN/ACCOUNTANT đọc với PAYMENT_HISTORY_VIEW_ALL. RESIDENT/no session không đọc hoặc ghi trong US-008 này. Kiểm quyền trước validate/I/O, recheck quyền + lấy actor cùng monitor session trước ghi; không tạo Auth/Session/RBAC thứ hai.
- Payer 2–50 Unicode code points, tiền >0 whole VND trong DECIMAL, ngày >=1000 và <=today Asia/Ho_Chi_Minh từ Clock, note <=255, method TIEN_MAT/CHUYEN_KHOAN. Không thông tin DB trong lỗi UI.
- DAO READ_COMMITTED, server lock wait 5s theo connection riêng (nhỏ hơn socket 10s). Lock order: invoice FOR UPDATE -> request current read -> receipt insert -> update paid/status -> credit upsert -> commit. Không khóa ngược household/apartment. Cùng invoice serialize mọi payment/replay, không lost update.
- Exact replay kiểm trước ALREADY_SETTLED nên yêu cầu đã commit vẫn xác định được sau khi đóng đủ; payload khác không được reuse request. Unique request_id là hàng rào cuối cùng xuyên client.
- Ngoại lệ SQL/runtime rollback toàn bộ receipt/payment/credit; giữ suppressed rollback error, đóng resources. Không retry 1213/1205 hoặc communication error tự động. Commit có thể đã thành công dù báo SYSTEM_ERROR: phải lookup requestId, không suy ra rollback đã đảo commit.
- BR-12 cộng dồn, BR-13 dùng ChargeStatus đã có: DONG_THIEU khi còn dương, DA_DONG_DU khi 0, DONG_THUA khi âm; giữ CHUA_DONG của Billing khi chưa có phiếu. Không nhận intent mới khi hóa đơn đã đủ/thừa.

## UI / module factory

`ReceiptModule.open(DatabaseConnection)` implements DashboardModule: view(), busyProperty(), close(). Production factory dùng ReceiptDAO thật. PO đăng ký menu với PAYMENT_HISTORY_VIEW_ALL; không edit ProductionModules trong PR này. ADMIN có màn hình đọc, nút ghi bị ẩn; Service vẫn tự kiểm quyền.

FXML có danh sách hóa đơn còn thiếu, form, danh sách phiếu theo mã hộ, thông báo và mã đối soát. Mọi call chạy worker ngoài FX thread, busyProperty cho App Shell chặn chuyển module/logout khi đang xử lý; dispose bỏ late callbacks. Double-click khi saving không submit lần hai.

SYSTEM_ERROR hoặc intent conflict giữ mã/giá trị nhập và khóa save. Tra cứu mã đối soát là read-only; nếu tìm thấy phiếu thì không gửi lại. Tạo ý định mới khi kết quả chưa rõ đòi người dùng xác nhận đã đối soát. Không có bank API hoặc production in-memory repository.

## QA

1. Dùng schema `_test` riêng/user chỉ có quyền schema; apply migrations theo thứ tự trên. Tạo ACCOUNTANT và ADMIN bằng Foundation, khởi tạo Billing kỳ hiện tại với invoice còn thiếu. Không seed credentials thật trong Git.
2. PO host `ReceiptModule.open(db)` trong App Shell và giữ/close theo DashboardModule lifecycle. Chưa nối menu trong PR này; đây là **module integration pending**, không UI E2E PASS.
3. ACCOUNTANT chọn hóa đơn 1.384.000, ghi 1.000.000 tiền mặt -> DONG_THIEU, còn 384.000; ghi tiếp 384.000 chuyển khoản -> DA_DONG_DU, có đúng 2 phiếu; invoice_lines/tổng đã chốt không đổi.
4. Gửi cùng request/payload -> DUPLICATE, số phiếu không tăng; đổi payload cùng mã -> CONFLICT. Mã mới với hóa đơn đã đủ bị từ chối.
5. Hóa đơn mới còn thiếu: ghi 1.400.000 -> DONG_THUA, credit16.000. Tự trừ kỳ sau vẫn PENDING như trên.
6. ADMIN chỉ xem, không ghi; RESIDENT/no session từ chối. Thử payer/date/amount lỗi, duplicate, ngắt connection; đối soát mã trước tạo intent mới.

Verification thực tế sau sync hotfix #17 (Temurin17.0.20.1/Maven3.9.9, Enforcer bật): `mvn clean verify` **553 PASS**, `mvn -Pjavafx-smoke clean verify` **554 PASS**; targeted `mvn -Pmysql-it '-Dtest=ReceiptMySqlIT' verify` **11/11 PASS**; **full `mvn -Pmysql-it clean verify` 644/644 PASS**, đều 0 failures/errors/skips. Có 28 Service + 6 controller tests mới trong normal. MySQL8.4.13/schema `bluemoon_payment_11f9ac06_test`, user chỉ có quyền schema đó, đã chứng minh không đọc được schema QA khác; BLUEMOON_MYSQL_TEST=1 và config ngoài source. IT gồm AC01/02, duplicate/payload conflict, overpayment credit, rollback sau update thật và sau credit thật, lost commit reply không retry, 8 requests cùng intent, 8 payments khác intent, hai request thanh toán đủ đồng thời, RBAC, production lock wait BUSY, migration lặp/CHECK/FK. FeeRate6/FeeType2/Billing27 cùng chạy PASS; hotfix fixture đến từ main/Dev-2, không sửa fixture trong US-008. Một lần smoke hai GUI JVM đồng thời có timeout harness FX ở open Scene; chạy lại US-008 tuần tự PASS 554, không sửa/nới test. Log lần lỗi giữ trong `.tools/payment-javafx-parallel-failure.log` ignored.

AC-01/02 được kiểm Service/DAO MySQL; shell-host UI E2E, độ dài mã phiếu và policy precision/carry-forward cần PO/Lead xác nhận trước nghiệm thu toàn bộ story. Không merge PR tự động.
