package config;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Cung cấp kết nối JDBC tới MySQL và hàm băm mật khẩu chuẩn PBKDF2WithHmacSHA256.
 */
public class KetNoiCSDL {

    private static final String THUAT_TOAN_PBKDF2 = "PBKDF2WithHmacSHA256";
    private static final int SO_VONG_LAP_PBKDF2 = 600_000;
    private static final int DO_DAI_MUOI_BYTE = 16;
    private static final int DO_DAI_KHOA_BIT = 256;
    private static final SecureRandom NGUON_NGAU_NHIEN = new SecureRandom();
    private static final String KY_TU_MAT_KHAU_TAM = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    public KetNoiCSDL() {
    }

    public static Connection layKetNoi() {
        Connection ketNoi = null;
        try {
            Properties thuocTinh = new Properties();
            File fileCauHinh = new File("config/database.properties");
            if (!fileCauHinh.exists()) {
                fileCauHinh = new File("../config/database.properties");
            }
            if (!fileCauHinh.exists()) {
                fileCauHinh = new File("../../config/database.properties");
            }
            if (fileCauHinh.exists()) {
                try (FileInputStream luongDocFile = new FileInputStream(fileCauHinh)) {
                    thuocTinh.load(luongDocFile);
                }
            }

            // Ưu tiên biến môi trường nếu có, sau đó mới đọc scr/config/database.properties
            String duongDanCSDL = docBienMoiTruongHoacCauHinh("DB_URL", thuocTinh, "db.url");
            String taiKhoanCSDL = docBienMoiTruongHoacCauHinh("DB_USER", thuocTinh, "db.user");
            String matKhauCSDL = docBienMoiTruongHoacCauHinh("DB_PASSWORD", thuocTinh, "db.password");

            Class.forName("com.mysql.cj.jdbc.Driver");
            ketNoi = DriverManager.getConnection(duongDanCSDL, taiKhoanCSDL, matKhauCSDL == null ? "" : matKhauCSDL);
        } catch (Exception loi) {
            System.err.println("Lỗi kết nối Cơ sở dữ liệu: " + loi.getMessage());
        }
        return ketNoi;
    }

    private static String docBienMoiTruongHoacCauHinh(String tenBien, Properties thuocTinh, String khoaCauHinh) {
        String giaTriEnv = System.getenv(tenBien);
        if (giaTriEnv != null) {
            return giaTriEnv;
        }
        return thuocTinh.getProperty(khoaCauHinh);
    }

    /**
     * Tạo chuỗi băm PBKDF2WithHmacSHA256 kèm salt ngẫu nhiên 16 byte (chuẩn OWASP).
     */
    public static String maHoaMatKhau(String matKhauGoc) {
        if (matKhauGoc == null || matKhauGoc.isEmpty()) {
            return "";
        }
        try {
            byte[] muoi = new byte[DO_DAI_MUOI_BYTE];
            NGUON_NGAU_NHIEN.nextBytes(muoi);
            byte[] bam = tinhPBKDF2(matKhauGoc.toCharArray(), muoi, SO_VONG_LAP_PBKDF2, DO_DAI_KHOA_BIT);
            Base64.Encoder maHoaB64 = Base64.getEncoder();
            return "pbkdf2_sha256$" + SO_VONG_LAP_PBKDF2 + "$"
                 + maHoaB64.encodeToString(muoi) + "$" + maHoaB64.encodeToString(bam);
        } catch (Exception loi) {
            throw new IllegalStateException("Không thể mã hóa mật khẩu: " + loi.getMessage(), loi);
        }
    }

    /**
     * Sinh mật khẩu tạm ngẫu nhiên an toàn bằng SecureRandom (loại bỏ ký tự dễ nhầm O/0, I/l/1).
     */
    public static String taoMatKhauNgauNhien(int doDai) {
        int doDaiChuan = Math.max(8, doDai);
        StringBuilder sb = new StringBuilder(doDaiChuan);
        for (int i = 0; i < doDaiChuan; i++) {
            int viTri = NGUON_NGAU_NHIEN.nextInt(KY_TU_MAT_KHAU_TAM.length());
            sb.append(KY_TU_MAT_KHAU_TAM.charAt(viTri));
        }
        return sb.toString();
    }

