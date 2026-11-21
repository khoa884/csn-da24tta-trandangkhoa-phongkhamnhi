package api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.BenhNhiDAO;
import dao.LichHenDAO;
import dao.PhieuKhamDAO;
import dao.TaiKhoanDAO;
import dao.ThuocDAO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import model.BenhNhi;
import model.LichHen;
import model.PhieuKham;
import model.TaiKhoan;
import model.Thuoc;
import model.ToaThuoc;

/**
 * Máy chủ HTTP Core Java trên cổng 8080: ThreadPool giới hạn, Bearer Token có TTL, RBAC và bảo vệ JSON/Static.
 */
public class MayChuAPI {

    private static final long THOI_GIAN_PHIEN_MS = 8L * 60 * 60 * 1000;
    private static final int GIOI_HAN_PHIEN = 5000;
    private static final int GIOI_HAN_BO_DEM_THU_SAI = 2000;
    private static final int GIOI_HAN_BODY_BYTES = 1_048_576;
    private static final long CUA_SO_GIOI_HAN_DANG_NHAP_MS = 60_000L;
    private static final int SO_LAN_THU_SAI_TOI_DA = 5;
    private static final int SO_LAN_THU_SAI_THEO_IP_TOI_DA = 15;
    private static final int SO_PHIEN_TOI_DA_MOI_TAI_KHOAN = 3;

    private static final BigDecimal GIOI_HAN_TIEN_TE = new BigDecimal("9999999999.99");
    private static final BigDecimal GIOI_HAN_CAN_NANG = new BigDecimal("200.00");
    private static final Map<String, PhienDangNhap> BANG_PHIEN = new ConcurrentHashMap<>();
    private static final Map<String, BoDemThuSai> BANG_THU_SAI = new ConcurrentHashMap<>();

    private static class PhienDangNhap {
        final TaiKhoan taiKhoan;
        final long hetHanLuc;

        PhienDangNhap(TaiKhoan taiKhoan, long hetHanLuc) {
            this.taiKhoan = taiKhoan;
            this.hetHanLuc = hetHanLuc;
        }
    }

    private static class BoDemThuSai {
        int soLanSai;
        volatile long thoiDiemBatDau;
    }

    public MayChuAPI() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.net.httpserver.idleInterval", "15");
        System.setProperty("jdk.httpserver.maxConnections", "500");

        HttpServer mayChu = HttpServer.create(new InetSocketAddress(8080), 64);
        ThreadPoolExecutor nhomLuong = new ThreadPoolExecutor(
            8, 32, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        mayChu.setExecutor(nhomLuong);

        TaiKhoanDAO taiKhoanDAO = new TaiKhoanDAO();
        BenhNhiDAO benhNhiDAO = new BenhNhiDAO();
        LichHenDAO lichHenDAO = new LichHenDAO();
        ThuocDAO thuocDAO = new ThuocDAO();
        PhieuKhamDAO phieuKhamDAO = new PhieuKhamDAO();

        mayChu.createContext("/api/dang-nhap", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            if (!goiTin.getRequestMethod().equalsIgnoreCase("POST")) {
                guiPhanHoiJSON(goiTin, 405, "{\"thanhCong\":false,\"thongBao\":\"Phương thức HTTP không được hỗ trợ (Chỉ dùng POST)!\"}");
                return;
            }
            String duLieu = docDuLieuGuiLen(goiTin);
            if (duLieu == null) return;

            String tenDangNhap = layGiaTri(duLieu, "tenDangNhap").trim();
            String matKhau = layGiaTri(duLieu, "matKhau");
            String khoaGioiHan = layKhoaGioiHanDangNhap(goiTin, tenDangNhap);
            String khoaIP = layKhoaGioiHanIP(goiTin);
            if (dangBiGioiHanDangNhap(khoaGioiHan, SO_LAN_THU_SAI_TOI_DA)
                || dangBiGioiHanDangNhap(khoaIP, SO_LAN_THU_SAI_THEO_IP_TOI_DA)) {
                guiPhanHoiJSON(goiTin, 429, "{\"thanhCong\":false,\"thongBao\":\"Thử đăng nhập sai quá nhiều lần. Vui lòng đợi 60 giây rồi thử lại.\"}");
                return;
            }

            TaiKhoan tk = new TaiKhoan();
            String ketQua = taiKhoanDAO.kiemTraDangNhap(tenDangNhap, matKhau, tk);
            if ("THANH_CONG".equals(ketQua)) {
                BANG_THU_SAI.remove(khoaGioiHan);
                String tokenMoi = taoPhienMoi(tk);
                String json = "{\"thanhCong\":true,\"thongBao\":\"Đăng nhập thành công!\",\"token\":\"" + tokenMoi
                            + "\",\"maTaiKhoan\":" + tk.maTaiKhoan
                            + ",\"tenDangNhap\":\"" + lamSachChuoi(tk.tenDangNhap) + "\",\"hoTen\":\"" + lamSachChuoi(tk.hoTen)
                            + "\",\"vaiTro\":\"" + lamSachChuoi(tk.vaiTro)
                            + "\",\"phaiDoiMatKhau\":" + (tk.phaiDoiMatKhau == 1) + "}";
                guiPhanHoiJSON(goiTin, 200, json);
            } else {
                ghiNhanDangNhapSai(khoaGioiHan);
                ghiNhanDangNhapSai(khoaIP);
                String json = "{\"thanhCong\":false,\"thongBao\":\"" + lamSachChuoi(ketQua) + "\"}";
                guiPhanHoiJSON(goiTin, 401, json);
            }
        });

