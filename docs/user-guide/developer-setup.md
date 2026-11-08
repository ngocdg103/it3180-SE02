# Hướng Dẫn Thiết Lập Môi Trường Phát Triển — BlueMoon (Sprint 1)

Tài liệu này hướng dẫn các thành viên trong nhóm và người chấm cài đặt môi trường, cấu hình cơ sở dữ liệu và thực thi ứng dụng BlueMoon trên máy trạm.

---

## 1. Yêu Cầu Môi Trường (System Requirements)

- **Java Development Kit (JDK):** Bắt buộc **JDK 17 LTS** (ví dụ Eclipse Temurin 17, Oracle OpenJDK 17). Biến môi trường `JAVA_HOME` phải trỏ tới thư mục gốc của JDK 17.
- **Apache Maven:** Phiên bản **3.9.x** có sẵn trên biến môi trường `PATH`.
- **Hệ quản trị CSDL:** **MySQL 8.0 trở lên** hoặc **MySQL 8.4 LTS** (kiểm tra `mysql -V`).
- **Hệ điều hành:** Windows 10/11, macOS hoặc Linux.

---

## 2. Cài Đặt CSDL & Khởi Tạo Dữ Liệu

1. Khởi động MySQL Server.
2. Tạo CSDL mới sử dụng bảng mã `utf8mb4`:
   ```sql
   CREATE DATABASE bluemoon_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   USE bluemoon_db;
   ```
3. Chạy lần lượt các tập tin schema và migrations chính thức theo đúng thứ tự:
   ```sql
   SOURCE database/schema.sql;
   SOURCE database/seed.sql;
   SOURCE database/migrations/us001_household_apartment.sql;
   SOURCE database/migrations/us001_seed.sql;
   SOURCE database/migrations/us002_residents.sql;
   SOURCE database/migrations/us002_seed.sql;
   SOURCE database/migrations/us005_fee_types.sql;
   SOURCE database/migrations/us006_fee_rates.sql;
   SOURCE database/migrations/us007_billing.sql;
   SOURCE database/migrations/us008_receipts.sql;
   ```

---

## 3. Cấu Hình Ứng Dụng (Database Properties)

1. Sao chép tệp cấu hình mẫu:
   ```powershell
   Copy-Item config/db.properties.example config/db.properties
   ```
2. Mở `config/db.properties` và điền thông tin kết nối CSDL của bạn:
   ```properties
   db.url=jdbc:mysql://localhost:3306/bluemoon_db?connectionTimeZone=UTC&useUnicode=true&characterEncoding=UTF-8
   db.username=root
   db.password=your_secret_password
   ```

*Hệ thống hỗ trợ các biến môi trường ghi đè:*
- `BLUEMOON_DB_CONFIG`: Đường dẫn tới tệp properties thay thế.
- `BLUEMOON_DB_URL`: Ghi đè URL kết nối.
- `BLUEMOON_DB_USERNAME`: Ghi đè tên người dùng CSDL.
- `BLUEMOON_DB_PASSWORD`: Ghi đè mật khẩu CSDL.

---

## 4. Biên Dịch & Chạy Ứng Dụng

- **Biên dịch và kiểm tra tính toàn vẹn (Build & verify):**
  ```powershell
  mvn clean compile
  ```
- **Khởi chạy ứng dụng đồ họa (JavaFX Desktop):**
  ```powershell
  mvn javafx:run
  ```
- **Chạy bộ kiểm thử đơn vị (Unit Tests):**
  ```powershell
  mvn test
  ```
- **Chạy kiểm thử giao diện khói (JavaFX Smoke Tests):**
  ```powershell
  mvn -Pjavafx-smoke test
  ```

---

## 5. Tài Khoản Thử Nghiệm Sẵn Có (Demo Credentials)

Sau khi chạy `database/seed.sql`, hệ thống có sẵn 3 tài khoản mẫu phục vụ việc đánh giá:
- `admin` / `admin@123` (Vai trò `ADMIN`: Quản trị toàn hệ thống, danh mục phí, căn hộ, cư dân, công nợ).
- `accountant` / `accountant@123` (Vai trò `ACCOUNTANT`: Ghi nhận thu tiền, lập phiếu thu, báo cáo công nợ, xuất thông báo thu phí).
- `resident` / `resident@123` (Vai trò `RESIDENT`: Tra cứu thông tin cá nhân).
