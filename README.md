# Xây dựng Ứng dụng Quản lý Phòng khám Nhi (`csn-da24tta-trandangkhoa-phongkhamnhi`)

Kho lưu trữ chính thức của học phần **Thực tập Đồ án Cơ sở ngành** tại Khoa Công nghệ Thông tin, Trường Kỹ thuật và Công nghệ, Trường Đại học Trà Vinh (năm học 2026).

> **Trạng thái cập nhật:** Tính đến ngày **20/11/2026** (hoàn thành **Tuần 01 – Tuần 06 / 08 tuần** theo đề cương chi tiết). Mã nguồn backend, bộ kiểm thử API, 5 phân hệ nhân sự và Cổng tra cứu Phụ huynh (kèm API gộp đơn thuốc khử N+1 và CSS responsive) đã vận hành ổn định, thông luồng. Các hạng mục kiểm thử hiển thị di động thực tế, đo kiểm định lượng hiệu năng/bảo mật, quyển báo cáo hoàn chỉnh và Poster/Slide dự kiến hoàn tất trong Tuần 07–08 (`23/11/2026 - 06/12/2026`).

---

## 1. Thông tin Sinh viên và Giảng viên Hướng dẫn

| Hạng mục | Thông tin chi tiết |
| :--- | :--- |
| **Tên đề tài** | Xây dựng ứng dụng quản lý phòng khám nhi |
| **Học phần** | Thực tập Đồ án Cơ sở ngành |
| **Sinh viên thực hiện** | Trần Đăng Khoa |
| **Mã số sinh viên** | `110124097` |
| **Mã lớp** | `DA24TTA` |
| **Điện thoại** | `0867749262` |
| **Email** | `110124097@st.tvu.edu.vn` |
| **Giảng viên hướng dẫn** | ThS. Nguyễn Thừa Phát Tài |
| **Đơn vị đào tạo** | Khoa Công nghệ Thông tin, Trường Kỹ thuật và Công nghệ, Trường Đại học Trà Vinh |

---

## 2. Tiến độ Thực hiện đến Ngày 20/11/2026 (Tuần 06/08)

| Tuần | Thời gian | Nội dung thực hiện | Trạng thái |
| :---: | :---: | :--- | :---: |
| 1 | `12/10/2026 - 18/10/2026` | Khảo sát quy trình khám ngoại trú nhi khoa, đặc tả 12 ca sử dụng, phân quyền 4 vai trò và thiết kế lược đồ `co_so_du_lieu.dbml` chuẩn 3NF | Hoàn thành |
| 2 | `19/10/2026 - 25/10/2026` | Cài đặt CSDL MySQL 7 bảng (`utf8mb4_unicode_ci`, quan hệ `1:1` phụ huynh - tài khoản, 2 chỉ mục ghép, dữ liệu mẫu) và xuất `so_do_csdl.png` | Hoàn thành |
| 3 | `26/10/2026 - 01/11/2026` | Cài đặt `KetNoiCSDL.java` (`PBKDF2WithHmacSHA256` 600.000 vòng), 7 lớp Model dùng `BigDecimal` và 3 lớp DAO đầu (`TaiKhoanDAO`, `BenhNhiDAO`, `LichHenDAO`) | Hoàn thành |
| 4 | `02/11/2026 - 08/11/2026` | Hoàn thiện `ThuocDAO`, `PhieuKhamDAO` (đủ 5 lớp DAO = `1.639` dòng khác rỗng / `1.769` dòng tổng, khóa dòng `SELECT ... FOR UPDATE`, hoàn tồn kho khi hủy thuốc) và khung `MayChuAPI` | Hoàn thành |
| 5 | `09/11/2026 - 15/11/2026` | Hoàn chỉnh `MayChuAPI.java` (`1.041` dòng khác rỗng / `1.113` dòng tổng; 10 điểm cuối REST API + 1 ngữ cảnh tệp tĩnh = 11 ngữ cảnh HTTP, RBAC, IDOR, rate-limit `429`, regex chặn `e`/`E` `BigDecimal`) và vượt qua `24/24` ca Postman | Hoàn thành |
| 6 | `16/11/2026 - 20/11/2026` | Hoàn thiện 5 phân hệ nhân sự (`index.html`, `giao_dien.css`, `xu_ly.js`), tính tiền `BigDecimal`, cảnh báo dị ứng, in phiếu A4 1 trang và Cổng Phụ huynh `tra_cuu_phu_huynh.html` tích hợp API gộp đơn thuốc khử N+1 cùng CSS responsive (`4` tệp Frontend = `2.202` dòng khác rỗng / `2.405` dòng tổng; `14` tệp Java = `3.022` dòng khác rỗng / `3.264` dòng tổng) | Hoàn thành |
| 7 | `23/11/2026 - 29/11/2026` | Kiểm thử hiển thị thực tế trên thiết bị di động cho Cổng Phụ huynh, đo kiểm định lượng hiệu năng truy vấn khử N+1 (`EXPLAIN`) và triển khai viết Chương 3–4 quyển báo cáo | Kế hoạch |
| 8 | `30/11/2026 - 06/12/2026` | Kiểm thử bảo mật, chịu tải đa luồng, hoàn thiện quyển báo cáo chuẩn TVU 2026, đủ 8 phiếu tiến độ, Slide và Poster `60x90cm` | Kế hoạch |

