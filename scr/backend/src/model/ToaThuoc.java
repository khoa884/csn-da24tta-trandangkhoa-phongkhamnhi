package model;

import java.math.BigDecimal;

public class ToaThuoc {
    public int maToaThuoc;
    public int maPhieuKham;
    public int maThuoc;
    public int soLuong;
    public BigDecimal donGiaLucKe;
    public String lieuLuongDung;
    public BigDecimal thanhTien;
    public String tenThuoc;
    public String donViTinh;

    public ToaThuoc() {
    }

    public ToaThuoc(int maToaThuoc, int maPhieuKham, int maThuoc, int soLuong, BigDecimal donGiaLucKe, String lieuLuongDung, BigDecimal thanhTien) {
        this.maToaThuoc = maToaThuoc;
        this.maPhieuKham = maPhieuKham;
        this.maThuoc = maThuoc;
        this.soLuong = soLuong;
        this.donGiaLucKe = donGiaLucKe;
        this.lieuLuongDung = lieuLuongDung;
        this.thanhTien = thanhTien;
    }
}
