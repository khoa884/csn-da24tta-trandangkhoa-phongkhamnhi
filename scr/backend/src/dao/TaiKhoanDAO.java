package dao;

import config.KetNoiCSDL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import model.TaiKhoan;

/**
 * Xử lý xác thực tài khoản, khóa tạm có thời hạn, cân bằng thời gian PBKDF2 và đổi/mở khóa mật khẩu.
 */
public class TaiKhoanDAO {

    private static final int SO_LAN_SAI_TOI_DA_KHOA = 5;
    private static final int SO_PHUT_KHOA_TAM = 15;
    // Hash giả dùng để cân bằng thời gian xử lý khi tên đăng nhập không tồn tại, chống dò tài khoản
    private static final String HASH_GIA_CHONG_TIMING = KetNoiCSDL.maHoaMatKhau(UUID.randomUUID().toString());

    public TaiKhoanDAO() {
    }

    /**
     * Kiểm tra thông tin đăng nhập; tự động nâng cấp hash SHA-256 cũ lên PBKDF2 và tạm khóa 15 phút sau 5 lần sai (trừ QUAN_TRI).
     */
    public String kiemTraDangNhap(String tenDangNhap, String matKhauNhap, TaiKhoan taiKhoanKetQua) {
        if (tenDangNhap == null || tenDangNhap.trim().isEmpty() || matKhauNhap == null || matKhauNhap.isEmpty()) {
            return "Sai tên tài khoản hoặc mật khẩu";
        }
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) {
                return "Lỗi kết nối Cơ sở dữ liệu";
            }