        mayChu.createContext("/api/dang-xuat", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            if (!goiTin.getRequestMethod().equalsIgnoreCase("POST")) {
                guiPhanHoiJSON(goiTin, 405, "{\"thanhCong\":false,\"thongBao\":\"Phương thức HTTP không được hỗ trợ (Chỉ dùng POST)!\"}");
                return;
            }
            TaiKhoan nguoiDung = xacThucToken(goiTin);
            if (nguoiDung == null) return;
            String token = layBearerToken(goiTin);
            if (!token.isEmpty()) {
                BANG_PHIEN.remove(token);
            }
            guiPhanHoiJSON(goiTin, 200, "{\"thanhCong\":true,\"thongBao\":\"Đã đăng xuất an toàn khỏi hệ thống.\"}");
        });

        mayChu.createContext("/api/doi-mat-khau", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            if (!goiTin.getRequestMethod().equalsIgnoreCase("POST")) {
                guiPhanHoiJSON(goiTin, 405, "{\"thanhCong\":false,\"thongBao\":\"Phương thức HTTP không được hỗ trợ (Chỉ dùng POST)!\"}");
                return;
            }
            TaiKhoan nguoiDung = xacThucToken(goiTin);
            if (nguoiDung == null) return;
            String duLieu = docDuLieuGuiLen(goiTin);
            if (duLieu == null) return;

            String matKhauCu = layGiaTri(duLieu, "matKhauCu");
            String matKhauMoi = layGiaTri(duLieu, "matKhauMoi");
            String kq = taiKhoanDAO.doiMatKhau(nguoiDung.maTaiKhoan, matKhauCu, matKhauMoi);
            boolean ok = "THANH_CONG".equals(kq);
            if (ok) {
                nguoiDung.phaiDoiMatKhau = 0;
                String tokenHienTai = layBearerToken(goiTin);
                BANG_PHIEN.entrySet().removeIf(entry ->
                    entry.getValue().taiKhoan.maTaiKhoan == nguoiDung.maTaiKhoan
                    && !entry.getKey().equals(tokenHienTai)
                );
            }
            String tb = ok ? "Đổi mật khẩu thành công!" : kq;
            guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
        });

        mayChu.createContext("/api/tai-khoan", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            TaiKhoan nguoiDung = xacThucDayDu(goiTin);
            if (nguoiDung == null) return;

            String phuongThuc = goiTin.getRequestMethod();
            if (phuongThuc.equalsIgnoreCase("GET")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI", "LE_TAN", "BAC_SI")) return;
                List<TaiKhoan> ds = taiKhoanDAO.layDanhSachTaiKhoan();
                StringBuilder json = new StringBuilder("[");
                boolean daGhiPhanTuDau = false;
                for (int i = 0; i < ds.size(); i++) {
                    TaiKhoan t = ds.get(i);
                    // Nhân viên chỉ xem thông tin cơ bản của bác sĩ để xếp lịch hẹn, ẩn siêu dữ liệu bảo mật
                    if (!"QUAN_TRI".equals(nguoiDung.vaiTro)) {
                        if (!"BAC_SI".equals(t.vaiTro)) {
                            continue;
                        }
                        if (daGhiPhanTuDau) json.append(",");
                        daGhiPhanTuDau = true;
                        json.append("{\"maTaiKhoan\":").append(t.maTaiKhoan)
                            .append(",\"hoTen\":\"").append(lamSachChuoi(t.hoTen))
                            .append("\",\"vaiTro\":\"").append(lamSachChuoi(t.vaiTro)).append("\"}");
                        continue;
                    }
                    if (daGhiPhanTuDau) json.append(",");
                    daGhiPhanTuDau = true;
                    json.append("{\"maTaiKhoan\":").append(t.maTaiKhoan)
                        .append(",\"tenDangNhap\":\"").append(lamSachChuoi(t.tenDangNhap))
                        .append("\",\"hoTen\":\"").append(lamSachChuoi(t.hoTen))
                        .append("\",\"vaiTro\":\"").append(lamSachChuoi(t.vaiTro))
                        .append("\",\"soLanSai\":").append(t.soLanSai)
                        .append(",\"trangThaiKhoa\":").append(t.trangThaiKhoa)
                        .append(",\"khoaDenLuc\":\"").append(lamSachChuoi(t.khoaDenLuc))
                        .append("\",\"phaiDoiMatKhau\":").append(t.phaiDoiMatKhau).append("}");
                }
                json.append("]");
                guiPhanHoiJSON(goiTin, 200, json.toString());
            } else if (phuongThuc.equalsIgnoreCase("POST") || phuongThuc.equalsIgnoreCase("PUT")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI")) return;
                String duLieu = docDuLieuGuiLen(goiTin);
                if (duLieu == null) return;
                int maTK = doiSangSoNguyen(layGiaTri(duLieu, "maTaiKhoan"));
                boolean ok = taiKhoanDAO.moKhoaTaiKhoan(maTK);
                if (ok) {
                    String tenDangNhapMoKhoa = taiKhoanDAO.layTenDangNhapTheoMa(maTK);
                    if (tenDangNhapMoKhoa != null && !tenDangNhapMoKhoa.isEmpty()) {
                        String hauTo = "|" + tenDangNhapMoKhoa.toLowerCase(Locale.ROOT);
                        BANG_THU_SAI.entrySet().removeIf(e -> e.getKey().endsWith(hauTo));
                    }
                }
                String thongBao = ok ? "Đã mở khóa tài khoản và đặt lại số lần sai về 0!" : "Không tìm thấy tài khoản cần mở khóa!";
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(thongBao) + "\"}");
            } else {
                guiPhanHoiJSON(goiTin, 405, "{\"thanhCong\":false,\"thongBao\":\"Phương thức HTTP không được hỗ trợ!\"}");
            }
        });

        mayChu.createContext("/api/benh-nhi", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            TaiKhoan nguoiDung = xacThucDayDu(goiTin);
            if (nguoiDung == null) return;

            String phuongThuc = goiTin.getRequestMethod();
            String duLieu = docDuLieuGuiLen(goiTin);
            if (duLieu == null) return;

            if (phuongThuc.equalsIgnoreCase("GET")) {
                List<BenhNhi> ds;
                if ("PHU_HUYNH".equals(nguoiDung.vaiTro)) {
                    ds = benhNhiDAO.layDanhSachBenhNhiTheoSDT(nguoiDung.tenDangNhap);
                } else {
                    ds = benhNhiDAO.timKiemBenhNhi(layGiaTri(duLieu, "tuKhoa"));
                }
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < ds.size(); i++) {
                    BenhNhi b = ds.get(i);
                    if (i > 0) json.append(",");
                    json.append("{\"maBenhNhi\":").append(b.maBenhNhi)
                        .append(",\"hoTenBe\":\"").append(lamSachChuoi(b.hoTenBe))
                        .append("\",\"ngaySinh\":\"").append(lamSachChuoi(b.ngaySinh))
                        .append("\",\"gioiTinh\":\"").append(lamSachChuoi(b.gioiTinh))
                        .append("\",\"tienSuBenh\":\"").append(lamSachChuoi(b.tienSuBenh))
                        .append("\",\"maPhuHuynh\":").append(b.maPhuHuynh)
                        .append(",\"hoTenPhuHuynh\":\"").append(lamSachChuoi(b.hoTenPhuHuynh))
                        .append("\",\"soDienThoai\":\"").append(lamSachChuoi(b.soDienThoai))
                        .append("\",\"diaChi\":\"").append(lamSachChuoi(b.diaChi)).append("\"}");
                }
                json.append("]");
                guiPhanHoiJSON(goiTin, 200, json.toString());
            } else if (phuongThuc.equalsIgnoreCase("POST")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI", "LE_TAN", "BAC_SI")) return;
                BenhNhi bn = new BenhNhi();
                bn.hoTenBe = layGiaTri(duLieu, "hoTenBe");
                bn.ngaySinh = layGiaTri(duLieu, "ngaySinh");
                bn.gioiTinh = layGiaTri(duLieu, "gioiTinh");
                bn.tienSuBenh = layGiaTri(duLieu, "tienSuBenh");
                bn.hoTenPhuHuynh = layGiaTri(duLieu, "hoTenPhuHuynh");
                bn.soDienThoai = layGiaTri(duLieu, "soDienThoai");
                bn.diaChi = layGiaTri(duLieu, "diaChi");
                StringBuilder matKhauTam = new StringBuilder();
                String kq = benhNhiDAO.themBenhNhi(bn, matKhauTam);
                boolean ok = "THANH_CONG".equals(kq);
                String tb = ok ? "Đã lưu hồ sơ bệnh nhi thành công!" : kq;
                StringBuilder json = new StringBuilder("{\"thanhCong\":");
                json.append(ok).append(",\"thongBao\":\"").append(lamSachChuoi(tb)).append("\"");
                if (ok && matKhauTam.length() > 0) {
                    json.append(",\"taiKhoanMoi\":{\"tenDangNhap\":\"")
                        .append(lamSachChuoi(bn.soDienThoai.trim()))
                        .append("\",\"matKhauTam\":\"")
                        .append(lamSachChuoi(matKhauTam.toString()))
                        .append("\"}");
                }
                json.append("}");
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, json.toString());
            } else if (phuongThuc.equalsIgnoreCase("PUT")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI", "LE_TAN", "BAC_SI")) return;
                BenhNhi bn = new BenhNhi();
                bn.maBenhNhi = doiSangSoNguyen(layGiaTri(duLieu, "maBenhNhi"));
                bn.hoTenBe = layGiaTri(duLieu, "hoTenBe");
                bn.ngaySinh = layGiaTri(duLieu, "ngaySinh");
                bn.gioiTinh = layGiaTri(duLieu, "gioiTinh");
                bn.tienSuBenh = layGiaTri(duLieu, "tienSuBenh");
                bn.hoTenPhuHuynh = layGiaTri(duLieu, "hoTenPhuHuynh");
                bn.diaChi = layGiaTri(duLieu, "diaChi");
                String kq = benhNhiDAO.suaBenhNhiCoThongBao(bn);
                boolean ok = "THANH_CONG".equals(kq);
                String tb = ok ? "Cập nhật hồ sơ bệnh nhi thành công!" : kq;
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
            } else if (phuongThuc.equalsIgnoreCase("DELETE")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI")) return;
                int maBN = doiSangSoNguyen(layGiaTri(duLieu, "maBenhNhi"));
                boolean ok = benhNhiDAO.xoaBenhNhi(maBN);
                String tb = ok ? "Đã xóa hồ sơ bệnh nhi thành công!" : "Không thể xóa bé đã có lịch sử phiếu khám hoặc lịch hẹn!";
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
            } else {
                guiPhanHoiJSON(goiTin, 405, "{\"thanhCong\":false,\"thongBao\":\"Phương thức HTTP không được hỗ trợ!\"}");
            }
        });

        mayChu.createContext("/api/lich-hen", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            TaiKhoan nguoiDung = xacThucDayDu(goiTin);
            if (nguoiDung == null) return;

            String phuongThuc = goiTin.getRequestMethod();
            String duLieu = docDuLieuGuiLen(goiTin);
            if (duLieu == null) return;

            if (phuongThuc.equalsIgnoreCase("GET")) {
                List<LichHen> ds = "PHU_HUYNH".equals(nguoiDung.vaiTro)
                    ? lichHenDAO.layDanhSachLichHenTheoPhuHuynh(nguoiDung.tenDangNhap)
                    : lichHenDAO.layDanhSachLichHen();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < ds.size(); i++) {
                    LichHen l = ds.get(i);
                    if (i > 0) json.append(",");
                    json.append("{\"maLichHen\":").append(l.maLichHen)
                        .append(",\"maBenhNhi\":").append(l.maBenhNhi)
                        .append(",\"maBacSi\":").append(l.maBacSi)
                        .append(",\"hoTenBe\":\"").append(lamSachChuoi(l.hoTenBe))
                        .append("\",\"hoTenBacSi\":\"").append(lamSachChuoi(l.hoTenBacSi))
                        .append("\",\"ngayHen\":\"").append(lamSachChuoi(l.ngayHen))
                        .append("\",\"gioHen\":\"").append(lamSachChuoi(l.gioHen))
                        .append("\",\"lyDoKham\":\"").append(lamSachChuoi(l.lyDoKham))
                        .append("\",\"trangThai\":\"").append(lamSachChuoi(l.trangThai)).append("\"}");
                }
                json.append("]");
                guiPhanHoiJSON(goiTin, 200, json.toString());
            } else if (phuongThuc.equalsIgnoreCase("POST")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI", "LE_TAN", "BAC_SI")) return;
                LichHen lh = new LichHen();
                lh.maBenhNhi = doiSangSoNguyen(layGiaTri(duLieu, "maBenhNhi"));
                lh.maBacSi = doiSangSoNguyen(layGiaTri(duLieu, "maBacSi"));
                lh.ngayHen = layGiaTri(duLieu, "ngayHen");
                lh.gioHen = layGiaTri(duLieu, "gioHen");
                lh.lyDoKham = layGiaTri(duLieu, "lyDoKham");
                String kq = lichHenDAO.themLichHen(lh);
                boolean ok = "THANH_CONG".equals(kq);
                String tb = ok ? "Đặt lịch hẹn khám thành công!" : kq;
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
            } else if (phuongThuc.equalsIgnoreCase("PUT")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI", "LE_TAN", "BAC_SI")) return;
                int maLH = doiSangSoNguyen(layGiaTri(duLieu, "maLichHen"));
                String trangThaiMoi = layGiaTri(duLieu, "trangThai");
                String kq = lichHenDAO.capNhatTrangThaiLichHenCoThongBao(maLH, trangThaiMoi);
                boolean ok = "THANH_CONG".equals(kq);
                String tb = ok ? "Đã cập nhật trạng thái lịch hẹn!" : kq;
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
            } else {
                guiPhanHoiJSON(goiTin, 405, "{\"thanhCong\":false,\"thongBao\":\"Phương thức HTTP không được hỗ trợ!\"}");
            }
        });

        mayChu.createContext("/api/thuoc", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            TaiKhoan nguoiDung = xacThucDayDu(goiTin);
            if (nguoiDung == null) return;
            if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI", "BAC_SI", "LE_TAN")) return;

            String phuongThuc = goiTin.getRequestMethod();
            String duLieu = docDuLieuGuiLen(goiTin);
            if (duLieu == null) return;

            if (phuongThuc.equalsIgnoreCase("GET")) {
                List<Thuoc> ds = thuocDAO.layDanhSachThuoc();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < ds.size(); i++) {
                    Thuoc t = ds.get(i);
                    if (i > 0) json.append(",");
                    json.append("{\"maThuoc\":").append(t.maThuoc)
                        .append(",\"tenThuoc\":\"").append(lamSachChuoi(t.tenThuoc))
                        .append("\",\"donViTinh\":\"").append(lamSachChuoi(t.donViTinh))
                        .append("\",\"donGia\":").append(dinhDangTienJSON(t.donGia))
                        .append(",\"cachDungMacDinh\":\"").append(lamSachChuoi(t.cachDungMacDinh))
                        .append("\",\"soLuongTon\":").append(t.soLuongTon).append("}");
                }
                json.append("]");
                guiPhanHoiJSON(goiTin, 200, json.toString());
            } else if (phuongThuc.equalsIgnoreCase("POST")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI", "BAC_SI")) return;
                Thuoc t = new Thuoc();
                t.tenThuoc = layGiaTri(duLieu, "tenThuoc");
                t.donViTinh = layGiaTri(duLieu, "donViTinh");
                t.donGia = doiSangTienTe(layGiaTri(duLieu, "donGia"));
                t.cachDungMacDinh = layGiaTri(duLieu, "cachDungMacDinh");
                t.soLuongTon = doiSangSoNguyenKhongAm(layGiaTri(duLieu, "soLuongTon"));
                String kq = thuocDAO.themThuoc(t);
                boolean ok = "THANH_CONG".equals(kq);
                String tb = ok ? "Đã thêm thuốc nhi khoa mới vào kho!" : kq;
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
            } else if (phuongThuc.equalsIgnoreCase("PUT")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI", "BAC_SI")) return;
                Thuoc t = new Thuoc();
                t.maThuoc = doiSangSoNguyen(layGiaTri(duLieu, "maThuoc"));
                t.tenThuoc = layGiaTri(duLieu, "tenThuoc");
                t.donViTinh = layGiaTri(duLieu, "donViTinh");
                t.donGia = doiSangTienTe(layGiaTri(duLieu, "donGia"));
                t.cachDungMacDinh = layGiaTri(duLieu, "cachDungMacDinh");
                t.soLuongTon = doiSangSoNguyenKhongAm(layGiaTri(duLieu, "soLuongTon"));
                boolean ok = thuocDAO.suaThuoc(t);
                String tb = ok ? "Cập nhật thông tin thuốc thành công!" : "Không thể cập nhật thuốc (Kiểm tra tên thuốc, đơn giá > 0 và tồn kho >= 0)!";
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
            } else if (phuongThuc.equalsIgnoreCase("DELETE")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI", "BAC_SI")) return;
                int maThuoc = doiSangSoNguyen(layGiaTri(duLieu, "maThuoc"));
                boolean ok = thuocDAO.xoaThuoc(maThuoc);
                String tb = ok ? "Đã xóa thuốc khỏi danh mục!" : "Không thể xóa thuốc đã từng được kê trong toa!";
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
            } else {
                guiPhanHoiJSON(goiTin, 405, "{\"thanhCong\":false,\"thongBao\":\"Phương thức HTTP không được hỗ trợ!\"}");
            }
        });

        mayChu.createContext("/api/phieu-kham", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            TaiKhoan nguoiDung = xacThucDayDu(goiTin);
            if (nguoiDung == null) return;

            String phuongThuc = goiTin.getRequestMethod();
            String duLieu = docDuLieuGuiLen(goiTin);
            if (duLieu == null) return;

            if (phuongThuc.equalsIgnoreCase("GET")) {
                String rawMaBN = layGiaTri(duLieu, "maBenhNhi").trim();
                int maBN = doiSangSoNguyen(rawMaBN);
                if (!rawMaBN.isEmpty() && maBN <= 0) {
                    guiPhanHoiJSON(goiTin, 400, "{\"thanhCong\":false,\"thongBao\":\"Mã bệnh nhi truy vấn không hợp lệ!\"}");
                    return;
                }
                List<PhieuKham> ds;
                if ("PHU_HUYNH".equals(nguoiDung.vaiTro)) {
                    if (maBN > 0) {
                        if (!phieuKhamDAO.kiemTraBeThuocPhuHuynh(maBN, nguoiDung.tenDangNhap)) {
                            guiPhanHoiJSON(goiTin, 403, "{\"thanhCong\":false,\"thongBao\":\"Bạn không có quyền xem hồ sơ của bệnh nhi gia đình khác!\"}");
                            return;
                        }
                        ds = phieuKhamDAO.layDanhSachPhieuKham(maBN);
                    } else {
                        ds = phieuKhamDAO.layDanhSachPhieuKhamTheoSDT(nguoiDung.tenDangNhap);
                    }
                } else {
                    ds = phieuKhamDAO.layDanhSachPhieuKham(maBN);
                }
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < ds.size(); i++) {
                    PhieuKham p = ds.get(i);
                    if (i > 0) json.append(",");
                    json.append("{\"maPhieuKham\":").append(p.maPhieuKham)
                        .append(",\"maBenhNhi\":").append(p.maBenhNhi)
                        .append(",\"maBacSi\":").append(p.maBacSi)
                        .append(",\"hoTenBe\":\"").append(lamSachChuoi(p.hoTenBe))
                        .append("\",\"hoTenBacSi\":\"").append(lamSachChuoi(p.hoTenBacSi))
                        .append("\",\"ngayKham\":\"").append(lamSachChuoi(p.ngayKham))
                        .append("\",\"canNangKg\":").append(dinhDangTienJSON(p.canNangKg))
                        .append(",\"trieuChung\":\"").append(lamSachChuoi(p.trieuChung))
                        .append("\",\"chanDoan\":\"").append(lamSachChuoi(p.chanDoan))
                        .append("\",\"loiDanBacSi\":\"").append(lamSachChuoi(p.loiDanBacSi))
                        .append("\",\"tienKham\":").append(dinhDangTienJSON(p.tienKham))
                        .append(",\"tienThuoc\":").append(dinhDangTienJSON(p.tienThuoc))
                        .append(",\"tongVienPhi\":").append(dinhDangTienJSON(p.tongVienPhi))
                        .append(",\"trangThaiPhieu\":\"").append(lamSachChuoi(p.trangThaiPhieu)).append("\"}");
                }
                json.append("]");
                guiPhanHoiJSON(goiTin, 200, json.toString());
            } else if (phuongThuc.equalsIgnoreCase("POST")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "BAC_SI", "QUAN_TRI")) return;
                PhieuKham pk = new PhieuKham();
                pk.maBenhNhi = doiSangSoNguyen(layGiaTri(duLieu, "maBenhNhi"));
                pk.maBacSi = "BAC_SI".equals(nguoiDung.vaiTro)
                    ? nguoiDung.maTaiKhoan
                    : doiSangSoNguyen(layGiaTri(duLieu, "maBacSi"));
                pk.canNangKg = doiSangCanNang(layGiaTri(duLieu, "canNangKg"));
                pk.trieuChung = layGiaTri(duLieu, "trieuChung");
                pk.chanDoan = layGiaTri(duLieu, "chanDoan");
                pk.loiDanBacSi = layGiaTri(duLieu, "loiDanBacSi");
                pk.tienKham = doiSangTienTe(layGiaTri(duLieu, "tienKham"));
                int maPhieuMoi = phieuKhamDAO.taoPhieuKham(pk);
                if (maPhieuMoi > 0) {
                    guiPhanHoiJSON(goiTin, 200, "{\"thanhCong\":true,\"maPhieuKham\":" + maPhieuMoi + ",\"thongBao\":\"Đã tạo phiếu khám và lưu cân nặng thành công!\"}");
                } else if (maPhieuMoi == -1) {
                    guiPhanHoiJSON(goiTin, 400, "{\"thanhCong\":false,\"thongBao\":\"Cân nặng thực tế của bé phải lớn hơn 0 kg và nhỏ hơn hoặc bằng 200 kg!\"}");
                } else if (maPhieuMoi == -2) {
                    guiPhanHoiJSON(goiTin, 400, "{\"thanhCong\":false,\"thongBao\":\"Vui lòng nhập đầy đủ Triệu chứng lâm sàng và Kết luận chẩn đoán bệnh!\"}");
                } else if (maPhieuMoi == -3) {
                    guiPhanHoiJSON(goiTin, 400, "{\"thanhCong\":false,\"thongBao\":\"Mã bệnh nhi hoặc Mã bác sĩ khám không tồn tại trong hệ thống!\"}");
                } else if (maPhieuMoi == -4) {
                    guiPhanHoiJSON(goiTin, 400, "{\"thanhCong\":false,\"thongBao\":\"Tiền công khám không hợp lệ (chỉ nhận số từ 0 đến 9.999.999.999,99 VNĐ)!\"}");
                } else if (maPhieuMoi == -5) {
                    guiPhanHoiJSON(goiTin, 400, "{\"thanhCong\":false,\"thongBao\":\"Triệu chứng và chẩn đoán không được vượt quá 255 ký tự (lời dặn tối đa 2000 ký tự)!\"}");
                } else {
                    guiPhanHoiJSON(goiTin, 500, "{\"thanhCong\":false,\"thongBao\":\"Không thể tạo phiếu khám do lỗi Cơ sở dữ liệu!\"}");
                }
            } else if (phuongThuc.equalsIgnoreCase("PUT")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "LE_TAN", "QUAN_TRI")) return;
                int maPK = doiSangSoNguyen(layGiaTri(duLieu, "maPhieuKham"));
                boolean ok = phieuKhamDAO.thuTienVienPhi(maPK);
                String tb = ok ? "Đã xác nhận thu tiền viện phí thành công!" : "Không tìm thấy phiếu khám chờ thu tiền (Hoặc phiếu đã thanh toán trước đó)!";
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
            } else {
                guiPhanHoiJSON(goiTin, 405, "{\"thanhCong\":false,\"thongBao\":\"Phương thức HTTP không được hỗ trợ!\"}");
            }
        });

        mayChu.createContext("/api/toa-thuoc", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            TaiKhoan nguoiDung = xacThucDayDu(goiTin);
            if (nguoiDung == null) return;

            String phuongThuc = goiTin.getRequestMethod();
            String duLieu = docDuLieuGuiLen(goiTin);
            if (duLieu == null) return;

            if (phuongThuc.equalsIgnoreCase("GET")) {
                String rawMaBN = layGiaTri(duLieu, "maBenhNhi").trim();
                String rawMaPK = layGiaTri(duLieu, "maPhieuKham").trim();
                int maBN = doiSangSoNguyen(rawMaBN);
                int maPK = doiSangSoNguyen(rawMaPK);
                if ((!rawMaBN.isEmpty() && maBN <= 0) || (!rawMaPK.isEmpty() && maPK <= 0) || (maBN <= 0 && maPK <= 0)) {
                    guiPhanHoiJSON(goiTin, 400, "{\"thanhCong\":false,\"thongBao\":\"Vui lòng truyền tham số maPhieuKham hoặc maBenhNhi hợp lệ (> 0)!\"}");
                    return;
                }
                List<ToaThuoc> ds;
                if (maBN > 0) {
                    if ("PHU_HUYNH".equals(nguoiDung.vaiTro)
                        && !phieuKhamDAO.kiemTraBeThuocPhuHuynh(maBN, nguoiDung.tenDangNhap)) {
                        guiPhanHoiJSON(goiTin, 403, "{\"thanhCong\":false,\"thongBao\":\"Bạn không có quyền xem toa thuốc của bệnh nhi gia đình khác!\"}");
                        return;
                    }
                    ds = phieuKhamDAO.layToaThuocTheoBenhNhi(maBN);
                } else {
                    if ("PHU_HUYNH".equals(nguoiDung.vaiTro)
                        && !phieuKhamDAO.kiemTraPhieuThuocPhuHuynh(maPK, nguoiDung.tenDangNhap)) {
                        guiPhanHoiJSON(goiTin, 403, "{\"thanhCong\":false,\"thongBao\":\"Bạn không có quyền xem toa thuốc của bệnh nhi gia đình khác!\"}");
                        return;
                    }
                    ds = phieuKhamDAO.layToaThuocTheoPhieu(maPK);
                }
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < ds.size(); i++) {
                    ToaThuoc t = ds.get(i);
                    if (i > 0) json.append(",");
                    json.append("{\"maToaThuoc\":").append(t.maToaThuoc)
                        .append(",\"maPhieuKham\":").append(t.maPhieuKham)
                        .append(",\"maThuoc\":").append(t.maThuoc)
                        .append(",\"tenThuoc\":\"").append(lamSachChuoi(t.tenThuoc))
                        .append("\",\"donViTinh\":\"").append(lamSachChuoi(t.donViTinh))
                        .append("\",\"soLuong\":").append(t.soLuong)
                        .append(",\"donGiaLucKe\":").append(dinhDangTienJSON(t.donGiaLucKe))
                        .append(",\"lieuLuongDung\":\"").append(lamSachChuoi(t.lieuLuongDung))
                        .append("\",\"thanhTien\":").append(dinhDangTienJSON(t.thanhTien)).append("}");
                }
                json.append("]");
                guiPhanHoiJSON(goiTin, 200, json.toString());
            } else if (phuongThuc.equalsIgnoreCase("POST")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "BAC_SI", "QUAN_TRI")) return;
                ToaThuoc tt = new ToaThuoc();
                tt.maPhieuKham = doiSangSoNguyen(layGiaTri(duLieu, "maPhieuKham"));
                tt.maThuoc = doiSangSoNguyen(layGiaTri(duLieu, "maThuoc"));
                tt.soLuong = doiSangSoNguyen(layGiaTri(duLieu, "soLuong"));
                tt.lieuLuongDung = layGiaTri(duLieu, "lieuLuongDung");
                String kq = phieuKhamDAO.keThuocVaoToa(tt);
                boolean ok = "THANH_CONG".equals(kq);
                String tb = ok ? "Đã kê thuốc vào toa, trừ tồn kho và tự động cập nhật tổng viện phí!" : kq;
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
            } else if (phuongThuc.equalsIgnoreCase("DELETE")) {
                if (!kiemTraQuyen(goiTin, nguoiDung, "BAC_SI", "QUAN_TRI")) return;
                int maToaThuoc = doiSangSoNguyen(layGiaTri(duLieu, "maToaThuoc"));
                String kq = phieuKhamDAO.xoaThuocKhoiToa(maToaThuoc);
                boolean ok = "THANH_CONG".equals(kq);
                String tb = ok ? "Đã bỏ thuốc khỏi toa, hoàn lại tồn kho và cập nhật tổng viện phí!" : kq;
                guiPhanHoiJSON(goiTin, ok ? 200 : 400, "{\"thanhCong\":" + ok + ",\"thongBao\":\"" + lamSachChuoi(tb) + "\"}");
            } else {
                guiPhanHoiJSON(goiTin, 405, "{\"thanhCong\":false,\"thongBao\":\"Phương thức HTTP không được hỗ trợ!\"}");
            }
        });

        mayChu.createContext("/api/thong-ke", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            TaiKhoan nguoiDung = xacThucDayDu(goiTin);
            if (nguoiDung == null) return;
            if (!kiemTraQuyen(goiTin, nguoiDung, "QUAN_TRI", "LE_TAN")) return;
            if (!goiTin.getRequestMethod().equalsIgnoreCase("GET")) {
                guiPhanHoiJSON(goiTin, 405, "{\"thanhCong\":false,\"thongBao\":\"Phương thức HTTP không được hỗ trợ!\"}");
                return;
            }
            BigDecimal[] tk = phieuKhamDAO.thongKeDoanhThu();
            StringBuilder json = new StringBuilder();
            json.append("{\"soCaDaThu\":").append(tk[0].intValue())
                .append(",\"tongTienKham\":").append(dinhDangTienJSON(tk[1]))
                .append(",\"tongTienThuoc\":").append(dinhDangTienJSON(tk[2]))
                .append(",\"tongDoanhThu\":").append(dinhDangTienJSON(tk[3]));
            if ("QUAN_TRI".equals(nguoiDung.vaiTro)) {
                json.append(",\"soCaTichLuy\":").append(tk[4].intValue())
                    .append(",\"tongDoanhThuTichLuy\":").append(dinhDangTienJSON(tk[5]));
            }
            json.append("}");
            guiPhanHoiJSON(goiTin, 200, json.toString());
        });

        mayChu.createContext("/", (HttpExchange goiTin) -> {
            if (xuLyCORS(goiTin)) return;
            String duongDanURI = goiTin.getRequestURI().getPath();
            if (duongDanURI == null || duongDanURI.equals("/") || duongDanURI.isEmpty()) {
                duongDanURI = "/index.html";
            }
            if (duongDanURI.startsWith("/api/") || "/api".equals(duongDanURI)) {
                guiPhanHoiJSON(goiTin, 404, "{\"thanhCong\":false,\"thongBao\":\"Không tìm thấy API yêu cầu!\"}");
                return;
            }
            if (duongDanURI.contains("..") || duongDanURI.contains("\\") || !laDuongDanTinhHopLe(duongDanURI)) {
                guiPhanHoiJSON(goiTin, 403, "{\"thanhCong\":false,\"thongBao\":\"Đường dẫn không hợp lệ!\"}");
                return;
            }
            File thuMucFrontend = new File("frontend");
            if (!thuMucFrontend.exists()) thuMucFrontend = new File("../frontend");
            if (!thuMucFrontend.exists()) thuMucFrontend = new File("../../frontend");

            String gocChuan = thuMucFrontend.getCanonicalPath() + File.separator;
            File fileCanDoc = new File(thuMucFrontend, duongDanURI.substring(1));
            if (!fileCanDoc.getCanonicalPath().startsWith(gocChuan)) {
                guiPhanHoiJSON(goiTin, 403, "{\"thanhCong\":false,\"thongBao\":\"Truy cập bị từ chối!\"}");
                return;
            }

            if (fileCanDoc.exists() && fileCanDoc.isFile()) {
                String kieuNoiDung = "text/html; charset=UTF-8";
                if (duongDanURI.endsWith(".css")) kieuNoiDung = "text/css; charset=UTF-8";
                else if (duongDanURI.endsWith(".js")) kieuNoiDung = "application/javascript; charset=UTF-8";
                else if (duongDanURI.endsWith(".png")) kieuNoiDung = "image/png";
                else if (duongDanURI.endsWith(".svg")) kieuNoiDung = "image/svg+xml";
                else if (duongDanURI.endsWith(".ico")) kieuNoiDung = "image/x-icon";

                byte[] noiDungByte = Files.readAllBytes(fileCanDoc.toPath());
                goiTin.getResponseHeaders().set("Content-Type", kieuNoiDung);
                goiTin.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                goiTin.getResponseHeaders().set("X-Frame-Options", "SAMEORIGIN");
                goiTin.sendResponseHeaders(200, noiDungByte.length);
                try (OutputStream luongGhi = goiTin.getResponseBody()) {
                    luongGhi.write(noiDungByte);
                }
            } else {
                guiPhanHoiJSON(goiTin, 404, "{\"thanhCong\":false,\"thongBao\":\"Không tìm thấy đường dẫn yêu cầu!\"}");
            }
        });

        mayChu.start();
        System.out.println("=================================================================");
        System.out.println("  MÁY CHỦ BACKEND PHÒNG KHÁM NHI ĐÃ KHỞI ĐỘNG THÀNH CÔNG!        ");
        System.out.println("  Địa chỉ API & Web: http://localhost:8080                       ");
        System.out.println("=================================================================");
    }

    private static boolean laDuongDanTinhHopLe(String duongDan) {
        String thuong = duongDan.toLowerCase(Locale.ROOT);
        return thuong.endsWith(".html")
            || thuong.endsWith(".css")
            || thuong.endsWith(".js")
            || thuong.endsWith(".png")
            || thuong.endsWith(".svg")
            || thuong.endsWith(".ico");
    }

    private static String layIpNguon(HttpExchange goiTin) {
        return goiTin.getRemoteAddress() != null && goiTin.getRemoteAddress().getAddress() != null
            ? goiTin.getRemoteAddress().getAddress().getHostAddress()
            : "local";
    }

    private static String layKhoaGioiHanDangNhap(HttpExchange goiTin, String tenDangNhap) {
        return layIpNguon(goiTin) + "|" + tenDangNhap.toLowerCase(Locale.ROOT);
    }

    private static String layKhoaGioiHanIP(HttpExchange goiTin) {
        return "IP|" + layIpNguon(goiTin);
    }

    private static boolean dangBiGioiHanDangNhap(String khoa, int gioiHanToiDa) {
        BoDemThuSai boDem = BANG_THU_SAI.get(khoa);
        if (boDem == null) return false;
        synchronized (boDem) {
            long daQua = System.currentTimeMillis() - boDem.thoiDiemBatDau;
            if (daQua > CUA_SO_GIOI_HAN_DANG_NHAP_MS) {
                BANG_THU_SAI.remove(khoa);
                return false;
            }
            return boDem.soLanSai >= gioiHanToiDa;
        }
    }

    private static void ghiNhanDangNhapSai(String khoa) {
        long hienTai = System.currentTimeMillis();
        if (!BANG_THU_SAI.containsKey(khoa) && BANG_THU_SAI.size() >= GIOI_HAN_BO_DEM_THU_SAI) {
            synchronized (BANG_THU_SAI) {
                BANG_THU_SAI.entrySet().removeIf(entry -> hienTai - entry.getValue().thoiDiemBatDau > CUA_SO_GIOI_HAN_DANG_NHAP_MS);
                while (BANG_THU_SAI.size() >= GIOI_HAN_BO_DEM_THU_SAI) {
                    String khoaCuNhat = null;
                    long thoiDiemCuNhat = Long.MAX_VALUE;
                    for (Map.Entry<String, BoDemThuSai> entry : BANG_THU_SAI.entrySet()) {
                        long moc = entry.getValue().thoiDiemBatDau;
                        if (moc < thoiDiemCuNhat) {
                            thoiDiemCuNhat = moc;
                            khoaCuNhat = entry.getKey();
                        }
                    }
                    if (khoaCuNhat == null) break;
                    BANG_THU_SAI.remove(khoaCuNhat);
                }
            }
        }
        BoDemThuSai boDem = BANG_THU_SAI.computeIfAbsent(khoa, k -> {
            BoDemThuSai moi = new BoDemThuSai();
            moi.thoiDiemBatDau = hienTai;
            return moi;
        });
        synchronized (boDem) {
            if (hienTai - boDem.thoiDiemBatDau > CUA_SO_GIOI_HAN_DANG_NHAP_MS) {
                boDem.soLanSai = 1;
                boDem.thoiDiemBatDau = hienTai;
            } else {
                boDem.soLanSai++;
            }
        }
    }

    private static String taoPhienMoi(TaiKhoan tk) {
        String tokenMoi = UUID.randomUUID().toString();
        synchronized (BANG_PHIEN) {
            long hienTai = System.currentTimeMillis();
            BANG_PHIEN.entrySet().removeIf(entry -> entry.getValue().hetHanLuc <= hienTai);
            // Giới hạn tối đa 3 phiên hoạt động cho mỗi tài khoản để chặn chiếm dụng bảng phiên toàn cục
            while (true) {
                int demPhien = 0;
                String tokenCuNhat = null;
                long hetHanCuNhat = Long.MAX_VALUE;
                for (Map.Entry<String, PhienDangNhap> entry : BANG_PHIEN.entrySet()) {
                    PhienDangNhap p = entry.getValue();
                    if (p.taiKhoan.maTaiKhoan == tk.maTaiKhoan) {
                        demPhien++;
                        if (p.hetHanLuc < hetHanCuNhat) {
                            hetHanCuNhat = p.hetHanLuc;
                            tokenCuNhat = entry.getKey();
                        }
                    }
                }
                if (demPhien < SO_PHIEN_TOI_DA_MOI_TAI_KHOAN || tokenCuNhat == null) {
                    break;
                }
                BANG_PHIEN.remove(tokenCuNhat);
            }
            donPhienHetHan();
            BANG_PHIEN.put(tokenMoi, new PhienDangNhap(tk, hienTai + THOI_GIAN_PHIEN_MS));
        }
        return tokenMoi;
    }

    private static void donPhienHetHan() {
        if (BANG_PHIEN.size() < GIOI_HAN_PHIEN) {
            return;
        }
        long hienTai = System.currentTimeMillis();
        BANG_PHIEN.entrySet().removeIf(entry -> entry.getValue().hetHanLuc <= hienTai);
        while (BANG_PHIEN.size() >= GIOI_HAN_PHIEN) {
            String khoaSomNhat = null;
            long minHetHan = Long.MAX_VALUE;
            for (Map.Entry<String, PhienDangNhap> entry : BANG_PHIEN.entrySet()) {
                long han = entry.getValue().hetHanLuc;
                if (han < minHetHan) {
                    minHetHan = han;
                    khoaSomNhat = entry.getKey();
                }
            }
            if (khoaSomNhat == null) break;
            BANG_PHIEN.remove(khoaSomNhat);
        }
    }

    private static String layBearerToken(HttpExchange goiTin) {
        String headerAuth = goiTin.getRequestHeaders().getFirst("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7).trim();
        }
        return "";
    }

    public static TaiKhoan xacThucToken(HttpExchange goiTin) {
        String token = layBearerToken(goiTin);
        if (!token.isEmpty()) {
            PhienDangNhap phien = BANG_PHIEN.get(token);
            if (phien != null) {
                if (System.currentTimeMillis() <= phien.hetHanLuc) {
                    return phien.taiKhoan;
                }
                BANG_PHIEN.remove(token);
            }
        }
        guiPhanHoiJSON(goiTin, 401, "{\"thanhCong\":false,\"thongBao\":\"Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại!\"}");
        return null;
    }

    public static TaiKhoan xacThucDayDu(HttpExchange goiTin) {
        TaiKhoan nguoiDung = xacThucToken(goiTin);
        if (nguoiDung == null) {
            return null;
        }
        if (nguoiDung.phaiDoiMatKhau == 1) {
            guiPhanHoiJSON(goiTin, 403, "{\"thanhCong\":false,\"yeuCauDoiMatKhau\":true,\"thongBao\":\"Vui lòng đổi mật khẩu tạm ở lần đăng nhập đầu tiên trước khi sử dụng chức năng này!\"}");
            return null;
        }
        return nguoiDung;
    }

    public static boolean kiemTraQuyen(HttpExchange goiTin, TaiKhoan nguoiDung, String... danhSachVaiTroChoPhep) {
        for (int i = 0; i < danhSachVaiTroChoPhep.length; i++) {
            if (danhSachVaiTroChoPhep[i].equals(nguoiDung.vaiTro)) {
                return true;
            }
        }
        guiPhanHoiJSON(goiTin, 403, "{\"thanhCong\":false,\"thongBao\":\"Tài khoản [" + lamSachChuoi(nguoiDung.vaiTro) + "] không có quyền thực hiện chức năng này!\"}");
        return false;
    }

    public static boolean xuLyCORS(HttpExchange goiTin) {
        try {
            String origin = goiTin.getRequestHeaders().getFirst("Origin");
            String allowOrigin = "http://localhost:8080";
            if ("http://127.0.0.1:8080".equals(origin) || "http://localhost:8080".equals(origin)) {
                allowOrigin = origin;
            }
            goiTin.getResponseHeaders().set("Access-Control-Allow-Origin", allowOrigin);
            goiTin.getResponseHeaders().set("Vary", "Origin");
            goiTin.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            goiTin.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            if (goiTin.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                goiTin.sendResponseHeaders(204, -1);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Lỗi CORS: " + e.getMessage());
        }
        return false;
    }

    public static void guiPhanHoiJSON(HttpExchange goiTin, int maTrangThai, String chuoiJSON) {
        try {
            goiTin.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            goiTin.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            goiTin.getResponseHeaders().set("Cache-Control", "no-store, no-cache, max-age=0");
            byte[] mangByte = chuoiJSON.getBytes(StandardCharsets.UTF_8);
            goiTin.sendResponseHeaders(maTrangThai, mangByte.length);
            try (OutputStream luongGhi = goiTin.getResponseBody()) {
                luongGhi.write(mangByte);
            }
        } catch (Exception e) {
            System.err.println("Lỗi gửi phản hồi JSON: " + e.getMessage());
        }
    }

    public static String docDuLieuGuiLen(HttpExchange goiTin) {
        try {
            String thamSoURL = goiTin.getRequestURI().getRawQuery();
            ByteArrayOutputStream boDem = new ByteArrayOutputStream();
            byte[] mangTam = new byte[8192];
            int tongByte = 0;
            try (InputStream luongDoc = goiTin.getRequestBody()) {
                int soDoc;
                while ((soDoc = luongDoc.read(mangTam)) != -1) {
                    tongByte += soDoc;
                    if (tongByte > GIOI_HAN_BODY_BYTES) {
                        guiPhanHoiJSON(goiTin, 413, "{\"thanhCong\":false,\"thongBao\":\"Dữ liệu gửi lên vượt quá giới hạn cho phép (1 MB)!\"}");
                        return null;
                    }
                    boDem.write(mangTam, 0, soDoc);
                }
            }
            String duLieuBody = boDem.toString(StandardCharsets.UTF_8).trim();
            if (!duLieuBody.isEmpty()) {
                return duLieuBody;
            }
            if (thamSoURL != null) {
                return thamSoURL;
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }

    public static String layGiaTri(String duLieu, String tenTruong) {
        if (duLieu == null || duLieu.isEmpty() || tenTruong == null || tenTruong.isEmpty()) return "";
        String sach = duLieu.trim();
        if (sach.startsWith("{")) {
            String kqJSON = docTruongTuJSON(sach, tenTruong);
            return kqJSON != null ? kqJSON : "";
        }
        return layGiaTriTuForm(sach, tenTruong);
    }

    // Phân tích JSON tầng 1, bỏ qua an toàn các object {} và array [] lồng nhau
    private static String docTruongTuJSON(String json, String tenTruongCanTim) {
        int n = json.length();
        int i = 0;
        while (i < n && json.charAt(i) != '{') i++;
        i++;

        while (i < n) {
            while (i < n && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
            if (i >= n || json.charAt(i) == '}') break;
            if (json.charAt(i) != '"') break;

            int[] viTriKetThucKhoa = new int[1];
            String tenKhoa = docChuoiJSONCoEscape(json, i, viTriKetThucKhoa);
            i = viTriKetThucKhoa[0];

            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= n || json.charAt(i) != ':') break;
            i++;
            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= n) break;

            String giaTriDocDuoc = "";
            char kyTuDauGiaTri = json.charAt(i);
            if (kyTuDauGiaTri == '"') {
                int[] viTriKetThucGiaTri = new int[1];
                giaTriDocDuoc = docChuoiJSONCoEscape(json, i, viTriKetThucGiaTri);
                i = viTriKetThucGiaTri[0];
            } else if (kyTuDauGiaTri == '{' || kyTuDauGiaTri == '[') {
                int[] viTriKetThucKhoi = new int[1];
                giaTriDocDuoc = boQuaKhoiJSONLong(json, i, viTriKetThucKhoi);
                i = viTriKetThucKhoi[0];
            } else {
                int batDau = i;
                while (i < n && json.charAt(i) != ',' && json.charAt(i) != '}' && !Character.isWhitespace(json.charAt(i))) {
                    i++;
                }
                giaTriDocDuoc = json.substring(batDau, i).trim();
                if ("null".equals(giaTriDocDuoc)) giaTriDocDuoc = "";
            }

            if (tenTruongCanTim.equals(tenKhoa)) {
                return giaTriDocDuoc;
            }
        }
        return null;
    }

    private static String boQuaKhoiJSONLong(String json, int viTriBatDau, int[] viTriTiepTheo) {
        int n = json.length();
        int doSau = 0;
        int i = viTriBatDau;
        while (i < n) {
            char c = json.charAt(i);
            if (c == '"') {
                int[] sauChuoi = new int[1];
                docChuoiJSONCoEscape(json, i, sauChuoi);
                i = sauChuoi[0];
                continue;
            }
            if (c == '{' || c == '[') {
                doSau++;
            } else if (c == '}' || c == ']') {
                doSau--;
                if (doSau <= 0) {
                    viTriTiepTheo[0] = i + 1;
                    return json.substring(viTriBatDau, i + 1);
                }
            }
            i++;
        }
        viTriTiepTheo[0] = n;
        return json.substring(viTriBatDau);
    }

    private static String docChuoiJSONCoEscape(String json, int viTriMoNgoacKep, int[] viTriTiepTheo) {
        StringBuilder kq = new StringBuilder();
        int n = json.length();
        int i = viTriMoNgoacKep + 1;
        while (i < n) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char kyTuSau = json.charAt(i + 1);
                if (kyTuSau == '"' || kyTuSau == '\\' || kyTuSau == '/') kq.append(kyTuSau);
                else if (kyTuSau == 'b') kq.append('\b');
                else if (kyTuSau == 'f') kq.append('\f');
                else if (kyTuSau == 'n') kq.append('\n');
                else if (kyTuSau == 'r') kq.append('\r');
                else if (kyTuSau == 't') kq.append('\t');
                else if (kyTuSau == 'u') {
                    if (i + 5 < n) {
                        String maHex = json.substring(i + 2, i + 6);
                        if (maHex.matches("^[0-9a-fA-F]{4}$")) {
                            kq.append((char) Integer.parseInt(maHex, 16));
                            i += 6;
                            continue;
                        }
                    }
                    kq.append("\\u");
                } else kq.append(kyTuSau);
                i += 2;
            } else if (c == '"') {
                viTriTiepTheo[0] = i + 1;
                return kq.toString();
            } else {
                kq.append(c);
                i++;
            }
        }
        viTriTiepTheo[0] = n;
        return kq.toString();
    }

    private static String layGiaTriTuForm(String chuoiForm, String tenTruong) {
        if (chuoiForm == null || chuoiForm.isEmpty()) return "";
        try {
            String[] cacCap = chuoiForm.split("&");
            for (int i = 0; i < cacCap.length; i++) {
                String[] cap = cacCap[i].split("=", 2);
                if (cap.length == 2 && cap[0].trim().equals(tenTruong)) {
                    return URLDecoder.decode(cap[1].trim(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }

    public static int doiSangSoNguyen(String chuoiSo) {
        if (chuoiSo == null || chuoiSo.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(chuoiSo.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public static int doiSangSoNguyenKhongAm(String chuoiSo) {
        if (chuoiSo == null || chuoiSo.trim().isEmpty()) return -1;
        try {
            int giaTri = Integer.parseInt(chuoiSo.trim());
            return giaTri >= 0 ? giaTri : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static BigDecimal phanTichSoThapPhan(String chuoiSo, BigDecimal gioiHanToiDa) {
        if (chuoiSo == null || chuoiSo.trim().isEmpty()) {
            return null;
        }
        String sach = chuoiSo.trim();
        // Chặn định dạng số mũ e/E (ví dụ "1e-5000000") trước khi khởi tạo BigDecimal nhằm ngăn CPU DoS
        if (!sach.matches("^\\d{1,10}(\\.\\d{1,2})?$")) {
            return new BigDecimal("-1");
        }
        try {
            BigDecimal giaTri = new BigDecimal(sach);
            if (giaTri.compareTo(BigDecimal.ZERO) < 0 || giaTri.compareTo(gioiHanToiDa) > 0) {
                return new BigDecimal("-1");
            }
            return giaTri.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return new BigDecimal("-1");
        }
    }

    /**
     * Phân tích chuỗi tiền tệ sang BigDecimal (tối đa DECIMAL(12,2)). Trả về null nếu rỗng/bỏ trống, hoặc -1 nếu âm/lỗi định dạng.
     */
    public static BigDecimal doiSangTienTe(String chuoiSo) {
        return phanTichSoThapPhan(chuoiSo, GIOI_HAN_TIEN_TE);
    }

    public static BigDecimal doiSangCanNang(String chuoiSo) {
        return phanTichSoThapPhan(chuoiSo, GIOI_HAN_CAN_NANG);
    }

    public static String dinhDangTienJSON(BigDecimal tien) {
        if (tien == null) return "0";
        if (tien.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            return tien.setScale(0, RoundingMode.DOWN).toPlainString();
        }
        return tien.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String lamSachChuoi(String chuoiGoc) {
        if (chuoiGoc == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chuoiGoc.length(); i++) {
            char c = chuoiGoc.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '"') sb.append("\\\"");
            else if (c == '\b') sb.append("\\b");
            else if (c == '\f') sb.append("\\f");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) continue;
            else sb.append(c);
        }
        return sb.toString();
    }
}
