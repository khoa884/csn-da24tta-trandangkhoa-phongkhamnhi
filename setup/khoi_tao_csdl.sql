-- Kịch bản khởi tạo CSDL MySQL cho Hệ thống Quản lý Phòng khám Nhi (Đồng bộ co_so_du_lieu.dbml & so_do_csdl.png)

DROP DATABASE IF EXISTS phong_kham_nhi;
CREATE DATABASE phong_kham_nhi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE phong_kham_nhi;

-- Tài khoản nhân sự và phụ huynh (tạm khóa 15 phút khi sai mật khẩu >= 5 lần, riêng QUAN_TRI không bị khóa)
CREATE TABLE tai_khoan (
    ma_tai_khoan INT PRIMARY KEY AUTO_INCREMENT,
    ten_dang_nhap VARCHAR(50) NOT NULL UNIQUE,
    mat_khau VARCHAR(255) NOT NULL,
    ho_ten VARCHAR(100) NOT NULL,
    vai_tro VARCHAR(20) NOT NULL,
    so_lan_sai INT DEFAULT 0,
    trang_thai_khoa INT DEFAULT 0,
    khoa_den_luc DATETIME NULL DEFAULT NULL,
    phai_doi_mat_khau INT DEFAULT 0
);

-- Phụ huynh dùng SĐT làm khóa liên kết nhiều hồ sơ con (anh/chị/em ruột)
CREATE TABLE phu_huynh (
    ma_phu_huynh INT PRIMARY KEY AUTO_INCREMENT,
    ho_ten_phu_huynh VARCHAR(100) NOT NULL,
    so_dien_thoai VARCHAR(15) NOT NULL UNIQUE,
    dia_chi VARCHAR(255),
    ma_tai_khoan INT UNIQUE,
    FOREIGN KEY (ma_tai_khoan) REFERENCES tai_khoan(ma_tai_khoan)
);

CREATE TABLE benh_nhi (
    ma_benh_nhi INT PRIMARY KEY AUTO_INCREMENT,
    ho_ten_be VARCHAR(100) NOT NULL,
    ngay_sinh DATE NOT NULL,
    gioi_tinh VARCHAR(10) NOT NULL,
    tien_su_benh TEXT,
    ma_phu_huynh INT NOT NULL,
    FOREIGN KEY (ma_phu_huynh) REFERENCES phu_huynh(ma_phu_huynh)
);

CREATE TABLE lich_hen (
    ma_lich_hen INT PRIMARY KEY AUTO_INCREMENT,
    ma_benh_nhi INT NOT NULL,
    ma_bac_si INT NOT NULL,
    ngay_hen DATE NOT NULL,
    gio_hen VARCHAR(10) NOT NULL,
    ly_do_kham VARCHAR(255),
    trang_thai VARCHAR(30) DEFAULT 'Chờ khám',
    FOREIGN KEY (ma_benh_nhi) REFERENCES benh_nhi(ma_benh_nhi),
    FOREIGN KEY (ma_bac_si) REFERENCES tai_khoan(ma_tai_khoan)
);

CREATE TABLE thuoc (
    ma_thuoc INT PRIMARY KEY AUTO_INCREMENT,
    ten_thuoc VARCHAR(150) NOT NULL,
    don_vi_tinh VARCHAR(30) NOT NULL,
    don_gia DECIMAL(12,2) NOT NULL,
    cach_dung_mac_dinh VARCHAR(255),
    so_luong_ton INT DEFAULT 0
);

CREATE TABLE phieu_kham (
    ma_phieu_kham INT PRIMARY KEY AUTO_INCREMENT,
    ma_benh_nhi INT NOT NULL,
    ma_bac_si INT NOT NULL,
    ngay_kham DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    can_nang_kg DECIMAL(5,2) NOT NULL,
    trieu_chung VARCHAR(255) NOT NULL,
    chan_doan VARCHAR(255) NOT NULL,
    loi_dan_bac_si TEXT,
    tien_kham DECIMAL(12,2) DEFAULT 100000,
    tien_thuoc DECIMAL(12,2) DEFAULT 0,
    tong_vien_phi DECIMAL(12,2) DEFAULT 100000,
    trang_thai_phieu VARCHAR(50) DEFAULT 'Đã khám (Chờ thu tiền)',
    FOREIGN KEY (ma_benh_nhi) REFERENCES benh_nhi(ma_benh_nhi),
    FOREIGN KEY (ma_bac_si) REFERENCES tai_khoan(ma_tai_khoan)
);

-- Lưu don_gia_luc_ke để việc cập nhật giá thuốc sau này không làm sai lệch doanh thu cũ
CREATE TABLE toa_thuoc (
    ma_toa_thuoc INT PRIMARY KEY AUTO_INCREMENT,
    ma_phieu_kham INT NOT NULL,
    ma_thuoc INT NOT NULL,
    so_luong INT NOT NULL,
    don_gia_luc_ke DECIMAL(12,2) NOT NULL,
    lieu_luong_dung VARCHAR(255) NOT NULL,
    thanh_tien DECIMAL(12,2) NOT NULL,
    FOREIGN KEY (ma_phieu_kham) REFERENCES phieu_kham(ma_phieu_kham),
    FOREIGN KEY (ma_thuoc) REFERENCES thuoc(ma_thuoc)
);