            String sqlTim = "SELECT ma_tai_khoan, mat_khau, ho_ten, vai_tro, trang_thai_khoa, phai_doi_mat_khau, "
                          + "DATE_FORMAT(khoa_den_luc, '%H:%i %d/%m/%Y') AS khoa_den_luc_str, "
                          + "TIMESTAMPDIFF(SECOND, NOW(), khoa_den_luc) AS giay_con_lai "
                          + "FROM tai_khoan WHERE ten_dang_nhap = ?";
            try (PreparedStatement lenhTim = ketNoi.prepareStatement(sqlTim)) {
                lenhTim.setString(1, tenDangNhap.trim());
                try (ResultSet bangKetQua = lenhTim.executeQuery()) {
                    if (!bangKetQua.next()) {
                        KetNoiCSDL.kiemTraMatKhau(matKhauNhap, HASH_GIA_CHONG_TIMING);
                        return "Sai tên tài khoản hoặc mật khẩu";
                    }

                    int maTaiKhoan = bangKetQua.getInt("ma_tai_khoan");
                    String matKhauTrongCSDL = bangKetQua.getString("mat_khau");
                    String hoTen = bangKetQua.getString("ho_ten");
                    String vaiTro = bangKetQua.getString("vai_tro");
                    int trangThaiKhoa = bangKetQua.getInt("trang_thai_khoa");
                    int phaiDoiMatKhau = bangKetQua.getInt("phai_doi_mat_khau");
                    Object giayObj = bangKetQua.getObject("giay_con_lai");
                    long giayConLai = giayObj == null ? -1L : ((Number) giayObj).longValue();

                    if (trangThaiKhoa == 1) {
                        if (giayObj == null) {
                            KetNoiCSDL.kiemTraMatKhau(matKhauNhap, HASH_GIA_CHONG_TIMING);
                            return "Tài khoản đã bị khóa. Vui lòng liên hệ Quản trị viên để mở khóa";
                        }
                        if (giayConLai > 0) {
                            KetNoiCSDL.kiemTraMatKhau(matKhauNhap, HASH_GIA_CHONG_TIMING);
                            long phutConLai = Math.max(1L, (giayConLai + 59L) / 60L);
                            return "Tài khoản đang tạm khóa do nhập sai nhiều lần. Vui lòng thử lại sau "
                                 + phutConLai + " phút hoặc liên hệ Quản trị viên để mở khóa";
                        }
                        String sqlHetHanKhoa = "UPDATE tai_khoan SET so_lan_sai = 0, trang_thai_khoa = 0, khoa_den_luc = NULL "
                                             + "WHERE ma_tai_khoan = ? AND trang_thai_khoa = 1 AND khoa_den_luc IS NOT NULL AND khoa_den_luc <= NOW()";
                        try (PreparedStatement lenhHetHan = ketNoi.prepareStatement(sqlHetHanKhoa)) {
                            lenhHetHan.setInt(1, maTaiKhoan);
                            lenhHetHan.executeUpdate();
                        }
                    }

                    if (KetNoiCSDL.kiemTraMatKhau(matKhauNhap, matKhauTrongCSDL)) {
                        if (KetNoiCSDL.laHashCuSHA256(matKhauTrongCSDL)) {
                            String hashMoi = KetNoiCSDL.maHoaMatKhau(matKhauNhap);
                            String sqlNangCap = "UPDATE tai_khoan SET so_lan_sai = 0, trang_thai_khoa = 0, khoa_den_luc = NULL, mat_khau = ? WHERE ma_tai_khoan = ?";
                            try (PreparedStatement lenhNangCap = ketNoi.prepareStatement(sqlNangCap)) {
                                lenhNangCap.setString(1, hashMoi);
                                lenhNangCap.setInt(2, maTaiKhoan);
                                lenhNangCap.executeUpdate();
                            }
                        } else {
                            String sqlReset = "UPDATE tai_khoan SET so_lan_sai = 0, trang_thai_khoa = 0, khoa_den_luc = NULL WHERE ma_tai_khoan = ?";
                            try (PreparedStatement lenhReset = ketNoi.prepareStatement(sqlReset)) {
                                lenhReset.setInt(1, maTaiKhoan);
                                lenhReset.executeUpdate();
                            }
                        }

                        taiKhoanKetQua.maTaiKhoan = maTaiKhoan;
                        taiKhoanKetQua.tenDangNhap = tenDangNhap.trim();
                        taiKhoanKetQua.hoTen = hoTen;
                        taiKhoanKetQua.vaiTro = vaiTro;
                        taiKhoanKetQua.soLanSai = 0;
                        taiKhoanKetQua.trangThaiKhoa = 0;
                        taiKhoanKetQua.khoaDenLuc = null;
                        taiKhoanKetQua.phaiDoiMatKhau = phaiDoiMatKhau;
                        return "THANH_CONG";
                    } else {
                        if (KetNoiCSDL.laHashCuSHA256(matKhauTrongCSDL)) {
                            KetNoiCSDL.kiemTraMatKhau(matKhauNhap, HASH_GIA_CHONG_TIMING);
                        }
                        // Cộng dồn nguyên tử trong SQL để tránh race condition khi nhiều request sai đến đồng thời
                        String sqlTangSai = "UPDATE tai_khoan SET so_lan_sai = so_lan_sai + 1 WHERE ma_tai_khoan = ?";
                        try (PreparedStatement lenhTangSai = ketNoi.prepareStatement(sqlTangSai)) {
                            lenhTangSai.setInt(1, maTaiKhoan);
                            lenhTangSai.executeUpdate();
                        }

                        String sqlKhoaTam = "UPDATE tai_khoan "
                                          + "SET trang_thai_khoa = 1, "
                                          + "khoa_den_luc = DATE_ADD(NOW(), INTERVAL ? MINUTE) "
                                          + "WHERE ma_tai_khoan = ? AND vai_tro <> 'QUAN_TRI' "
                                          + "AND so_lan_sai >= ? AND trang_thai_khoa = 0";
                        try (PreparedStatement lenhKhoaTam = ketNoi.prepareStatement(sqlKhoaTam)) {
                            lenhKhoaTam.setInt(1, SO_PHUT_KHOA_TAM);
                            lenhKhoaTam.setInt(2, maTaiKhoan);
                            lenhKhoaTam.setInt(3, SO_LAN_SAI_TOI_DA_KHOA);
                            lenhKhoaTam.executeUpdate();
                        }

                        String sqlKiemTraKhoa = "SELECT trang_thai_khoa FROM tai_khoan WHERE ma_tai_khoan = ?";
                        try (PreparedStatement lenhKT = ketNoi.prepareStatement(sqlKiemTraKhoa)) {
                            lenhKT.setInt(1, maTaiKhoan);
                            try (ResultSet kqKT = lenhKT.executeQuery()) {
                                if (kqKT.next() && kqKT.getInt("trang_thai_khoa") == 1) {
                                    return "Tài khoản đang tạm khóa " + SO_PHUT_KHOA_TAM
                                         + " phút do nhập sai quá " + SO_LAN_SAI_TOI_DA_KHOA
                                         + " lần. Vui lòng thử lại sau hoặc liên hệ Quản trị viên để mở khóa";
                                }
                            }
                        }
                        return "Sai tên tài khoản hoặc mật khẩu";
                    }
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi khi kiểm tra đăng nhập: " + loi.getMessage());
            return "Lỗi kết nối Cơ sở dữ liệu";
        }
    }

