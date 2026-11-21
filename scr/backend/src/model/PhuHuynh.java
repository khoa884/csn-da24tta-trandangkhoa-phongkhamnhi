package model;

public class PhuHuynh {
    public int maPhuHuynh;
    public String hoTenPhuHuynh;
    public String soDienThoai;
    public String diaChi;
    public int maTaiKhoan;

    public PhuHuynh() {
    }

    public PhuHuynh(int maPhuHuynh, String hoTenPhuHuynh, String soDienThoai, String diaChi, int maTaiKhoan) {
        this.maPhuHuynh = maPhuHuynh;
        this.hoTenPhuHuynh = hoTenPhuHuynh;
        this.soDienThoai = soDienThoai;
        this.diaChi = diaChi;
        this.maTaiKhoan = maTaiKhoan;
    }
}
