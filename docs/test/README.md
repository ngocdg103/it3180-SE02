# Tài Liệu Kiểm Thử & Bằng Chứng QA — BlueMoon (Sprint 1)

Tài liệu này tổng hợp các kết quả kiểm thử tự động, kiểm thử giao diện khói và kiểm thử tích hợp cơ sở dữ liệu MySQL thật phục vụ việc nghiệm thu Sprint 1.

---

## 1. Môi Trường & Tiêu Chuẩn Kiểm Thử

- **Java Development Kit:** Eclipse Temurin JDK **17.0.14+7** (Maven Enforcer kiểm soát bắt buộc `[17, 18)`).
- **Maven Build Tool:** Apache Maven **3.9.9**.
- **Cơ sở dữ liệu kiểm thử tích hợp:** MySQL Community Server **8.0 / 8.4 LTS**.
- **Thư viện kiểm thử:** JUnit Jupiter 5.13.4.

---

## 2. Kết Quả Kiểm Thử Tự Động Trên Nhánh `main`

Xác thực tại commit HEAD hiện tại: `89b3a2861e6878bb111663e0eb60f5451bfd1487` (sau khi tích hợp PR #27 và PR #25).

### 2.1. Kiểm thử Đơn vị & Hồi quy Cơ bản (Default Profile: `mvn test`)
- **Lệnh thực thi:**
  ```powershell
  mvn test "-Dsurefire.useManifestOnlyJar=false"
  ```
- **Kết quả:**
  - **Tests run:** **695**
  - **Failures:** **0**
  - **Errors:** **0**
  - **Skipped:** **0**
  - **Thời gian chạy:** ~1 phút
  - **Trạng thái:** **BUILD SUCCESS**
- **Phạm vi kiểm tra:**
  - Cấu hình & Kết nối CSDL (`DatabaseConfigTest`, `FeeCategoryWiringTest`, `FeeRateWiringTest`).
  - Xác thực & Phân quyền (`AuthServiceTest`, `RoleBasedAuthorizationServiceTest`, `AuthenticationContractTest`).
  - Hộ gia đình & Cư dân (`HouseholdServiceTest`, `ResidentServiceTest`, `ResidentSearchServiceTest`, `HouseholdQ17ServiceTest`, `HouseholdAuthorizationIntegrationTest`).
  - Định mức & Danh mục phí (`FeeTypeServiceTest`, `FeeRateServiceTest`, `FeeRateTest`, `FeeTypeDAOTest`, `FeeRateDAOTest`).
  - Động cơ Lập kỳ thu (`BillingModelsTest`, `BillingCalculatorTest`, `BillingResultTest`, `BillingServiceTest`).
  - Thu phí & Phiếu thu (`ReceiptServiceTest`, `ReceiptControllerTest`).
  - Báo cáo Công nợ (`DebtServiceTest`, `DebtControllerTest`).
  - Xuất Thông báo (`NoticeServiceTest`, `NoticeFormatterTest`, `NoticeControllerTest`).
  - Bộ điều khiển App Shell & Điều hướng (`AppShellTest`, `LoginUiTest`, `HouseholdUiTest`, `FeeCategoryControllerTest`, `FeeRateControllerTest`, `ResidentPanelUiTest`, `ResidentSearchUiTest`, `ProductionNavigationTest`, `MemoryBillingHistoryTest`).

### 2.2. Kiểm thử Giao Diện Khói (JavaFX Smoke Test Profile: `mvn -Pjavafx-smoke test`)
- **Lệnh thực thi:**
  ```powershell
  mvn -Pjavafx-smoke test "-Dsurefire.useManifestOnlyJar=false"
  ```
- **Kết quả:**
  - **Tests run:** **696**
  - **Failures:** **0**
  - **Errors:** **0**
  - **Skipped:** **0**
  - **Thời gian chạy:** ~2 phút 30 giây
  - **Trạng thái:** **BUILD SUCCESS**
- **Bổ sung:** Kèm theo `FoundationSmokeIT` kiểm tra khởi tạo thực tế của Stage, Scene và vòng đời cửa sổ ứng dụng desktop.

### 2.3. Kiểm thử Tích hợp MySQL Thật (`-Pmysql-it verify`)
- Chạy trên cơ sở dữ liệu kiểm thử cô lập (tên CSDL kết thúc bằng `_test`, ví dụ `bluemoon_test`):
  ```powershell
  $env:BLUEMOON_DB_URL = "jdbc:mysql://localhost:3306/bluemoon_test?connectionTimeZone=UTC"
  $env:BLUEMOON_DB_USERNAME = "test_user"
  $env:BLUEMOON_DB_PASSWORD = "test_password"
  $env:BLUEMOON_MYSQL_TEST = "1"
  mvn -Pmysql-it verify "-Dsurefire.useManifestOnlyJar=false"
  ```
- **Các kịch bản MySQL IT trọng tâm đã kiểm chứng:**
  - `BillingMySqlIT` (27/27 PASS): Kiểm tra tính nhất quán snapshot hóa đơn, single-connection transaction, khóa dòng FOR UPDATE và ràng buộc FK.
  - `ReceiptMySqlIT` (11/11 PASS): Ghi nhận thanh toán nguyên tử, cập nhật số dư nộp thừa `apartment_credit`, chống ghi đè và chống lặp thanh toán (idempotency).
  - `DebtMySqlIT` (5/5 PASS): Tra cứu công nợ với mức cô lập `REPEATABLE READ`, tính toán KPI toàn tòa và bảo đảm không đọc dữ liệu bẩn (dirty read).
  - `ProductionNavigationMySqlIT`: Luồng điều hướng App Shell thực tế với tài khoản BCrypt thật và dọn dẹp dữ liệu fixture.
  - `ResidentSearchMySqlIT` & `HouseholdBr02MySqlIT`: Kiểm tra hiệu năng chỉ mục tìm kiếm và hook kiểm tra diện tích căn hộ.

---

## 3. Trạng Thái Nghiệm Thu E2E Thủ Công (Manual E2E Status)

- **Quy trình luồng nghiệp vụ liên thông (Business Flow):**
  1. `ADMIN` đăng nhập $\rightarrow$ Quản lý căn hộ và cư dân.
  2. `ADMIN` thiết lập danh mục phí và định mức thu phí hiệu lực.
  3. `ADMIN` hoặc `ACCOUNTANT` lập kỳ thu phí tháng (ví dụ `2026-11`), kiểm tra xem trước và chốt snapshot hóa đơn.
  4. `ACCOUNTANT` mở màn hình Thu phí, chọn hóa đơn và lập phiếu thu tiền (thanh toán đủ hoặc thanh toán một phần).
  5. `ACCOUNTANT` mở Báo cáo công nợ để kiểm tra số liệu đã thu, còn thiếu và tỷ lệ hoàn thành toàn tòa.
  6. `ACCOUNTANT` mở Thông báo thu phí, nhập thông tin ngân hàng và xuất tệp HTML in ấn.
- **Tình trạng:** Các module đơn lẻ đã PASS 100% qua các PR. Nhóm QA đang tiến hành nghiệm thu liên thông toàn diện trên giao diện App Shell hoàn chỉnh sau khi kết hợp với PR #28.
