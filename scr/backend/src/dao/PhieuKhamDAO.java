package dao;

import config.KetNoiCSDL;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import model.PhieuKham;
import model.ToaThuoc;

/**
 * Xử lý phiếu khám, kê toa thuốc (Transaction khóa tồn kho), thu tiền và thống kê doanh thu.
 */
public class PhieuKhamDAO {

    private static final BigDecimal TIEN_KHAM_MAC_DINH = new BigDecimal("100000.00");
    private static final BigDecimal TIEN_TOI_DA = new BigDecimal("9999999999.99");
    private static final BigDecimal CAN_NANG_TOI_DA = new BigDecimal("200.00");

    public PhieuKhamDAO() {
    }

    private static BigDecimal chuanTien(BigDecimal giaTri) {
        return giaTri == null ? BigDecimal.ZERO : giaTri;
    }

    public List<PhieuKham> layDanhSachPhieuKham(int maBenhNhiLoc) {
        String sql = "SELECT pk.*, b.ho_ten_be, t.ho_ten AS ho_ten_bac_si "
                   + "FROM phieu_kham pk "
                   + "INNER JOIN benh_nhi b ON pk.ma_benh_nhi = b.ma_benh_nhi "
                   + "INNER JOIN tai_khoan t ON pk.ma_bac_si = t.ma_tai_khoan ";
        if (maBenhNhiLoc > 0) {
            sql = sql + "WHERE pk.ma_benh_nhi = ? ";
        }
        sql = sql + "ORDER BY pk.ma_phieu_kham DESC";
        return truyVanDanhSachPhieuKham(sql, maBenhNhiLoc > 0 ? maBenhNhiLoc : null, null);
    }

