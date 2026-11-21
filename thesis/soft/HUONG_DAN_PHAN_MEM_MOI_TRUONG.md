# Thư viện và Phần mềm Môi trường Đi kèm (`thesis/soft/`)

Thư mục này lưu trữ gói thư viện kết nối cơ sở dữ liệu đóng gói sẵn của đề tài và danh mục các phần mềm nền tảng phục vụ biên dịch, chạy kiểm chứng hệ thống **Quản lý Phòng khám Nhi**.

## 1. Thư viện đóng gói trực tiếp trong dự án

Tệp `mysql-connector.jar` là trình điều khiển chuẩn JDBC Type-4 (**MySQL Connector/J**) phục vụ kết nối tầng truy xuất dữ liệu Core Java (`KetNoiCSDL.java`) với hệ quản trị MySQL Server qua cổng `3306`. Bản thực thi chính được đặt đồng bộ tại `../../scr/backend/lib/mysql-connector.jar` và tự động nạp vào đường dẫn lớp qua tham số `-cp "lib/*;src"` khi khởi chạy tệp `../../setup/chay_he_thong.bat`.

## 2. Phần mềm nền tảng trên môi trường triển khai tiêu chuẩn

Tuân thủ quy định quản lý dung lượng kho lưu trữ GitHub Repository (không đưa bộ cài hệ điều hành dung lượng hàng trăm MB lên kho mã nguồn), hệ thống vận hành trực tiếp trên bộ công cụ tiêu chuẩn dưới đây:

| Phần mềm nền tảng | Phiên bản khuyến nghị | Vai trò trong quá trình chạy kiểm chứng |
| :--- | :--- | :--- |
| Java SE Development Kit (JDK) | JDK 25 (tương thích ngược JDK 17+) | Cung cấp trình biên dịch `javac -Xlint:all` và mô-đun `jdk.httpserver` chạy máy chủ cổng `8080` |
| XAMPP (MySQL Server & phpMyAdmin) | MySQL 8.0 / MariaDB cổng `3306` | Khởi tạo CSDL `phong_kham_nhi` từ tệp `../../setup/khoi_tao_csdl.sql` chuẩn `utf8mb4_unicode_ci` |
| Postman Desktop | Postman v10+ | Thực thi tự động 24 kịch bản kiểm thử REST API (`Bo_Kiem_Thu_API_PhongKhamNhi.json`) |
| Microsoft Edge / Google Chrome | Phiên bản Chromium hiện hành | Vận hành 5 phân hệ nhân sự, Cổng tra cứu Phụ huynh và xuất in phiếu khám chuẩn khổ A4 |