---

## 3. Cấu trúc Cây Thư mục Repository

```text
csn-da24tta-trandangkhoa-phongkhamnhi/
├── .gitignore                        # Loại trừ tệp cấu hình database.properties, *.class, *.log, tệp tạm
├── README.md                         # Tài liệu tổng quan, tiến độ đến 20/11/2026 và hướng dẫn chạy thử
├── setup/                            # Tập tin khởi tạo CSDL, sơ đồ thiết kế và script chạy 1-Click
│   ├── chay_he_thong.bat             # Biên dịch UTF-8 (-Xlint:all) và khởi chạy máy chủ cổng 8080
│   ├── khoi_tao_csdl.sql             # Script tạo CSDL phong_kham_nhi (7 bảng 3NF) và dữ liệu mẫu
│   ├── co_so_du_lieu.dbml            # Đặc tả lược đồ CSDL chuẩn DBML v2
│   ├── so_do_csdl.png                # Sơ đồ quan hệ thực thể ERD 7 bảng
│   ├── so_do_trien_khai.png          # Sơ đồ triển khai kiến trúc 3 tầng của hệ thống
│   └── HUONG_DAN_CAI_DAT.md          # Hướng dẫn cài đặt và xử lý sự cố môi trường
├── scr/                              # Toàn bộ mã nguồn, mẫu cấu hình và bộ kiểm thử API
│   ├── backend/                      # Máy chủ Core Java (14 tệp .java: 3.022 dòng khác rỗng / 3.264 dòng tổng + mysql-connector.jar)
│   ├── config/                       # Tệp mẫu cấu hình JDBC (database.properties.example)
│   ├── frontend/                     # Giao diện nhân sự (index.html), cổng phụ huynh, CSS và JS (4 tệp: 2.202 dòng khác rỗng / 2.405 dòng tổng)
│   └── postman/                      # Bộ 24 kịch bản kiểm thử REST API (Bo_Kiem_Thu_API_PhongKhamNhi.json)
├── progress-report/                  # [Bắt buộc] Bộ báo cáo tiến độ dạng Word (.docx) từ Tuần 01 đến Tuần 06
└── thesis/                           # [Bắt buộc] Tài liệu tham khảo, phần mềm môi trường và thư mục hồ sơ đồ án
    ├── doc/                          # Thư mục lưu quyển báo cáo Word (sẽ cập nhật khi hoàn thiện)
    ├── pdf/                          # Thư mục lưu quyển báo cáo PDF và Poster (sẽ cập nhật khi hoàn thiện)
    ├── html/                         # Thư mục lưu ấn phẩm web/hình ảnh (sẽ cập nhật khi hoàn thiện)
    ├── abs/                          # Thư mục lưu Slide thuyết trình bảo vệ (sẽ cập nhật ở Tuần 08)
    ├── refs/                         # 12 tệp hồ sơ tài liệu tham khảo chuẩn IEEE
    └── soft/                         # Thư viện mysql-connector.jar và hướng dẫn công cụ môi trường
```

---

## 4. Hướng dẫn Cài đặt và Chạy thử Nhanh (3 Bước)

### Yêu cầu môi trường
- Hệ điều hành **Windows 10/11**.
- Bộ công cụ **Java JDK** (JDK 17 trở lên; phát triển và kiểm tra biên dịch trên **JDK 25**).
- Dịch vụ **MySQL Server** cổng `3306` (khuyến nghị dùng **XAMPP Control Panel**).

