package model;

public class TaiKhoan {
    public int maTaiKhoan;
    public String tenDangNhap;
    public String matKhau;
    public String hoTen;
    public String vaiTro;
    public int soLanSai;
    public volatile int trangThaiKhoa;
    public String khoaDenLuc;
    public volatile int phaiDoiMatKhau;

    public TaiKhoan() {
    }

    public TaiKhoan(int maTaiKhoan, String tenDangNhap, String matKhau, String hoTen, String vaiTro, int soLanSai, int trangThaiKhoa) {
        this(maTaiKhoan, tenDangNhap, matKhau, hoTen, vaiTro, soLanSai, trangThaiKhoa, null, 0);
    }

    public TaiKhoan(int maTaiKhoan, String tenDangNhap, String matKhau, String hoTen, String vaiTro,
                    int soLanSai, int trangThaiKhoa, String khoaDenLuc, int phaiDoiMatKhau) {
        this.maTaiKhoan = maTaiKhoan;
        this.tenDangNhap = tenDangNhap;
        this.matKhau = matKhau;
        this.hoTen = hoTen;
        this.vaiTro = vaiTro;
        this.soLanSai = soLanSai;
        this.trangThaiKhoa = trangThaiKhoa;
        this.khoaDenLuc = khoaDenLuc;
        this.phaiDoiMatKhau = phaiDoiMatKhau;
    }
}
