package dao;

import config.KetNoiCSDL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import model.BenhNhi;
import model.LichHen;

/**
 * Quản lý lịch hẹn khám bệnh và kiểm tra trùng khung giờ theo từng bác sĩ.
 */
public class LichHenDAO {

    public LichHenDAO() {
    }

    public List<LichHen> layDanhSachLichHen() {
        String sql = "SELECT l.*, b.ho_ten_be, t.ho_ten AS ho_ten_bac_si "
                   + "FROM lich_hen l "
                   + "INNER JOIN benh_nhi b ON l.ma_benh_nhi = b.ma_benh_nhi "
                   + "INNER JOIN tai_khoan t ON l.ma_bac_si = t.ma_tai_khoan "
                   + "ORDER BY l.ngay_hen DESC, l.gio_hen ASC";
        return truyVanDanhSachLichHen(sql, null);
    }

    public List<LichHen> layDanhSachLichHenTheoPhuHuynh(String soDienThoai) {
        if (soDienThoai == null || soDienThoai.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String sql = "SELECT l.*, b.ho_ten_be, t.ho_ten AS ho_ten_bac_si "
                   + "FROM lich_hen l "
                   + "INNER JOIN benh_nhi b ON l.ma_benh_nhi = b.ma_benh_nhi "
                   + "INNER JOIN phu_huynh p ON b.ma_phu_huynh = p.ma_phu_huynh "
                   + "INNER JOIN tai_khoan t ON l.ma_bac_si = t.ma_tai_khoan "
                   + "WHERE p.so_dien_thoai = ? "
                   + "ORDER BY l.ngay_hen DESC, l.gio_hen ASC";
        return truyVanDanhSachLichHen(sql, soDienThoai.trim());
    }

    private List<LichHen> truyVanDanhSachLichHen(String sql, String soDienThoai) {
        List<LichHen> danhSach = new ArrayList<>();
        try (Connection ketNoi = KetNoiCSDL.layKetNoi()) {
            if (ketNoi == null) return danhSach;
            try (PreparedStatement lenh = ketNoi.prepareStatement(sql)) {
                if (soDienThoai != null) {
                    lenh.setString(1, soDienThoai);
                }
                try (ResultSet kq = lenh.executeQuery()) {
                    while (kq.next()) {
                        LichHen lh = new LichHen();
                        lh.maLichHen = kq.getInt("ma_lich_hen");
                        lh.maBenhNhi = kq.getInt("ma_benh_nhi");
                        lh.maBacSi = kq.getInt("ma_bac_si");
                        lh.ngayHen = kq.getString("ngay_hen");
                        lh.gioHen = kq.getString("gio_hen");
                        lh.lyDoKham = kq.getString("ly_do_kham");
                        lh.trangThai = kq.getString("trang_thai");
                        lh.hoTenBe = kq.getString("ho_ten_be");
                        lh.hoTenBacSi = kq.getString("ho_ten_bac_si");
                        danhSach.add(lh);
                    }
                }
            }
        } catch (Exception loi) {
            System.err.println("Lỗi lấy danh sách lịch hẹn: " + loi.getMessage());
        }
        return danhSach;
    }

    public String themLichHen(LichHen lh) {
        if (lh == null || lh.maBenhNhi <= 0 || lh.maBacSi <= 0) {
            return "Vui lòng chọn Bệnh nhi và Bác sĩ khám hợp lệ!";
        }
        if (lh.ngayHen == null || lh.ngayHen.trim().isEmpty()) {
            return "Vui lòng chọn Ngày hẹn khám!";
        }
        String ngayHenChuan = lh.ngayHen.trim();
        LocalDate ngayHenParsed;
        try {
            ngayHenParsed = LocalDate.parse(ngayHenChuan);
        } catch (Exception e) {
            return "Ngày hẹn không đúng định dạng Năm-Tháng-Ngày (YYYY-MM-DD)!";
        }
        if (ngayHenParsed.isBefore(LocalDate.now())) {
            return "Ngày hẹn khám không được nằm trong quá khứ!";
        }

        if (lh.gioHen == null || lh.gioHen.trim().isEmpty()) {
            return "Vui lòng chọn Khung giờ hẹn khám!";
        }
        String gioHenChuan = lh.gioHen.trim();
        // Chuẩn hóa giờ dạng 8:30 -> 08:30 để so khớp trùng lịch chính xác
        if (gioHenChuan.matches("^[0-9]:[0-5][0-9]$")) {
            gioHenChuan = "0" + gioHenChuan;
        }
        if (!gioHenChuan.matches("^([01][0-9]|2[0-3]):[0-5][0-9]$")) {
            return "Giờ hẹn khám phải đúng định dạng HH:mm (ví dụ: 08:30, 14:00)!";
        }
        if (ngayHenParsed.equals(LocalDate.now()) && LocalTime.parse(gioHenChuan).isBefore(LocalTime.now())) {
            return "Giờ hẹn khám trong ngày hôm nay không được lùi về thời điểm đã qua!";
        }
        String lyDoChuan = lh.lyDoKham == null ? "" : lh.lyDoKham.trim();
        if (lyDoChuan.length() > 255) {
            return "Lý do khám không được vượt quá 255 ký tự!";
        }

        Connection ketNoi = null;
        try {
            ketNoi = KetNoiCSDL.layKetNoi();
            if (ketNoi == null) return "Lỗi kết nối Cơ sở dữ liệu!";
            ketNoi.setAutoCommit(false);

            String sqlKiemTraBe = "SELECT ma_benh_nhi FROM benh_nhi WHERE ma_benh_nhi = ? FOR UPDATE";
            try (PreparedStatement lenhBe = ketNoi.prepareStatement(sqlKiemTraBe)) {
                lenhBe.setInt(1, lh.maBenhNhi);
                try (ResultSet kqBe = lenhBe.executeQuery()) {
                    if (!kqBe.next()) {
                        ketNoi.rollback();
                        return "Không tìm thấy hồ sơ bệnh nhi mã #" + lh.maBenhNhi + "!";
                    }
                }
            }

            // Khóa dòng bác sĩ trong transaction để tránh đặt trùng lịch đồng thời
            String sqlKiemTraBS = "SELECT ma_tai_khoan FROM tai_khoan WHERE ma_tai_khoan = ? AND vai_tro = 'BAC_SI' FOR UPDATE";
            try (PreparedStatement lenhBS = ketNoi.prepareStatement(sqlKiemTraBS)) {
                lenhBS.setInt(1, lh.maBacSi);
                try (ResultSet kqBS = lenhBS.executeQuery()) {
                    if (!kqBS.next()) {
                        ketNoi.rollback();
                        return "Mã bác sĩ #" + lh.maBacSi + " không tồn tại hoặc không có vai trò Bác sĩ nhi khoa!";
                    }
                }
            }

            String sqlKiemTraTrung = "SELECT COUNT(*) FROM lich_hen WHERE ma_bac_si = ? AND ngay_hen = ? AND gio_hen = ? AND trang_thai <> 'Đã hủy' FOR UPDATE";
            try (PreparedStatement lenhKiemTra = ketNoi.prepareStatement(sqlKiemTraTrung)) {
                lenhKiemTra.setInt(1, lh.maBacSi);
                lenhKiemTra.setString(2, ngayHenChuan);
                lenhKiemTra.setString(3, gioHenChuan);
                try (ResultSet kqTrung = lenhKiemTra.executeQuery()) {
                    if (kqTrung.next() && kqTrung.getInt(1) > 0) {
                        ketNoi.rollback();
                        return "Bác sĩ này đã có lịch hẹn vào khung giờ " + gioHenChuan + " ngày " + ngayHenChuan + ". Vui lòng chọn giờ khác!";
                    }
                }
            }

            String sqlKiemTraTrungBe = "SELECT COUNT(*) FROM lich_hen WHERE ma_benh_nhi = ? AND ngay_hen = ? AND gio_hen = ? AND trang_thai <> 'Đã hủy' FOR UPDATE";
            try (PreparedStatement lenhTrungBe = ketNoi.prepareStatement(sqlKiemTraTrungBe)) {
                lenhTrungBe.setInt(1, lh.maBenhNhi);
                lenhTrungBe.setString(2, ngayHenChuan);
                lenhTrungBe.setString(3, gioHenChuan);
                try (ResultSet kqTrungBe = lenhTrungBe.executeQuery()) {
                    if (kqTrungBe.next() && kqTrungBe.getInt(1) > 0) {
                        ketNoi.rollback();
                        return "Bệnh nhi này đã có lịch hẹn vào khung giờ " + gioHenChuan + " ngày " + ngayHenChuan + "!";
                    }
                }
            }

            String sqlThem = "INSERT INTO lich_hen (ma_benh_nhi, ma_bac_si, ngay_hen, gio_hen, ly_do_kham, trang_thai) VALUES (?, ?, ?, ?, ?, 'Chờ khám')";
            try (PreparedStatement lenhThem = ketNoi.prepareStatement(sqlThem)) {
                lenhThem.setInt(1, lh.maBenhNhi);
                lenhThem.setInt(2, lh.maBacSi);
                lenhThem.setString(3, ngayHenChuan);
                lenhThem.setString(4, gioHenChuan);
                lenhThem.setString(5, lh.lyDoKham == null ? "" : lh.lyDoKham.trim());
                lenhThem.executeUpdate();
            }
            ketNoi.commit();
            return "THANH_CONG";
        } catch (Exception loi) {
            if (ketNoi != null) {
                try {
                    ketNoi.rollback();
                } catch (Exception loiRollback) {
                    System.err.println("[LichHenDAO.themLichHen] Lỗi rollback giao dịch: " + loiRollback.getMessage());
                }
            }
            System.err.println("Lỗi thêm lịch hẹn: " + loi.getMessage());
            return "Không thể đặt lịch hẹn do lỗi hệ thống.";
        } finally {
            if (ketNoi != null) {
                try {
                    ketNoi.setAutoCommit(true);
                } catch (Exception loiAutoCommit) {
                    System.err.println("[LichHenDAO.themLichHen] Lỗi khôi phục autoCommit: " + loiAutoCommit.getMessage());
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
     * Chỉ cho phép chuyển lịch hẹn từ 'Chờ khám' sang 'Đã khám' hoặc 'Đã hủy' (không cho hồi sinh lịch đã hủy).
     */
    public String capNhatTrangThaiLichHenCoThongBao(int maLichHen, String trangThaiMoi) {
        if (maLichHen <= 0 || trangThaiMoi == null) {
            return "Mã lịch hẹn hoặc trạng thái không hợp lệ!";
        }
        String tt = trangThaiMoi.trim();
        if (!"Đã khám".equals(tt) && !"Đã hủy".equals(tt)) {
            return "Chỉ cho phép chuyển lịch hẹn từ 'Chờ khám' sang 'Đã khám' hoặc 'Đã hủy'!";
        }

        Connection ketNoi = null;
        try {
            ketNoi = KetNoiCSDL.layKetNoi();
            if (ketNoi == null) return "Lỗi kết nối Cơ sở dữ liệu!";
            ketNoi.setAutoCommit(false);

            String trangThaiHienTai = null;
            String sqlDoc = "SELECT trang_thai FROM lich_hen WHERE ma_lich_hen = ? FOR UPDATE";
            try (PreparedStatement lenhDoc = ketNoi.prepareStatement(sqlDoc)) {
                lenhDoc.setInt(1, maLichHen);
                try (ResultSet kq = lenhDoc.executeQuery()) {
                    if (kq.next()) {
                        trangThaiHienTai = kq.getString("trang_thai");
                    }
                }
            }
            if (trangThaiHienTai == null) {
                ketNoi.rollback();
                return "Không tìm thấy lịch hẹn mã #" + maLichHen + "!";
            }
            if (!"Chờ khám".equals(trangThaiHienTai)) {
                ketNoi.rollback();
                return "Lịch hẹn đang ở trạng thái '" + trangThaiHienTai + "', không thể chuyển sang '" + tt + "'!";
            }

            String sqlCapNhat = "UPDATE lich_hen SET trang_thai = ? WHERE ma_lich_hen = ? AND trang_thai = 'Chờ khám'";
            try (PreparedStatement lenh = ketNoi.prepareStatement(sqlCapNhat)) {
                lenh.setString(1, tt);
                lenh.setInt(2, maLichHen);
                lenh.executeUpdate();
            }
            ketNoi.commit();
            return "THANH_CONG";
        } catch (Exception loi) {
            if (ketNoi != null) {
                try {
                    ketNoi.rollback();
                } catch (Exception loiRollback) {
                    System.err.println("[LichHenDAO.capNhatTrangThaiLichHenCoThongBao] Lỗi rollback giao dịch: " + loiRollback.getMessage());
                }
            }
            System.err.println("Lỗi cập nhật trạng thái lịch hẹn: " + loi.getMessage());
            return "Không thể cập nhật lịch hẹn do lỗi hệ thống.";
        } finally {
            if (ketNoi != null) {
                try {
                    ketNoi.setAutoCommit(true);
                } catch (Exception loiAutoCommit) {
                    System.err.println("[LichHenDAO.capNhatTrangThaiLichHenCoThongBao] Lỗi khôi phục autoCommit: " + loiAutoCommit.getMessage());
                }
                try {
                    ketNoi.close();
                } catch (Exception loiDong) {
                    System.err.println("Lỗi đóng kết nối: " + loiDong.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) {
        BenhNhiDAO bnDao = new BenhNhiDAO();
        LichHenDAO lhDao = new LichHenDAO();

        System.out.println("1. Tra cứu các bé chung SĐT 0901234567:");
        List<BenhNhi> dsBe = bnDao.timKiemBenhNhi("0901234567");
        for (int i = 0; i < dsBe.size(); i++) {
            BenhNhi b = dsBe.get(i);
            System.out.println("   - #" + b.maBenhNhi + " | " + b.hoTenBe + " | PH: " + b.hoTenPhuHuynh);
        }

        System.out.println("2. Thử đặt lịch hẹn trùng giờ bác sĩ:");
        List<LichHen> dsLich = lhDao.layDanhSachLichHen();
        if (!dsLich.isEmpty()) {
            LichHen lichMau = dsLich.get(0);
            LichHen lichTrung = new LichHen(0, 1, lichMau.maBacSi, lichMau.ngayHen, lichMau.gioHen, "Thử đặt trùng giờ", "Chờ khám");
            System.out.println("   - Kết quả: " + lhDao.themLichHen(lichTrung));
        }
    }
}