    public List<PhieuKham> layDanhSachPhieuKhamTheoSDT(String soDienThoai) {
        if (soDienThoai == null || soDienThoai.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String sql = "SELECT pk.*, b.ho_ten_be, t.ho_ten AS ho_ten_bac_si "
                   + "FROM phieu_kham pk "
                   + "INNER JOIN benh_nhi b ON pk.ma_benh_nhi = b.ma_benh_nhi "
                   + "INNER JOIN phu_huynh p ON b.ma_phu_huynh = p.ma_phu_huynh "
                   + "INNER JOIN tai_khoan t ON pk.ma_bac_si = t.ma_tai_khoan "
                   + "WHERE p.so_dien_thoai = ? "
                   + "ORDER BY pk.ma_phieu_kham DESC";
        return truyVanDanhSachPhieuKham(sql, null, soDienThoai.trim());
    }

    private List<PhieuKham> truyVanDanhSachPhieuKham(String sql, Integer maBenhNhi, String soDienThoai) {
        List<PhieuKham> danhSach = new ArrayList<>();
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return danhSach;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                if (maBenhNhi != null) {
                    lenh.setInt(1, maBenhNhi);
                } else if (soDienThoai != null) {
                    lenh.setString(1, soDienThoai);
                }
                try (ResultSet kq = lenh.executeQuery()) {
                    while (kq.next()) {
                        PhieuKham pk = new PhieuKham();
                        pk.maPhieuKham = kq.getInt("ma_phieu_kham");
                        pk.maBenhNhi = kq.getInt("ma_benh_nhi");
                        pk.maBacSi = kq.getInt("ma_bac_si");
                        pk.ngayKham = kq.getString("ngay_kham");
                        pk.canNangKg = chuanTien(kq.getBigDecimal("can_nang_kg"));
                        pk.trieuChung = kq.getString("trieu_chung");
                        pk.chanDoan = kq.getString("chan_doan");
                        pk.loiDanBacSi = kq.getString("loi_dan_bac_si");
                        pk.tienKham = chuanTien(kq.getBigDecimal("tien_kham"));
                        pk.tienThuoc = chuanTien(kq.getBigDecimal("tien_thuoc"));
                        pk.tongVienPhi = chuanTien(kq.getBigDecimal("tong_vien_phi"));
                        pk.trangThaiPhieu = kq.getString("trang_thai_phieu");
                        pk.hoTenBe = kq.getString("ho_ten_be");
                        pk.hoTenBacSi = kq.getString("ho_ten_bac_si");
                        danhSach.add(pk);
                    }
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi lấy danh sách phiếu khám: " + loi.getMessage());
        }
        return danhSach;
    }

    /**
     * Tạo phiếu khám mới. Trả về: >0 mã phiếu mới, -1 sai cân nặng, -2 thiếu triệu chứng/chẩn đoán, -3 sai mã bé/bác sĩ, -4 sai tiền khám, -5 vượt độ dài chuỗi.
     */
    public int taoPhieuKham(PhieuKham pk) {
        if (pk == null || pk.canNangKg == null) {
            return -1;
        }
        BigDecimal canNangChuan = pk.canNangKg.setScale(2, RoundingMode.HALF_UP);
        if (canNangChuan.compareTo(BigDecimal.ZERO) <= 0 || canNangChuan.compareTo(CAN_NANG_TOI_DA) > 0) {
            return -1;
        }
        if (pk.trieuChung == null || pk.trieuChung.trim().isEmpty() || pk.chanDoan == null || pk.chanDoan.trim().isEmpty()) {
            return -2;
        }
        String trieuChungChuan = pk.trieuChung.trim();
        String chanDoanChuan = pk.chanDoan.trim();
        String loiDanChuan = pk.loiDanBacSi == null ? "" : pk.loiDanBacSi.trim();
        if (trieuChungChuan.length() > 255 || chanDoanChuan.length() > 255 || loiDanChuan.length() > 2000) {
            return -5;
        }
        if (pk.maBenhNhi <= 0 || pk.maBacSi <= 0) {
            return -3;
        }

        BigDecimal tienKhamChuan = TIEN_KHAM_MAC_DINH;
        if (pk.tienKham != null) {
            if (pk.tienKham.compareTo(BigDecimal.ZERO) < 0 || pk.tienKham.compareTo(TIEN_TOI_DA) > 0) {
                return -4;
            }
            tienKhamChuan = pk.tienKham.setScale(2, RoundingMode.HALF_UP);
        }

        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return 0;

            String sqlKiemTra = "SELECT (SELECT COUNT(*) FROM benh_nhi WHERE ma_benh_nhi = ?) AS co_be, "
                              + "(SELECT COUNT(*) FROM tai_khoan WHERE ma_tai_khoan = ? AND vai_tro = 'BAC_SI') AS co_bs";
            try (PreparedStatement lenhKT = ketNoi.prepareStatement(sqlKiemTra)) {
                lenhKT.setInt(1, pk.maBenhNhi);
                lenhKT.setInt(2, pk.maBacSi);
                try (ResultSet kqKT = lenhKT.executeQuery()) {
                    if (!kqKT.next() || kqKT.getInt("co_be") == 0 || kqKT.getInt("co_bs") == 0) {
                        return -3;
                    }
                }
            }

            String sql = "INSERT INTO phieu_kham (ma_benh_nhi, ma_bac_si, can_nang_kg, trieu_chung, chan_doan, loi_dan_bac_si, tien_kham, tien_thuoc, tong_vien_phi, trang_thai_phieu) "
                       + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 'Đã khám (Chờ thu tiền)')";
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                lenh.setInt(1, pk.maBenhNhi);
                lenh.setInt(2, pk.maBacSi);
                lenh.setBigDecimal(3, canNangChuan);
                lenh.setString(4, trieuChungChuan);
                lenh.setString(5, chanDoanChuan);
                lenh.setString(6, loiDanChuan);
                lenh.setBigDecimal(7, tienKhamChuan);
                lenh.setBigDecimal(8, tienKhamChuan);
                lenh.executeUpdate();

                try (ResultSet kqMa = lenh.getGeneratedKeys()) {
                    if (kqMa.next()) {
                        return kqMa.getInt(1);
                    }
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi tạo phiếu khám: " + loi.getMessage());
        }
        return 0;
    }