    public static boolean laHashCuSHA256(String matKhauDaLuu) {
        return matKhauDaLuu != null && !matKhauDaLuu.startsWith("pbkdf2_sha256$");
    }

    /**
     * Đối chiếu mật khẩu theo hằng thời gian, hỗ trợ cả PBKDF2 mới và SHA-256 cũ để chuyển đổi mượt.
     */
    public static boolean kiemTraMatKhau(String matKhauNhap, String matKhauDaLuu) {
        if (matKhauNhap == null || matKhauDaLuu == null || matKhauDaLuu.isEmpty()) {
            return false;
        }
        if (matKhauDaLuu.startsWith("pbkdf2_sha256$")) {
            try {
                String[] phan = matKhauDaLuu.split("\\$");
                if (phan.length != 4) {
                    return false;
                }
                int soVong = Integer.parseInt(phan[1]);
                if (soVong < 10_000) {
                    return false;
                }
                byte[] muoi = Base64.getDecoder().decode(phan[2]);
                byte[] bamLuu = Base64.getDecoder().decode(phan[3]);
                byte[] bamTinh = tinhPBKDF2(matKhauNhap.toCharArray(), muoi, soVong, bamLuu.length * 8);
                return MessageDigest.isEqual(bamLuu, bamTinh);
            } catch (Exception loi) {
                return false;
            }
        }
        String bamCu = maHoaSHA256(matKhauNhap);
        if (bamCu.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
            bamCu.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
            matKhauDaLuu.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static byte[] tinhPBKDF2(char[] matKhau, byte[] muoi, int soVong, int doDaiBit) throws Exception {
        PBEKeySpec thongSo = new PBEKeySpec(matKhau, muoi, soVong, doDaiBit);
        try {
            SecretKeyFactory boSinh = SecretKeyFactory.getInstance(THUAT_TOAN_PBKDF2);
            return boSinh.generateSecret(thongSo).getEncoded();
        } finally {
            thongSo.clearPassword();
        }
    }

    public static String maHoaSHA256(String matKhauGoc) {
        if (matKhauGoc == null) {
            return "";
        }
        try {
            MessageDigest boBam = MessageDigest.getInstance("SHA-256");
            byte[] mangByteDaBam = boBam.digest(matKhauGoc.getBytes(StandardCharsets.UTF_8));
            StringBuilder chuoiKetQua = new StringBuilder();
            for (int i = 0; i < mangByteDaBam.length; i++) {
                String kyTuHex = Integer.toHexString(0xff & mangByteDaBam[i]);
                if (kyTuHex.length() == 1) {
                    chuoiKetQua.append('0');
                }
                chuoiKetQua.append(kyTuHex);
            }
            return chuoiKetQua.toString();
        } catch (Exception loi) {
            System.err.println("Lỗi mã hóa SHA-256: " + loi.getMessage());
            return "";
        }
    }

    public static void main(String[] args) {
        System.out.println("Kiểm tra kết nối MySQL (phong_kham_nhi)...");
        try (Connection thuKetNoi = layKetNoi()) {
            if (thuKetNoi != null) {
                System.out.println("Kết nối thành công tới phong_kham_nhi.");
                String bamPBKDF2 = maHoaMatKhau("123456");
                System.out.println("PBKDF2 test match: " + kiemTraMatKhau("123456", bamPBKDF2));
            } else {
                System.out.println("Kết nối thất bại. Kiểm tra dịch vụ MySQL và scr/config/database.properties.");
            }
        } catch (Exception loi) {
            System.err.println("Lỗi kiểm tra kết nối: " + loi.getMessage());
        }
    }
}
