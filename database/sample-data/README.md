# Dữ liệu mẫu BlueMoon: 100 căn hộ

Tệp: `bluemoon_sample_100_apartments.xlsx` (BlueMoon – Hybrid Reference-Based Dataset v1).

Đây là **dữ liệu mô phỏng** dùng cho demo và kiểm thử. Tệp này chưa được nạp vào `schema.sql` hay `seed.sql`. Muốn dùng trong ứng dụng cần chuyển sang SQL, xem mục [Lưu ý trước khi nạp](#lưu-ý-trước-khi-nạp).

## Nội dung

| Sheet | Nội dung | Số dòng | Ứng với bảng |
|---|---|---|---|
| `README` | Mô tả gói dữ liệu, nguồn, giấy phép | | |
| `Can ho` | `so_can_ho`, `tang`, `dien_tich`, `trang_thai` | 100 | `apartments` |
| `Ho gia dinh` | `ma_ho`, `so_can_ho`, `ngay_nhan_nha` | 92 | `households` |
| `Nhan khau` | `ma_ho`, `ho_ten`, `ngay_sinh`, `gioi_tinh`, `so_cccd`, `quan_he_chu_ho`, `ngay_chuyen_den` | 254 | `residents` |
| `Khoan thu` | `ma_loai_thu`, `ten_loai_thu`, `loai_thu`, `phuong_thuc_tinh`, `don_gia`, `ngay_ap_dung` | 3 | `fee_types`, `fee_rates` |
| `10_MAU_REAL` | 10 tin đăng căn hộ công khai tại Hà Nội, là nguồn của diện tích | 10 | |
| `Map_nguon` | Ánh xạ mỗi căn mô phỏng tới tin đăng nguồn | 100 | |
| `Nguon_goc_cot` | Phân loại nguồn từng cột: SYNTHETIC, REFERENCE_BASED hoặc DERIVED | | |

## Nguồn và giấy phép

- **Dữ liệu tự sinh:** mã căn, tầng, trạng thái, hộ gia đình, nhân khẩu và khoản thu. Họ tên là tên giả. Số CCCD dạng `9990000000xx` là giả, **không phải dữ liệu cá nhân thật**.
- **Dữ liệu lấy từ nguồn:** chỉ có diện tích, lấy từ 10 tin đăng trong bộ [Phuscca/vietnam-real-estates](https://huggingface.co/datasets/Phuscca/vietnam-real-estates) theo giấy phép **CC BY-NC 4.0**. Phải ghi công nguồn và chỉ dùng cho mục đích học tập, phi thương mại.

## Lưu ý trước khi nạp

Đối chiếu với các quy tắc của ứng dụng (SRS EP01, `us002_residents.sql`), tệp hiện có một số dòng cần sửa trước khi dùng làm dữ liệu demo:

| Vấn đề | Số dòng | Ví dụ |
|---|---|---|
| Nhân khẩu từ 14 tuổi trở lên thiếu CCCD (vi phạm quy tắc CCCD của US-002) | 27 | HK0002 – Đỗ Quốc Thảo (2012-01-28) |
| `VO`/`CHONG` dưới 18 tuổi | 45 | HK0004 – Hoàng Minh Ngọc (2017-12-15) |
| Tên có chữ "Thị" nhưng `gioi_tinh = NAM` | 23 | Đỗ Thị Quân |
| Hộ không có chủ hộ (`CHU_HO`) | 11 | HK0010, HK0019, HK0028… |
| Mã hộ có dạng `HK0002`, khác dạng `HK-0101` trong seed hiện tại | toàn bộ | |
| `PHI_GUI_XE` tính `THEO_NHAN_KHAU`, trong khi phí gửi xe thuộc US-022 (Sprint 2) | 1 | |

Các giá trị khác đều khớp ràng buộc CSDL: `so_can_ho` dạng `P[0-9]{4}`, tầng từ 1 đến 30, diện tích lớn hơn 0, enum giới tính, quan hệ và phương thức tính hợp lệ.
