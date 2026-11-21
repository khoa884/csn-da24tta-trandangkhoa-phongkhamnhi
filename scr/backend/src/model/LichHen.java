package model;

public class LichHen {
    public int maLichHen;
    public int maBenhNhi;
    public int maBacSi;
    public String ngayHen;
    public String gioHen;
    public String lyDoKham;
    public String trangThai;
    // Thuộc tính ghép từ benh_nhi và tai_khoan khi hiển thị danh sách
    public String hoTenBe;
    public String hoTenBacSi;

    public LichHen() {
    }

    public LichHen(int maLichHen, int maBenhNhi, int maBacSi, String ngayHen, String gioHen, String lyDoKham, String trangThai) {
        this.maLichHen = maLichHen;
        this.maBenhNhi = maBenhNhi;
        this.maBacSi = maBacSi;
        this.ngayHen = ngayHen;
        this.gioHen = gioHen;
        this.lyDoKham = lyDoKham;
        this.trangThai = trangThai;
    }
}
