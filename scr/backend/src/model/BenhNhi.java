package model;

public class BenhNhi {
    public int maBenhNhi;
    public String hoTenBe;
    public String ngaySinh;
    public String gioiTinh;
    public String tienSuBenh;
    public int maPhuHuynh;
    // Thuộc tính ghép từ bảng phu_huynh khi trả về API
    public String hoTenPhuHuynh;
    public String soDienThoai;
    public String diaChi;

    public BenhNhi() {
    }

    public BenhNhi(int maBenhNhi, String hoTenBe, String ngaySinh, String gioiTinh, String tienSuBenh, int maPhuHuynh) {
        this.maBenhNhi = maBenhNhi;
        this.hoTenBe = hoTenBe;
        this.ngaySinh = ngaySinh;
        this.gioiTinh = gioiTinh;
        this.tienSuBenh = tienSuBenh;
        this.maPhuHuynh = maPhuHuynh;
    }
}