CREATE INDEX idx_lich_hen_bac_si_ngay_gio ON lich_hen (ma_bac_si, ngay_hen, gio_hen);
CREATE INDEX idx_phieu_kham_trang_thai_ngay ON phieu_kham (trang_thai_phieu, ngay_kham);

-- Dữ liệu mẫu khởi tạo (mật khẩu mặc định 123456)
INSERT INTO tai_khoan (ten_dang_nhap, mat_khau, ho_ten, vai_tro, so_lan_sai, trang_thai_khoa) VALUES
('admin', SHA2('123456', 256), 'Đỗ Thành Công', 'QUAN_TRI', 0, 0),
('bacsi1', SHA2('123456', 256), 'BS. Trần Minh Tâm', 'BAC_SI', 0, 0),
('letan1', SHA2('123456', 256), 'Lê Thị Thu Hà', 'LE_TAN', 0, 0),
('0901234567', SHA2('123456', 256), 'Phạm Văn Hùng', 'PHU_HUYNH', 0, 0);

INSERT INTO phu_huynh (ho_ten_phu_huynh, so_dien_thoai, dia_chi, ma_tai_khoan) VALUES
('Phạm Văn Hùng', '0901234567', '48 Phạm Thái Bường, Phường 4, TP. Vĩnh Long', 4);

INSERT INTO benh_nhi (ho_ten_be, ngay_sinh, gioi_tinh, tien_su_benh, ma_phu_huynh) VALUES
('Phạm Gia Bảo', '2021-05-15', 'Nam', 'Dị ứng kháng sinh nhóm Penicillin, từng bị sốt co giật nhẹ năm 2 tuổi.', 1),
('Phạm Ngọc Hân', '2023-08-20', 'Nữ', 'Không có dị ứng thuốc. Cơ địa nhạy cảm thời tiết lạnh.', 1);

INSERT INTO lich_hen (ma_benh_nhi, ma_bac_si, ngay_hen, gio_hen, ly_do_kham, trang_thai) VALUES
(1, 2, CURDATE(), '08:30', 'Bé sốt nhẹ 38.2 độ, ho khan 2 ngày nay', 'Đã khám'),
(2, 2, CURDATE(), '09:00', 'Khám định kỳ dinh dưỡng và tư vấn tiêu hóa', 'Chờ khám');

INSERT INTO thuoc (ten_thuoc, don_vi_tinh, don_gia, cach_dung_mac_dinh, so_luong_ton) VALUES
('Hapacol 150mg (Hạ sốt cam ngọt)', 'Gói', 3000, 'Pha 1 gói với 10ml nước ấm, uống khi sốt trên 38.5 độ (cách nhau 4-6 tiếng)', 200),
('Siro ho thảo dược Prospan 100ml', 'Chai', 75000, 'Uống ngày 3 lần, mỗi lần 2.5ml sau bữa ăn sáng - trưa - tối', 50),
('Men vi sinh Enterogermina', 'Ống', 8000, 'Uống trực tiếp ngày 2 lần, mỗi lần 1 ống', 150);

INSERT INTO phieu_kham (ma_benh_nhi, ma_bac_si, ngay_kham, can_nang_kg, trieu_chung, chan_doan, loi_dan_bac_si, tien_kham, tien_thuoc, tong_vien_phi, trang_thai_phieu) VALUES
(1, 2, CONCAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), ' 08:45:00'), 14.20, 'Sốt 38.2 độ C, họng đỏ, ho khan', 'Viêm họng cấp ở trẻ em', 'Cho bé uống nhiều nước ấm, lau mát khi sốt. Hẹn tái khám sau 3 ngày nếu còn sốt.', 100000, 105000, 205000, 'Đã thanh toán'),
(1, 2, CONCAT(CURDATE(), ' 09:15:00'), 14.50, 'Hết sốt, giảm ho, tái khám kiểm tra họng và tiêu hóa', 'Theo dõi phục hồi sau viêm họng cấp', 'Tiếp tục giữ ấm cổ họng, bổ sung men vi sinh và uống đủ nước ấm.', 100000, 16000, 116000, 'Đã khám (Chờ thu tiền)');

INSERT INTO toa_thuoc (ma_phieu_kham, ma_thuoc, so_luong, don_gia_luc_ke, lieu_luong_dung, thanh_tien) VALUES
(1, 1, 10, 3000, 'Pha 1 gói với 10ml nước ấm, chỉ uống khi bé sốt >= 38.5 độ C (tính theo cân nặng 14.2kg)', 30000),
(1, 2, 1, 75000, 'Uống ngày 3 lần, mỗi lần 2.5ml sau khi ăn', 75000),
(2, 3, 2, 8000, 'Uống trực tiếp ngày 2 lần, mỗi lần 1 ống sau ăn (tính theo cân nặng 14.5kg)', 16000);