    public String doiMatKhau(int maTaiKhoan, String matKhauCu, String matKhauMoi) {
        if (maTaiKhoan <= 0 || matKhauCu == null || matKhauCu.isEmpty()) {
            return "Vui lòng nhập đầy đủ mật khẩu hiện tại.";
        }
        if (matKhauMoi == null || matKhauMoi.isEmpty()) {
            return "Mật khẩu mới phải có từ 6 đến 100 ký tự.";
        }
        if (!matKhauMoi.equals(matKhauMoi.trim())) {
            return "Mật khẩu mới không được chứa khoảng trắng ở đầu hoặc cuối.";
        }
        if (matKhauMoi.length() < 6 || matKhauMoi.length() > 100) {
            return "Mật khẩu mới phải có từ 6 đến 100 ký tự.";
        }
        if (matKhauCu.equals(matKhauMoi)) {
            return "Mật khẩu mới phải khác mật khẩu hiện tại.";
        }
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) {
                return "Lỗi kết nối Cơ sở dữ liệu.";
            }
            String sqlLay = "SELECT mat_khau FROM tai_khoan WHERE ma_tai_khoan = ?";
            String matKhauHienTai = null;
            try (PreparedStatement lenhLay = ketNoi.prepareStatement(sqlLay)) {
                lenhLay.setInt(1, maTaiKhoan);
                try (ResultSet kq = lenhLay.executeQuery()) {
                    if (kq.next()) {
                        matKhauHienTai = kq.getString("mat_khau");
                    }
                }
            }
            if (matKhauHienTai == null || !KetNoiCSDL.kiemTraMatKhau(matKhauCu, matKhauHienTai)) {
                return "Mật khẩu hiện tại không chính xác.";
            }

