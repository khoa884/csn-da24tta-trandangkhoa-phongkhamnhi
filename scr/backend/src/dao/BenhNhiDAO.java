package dao;

import config.KetNoiCSDL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import model.BenhNhi;
import model.PhuHuynh;

/**
 * Quản lý hồ sơ bệnh nhi và liên kết phụ huynh (1 SĐT cho nhiều bé).
 */
public class BenhNhiDAO {

    private static final Object[] KHOA_THEO_SDT = new Object[64];
    static {
        for (int i = 0; i < KHOA_THEO_SDT.length; i++) {
            KHOA_THEO_SDT[i] = new Object();
        }
    }

    public BenhNhiDAO() {
    }

    public List<BenhNhi> timKiemBenhNhi(String tuKhoa) {
        String tuKhoaSach = (tuKhoa == null) ? "" : tuKhoa.trim();
        if (tuKhoaSach.matches("^[0-9]+$")) {
            String sqlSdt = "SELECT b.*, p.ho_ten_phu_huynh, p.so_dien_thoai, p.dia_chi "
                          + "FROM benh_nhi b INNER JOIN phu_huynh p ON b.ma_phu_huynh = p.ma_phu_huynh "
                          + "WHERE p.so_dien_thoai LIKE ? "
                          + "ORDER BY b.ma_benh_nhi DESC";
            return truyVanDanhSachBenhNhi(sqlSdt, tuKhoaSach + "%");
        }
        String sql = "SELECT b.*, p.ho_ten_phu_huynh, p.so_dien_thoai, p.dia_chi "
                   + "FROM benh_nhi b INNER JOIN phu_huynh p ON b.ma_phu_huynh = p.ma_phu_huynh "
                   + "WHERE b.ho_ten_be LIKE ? ESCAPE '!' OR p.so_dien_thoai LIKE ? ESCAPE '!' "
                   + "ORDER BY b.ma_benh_nhi DESC";
        String goc = tuKhoaSach
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
        String chuoiTim = "%" + goc + "%";
        return truyVanDanhSachBenhNhi(sql, chuoiTim, chuoiTim);
    }

