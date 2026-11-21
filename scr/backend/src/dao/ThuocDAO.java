package dao;

import config.KetNoiCSDL;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import model.Thuoc;

/**
 * Thao tác CRUD danh mục thuốc nhi khoa và tồn kho.
 */
public class ThuocDAO {

    private static final BigDecimal DON_GIA_TOI_DA = new BigDecimal("9999999999.99");

    public ThuocDAO() {
    }

    public List<Thuoc> layDanhSachThuoc() {
        List<Thuoc> danhSach = new ArrayList<>();
        String sql = "SELECT * FROM thuoc ORDER BY ma_thuoc ASC";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return danhSach;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql);
                 ResultSet kq = lenh.executeQuery()) {
                while (kq.next()) {
                    Thuoc t = new Thuoc();
                    t.maThuoc = kq.getInt("ma_thuoc");
                    t.tenThuoc = kq.getString("ten_thuoc");
                    t.donViTinh = kq.getString("don_vi_tinh");
                    t.donGia = kq.getBigDecimal("don_gia");
                    if (t.donGia == null) t.donGia = BigDecimal.ZERO;
                    t.cachDungMacDinh = kq.getString("cach_dung_mac_dinh");
                    t.soLuongTon = kq.getInt("so_luong_ton");
                    danhSach.add(t);
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi lấy danh sách thuốc: " + loi.getMessage());
        }
        return danhSach;
    }

    public String themThuoc(Thuoc t) {
        if (t == null || t.tenThuoc == null || t.tenThuoc.trim().isEmpty()) {
            return "Vui lòng nhập Tên thuốc nhi khoa!";
        }
        if (t.donGia == null || t.donGia.compareTo(BigDecimal.ZERO) <= 0 || t.donGia.compareTo(DON_GIA_TOI_DA) > 0) {
            return "Đơn giá thuốc phải lớn hơn 0 và không vượt quá 9.999.999.999,99 VNĐ!";
        }
        if (t.soLuongTon < 0) {
            return "Số lượng tồn kho phải là số nguyên từ 0 trở lên!";
        }

        BigDecimal donGiaChuan = t.donGia.setScale(2, RoundingMode.HALF_UP);
        if (donGiaChuan.compareTo(BigDecimal.ZERO) <= 0) {
            return "Đơn giá thuốc phải lớn hơn 0 và không vượt quá 9.999.999.999,99 VNĐ!";
        }
        String tenChuan = t.tenThuoc.trim();
        String donViChuan = (t.donViTinh == null || t.donViTinh.trim().isEmpty()) ? "Gói" : t.donViTinh.trim();
        String cachDungChuan = (t.cachDungMacDinh == null) ? "" : t.cachDungMacDinh.trim();
        if (tenChuan.length() > 150 || donViChuan.length() > 30 || cachDungChuan.length() > 255) {
            return "Tên thuốc (<=150), đơn vị tính (<=30) hoặc cách dùng (<=255) vượt quá độ dài cho phép!";
        }
        String sql = "INSERT INTO thuoc (ten_thuoc, don_vi_tinh, don_gia, cach_dung_mac_dinh, so_luong_ton) VALUES (?, ?, ?, ?, ?)";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return "Lỗi kết nối Cơ sở dữ liệu!";
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                lenh.setString(1, tenChuan);
                lenh.setString(2, donViChuan);
                lenh.setBigDecimal(3, donGiaChuan);
                lenh.setString(4, cachDungChuan);
                lenh.setInt(5, t.soLuongTon);
                lenh.executeUpdate();
            }
            return "THANH_CONG";
        } catch (Exception loi) {
            System.err.println("Lỗi thêm thuốc mới: " + loi.getMessage());
            return "Không thể thêm thuốc do lỗi hệ thống.";
        }
    }

    // Cập nhật don_gia không ảnh hưởng các toa đã kê nhờ cột don_gia_luc_ke; khóa dòng để tránh Lost Update tồn kho
    public boolean suaThuoc(Thuoc t) {
        if (t == null || t.maThuoc <= 0 || t.tenThuoc == null || t.tenThuoc.trim().isEmpty()) {
            return false;
        }
        if (t.donViTinh == null || t.donViTinh.trim().isEmpty()
            || t.donGia == null || t.donGia.compareTo(BigDecimal.ZERO) <= 0
            || t.donGia.compareTo(DON_GIA_TOI_DA) > 0 || t.soLuongTon < 0) {
            return false;
        }
        String cachDungSua = t.cachDungMacDinh == null ? "" : t.cachDungMacDinh.trim();
        if (t.tenThuoc.trim().length() > 150 || t.donViTinh.trim().length() > 30 || cachDungSua.length() > 255) {
            return false;
        }

        BigDecimal donGiaChuan = t.donGia.setScale(2, RoundingMode.HALF_UP);
        if (donGiaChuan.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        Connection ketNoi = null;
        try {
            ketNoi = KetNoiCSDL.layKetNoi();
            if (ketNoi == null) return false;
            ketNoi.setAutoCommit(false);

            String sqlKhoa = "SELECT so_luong_ton FROM thuoc WHERE ma_thuoc = ? FOR UPDATE";
            try (PreparedStatement lenhKhoa = ketNoi.prepareStatement(sqlKhoa)) {
                lenhKhoa.setInt(1, t.maThuoc);
                try (ResultSet kqKhoa = lenhKhoa.executeQuery()) {
                    if (!kqKhoa.next()) {
                        ketNoi.rollback();
                        return false;
                    }
                }
            }

            String sql = "UPDATE thuoc SET ten_thuoc = ?, don_vi_tinh = ?, don_gia = ?, cach_dung_mac_dinh = ?, so_luong_ton = ? WHERE ma_thuoc = ?";
            boolean ok;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                lenh.setString(1, t.tenThuoc.trim());
                lenh.setString(2, t.donViTinh.trim());
                lenh.setBigDecimal(3, donGiaChuan);
                lenh.setString(4, cachDungSua);
                lenh.setInt(5, t.soLuongTon);
                lenh.setInt(6, t.maThuoc);
                ok = lenh.executeUpdate() > 0;
            }
            ketNoi.commit();
            return ok;
        } catch (Exception loi) {
            if (ketNoi != null) {
                try {
                    ketNoi.rollback();
                } catch (Exception loiRollback) {
                    System.err.println("[ThuocDAO.suaThuoc] Lỗi rollback: " + loiRollback.getMessage());
                }
            }
            System.err.println("Lỗi sửa thông tin thuốc: " + loi.getMessage());
            return false;
        } finally {
            if (ketNoi != null) {
                try {
                    ketNoi.setAutoCommit(true);
                } catch (Exception loiAutoCommit) {
                    System.err.println("[ThuocDAO.suaThuoc] Lỗi khôi phục autoCommit: " + loiAutoCommit.getMessage());
                }
                try {
                    ketNoi.close();
                } catch (Exception loiDong) {
                    System.err.println("Lỗi đóng kết nối: " + loiDong.getMessage());
                }
            }
        }
    }

    public boolean xoaThuoc(int maThuoc) {
        if (maThuoc <= 0) return false;
        String sql = "DELETE FROM thuoc WHERE ma_thuoc = ?";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return false;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                lenh.setInt(1, maThuoc);
                return lenh.executeUpdate() > 0;
            }
        } catch (Exception loi) {
            System.err.println("Không thể xóa thuốc đã kê trong toa: " + loi.getMessage());
            return false;
        }
    }
}
