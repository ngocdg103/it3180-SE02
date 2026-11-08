# BlueMoon — Hệ Thống Quản Lý Chung Cư & Khu Đô Thị

[![Java](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![JavaFX](https://img.shields.io/badge/JavaFX-17.0.18-blue.svg)](https://openjfx.io/)
[![Maven](https://img.shields.io/badge/Maven-3.9.9-red.svg)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0%2B%20%2F%208.4%20LTS-blue.svg)](https://www.mysql.com/)
[![Enforcer](https://img.shields.io/badge/Maven%20Enforcer-Active-success.svg)](pom.xml)

Dự án BlueMoon (SE_02, Học kỳ 2026.1) là ứng dụng máy tính dành cho Ban Quản Lý chung cư nhằm số hóa toàn diện quy trình quản trị cư dân, căn hộ, danh mục khoản thu, lập kỳ tính phí tự động, thu tiền lập phiếu, đối soát công nợ và phát hành thông báo thu phí.

---

## 1. Phạm Vi & Chức Năng Hoàn Thành Trong Sprint 1

Hệ thống được thiết kế theo kiến trúc phân lớp chuẩn mực (Layered Architecture: MVC + Service Layer + DAO + Repository), tích hợp trực tiếp trên nền tảng JavaFX Desktop và CSDL MySQL thông qua kết nối JDBC hướng transaction.

Các chức năng cốt lõi đã hoàn tất tích hợp và kiểm chứng trên nhánh `main`:

| Mã US | Chức Năng | Mô Tả Nghiệp Vụ & Kỹ Thuật |
|:---:|:---|:---|
| **US-012** | **Xác thực & Quản lý Phiên (Authentication & Session)** | Đăng nhập an toàn bằng mật mã băm BCrypt (cost-12); tự động khóa tạm tài khoản 5 phút nếu nhập sai quá 5 lần liên tiếp (BR-18); duy trì thông tin `SessionContext` trong suốt phiên làm việc. |
| **US-018** | **Phân quyền tập trung (RBAC Engine)** | Kiểm tra quyền truy cập dựa trên 3 vai trò hệ thống (`ADMIN`, `ACCOUNTANT`, `RESIDENT`) với ma trận `Permission` rõ ràng. Mọi Service và DAO đều tự bảo vệ trước khi truy cập dữ liệu. |
| **Shell** | **App Shell & Điều hướng Dashboard** | Giao diện quản trị hiện đại, điều hướng đa phân hệ trên cùng một Window/Scene; lưu giữ trạng thái cửa sổ (maximize/restore), kiểm soát trạng thái bận (`busyProperty`) và giải phóng tài nguyên (`close()`) khi chuyển module hoặc logout. |
| **US-001** | **Quản lý Căn hộ & Hộ gia đình (Apartment & Household)** | Quản lý danh sách căn hộ theo tầng, trạng thái cư trú (`DANG_O`, `TRONG`), thông tin chủ hộ, diện tích sử dụng ($m^2$) và lịch sử cư trú. |
| **US-002** | **Quản lý Nhân khẩu (Resident Management UI)** | Quản lý danh sách cư dân trực thuộc từng căn hộ; thêm mới nhân khẩu với các thông tin căn cước, ngày sinh, quan hệ với chủ hộ; tự động cập nhật số nhân khẩu thực tế. |
| **US-014** | **Tra cứu & Tìm kiếm Cư dân (Resident Search & Filtering)** | Bộ lọc tra cứu cư dân đa tiêu chí (họ tên, CCCD, số điện thoại, số căn hộ, trạng thái tạm trú/thường trú); hỗ trợ tìm kiếm không dấu/có dấu Unicode, tối ưu hóa chỉ mục CSDL. |
| **US-005** | **Quản lý Danh mục Khoản thu (Fee Category Management)** | Thiết lập danh mục các loại phí bắt buộc và tự nguyện; hỗ trợ tạo mới, chỉnh sửa thông tin và xác nhận tắt kích hoạt danh mục (Deactivate confirmation UI). |
| **US-006** | **Quản lý Định mức Thu phí (Fee Rate Management)** | Quản lý lịch sử biểu phí theo cơ chế bổ sung liên tục (Append-only / Effective Dated); tra cứu định mức đơn giá hiệu lực tại bất kỳ thời điểm nào; giao diện trực quan hỗ trợ nhập đơn giá VNĐ nguyên. |
| **US-007** | **Lập Kỳ Thu phí Hàng tháng (Monthly Billing Engine)** | Tính toán tự động tổng tiền phí quản lý (theo $m^2$), phí dịch vụ (theo nhân khẩu), tiền giữ xe và khoản tự nguyện; chốt snapshot hóa đơn bất biến tại thời điểm lập kỳ thu; cơ chế giao dịch `REPEATABLE READ` an toàn. |
| **US-008** | **Quản lý Thu tiền & Lập Phiếu thu (Payment & Receipts)** | Ghi nhận thanh toán từng phần hoặc thanh toán đủ; hỗ trợ đa hình thức (tiền mặt, chuyển khoản); lập phiếu thu định dạng `PT-[Timestamp]-[MãHộ]` (hỗ trợ độ dài an toàn tối đa 64 ký tự); kiểm soát thanh toán trùng lặp (Idempotency token); tự động chuyển trạng thái nợ và lưu số dư nộp thừa (`apartment_credit`). |
| **US-009** | **Báo cáo & Thống kê Công nợ (Debt Report & KPIs)** | Thống kê số tiền đã thu, còn thiếu, tỷ lệ thu phí toàn tòa; bảng danh sách công nợ chi tiết kèm bộ lọc theo tầng, trạng thái thu phí; tính toán tổng nợ thực tế sau khi lọc; xuất dữ liệu trực quan cho kế toán. |
| **US-011** | **Xuất Phiếu Thông Báo Thu phí (Fee Notices)** | Tổng hợp phiếu thông báo thu tiền từ snapshot hóa đơn US-007; cấu hình thông tin ngân hàng thanh toán và cú pháp chuyển khoản; cho phép xem trước mẫu thông báo và xuất tệp HTML phân trang in ấn hàng loạt. |

---

## 2. Công Nghệ & Yêu Cầu Môi Trường

- **Ngôn ngữ:** Java 17 LTS (Đã cấu hình Maven Enforcer chặn các phiên bản ngoài `[17, 18)`).
- **Giao diện (GUI):** JavaFX 17.0.18 + FXML (phân tách View và Controller theo mô hình MVC).
- **Công cụ đóng gói:** Apache Maven 3.9.x.
- **Hệ quản trị CSDL:** MySQL 8.0 trở lên hoặc MySQL 8.4 LTS (bắt buộc hỗ trợ InnoDB, UTF-8 `utf8mb4`, và enforced `CHECK` constraints).
- **Thư viện phụ trợ:**
  - `mysql-connector-j` 8.4.0 (Giao tiếp JDBC).
  - `jbcrypt` 0.4 (Mã hóa mật khẩu an toàn).
  - `junit-jupiter` 5.13.4 (Bộ kiểm thử đơn vị & tích hợp).

---

## 3. Cấu Trúc Mã Nguồn

```text
bluemoon/
├── pom.xml                                   # Cấu hình dự án Maven, enforcer & profiles
├── README.md                                 # Tài liệu tổng quan dự án
├── CHANGELOG.md                              # Lịch sử thay đổi qua các phiên bản
├── config/
│   └── db.properties.example                 # Mẫu cấu hình kết nối CSDL (không lưu mật khẩu)
├── database/
│   ├── schema.sql                            # Schema nền tảng (Bảng users, roles, locks)
│   ├── seed.sql                              # Dữ liệu tài khoản kiểm thử mẫu
│   └── migrations/                           # Các tập lệnh migration chính thức của Sprint 1
│       ├── us001_household_apartment.sql     # Căn hộ & Hộ gia đình
│       ├── us001_seed.sql                    # Dữ liệu mẫu căn hộ
│       ├── us002_residents.sql               # Cư dân / Nhân khẩu
│       ├── us002_seed.sql                    # Dữ liệu mẫu cư dân
│       ├── us005_fee_types.sql               # Danh mục loại phí
│       ├── us006_fee_rates.sql               # Định mức thu phí
│       ├── us007_billing.sql                 # Kỳ thu & Hóa đơn snapshot
│       └── us008_receipts.sql                # Phiếu thu & Số dư nộp thừa
├── src/
│   ├── main/
│   │   ├── java/com/bluemoon/
│   │   │   ├── BlueMoonApplication.java      # Điểm khởi chạy chính của ứng dụng
│   │   │   ├── config/                       # Quản lý cấu hình CSDL & Factory
│   │   │   ├── controllers/                  # Bộ điều khiển giao diện (JavaFX Controllers)
│   │   │   ├── dao/                          # Tầng truy xuất dữ liệu (Data Access Objects)
│   │   │   ├── models/                       # Mô hình dữ liệu & DTOs
│   │   │   ├── services/                     # Tầng nghiệp vụ (Business Service Layer)
│   │   │   └── views/                        # Navigator, Registry & App Shell Modules
│   │   └── resources/com/bluemoon/
│   │       ├── views/                        # Các tệp FXML định nghĩa giao diện
│   │       └── styles/                       # CSS định dạng giao diện
│   └── test/
│       └── java/com/bluemoon/                # Kiểm thử Unit Test, Smoke Test & MySQL IT
└── docs/
    ├── srs/                                  # Đặc tả yêu cầu phần mềm (SRS)
    ├── design/                               # Thiết kế kỹ thuật & Tài liệu bàn giao module
    ├── test/                                 # Báo cáo kiểm thử & Minh chứng QA
    ├── USER_GUIDE.md                         # Hướng dẫn thao tác cho người dùng
    ├── SPRINT1_REPORT.md                     # Báo cáo tổng kết bàn giao Sprint 1
    └── KNOWN_LIMITATIONS.md                  # Giới hạn kỹ thuật & Backlog Sprint 2
```

---

## 4. Cài Đặt & Cấu Hình Cơ Sở Dữ Liệu

### Bước 1: Thiết lập tệp cấu hình CSDL
Tạo tệp cấu hình kết nối từ tệp mẫu:
```bash
cp config/db.properties.example config/db.properties
```
Chỉnh sửa thông tin kết nối trong `config/db.properties` (tệp này được `.gitignore` bảo vệ, tuyệt đối không commit thông tin nhạy cảm vào Git):
```properties
db.url=jdbc:mysql://localhost:3306/bluemoon_db?connectionTimeZone=UTC&useUnicode=true&characterEncoding=UTF-8
db.username=your_db_username
db.password=your_db_password
```

*Hệ thống cũng hỗ trợ ghi đè biến môi trường:* `BLUEMOON_DB_URL`, `BLUEMOON_DB_USERNAME`, `BLUEMOON_DB_PASSWORD`.

### Bước 2: Chạy các tập lệnh CSDL theo đúng thứ tự
Tạo CSDL rỗng sử dụng bộ ký tự `utf8mb4`:
```sql
CREATE DATABASE bluemoon_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bluemoon_db;
```

Thực thi tuần tự các tệp SQL sau (bắt buộc đúng thứ tự do ràng buộc khóa ngoại):
1. `SOURCE database/schema.sql;`
2. `SOURCE database/seed.sql;`
3. `SOURCE database/migrations/us001_household_apartment.sql;`
4. `SOURCE database/migrations/us001_seed.sql;`
5. `SOURCE database/migrations/us002_residents.sql;`
6. `SOURCE database/migrations/us002_seed.sql;`
7. `SOURCE database/migrations/us005_fee_types.sql;`
8. `SOURCE database/migrations/us006_fee_rates.sql;`
9. `SOURCE database/migrations/us007_billing.sql;`
10. `SOURCE database/migrations/us008_receipts.sql;`

---

## 5. Tài Khoản Thử Nghiệm Mẫu (Demo Credentials)

Sau khi nạp `seed.sql`, hệ thống được nạp sẵn 3 tài khoản kiểm thử theo đúng tài liệu SRS (sử dụng mật khẩu kiểm thử công khai, chỉ dùng cho môi trường dev/demo):

| Tên Đăng Nhập | Mật Khẩu | Vai Trò | Phạm Vi Quyền Hạn |
|---|---|---|---|
| `admin` | `admin@123` | **Quản Trị Viên (ADMIN)** | Toàn quyền xem & quản trị hộ gia đình, cư dân, loại phí, định mức phí, công nợ, thông báo. Không được trực tiếp ghi nhận phiếu thu tiền. |
| `accountant` | `accountant@123` | **Kế Toán (ACCOUNTANT)** | Quản lý thu tiền, tạo phiếu thu tiền (`PAYMENT_RECORD`), tra cứu báo cáo công nợ toàn tòa, lập và xuất thông báo thu phí. |
| `resident` | `resident@123` | **Cư Dân (RESIDENT)** | Xem danh sách căn hộ và thông tin cá nhân. Bị hệ thống chặn toàn bộ các chức năng kế toán và quản trị theo ma trận RBAC. |

---

## 6. Hướng Dẫn Build & Kiểm Thử

Đảm bảo biến môi trường `JAVA_HOME` trỏ tới JDK 17.

### 1. Khởi chạy ứng dụng máy tính (Desktop App)
```bash
mvn javafx:run
```
Lớp khởi chạy chính: `com.bluemoon.BlueMoonApplication`. Ứng dụng sẽ mở cửa sổ Đăng nhập BlueMoon.

### 2. Chạy kiểm thử hồi quy thông thường (Normal Unit Tests)
Không yêu cầu môi trường đồ họa JavaFX hoặc CSDL MySQL ngoài:
```bash
mvn clean test
```
*Kết quả hiện tại trên `main`: **695/695 tests PASS** (0 failures, 0 errors).*

### 3. Chạy kiểm thử Smoke Test Giao Diện JavaFX
Kiểm tra vòng đời khởi tạo giao diện FXML, Stage, Scene và khả năng render:
```bash
mvn -Pjavafx-smoke test
```
*Kết quả hiện tại trên `main`: **696/696 tests PASS** (0 failures, 0 errors).*

### 4. Chạy kiểm thử tích hợp CSDL thật (MySQL Integration Tests)
Yêu cầu tạo một CSDL kiểm thử riêng biệt có tên kết thúc bằng `_test` (ví dụ `bluemoon_test`):
```powershell
$env:BLUEMOON_DB_URL = "jdbc:mysql://localhost:3306/bluemoon_test?connectionTimeZone=UTC"
$env:BLUEMOON_DB_USERNAME = "your_test_user"
$env:BLUEMOON_DB_PASSWORD = "your_test_password"
$env:BLUEMOON_MYSQL_TEST = "1"
mvn -Pmysql-it verify
```

---

## 7. Giới Hạn Kỹ Thuật Đã Ghi Nhận & Kế Hoạch Sprint 2

Các quyết định nghiệp vụ đã được PO và Ban Điều Phối phê duyệt cho phạm vi Sprint 1:
1. **BR-13 (Cấn trừ nợ tự động kỳ sau):** Số tiền nộp thừa đã được ghi nhận an toàn vào bảng `apartment_credit`. Logic tự động khấu trừ vào hóa đơn kỳ sau được xếp lịch triển khai trong Sprint 2 cùng với module tính phí nâng cao.
2. **BR-17 (Xuất thông báo thu tiền):** Xuất tệp HTML định dạng in ấn (`page-break`) chuẩn mực để in hoặc chuyển lưu thành PDF qua trình duyệt máy tính; chưa nhúng thư viện sinh file PDF nhị phân nội bộ.
3. **PR #28 (Household BR-02 UI Guard):** Đang trong giai đoạn hoàn thiện Review & QA; backend và repository hook của BR-02 đã nằm sẵn trong `main`.
4. **US-016 & Quản lý Phương tiện (Vehicle Management):** Thuộc phạm vi của Sprint 2.

Chi tiết xem tại tệp: [`docs/KNOWN_LIMITATIONS.md`](docs/KNOWN_LIMITATIONS.md).

---

## 8. Danh Mục Tài Liệu Bàn Giao Chi Tiết

- 📘 [Hướng dẫn sử dụng chi tiết (User Guide)](docs/USER_GUIDE.md)
- 📊 [Báo cáo nghiệm thu & bàn giao Sprint 1 (Sprint 1 Report)](docs/SPRINT1_REPORT.md)
- 🧪 [Bằng chứng kiểm thử & QA Summary (QA Evidence)](docs/test/README.md)
- 📝 [Nhật ký cập nhật phiên bản (Changelog)](CHANGELOG.md)
- ⚠️ [Danh mục giới hạn kỹ thuật (Known Limitations)](docs/KNOWN_LIMITATIONS.md)