    public List<BenhNhi> layDanhSachBenhNhiTheoSDT(String soDienThoai) {
        if (soDienThoai == null || soDienThoai.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String sql = "SELECT b.*, p.ho_ten_phu_huynh, p.so_dien_thoai, p.dia_chi "
                   + "FROM benh_nhi b INNER JOIN phu_huynh p ON b.ma_phu_huynh = p.ma_phu_huynh "
                   + "WHERE p.so_dien_thoai = ? "
                   + "ORDER BY b.ma_benh_nhi DESC";
        return truyVanDanhSachBenhNhi(sql, soDienThoai.trim());
    }

    private List<BenhNhi> truyVanDanhSachBenhNhi(String sql, String... thamSo) {
        List<BenhNhi> danhSach = new ArrayList<>();
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return danhSach;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                for (int i = 0; i < thamSo.length; i++) {
                    lenh.setString(i + 1, thamSo[i]);
                }
                try (ResultSet kq = lenh.executeQuery()) {
                    while (kq.next()) {
                        BenhNhi bn = new BenhNhi();
                        bn.maBenhNhi = kq.getInt("ma_benh_nhi");
                        bn.hoTenBe = kq.getString("ho_ten_be");
                        bn.ngaySinh = kq.getString("ngay_sinh");
                        bn.gioiTinh = kq.getString("gioi_tinh");
                        bn.tienSuBenh = kq.getString("tien_su_benh");
                        bn.maPhuHuynh = kq.getInt("ma_phu_huynh");
                        bn.hoTenPhuHuynh = kq.getString("ho_ten_phu_huynh");
                        bn.soDienThoai = kq.getString("so_dien_thoai");
                        bn.diaChi = kq.getString("dia_chi");
                        danhSach.add(bn);
                    }
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi tìm kiếm bệnh nhi: " + loi.getMessage());
        }
        return danhSach;
    }

    private static String kiemTraNgaySinhNhiKhoa(String ngaySinhRaw, boolean kiemTraTuoiNhiKhoa) {
        if (ngaySinhRaw == null || ngaySinhRaw.trim().isEmpty()) {
            return "Vui lòng nhập Ngày sinh của bé theo định dạng Năm-Tháng-Ngày (YYYY-MM-DD)!";
        }
        LocalDate ngaySinh;
        try {
            ngaySinh = LocalDate.parse(ngaySinhRaw.trim());
        } catch (Exception e) {
            return "Ngày sinh của bé không đúng định dạng hoặc không tồn tại trên lịch (YYYY-MM-DD)!";
        }
        LocalDate homNay = LocalDate.now();
        if (ngaySinh.isAfter(homNay)) {
            return "Ngày sinh của bé không được lớn hơn ngày hiện tại!";
        }
        if (kiemTraTuoiNhiKhoa && ngaySinh.isBefore(homNay.minusYears(18))) {
            return "Bệnh nhi thuộc diện khám nhi khoa từ 0 đến 18 tuổi, vui lòng kiểm tra lại năm sinh!";
        }
        return null;
    }

    /**
     * Tiếp nhận bệnh nhi trong Transaction: nếu SĐT phụ huynh chưa có sẽ tự tạo tài khoản PHU_HUYNH với mật khẩu ngẫu nhiên.
     */
    public String themBenhNhi(BenhNhi bn, StringBuilder matKhauTamOut) {
        if (bn == null || bn.hoTenBe == null || bn.hoTenBe.trim().isEmpty()) {
            return "Vui lòng nhập đầy đủ Họ và tên của bé!";
        }
        if (bn.soDienThoai == null || !bn.soDienThoai.trim().matches("^[0-9]{9,15}$")) {
            return "Vui lòng nhập Số điện thoại hợp lệ của bố mẹ (từ 9 đến 15 chữ số)!";
        }

        String loiNgaySinh = kiemTraNgaySinhNhiKhoa(bn.ngaySinh, true);
        if (loiNgaySinh != null) {
            return loiNgaySinh;
        }
        String ngaySinhChuan = bn.ngaySinh.trim();

        String gioiTinhChuan = "Nam";
        if (bn.gioiTinh != null && !bn.gioiTinh.trim().isEmpty()) {
            String gt = bn.gioiTinh.trim();
            if ("Nam".equalsIgnoreCase(gt)) {
                gioiTinhChuan = "Nam";
            } else if ("Nữ".equalsIgnoreCase(gt)) {
                gioiTinhChuan = "Nữ";
            } else {
                return "Giới tính của bé chỉ nhận giá trị 'Nam' hoặc 'Nữ'!";
            }
        }
        String tenBeChuan = bn.hoTenBe.trim();
        String sdtChuan = bn.soDienThoai.trim();
        String tenPhuHuynhChuan = (bn.hoTenPhuHuynh == null || bn.hoTenPhuHuynh.trim().isEmpty()) ? ("Phụ huynh " + sdtChuan) : bn.hoTenPhuHuynh.trim();
        String diaChiChuan = (bn.diaChi == null) ? "" : bn.diaChi.trim();
        String tienSuChuan = (bn.tienSuBenh == null || bn.tienSuBenh.trim().isEmpty()) ? "Không có dị ứng thuốc" : bn.tienSuBenh.trim();
        if (tenBeChuan.length() > 100 || tenPhuHuynhChuan.length() > 100 || diaChiChuan.length() > 255 || tienSuChuan.length() > 2000) {
            return "Họ tên (<=100), địa chỉ (<=255) hoặc tiền sử bệnh (<=2000) vượt quá độ dài cho phép!";
        }
        PhuHuynh phMoi = new PhuHuynh(0, tenPhuHuynhChuan, sdtChuan, diaChiChuan, 0);

        Object khoaSdt = KHOA_THEO_SDT[Math.floorMod(sdtChuan.hashCode(), KHOA_THEO_SDT.length)];
        synchronized (khoaSdt) {
            String bamMatKhauSan = null;
            String matKhauUngVien = null;
            for (int lanThu = 1; lanThu <= 4; lanThu++) {
                String matKhauTamTaoMoi = null;
                Connection ketNoi = null;
                try {
                    ketNoi = KetNoiCSDL.layKetNoi();
                    if (ketNoi == null) {
                        return "Lỗi kết nối Cơ sở dữ liệu!";
                    }
                    // Tính PBKDF2 ngoài giao dịch để không giữ khóa dòng/Gap Lock trong suốt ~227ms CPU
                    if (bamMatKhauSan == null) {
                        boolean chuaCoTK = true;
                        try (PreparedStatement ktNhanh = ketNoi.prepareStatement("SELECT 1 FROM tai_khoan WHERE ten_dang_nhap = ? LIMIT 1")) {
                            ktNhanh.setString(1, sdtChuan);
                            try (ResultSet kqNhanh = ktNhanh.executeQuery()) {
                                chuaCoTK = !kqNhanh.next();
                            }
                        }
                        if (chuaCoTK) {
                            matKhauUngVien = KetNoiCSDL.taoMatKhauNgauNhien(8);
                            bamMatKhauSan = KetNoiCSDL.maHoaMatKhau(matKhauUngVien);
                        }
                    }

                    ketNoi.setAutoCommit(false);

                    int maPhuHuynhDungChung = 0;

                    String sqlKiemTraPH = "SELECT ma_phu_huynh FROM phu_huynh WHERE so_dien_thoai = ? FOR UPDATE";
                    try (PreparedStatement lenhKiemTraPH = ketNoi.prepareStatement(sqlKiemTraPH)) {
                        lenhKiemTraPH.setString(1, sdtChuan);
                        try (ResultSet kqPH = lenhKiemTraPH.executeQuery()) {
                            if (kqPH.next()) {
                                maPhuHuynhDungChung = kqPH.getInt("ma_phu_huynh");
                            }
                        }
                    }

                    if (maPhuHuynhDungChung == 0) {
                        int maTaiKhoanMoi = 0;
                        String vaiTroTK = "";
                        String sqlTimTK = "SELECT ma_tai_khoan, vai_tro FROM tai_khoan WHERE ten_dang_nhap = ? FOR UPDATE";
                        try (PreparedStatement lenhTimTK = ketNoi.prepareStatement(sqlTimTK)) {
                            lenhTimTK.setString(1, sdtChuan);
                            try (ResultSet kqTK = lenhTimTK.executeQuery()) {
                                if (kqTK.next()) {
                                    maTaiKhoanMoi = kqTK.getInt("ma_tai_khoan");
                                    vaiTroTK = kqTK.getString("vai_tro");
                                }
                            }
                        }

                        if (maTaiKhoanMoi > 0 && !"PHU_HUYNH".equals(vaiTroTK)) {
                            ketNoi.rollback();
                            return "Số điện thoại này đang trùng với một tài khoản nội bộ khác.";
                        }

                        if (maTaiKhoanMoi == 0) {
                            if (bamMatKhauSan == null) {
                                matKhauUngVien = KetNoiCSDL.taoMatKhauNgauNhien(8);
                                bamMatKhauSan = KetNoiCSDL.maHoaMatKhau(matKhauUngVien);
                            }
                            matKhauTamTaoMoi = matKhauUngVien;
                            String sqlTaoTK = "INSERT INTO tai_khoan (ten_dang_nhap, mat_khau, ho_ten, vai_tro, so_lan_sai, trang_thai_khoa, phai_doi_mat_khau) "
                                            + "VALUES (?, ?, ?, 'PHU_HUYNH', 0, 0, 1)";
                            try (PreparedStatement lenhTaoTK = ketNoi.prepareStatement(sqlTaoTK, Statement.RETURN_GENERATED_KEYS)) {
                                lenhTaoTK.setString(1, sdtChuan);
                                lenhTaoTK.setString(2, bamMatKhauSan);
                                lenhTaoTK.setString(3, tenPhuHuynhChuan);
                                lenhTaoTK.executeUpdate();
                                try (ResultSet kqMaTK = lenhTaoTK.getGeneratedKeys()) {
                                    if (kqMaTK.next()) {
                                        maTaiKhoanMoi = kqMaTK.getInt(1);
                                    }
                                }
                            }
                        }

                        phMoi.maTaiKhoan = maTaiKhoanMoi;
                        String sqlTaoPH = "INSERT INTO phu_huynh (ho_ten_phu_huynh, so_dien_thoai, dia_chi, ma_tai_khoan) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement lenhTaoPH = ketNoi.prepareStatement(sqlTaoPH, Statement.RETURN_GENERATED_KEYS)) {
                            lenhTaoPH.setString(1, phMoi.hoTenPhuHuynh);
                            lenhTaoPH.setString(2, phMoi.soDienThoai);
                            lenhTaoPH.setString(3, phMoi.diaChi);
                            lenhTaoPH.setInt(4, phMoi.maTaiKhoan);
                            lenhTaoPH.executeUpdate();
                            try (ResultSet kqMaPH = lenhTaoPH.getGeneratedKeys()) {
                                if (kqMaPH.next()) {
                                    maPhuHuynhDungChung = kqMaPH.getInt(1);
                                }
                            }
                        }
                    }

                    String sqlThemBe = "INSERT INTO benh_nhi (ho_ten_be, ngay_sinh, gioi_tinh, tien_su_benh, ma_phu_huynh) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement lenhThemBe = ketNoi.prepareStatement(sqlThemBe)) {
                        lenhThemBe.setString(1, tenBeChuan);
                        lenhThemBe.setString(2, ngaySinhChuan);
                        lenhThemBe.setString(3, gioiTinhChuan);
                        lenhThemBe.setString(4, tienSuChuan);
                        lenhThemBe.setInt(5, maPhuHuynhDungChung);
                        lenhThemBe.executeUpdate();
                    }

                    ketNoi.commit();
                    if (matKhauTamOut != null && matKhauTamTaoMoi != null) {
                        matKhauTamOut.setLength(0);
                        matKhauTamOut.append(matKhauTamTaoMoi);
                    }
                    return "THANH_CONG";
                } catch (Exception loi) {
                    if (ketNoi != null) {
                        try {
                            ketNoi.rollback();
                        } catch (Exception loiRollback) {
                            System.err.println("Lỗi rollback khi thêm bệnh nhi: " + loiRollback.getMessage());
                        }
                    }
                    int maLoiSQL = (loi instanceof SQLException) ? ((SQLException) loi).getErrorCode() : 0;
                    if (lanThu < 4 && (maLoiSQL == 1213 || maLoiSQL == 1205 || maLoiSQL == 1062)) {
                        try { Thread.sleep(25L * lanThu); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        continue;
                    }
                    System.err.println("Lỗi thêm bệnh nhi: " + loi.getMessage());
                    return "Không thể lưu hồ sơ bệnh nhi do lỗi hệ thống.";
                } finally {
                    if (ketNoi != null) {
                        try {
                            ketNoi.setAutoCommit(true);
                        } catch (Exception loiAutoCommit) {
                            System.err.println("[BenhNhiDAO.themBenhNhi] Lỗi khôi phục autoCommit: " + loiAutoCommit.getMessage());
                        }
                        try {
                            ketNoi.close();
                        } catch (Exception loiDong) {
                            System.err.println("Lỗi đóng kết nối: " + loiDong.getMessage());
                        }
                    }
                }
            }
            return "Không thể lưu hồ sơ bệnh nhi do lỗi hệ thống.";
        }
    }