    /**
     * Kê thuốc vào toa trong Transaction: khóa dòng phiếu & tồn kho (FOR UPDATE), lưu don_gia_luc_ke và cập nhật tổng tiền.
     */
    public String keThuocVaoToa(ToaThuoc tt) {
        if (tt == null || tt.maPhieuKham <= 0 || tt.maThuoc <= 0) {
            return "Vui lòng chọn Phiếu khám và Thuốc hợp lệ!";
        }
        if (tt.soLuong <= 0) {
            return "Số lượng thuốc kê phải lớn hơn 0!";
        }
        if (tt.lieuLuongDung == null || tt.lieuLuongDung.trim().isEmpty()) {
            return "Vui lòng nhập Liều lượng và cách uống chi tiết theo cân nặng của bé!";
        }
        if (tt.lieuLuongDung.trim().length() > 255) {
            return "Liều lượng và cách uống không được vượt quá 255 ký tự!";
        }

        Connection ketNoi = null;
        try {
            ketNoi = KetNoiCSDL.layKetNoi();
            if (ketNoi == null) return "Lỗi kết nối Cơ sở dữ liệu!";
            ketNoi.setAutoCommit(false);

            BigDecimal tienKhamHienTai = BigDecimal.ZERO;
            BigDecimal tienThuocHienTai = BigDecimal.ZERO;
            String sqlPhieu = "SELECT trang_thai_phieu, tien_kham, tien_thuoc FROM phieu_kham WHERE ma_phieu_kham = ? FOR UPDATE";
            try (PreparedStatement lenhPhieu = ketNoi.prepareStatement(sqlPhieu)) {
                lenhPhieu.setInt(1, tt.maPhieuKham);
                try (ResultSet kqPhieu = lenhPhieu.executeQuery()) {
                    if (!kqPhieu.next()) {
                        ketNoi.rollback();
                        return "Không tìm thấy phiếu khám mã #" + tt.maPhieuKham + "!";
                    }
                    String trangThaiHienTai = kqPhieu.getString("trang_thai_phieu");
                    if ("Đã thanh toán".equals(trangThaiHienTai)) {
                        ketNoi.rollback();
                        return "Phiếu khám #" + tt.maPhieuKham + " đã thanh toán, không thể kê thêm thuốc!";
                    }
                    tienKhamHienTai = chuanTien(kqPhieu.getBigDecimal("tien_kham"));
                    tienThuocHienTai = chuanTien(kqPhieu.getBigDecimal("tien_thuoc"));
                }
            }

            String tenThuoc = "";
            BigDecimal donGiaHienTai = BigDecimal.ZERO;
            int soLuongTon = 0;
            String sqlKho = "SELECT ten_thuoc, don_gia, so_luong_ton FROM thuoc WHERE ma_thuoc = ? FOR UPDATE";
            try (PreparedStatement lenhKho = ketNoi.prepareStatement(sqlKho)) {
                lenhKho.setInt(1, tt.maThuoc);
                try (ResultSet kqKho = lenhKho.executeQuery()) {
                    if (!kqKho.next()) {
                        ketNoi.rollback();
                        return "Không tìm thấy loại thuốc này trong danh mục!";
                    }
                    tenThuoc = kqKho.getString("ten_thuoc");
                    donGiaHienTai = chuanTien(kqKho.getBigDecimal("don_gia"));
                    soLuongTon = kqKho.getInt("so_luong_ton");
                }
            }

            if (tt.soLuong > soLuongTon) {
                ketNoi.rollback();
                return "Thuốc [" + tenThuoc + "] trong kho chỉ còn " + soLuongTon + " đơn vị, không đủ để kê " + tt.soLuong + " đơn vị!";
            }

            BigDecimal thanhTienDongNay = donGiaHienTai.multiply(BigDecimal.valueOf(tt.soLuong)).setScale(2, RoundingMode.HALF_UP);
            if (thanhTienDongNay.compareTo(TIEN_TOI_DA) > 0
                || tienKhamHienTai.add(tienThuocHienTai).add(thanhTienDongNay).compareTo(TIEN_TOI_DA) > 0) {
                ketNoi.rollback();
                return "Tổng thành tiền vượt quá giới hạn hóa đơn cho phép (9.999.999.999,99 VNĐ)!";
            }
            String sqlThemToa = "INSERT INTO toa_thuoc (ma_phieu_kham, ma_thuoc, so_luong, don_gia_luc_ke, lieu_luong_dung, thanh_tien) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement lenhThemToa = ketNoi.prepareStatement(sqlThemToa)) {
                lenhThemToa.setInt(1, tt.maPhieuKham);
                lenhThemToa.setInt(2, tt.maThuoc);
                lenhThemToa.setInt(3, tt.soLuong);
                lenhThemToa.setBigDecimal(4, donGiaHienTai);
                lenhThemToa.setString(5, tt.lieuLuongDung.trim());
                lenhThemToa.setBigDecimal(6, thanhTienDongNay);
                lenhThemToa.executeUpdate();
            }

            String sqlTruKho = "UPDATE thuoc SET so_luong_ton = so_luong_ton - ? WHERE ma_thuoc = ? AND so_luong_ton >= ?";
            try (PreparedStatement lenhTruKho = ketNoi.prepareStatement(sqlTruKho)) {
                lenhTruKho.setInt(1, tt.soLuong);
                lenhTruKho.setInt(2, tt.maThuoc);
                lenhTruKho.setInt(3, tt.soLuong);
                if (lenhTruKho.executeUpdate() == 0) {
                    ketNoi.rollback();
                    return "Không đủ số lượng tồn kho để trừ!";
                }
            }

            capNhatTongTienPhieu(ketNoi, tt.maPhieuKham);

            ketNoi.commit();
            return "THANH_CONG";
        } catch (Exception loi) {
            if (ketNoi != null) {
                try {
                    ketNoi.rollback();
                } catch (Exception loiRollback) {
                    System.err.println("[PhieuKhamDAO.keThuocVaoToa] Lỗi rollback giao dịch: " + loiRollback.getMessage());
                }
            }
            System.err.println("Lỗi kê thuốc vào toa: " + loi.getMessage());
            return "Không thể kê thuốc vào toa do lỗi hệ thống.";
        } finally {
            if (ketNoi != null) {
                try {
                    ketNoi.setAutoCommit(true);
                } catch (Exception loiAutoCommit) {
                    System.err.println("[PhieuKhamDAO.keThuocVaoToa] Lỗi khôi phục autoCommit: " + loiAutoCommit.getMessage());
                }
                try {
                    ketNoi.close();
                } catch (Exception loiDong) {
                    System.err.println("Lỗi đóng kết nối: " + loiDong.getMessage());
                }
            }
        }
    }

    /**
     * Hủy 1 dòng thuốc đã kê nhầm trong Transaction (chỉ khi phiếu chưa thanh toán), hoàn lại tồn kho và tính lại tổng viện phí.
     */
    public String xoaThuocKhoiToa(int maToaThuoc) {
        if (maToaThuoc <= 0) {
            return "Mã dòng thuốc trong toa không hợp lệ!";
        }

        Connection ketNoi = null;
        try {
            ketNoi = KetNoiCSDL.layKetNoi();
            if (ketNoi == null) return "Lỗi kết nối Cơ sở dữ liệu!";
            ketNoi.setAutoCommit(false);

            int maPhieuKham = 0;
            String sqlTimPhieu = "SELECT ma_phieu_kham FROM toa_thuoc WHERE ma_toa_thuoc = ?";
            try (PreparedStatement lenhTim = ketNoi.prepareStatement(sqlTimPhieu)) {
                lenhTim.setInt(1, maToaThuoc);
                try (ResultSet kqTim = lenhTim.executeQuery()) {
                    if (!kqTim.next()) {
                        ketNoi.rollback();
                        return "Không tìm thấy dòng thuốc mã #" + maToaThuoc + " trong toa!";
                    }
                    maPhieuKham = kqTim.getInt("ma_phieu_kham");
                }
            }

            // Luôn khóa phieu_kham trước toa_thuoc và thuoc để đồng bộ thứ tự khóa với keThuocVaoToa
            String sqlPhieu = "SELECT trang_thai_phieu FROM phieu_kham WHERE ma_phieu_kham = ? FOR UPDATE";
            try (PreparedStatement lenhPhieu = ketNoi.prepareStatement(sqlPhieu)) {
                lenhPhieu.setInt(1, maPhieuKham);
                try (ResultSet kqPhieu = lenhPhieu.executeQuery()) {
                    if (!kqPhieu.next()) {
                        ketNoi.rollback();
                        return "Không tìm thấy phiếu khám liên quan!";
                    }
                    if ("Đã thanh toán".equals(kqPhieu.getString("trang_thai_phieu"))) {
                        ketNoi.rollback();
                        return "Phiếu khám #" + maPhieuKham + " đã thanh toán, không thể bỏ thuốc khỏi toa!";
                    }
                }
            }

            int maThuoc = 0;
            int soLuongHoan = 0;
            String sqlDocToa = "SELECT ma_thuoc, so_luong FROM toa_thuoc WHERE ma_toa_thuoc = ? FOR UPDATE";
            try (PreparedStatement lenhDoc = ketNoi.prepareStatement(sqlDocToa)) {
                lenhDoc.setInt(1, maToaThuoc);
                try (ResultSet kq = lenhDoc.executeQuery()) {
                    if (!kq.next()) {
                        ketNoi.rollback();
                        return "Không tìm thấy dòng thuốc mã #" + maToaThuoc + " trong toa!";
                    }
                    maThuoc = kq.getInt("ma_thuoc");
                    soLuongHoan = kq.getInt("so_luong");
                }
            }

            String sqlKhoaThuoc = "SELECT ma_thuoc FROM thuoc WHERE ma_thuoc = ? FOR UPDATE";
            try (PreparedStatement lenhKhoa = ketNoi.prepareStatement(sqlKhoaThuoc)) {
                lenhKhoa.setInt(1, maThuoc);
                lenhKhoa.executeQuery();
            }

            String sqlXoaToa = "DELETE FROM toa_thuoc WHERE ma_toa_thuoc = ?";
            try (PreparedStatement lenhXoa = ketNoi.prepareStatement(sqlXoaToa)) {
                lenhXoa.setInt(1, maToaThuoc);
                if (lenhXoa.executeUpdate() == 0) {
                    ketNoi.rollback();
                    return "Không thể xóa dòng thuốc khỏi toa!";
                }
            }

            String sqlHoanKho = "UPDATE thuoc SET so_luong_ton = so_luong_ton + ? WHERE ma_thuoc = ?";
            try (PreparedStatement lenhHoan = ketNoi.prepareStatement(sqlHoanKho)) {
                lenhHoan.setInt(1, soLuongHoan);
                lenhHoan.setInt(2, maThuoc);
                lenhHoan.executeUpdate();
            }

            capNhatTongTienPhieu(ketNoi, maPhieuKham);

            ketNoi.commit();
            return "THANH_CONG";
        } catch (Exception loi) {
            if (ketNoi != null) {
                try {
                    ketNoi.rollback();
                } catch (Exception loiRollback) {
                    System.err.println("[PhieuKhamDAO.xoaThuocKhoiToa] Lỗi rollback giao dịch: " + loiRollback.getMessage());
                }
            }
            System.err.println("Lỗi xóa thuốc khỏi toa: " + loi.getMessage());
            return "Không thể xóa thuốc khỏi toa do lỗi hệ thống.";
        } finally {
            if (ketNoi != null) {
                try {
                    ketNoi.setAutoCommit(true);
                } catch (Exception loiAutoCommit) {
                    System.err.println("[PhieuKhamDAO.xoaThuocKhoiToa] Lỗi khôi phục autoCommit: " + loiAutoCommit.getMessage());
                }
                try {
                    ketNoi.close();
                } catch (Exception loiDong) {
                    System.err.println("Lỗi đóng kết nối: " + loiDong.getMessage());
                }
            }
        }
    }

    private void capNhatTongTienPhieu(Connection ketNoi, int maPhieuKham) throws Exception {
        String sqlCapNhatPhieu = "UPDATE phieu_kham SET "
                               + "tien_thuoc = (SELECT IFNULL(SUM(thanh_tien), 0) FROM toa_thuoc WHERE ma_phieu_kham = ?), "
                               + "tong_vien_phi = tien_kham + (SELECT IFNULL(SUM(thanh_tien), 0) FROM toa_thuoc WHERE ma_phieu_kham = ?) "
                               + "WHERE ma_phieu_kham = ? AND trang_thai_phieu <> 'Đã thanh toán'";
        try (PreparedStatement lenhCapNhat = ketNoi.prepareStatement(sqlCapNhatPhieu)) {
            lenhCapNhat.setInt(1, maPhieuKham);
            lenhCapNhat.setInt(2, maPhieuKham);
            lenhCapNhat.setInt(3, maPhieuKham);
            lenhCapNhat.executeUpdate();
        }
    }

    private static ToaThuoc docDongToaThuoc(ResultSet kq) throws Exception {
        ToaThuoc tt = new ToaThuoc();
        tt.maToaThuoc = kq.getInt("ma_toa_thuoc");
        tt.maPhieuKham = kq.getInt("ma_phieu_kham");
        tt.maThuoc = kq.getInt("ma_thuoc");
        tt.soLuong = kq.getInt("so_luong");
        tt.donGiaLucKe = chuanTien(kq.getBigDecimal("don_gia_luc_ke"));
        tt.lieuLuongDung = kq.getString("lieu_luong_dung");
        tt.thanhTien = chuanTien(kq.getBigDecimal("thanh_tien"));
        tt.tenThuoc = kq.getString("ten_thuoc");
        tt.donViTinh = kq.getString("don_vi_tinh");
        return tt;
    }

    public List<ToaThuoc> layToaThuocTheoPhieu(int maPhieuKham) {
        List<ToaThuoc> danhSach = new ArrayList<>();
        if (maPhieuKham <= 0) return danhSach;
        String sql = "SELECT tt.*, t.ten_thuoc, t.don_vi_tinh "
                   + "FROM toa_thuoc tt INNER JOIN thuoc t ON tt.ma_thuoc = t.ma_thuoc "
                   + "WHERE tt.ma_phieu_kham = ?";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return danhSach;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                lenh.setInt(1, maPhieuKham);
                try (ResultSet kq = lenh.executeQuery()) {
                    while (kq.next()) {
                        danhSach.add(docDongToaThuoc(kq));
                    }
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi lấy chi tiết toa thuốc: " + loi.getMessage());
        }
        return danhSach;
    }

    public List<ToaThuoc> layToaThuocTheoBenhNhi(int maBenhNhi) {
        List<ToaThuoc> danhSach = new ArrayList<>();
        if (maBenhNhi <= 0) return danhSach;
        String sql = "SELECT tt.*, t.ten_thuoc, t.don_vi_tinh "
                   + "FROM toa_thuoc tt "
                   + "INNER JOIN thuoc t ON tt.ma_thuoc = t.ma_thuoc "
                   + "INNER JOIN phieu_kham pk ON tt.ma_phieu_kham = pk.ma_phieu_kham "
                   + "WHERE pk.ma_benh_nhi = ? "
                   + "ORDER BY tt.ma_phieu_kham DESC, tt.ma_toa_thuoc ASC";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return danhSach;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                lenh.setInt(1, maBenhNhi);
                try (ResultSet kq = lenh.executeQuery()) {
                    while (kq.next()) {
                        danhSach.add(docDongToaThuoc(kq));
                    }
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi lấy danh sách toa thuốc theo bệnh nhi: " + loi.getMessage());
        }
        return danhSach;
    }

    public boolean kiemTraPhieuThuocPhuHuynh(int maPhieuKham, String soDienThoai) {
        if (maPhieuKham <= 0 || soDienThoai == null || soDienThoai.trim().isEmpty()) return false;
        String sql = "SELECT 1 FROM phieu_kham pk "
                   + "INNER JOIN benh_nhi b ON pk.ma_benh_nhi = b.ma_benh_nhi "
                   + "INNER JOIN phu_huynh p ON b.ma_phu_huynh = p.ma_phu_huynh "
                   + "WHERE pk.ma_phieu_kham = ? AND p.so_dien_thoai = ? LIMIT 1";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return false;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                lenh.setInt(1, maPhieuKham);
                lenh.setString(2, soDienThoai.trim());
                try (ResultSet kq = lenh.executeQuery()) {
                    return kq.next();
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi kiểm tra quyền xem phiếu khám: " + loi.getMessage());
            return false;
        }
    }

    public boolean kiemTraBeThuocPhuHuynh(int maBenhNhi, String soDienThoai) {
        if (maBenhNhi <= 0 || soDienThoai == null || soDienThoai.trim().isEmpty()) return false;
        String sql = "SELECT 1 FROM benh_nhi b "
                   + "INNER JOIN phu_huynh p ON b.ma_phu_huynh = p.ma_phu_huynh "
                   + "WHERE b.ma_benh_nhi = ? AND p.so_dien_thoai = ? LIMIT 1";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return false;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                lenh.setInt(1, maBenhNhi);
                lenh.setString(2, soDienThoai.trim());
                try (ResultSet kq = lenh.executeQuery()) {
                    return kq.next();
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi kiểm tra quyền xem hồ sơ bé: " + loi.getMessage());
            return false;
        }
    }

    public boolean thuTienVienPhi(int maPhieuKham) {
        if (maPhieuKham <= 0) return false;
        String sql = "UPDATE phieu_kham SET trang_thai_phieu = 'Đã thanh toán' WHERE ma_phieu_kham = ? AND trang_thai_phieu <> 'Đã thanh toán'";
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return false;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                lenh.setInt(1, maPhieuKham);
                return lenh.executeUpdate() > 0;
            }
        } catch (Exception loi) {
            System.err.println("Lỗi thu tiền viện phí: " + loi.getMessage());
            return false;
        }
    }

    /**
     * Thống kê doanh thu trong ngày CURDATE() và tích lũy toàn thời gian (chỉ tính phiếu Đã thanh toán).
     */
    public BigDecimal[] thongKeDoanhThu() {
        BigDecimal[] ketQua = new BigDecimal[]{
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        };
        String sqlHomNay = "SELECT COUNT(*) AS so_ca, "
                         + "IFNULL(SUM(tien_kham), 0) AS tong_kham, "
                         + "IFNULL(SUM(tien_thuoc), 0) AS tong_thuoc, "
                         + "IFNULL(SUM(tong_vien_phi), 0) AS tong_cong "
                         + "FROM phieu_kham WHERE trang_thai_phieu = 'Đã thanh toán' "
                         + "AND ngay_kham >= CURDATE() AND ngay_kham < CURDATE() + INTERVAL 1 DAY";
        String sqlTichLuy = "SELECT COUNT(*) AS so_ca_tl, IFNULL(SUM(tong_vien_phi), 0) AS tong_cong_tl "
                          + "FROM phieu_kham WHERE trang_thai_phieu = 'Đã thanh toán'";

        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return ketQua;
            try (PreparedStatement lenhHN = ketNoi.prepareStatement(sqlHomNay);
                 ResultSet kqHN = lenhHN.executeQuery()) {
                if (kqHN.next()) {
                    ketQua[0] = BigDecimal.valueOf(kqHN.getLong("so_ca"));
                    ketQua[1] = chuanTien(kqHN.getBigDecimal("tong_kham"));
                    ketQua[2] = chuanTien(kqHN.getBigDecimal("tong_thuoc"));
                    ketQua[3] = chuanTien(kqHN.getBigDecimal("tong_cong"));
                }
            }
            try (PreparedStatement lenhTL = ketNoi.prepareStatement(sqlTichLuy);
                 ResultSet kqTL = lenhTL.executeQuery()) {
                if (kqTL.next()) {
                    ketQua[4] = BigDecimal.valueOf(kqTL.getLong("so_ca_tl"));
                    ketQua[5] = chuanTien(kqTL.getBigDecimal("tong_cong_tl"));
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi thống kê doanh thu: " + loi.getMessage());
        }
        return ketQua;
    }

    public static void main(String[] args) {
        PhieuKhamDAO pkDao = new PhieuKhamDAO();

        System.out.println("1. Thử kê thêm thuốc vào phiếu #1 (Đã thanh toán):");
        ToaThuoc toaPhieuDaThu = new ToaThuoc(0, 1, 2, 1, BigDecimal.ZERO, "Uống 3 lần/ngày", BigDecimal.ZERO);
        System.out.println("   - Kết quả: " + pkDao.keThuocVaoToa(toaPhieuDaThu));

        System.out.println("2. Chi tiết toa thuốc phiếu #1:");
        List<ToaThuoc> dsToa = pkDao.layToaThuocTheoPhieu(1);
        for (int i = 0; i < dsToa.size(); i++) {
            ToaThuoc t = dsToa.get(i);
            System.out.println("   - " + t.tenThuoc + " | SL: " + t.soLuong + " " + t.donViTinh + " | Giá lúc kê: " + t.donGiaLucKe.toPlainString() + "đ");
        }

        System.out.println("3. Thống kê doanh thu:");
        BigDecimal[] tk = pkDao.thongKeDoanhThu();
        System.out.println("   - Hôm nay: " + tk[0].intValue() + " ca | " + tk[3].toPlainString() + " VNĐ");
        System.out.println("   - Tích lũy: " + tk[4].intValue() + " ca | " + tk[5].toPlainString() + " VNĐ");
    }
}
