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
- Java Development Kit (JDK) 17+ (hoặc JDK 21)
- Hệ quản trị CSDL: MySQL / PostgreSQL / SQL Server
- IDE khuyên dùng: IntelliJ IDEA / Eclipse / VS Code

## Cài đặt & Khởi chạy
1. Sao chép tệp cấu hình:
   ```bash
   cp config/db.properties.example config/db.properties
   ```
2. Cập nhật thông tin kết nối CSDL trong `config/db.properties`.
3. Chạy các file SQL trong thư mục `database/` theo thứ tự:
   - `database/schema.sql`
   - `database/seed.sql`
4. Mở dự án trong IDE và tiến hành biên dịch/chạy ứng dụng.

