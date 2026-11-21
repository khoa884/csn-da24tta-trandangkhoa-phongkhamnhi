package model;

import java.math.BigDecimal;

public class Thuoc {
    public int maThuoc;
    public String tenThuoc;
    public String donViTinh;
    public BigDecimal donGia;
    public String cachDungMacDinh;
    public int soLuongTon;

    public Thuoc() {
    }

    public Thuoc(int maThuoc, String tenThuoc, String donViTinh, BigDecimal donGia, String cachDungMacDinh, int soLuongTon) {
        this.maThuoc = maThuoc;
        this.tenThuoc = tenThuoc;
        this.donViTinh = donViTinh;
        this.donGia = donGia;
        this.cachDungMacDinh = cachDungMacDinh;
        this.soLuongTon = soLuongTon;
    }
}