### Các bước thực hiện
1. **Khởi tạo Cơ sở dữ liệu và dữ liệu mẫu:**
   - Mở **XAMPP Control Panel**, nhấn **Start** tại dòng **MySQL** (và **Apache** nếu dùng phpMyAdmin).
   - Truy cập `http://localhost/phpmyadmin`, chọn thẻ **SQL**, dán toàn bộ nội dung tệp [setup/khoi_tao_csdl.sql](setup/khoi_tao_csdl.sql) và nhấn **Go** để tạo cơ sở dữ liệu `phong_kham_nhi` chuẩn `utf8mb4_unicode_ci`.
   - Nếu MySQL có đặt mật khẩu `root`, sao chép `scr/config/database.properties.example` thành `scr/config/database.properties` và điền mật khẩu vào khóa `db.password=`.
2. **Biên dịch và khởi chạy máy chủ HTTP (`8080`):**
   - Nhấp đúp vào tệp [setup/chay_he_thong.bat](setup/chay_he_thong.bat) (hoặc chạy `setup\chay_he_thong.bat --compile-only` để chỉ kiểm tra biên dịch).
   - Kịch bản tự động biên dịch toàn bộ 14 tệp `.java` bằng `javac -Xlint:all -encoding UTF-8` và khởi chạy `api.MayChuAPI` tại `http://localhost:8080`.
3. **Truy cập giao diện và kiểm thử API:**
   - **Phân hệ Nhân sự phòng khám (Lễ tân, Bác sĩ, Quản trị):** `http://localhost:8080`
   - **Cổng tra cứu Sổ sức khỏe Phụ huynh:** `http://localhost:8080/tra_cuu_phu_huynh.html`
   - **Kiểm thử REST API (24 ca):** Mở **Postman**, nhấn **Import** tệp [scr/postman/Bo_Kiem_Thu_API_PhongKhamNhi.json](scr/postman/Bo_Kiem_Thu_API_PhongKhamNhi.json) và chọn **Run Collection**.

---

## 5. Tài khoản Thử nghiệm Mặc định

*(Mật khẩu mẫu trong SQL tự động nâng cấp lên chuỗi băm `PBKDF2WithHmacSHA256` 600.000 vòng ngay ở lần đăng nhập thành công đầu tiên)*

| Vai trò hệ thống | Tên đăng nhập | Mật khẩu mẫu | Giao diện sử dụng | Quyền hạn trọng tâm |
| :--- | :---: | :---: | :--- | :--- |
| **Quản trị viên** (`QUAN_TRI`) | `admin` | `123456` | `http://localhost:8080` | Toàn quyền hệ thống, xóa hồ sơ chưa có bệnh án, thu viện phí, thống kê doanh thu, mở khóa tài khoản |
| **Bác sĩ nhi khoa** (`BAC_SI`) | `bacsi1` | `123456` | `http://localhost:8080` | Xem cảnh báo dị ứng, khám bệnh ghi cân nặng, kê/bỏ thuốc khóa tồn kho, quản lý kho thuốc, in phiếu A4 |
| **Nhân viên lễ tân** (`LE_TAN`) | `letan1` | `123456` | `http://localhost:8080` | Tiếp nhận bệnh nhi (`0-18` tuổi, 1 SĐT nhiều bé), xếp lịch hẹn bác sĩ, thu viện phí, đối soát ca trong ngày, in phiếu |
| **Phụ huynh** (`PHU_HUYNH`) | `0901234567` | `123456` | `http://localhost:8080/tra_cuu_phu_huynh.html` | Xem hồ sơ các con ruột (`Phạm Gia Bảo`, `Phạm Ngọc Hân`), lịch hẹn, đơn thuốc và đổi mật khẩu |

---

## 6. Liên kết Nhanh Tài liệu Hiện có (Mốc 20/11/2026)

- Báo cáo Tiến độ mới nhất (Tuần 06): [progress-report/Bao_Cao_Tien_Do_Tuan_06_TranDangKhoa_110124097.docx](progress-report/Bao_Cao_Tien_Do_Tuan_06_TranDangKhoa_110124097.docx)
- Hướng dẫn Cài đặt & Sơ đồ Triển khai: [setup/HUONG_DAN_CAI_DAT.md](setup/HUONG_DAN_CAI_DAT.md)
- Kịch bản Khởi tạo CSDL mẫu: [setup/khoi_tao_csdl.sql](setup/khoi_tao_csdl.sql)
- Bộ kiểm thử 24 API Postman: [scr/postman/Bo_Kiem_Thu_API_PhongKhamNhi.json](scr/postman/Bo_Kiem_Thu_API_PhongKhamNhi.json)
- Danh mục Tài liệu Tham khảo IEEE: [thesis/refs/00_Danh_Muc_Tai_Lieu_Tham_Khao_IEEE.md](thesis/refs/00_Danh_Muc_Tai_Lieu_Tham_Khao_IEEE.md)