    public String suaBenhNhiCoThongBao(BenhNhi bn) {
        if (bn == null || bn.maBenhNhi <= 0 || bn.hoTenBe == null || bn.hoTenBe.trim().isEmpty()) {
            return "Vui lòng chọn hồ sơ bệnh nhi và nhập đầy đủ Họ và tên của bé!";
        }
        String loiNgaySinh = kiemTraNgaySinhNhiKhoa(bn.ngaySinh, false);
        if (loiNgaySinh != null) {
            return loiNgaySinh;
        }
        if (bn.gioiTinh == null || bn.gioiTinh.trim().isEmpty()) {
            return "Giới tính của bé chỉ nhận giá trị 'Nam' hoặc 'Nữ'!";
        }
        String gtSua = bn.gioiTinh.trim();
        String gioiTinhChuan;
        if ("Nam".equalsIgnoreCase(gtSua)) {
            gioiTinhChuan = "Nam";
        } else if ("Nữ".equalsIgnoreCase(gtSua)) {
            gioiTinhChuan = "Nữ";
        } else {
            return "Giới tính của bé chỉ nhận giá trị 'Nam' hoặc 'Nữ'!";
        }

        String ngaySinhChuan = bn.ngaySinh.trim();
        String tienSuChuan = (bn.tienSuBenh == null || bn.tienSuBenh.trim().isEmpty()) ? "Không có dị ứng thuốc" : bn.tienSuBenh.trim();
        String tenPHSua = (bn.hoTenPhuHuynh == null) ? "" : bn.hoTenPhuHuynh.trim();
        String diaChiSua = (bn.diaChi == null) ? null : bn.diaChi.trim();
        if (bn.hoTenBe.trim().length() > 100 || tenPHSua.length() > 100
            || (diaChiSua != null && diaChiSua.length() > 255) || tienSuChuan.length() > 2000) {
            return "Họ tên (<=100), địa chỉ (<=255) hoặc tiền sử bệnh (<=2000) vượt quá độ dài cho phép!";
        }
        PhuHuynh phSua = new PhuHuynh(0, tenPHSua, null, diaChiSua, 0);

        Connection ketNoi = null;
        try {
            ketNoi = KetNoiCSDL.layKetNoi();
            if (ketNoi == null) return "Lỗi kết nối Cơ sở dữ liệu!";
            ketNoi.setAutoCommit(false);

            String sqlSuaBe = "UPDATE benh_nhi SET ho_ten_be = ?, ngay_sinh = ?, gioi_tinh = ?, tien_su_benh = ? WHERE ma_benh_nhi = ?";
            int soDongSua = 0;
            try (PreparedStatement lenhSua = ketNoi.prepareStatement(sqlSuaBe)) {
                lenhSua.setString(1, bn.hoTenBe.trim());
                lenhSua.setString(2, ngaySinhChuan);
                lenhSua.setString(3, gioiTinhChuan);
                lenhSua.setString(4, tienSuChuan);
                lenhSua.setInt(5, bn.maBenhNhi);
                soDongSua = lenhSua.executeUpdate();
            }

            if (soDongSua > 0) {
                if (!phSua.hoTenPhuHuynh.isEmpty()) {
                    if (phSua.diaChi != null) {
                        String sqlSuaPH = "UPDATE phu_huynh SET ho_ten_phu_huynh = ?, dia_chi = ? WHERE ma_phu_huynh = (SELECT ma_phu_huynh FROM benh_nhi WHERE ma_benh_nhi = ?)";
                        try (PreparedStatement lenhSuaPH = ketNoi.prepareStatement(sqlSuaPH)) {
                            lenhSuaPH.setString(1, phSua.hoTenPhuHuynh);
                            lenhSuaPH.setString(2, phSua.diaChi);
                            lenhSuaPH.setInt(3, bn.maBenhNhi);
                            lenhSuaPH.executeUpdate();
                        }
                    } else {
                        String sqlSuaPH = "UPDATE phu_huynh SET ho_ten_phu_huynh = ? WHERE ma_phu_huynh = (SELECT ma_phu_huynh FROM benh_nhi WHERE ma_benh_nhi = ?)";
                        try (PreparedStatement lenhSuaPH = ketNoi.prepareStatement(sqlSuaPH)) {
                            lenhSuaPH.setString(1, phSua.hoTenPhuHuynh);
                            lenhSuaPH.setInt(2, bn.maBenhNhi);
                            lenhSuaPH.executeUpdate();
                        }
                    }

                    String sqlSuaTK = "UPDATE tai_khoan tk "
                                    + "INNER JOIN phu_huynh p ON tk.ma_tai_khoan = p.ma_tai_khoan "
                                    + "INNER JOIN benh_nhi b ON p.ma_phu_huynh = b.ma_phu_huynh "
                                    + "SET tk.ho_ten = ? "
                                    + "WHERE b.ma_benh_nhi = ? AND tk.vai_tro = 'PHU_HUYNH'";
                    try (PreparedStatement lenhSuaTK = ketNoi.prepareStatement(sqlSuaTK)) {
                        lenhSuaTK.setString(1, phSua.hoTenPhuHuynh);
                        lenhSuaTK.setInt(2, bn.maBenhNhi);
                        lenhSuaTK.executeUpdate();
                    }
                } else if (phSua.diaChi != null) {
                    String sqlSuaDC = "UPDATE phu_huynh SET dia_chi = ? WHERE ma_phu_huynh = (SELECT ma_phu_huynh FROM benh_nhi WHERE ma_benh_nhi = ?)";
                    try (PreparedStatement lenhSuaDC = ketNoi.prepareStatement(sqlSuaDC)) {
                        lenhSuaDC.setString(1, phSua.diaChi);
                        lenhSuaDC.setInt(2, bn.maBenhNhi);
                        lenhSuaDC.executeUpdate();
                    }
                }
            }

            ketNoi.commit();
            return soDongSua > 0 ? "THANH_CONG" : "Không tìm thấy hồ sơ bệnh nhi cần cập nhật!";
        } catch (Exception loi) {
            if (ketNoi != null) {
                try {
                    ketNoi.rollback();
                } catch (Exception loiRollback) {
                    System.err.println("[BenhNhiDAO.suaBenhNhiCoThongBao] Lỗi rollback giao dịch: " + loiRollback.getMessage());
                }
            }
            System.err.println("Lỗi sửa bệnh nhi: " + loi.getMessage());
            return "Không thể cập nhật hồ sơ bệnh nhi do lỗi hệ thống.";
        } finally {
            if (ketNoi != null) {
                try {
                    ketNoi.setAutoCommit(true);
                } catch (Exception loiAutoCommit) {
                    System.err.println("[BenhNhiDAO.suaBenhNhiCoThongBao] Lỗi khôi phục autoCommit: " + loiAutoCommit.getMessage());
                }
                try {
                    ketNoi.close();
                } catch (Exception loiDong) {
                    System.err.println("Lỗi đóng kết nối: " + loiDong.getMessage());
                }
            }
        }
    }

    public boolean xoaBenhNhi(int maBenhNhi) {
        if (maBenhNhi <= 0) return false;
        String sqlXoa = "DELETE FROM benh_nhi WHERE ma_benh_nhi = ?";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return false;
            try (PreparedStatement lenhXoa = ketNoi.prepareStatement(sqlXoa)) {
                lenhXoa.setInt(1, maBenhNhi);
                return lenhXoa.executeUpdate() > 0;
            }
        } catch (Exception loi) {
            System.err.println("Không thể xóa bệnh nhi đã có hồ sơ khám: " + loi.getMessage());
            return false;
        }
    }
}
