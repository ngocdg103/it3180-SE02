# BlueMoon

## Giới thiệu
Dự án BlueMoon - Hệ thống quản lý dành cho chung cư / khu đô thị.

## Cấu trúc thư mục
```text
bluemoon/
├── README.md               # Tài liệu tổng quan dự án
├── CHANGELOG.md            # Nhật ký thay đổi phiên bản
├── .gitignore              # Cấu hình bỏ qua tệp tin trong Git
├── src/                    # Mã nguồn chính (Java MVC + Services)
│   └── main/
│       ├── java/
│       │   ├── models/     # Các lớp đối tượng dữ liệu (Entity/POJO)
│       │   ├── views/      # Giao diện người dùng (UI/Views)
│       │   ├── controllers/# Bộ điều hướng & xử lý sự kiện
│       │   └── services/   # Tầng nghiệp vụ xử lý logic
│       └── resources/      # Tài nguyên ứng dụng (ảnh, cấu hình, i18n,...)
├── database/               # Kịch bản cơ sở dữ liệu
│   ├── schema.sql          # Định nghĩa cấu trúc bảng (DDL)
│   └── seed.sql            # Dữ liệu khởi tạo ban đầu (DML)
├── docs/                   # Tài liệu dự án
│   ├── srs/                # Software Requirements Specification
│   ├── design/             # Thiết kế kiến trúc, DB, UI/UX, UML
│   ├── test/               # Kế hoạch & kịch bản kiểm thử
│   └── user-guide/         # Hướng dẫn sử dụng cho người dùng
├── test/                   # Mã nguồn & kịch bản kiểm thử
└── config/                 # Cấu hình hệ thống
    └── db.properties.example # Cấu hình kết nối DB mẫu
```

## Yêu cầu môi trường
- Java Development Kit (JDK) 17.
- Maven 3.9.x; JavaFX + FXML (dependency qua Maven).
- Hệ quản trị CSDL: MySQL 8.x, truy cập qua JDBC.
- IDE khuyên dùng: IntelliJ IDEA / Eclipse / VS Code

## Cài đặt & Khởi chạy
1. Sao chép tệp cấu hình:
   ```bash
   cp config/db.properties.example config/db.properties
   ```
2. Cập nhật thông tin kết nối CSDL trong `config/db.properties`.
3. Build/test: `mvn clean verify`.
4. Khởi chạy màn hình Foundation: `mvn javafx:run`.

Source mới theo Maven nằm trong `src/main/java/com/bluemoon/`;
FXML nằm trong `src/main/resources/com/bluemoon/views/`;
test nằm trong `src/test/java/com/bluemoon/`.

Backend US-012 sử dụng bảng `users`, BCrypt và `SessionContext` theo SRS EP-06.
Login UI do Dev-2 phụ trách; app hiện hiển thị màn hình Foundation.
Khởi tạo database MySQL 8.x riêng, chọn database rồi chạy `database/schema.sql`
và `database/seed.sql`. Seed chỉ dùng cho local/QA (tài khoản mẫu trong SRS).
Xem [hướng dẫn setup](docs/user-guide/developer-setup.md),
[đối chiếu yêu cầu](docs/design/iteration-01-review.md) và
[hợp đồng API cho Dev-2](docs/design/auth-api-proposal.md).