            String sqlCapNhat = "UPDATE tai_khoan SET mat_khau = ?, phai_doi_mat_khau = 0 WHERE ma_tai_khoan = ?";
            try (PreparedStatement lenhCapNhat = ketNoi.prepareStatement(sqlCapNhat)) {
                lenhCapNhat.setString(1, KetNoiCSDL.maHoaMatKhau(matKhauMoi));
                lenhCapNhat.setInt(2, maTaiKhoan);
                lenhCapNhat.executeUpdate();
            }
            return "THANH_CONG";
        } catch (Exception loi) {
            System.err.println("Lỗi đổi mật khẩu: " + loi.getMessage());
            return "Không thể đổi mật khẩu do lỗi hệ thống.";
        }
    }

    public boolean moKhoaTaiKhoan(int maTaiKhoanCanMo) {
        if (maTaiKhoanCanMo <= 0) {
            return false;
        }
        String sqlMoKhoa = "UPDATE tai_khoan SET trang_thai_khoa = 0, so_lan_sai = 0, khoa_den_luc = NULL WHERE ma_tai_khoan = ?";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return false;
            try (PreparedStatement lenhMoKhoa = ketNoi.prepareStatement(sqlMoKhoa)) {
                lenhMoKhoa.setInt(1, maTaiKhoanCanMo);
                int soDongCapNhat = lenhMoKhoa.executeUpdate();
                return soDongCapNhat > 0;
            }
        } catch (Exception loi) {
            System.err.println("Lỗi khi mở khóa tài khoản: " + loi.getMessage());
            return false;
        }
    }

    public String layTenDangNhapTheoMa(int maTaiKhoan) {
        if (maTaiKhoan <= 0) {
            return null;
        }
        String sql = "SELECT ten_dang_nhap FROM tai_khoan WHERE ma_tai_khoan = ?";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return null;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                lenh.setInt(1, maTaiKhoan);
                try (ResultSet kq = lenh.executeQuery()) {
                    if (kq.next()) {
                        return kq.getString("ten_dang_nhap");
                    }
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi lấy tên đăng nhập theo mã: " + loi.getMessage());
        }
        return null;
    }

    public List<TaiKhoan> layDanhSachTaiKhoan() {
        List<TaiKhoan> danhSach = new ArrayList<>();
        String sqlLay = "SELECT ma_tai_khoan, ten_dang_nhap, ho_ten, vai_tro, "
                      + "CASE WHEN trang_thai_khoa = 1 AND khoa_den_luc IS NOT NULL AND khoa_den_luc <= NOW() THEN 0 ELSE so_lan_sai END AS so_lan_sai, "
                      + "CASE WHEN trang_thai_khoa = 1 AND khoa_den_luc IS NOT NULL AND khoa_den_luc <= NOW() THEN 0 ELSE trang_thai_khoa END AS trang_thai_khoa, "
                      + "CASE WHEN trang_thai_khoa = 1 AND khoa_den_luc IS NOT NULL AND khoa_den_luc > NOW() THEN DATE_FORMAT(khoa_den_luc, '%H:%i %d/%m/%Y') ELSE '' END AS khoa_den_luc_str, "
                      + "phai_doi_mat_khau "
                      + "FROM tai_khoan ORDER BY ma_tai_khoan ASC";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return danhSach;
            try (PreparedStatement lenhLay = ketNoi.prepareStatement(sqlLay);
                 ResultSet bangKetQua = lenhLay.executeQuery()) {
                while (bangKetQua.next()) {
                    TaiKhoan tk = new TaiKhoan();
                    tk.maTaiKhoan = bangKetQua.getInt("ma_tai_khoan");
                    tk.tenDangNhap = bangKetQua.getString("ten_dang_nhap");
                    tk.hoTen = bangKetQua.getString("ho_ten");
                    tk.vaiTro = bangKetQua.getString("vai_tro");
                    tk.soLanSai = bangKetQua.getInt("so_lan_sai");
                    tk.trangThaiKhoa = bangKetQua.getInt("trang_thai_khoa");
                    tk.khoaDenLuc = bangKetQua.getString("khoa_den_luc_str");
                    tk.phaiDoiMatKhau = bangKetQua.getInt("phai_doi_mat_khau");
                    danhSach.add(tk);
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi lấy danh sách tài khoản: " + loi.getMessage());
        }
        return danhSach;
    }

    public static void main(String[] args) {
        TaiKhoanDAO dao = new TaiKhoanDAO();
        TaiKhoan tk = new TaiKhoan();

        System.out.println("1. Kiểm tra tạm khóa tài khoản letan1 sau 5 lần sai:");
        for (int i = 1; i <= 5; i++) {
            System.out.println("   - Lần " + i + ": " + dao.kiemTraDangNhap("letan1", "sai_mk_" + i, tk));
        }

        System.out.println("2. Thử đăng nhập đúng khi đang tạm khóa:");
        System.out.println("   - Kết quả: " + dao.kiemTraDangNhap("letan1", "123456", tk));

        System.out.println("3. Admin mở khóa tài khoản #3:");
        boolean daMo = dao.moKhoaTaiKhoan(3);
        System.out.println("   - Đã mở khóa: " + daMo);

        System.out.println("4. Đăng nhập lại sau khi mở khóa:");
        String ketQua = dao.kiemTraDangNhap("letan1", "123456", tk);
        System.out.println("   - Kết quả: " + ketQua + " | " + tk.hoTen + " (" + tk.vaiTro + ")");
    }
}
