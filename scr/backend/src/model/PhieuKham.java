package model;

import java.math.BigDecimal;

public class PhieuKham {
    public int maPhieuKham;
    public int maBenhNhi;
    public int maBacSi;
    public String ngayKham;
    public BigDecimal canNangKg;
    public String trieuChung;
    public String chanDoan;
    public String loiDanBacSi;
    public BigDecimal tienKham;
    public BigDecimal tienThuoc;
    public BigDecimal tongVienPhi;
    public String trangThaiPhieu;
    public String hoTenBe;
    public String hoTenBacSi;

    public PhieuKham() {
    }

    public PhieuKham(int maPhieuKham, int maBenhNhi, int maBacSi, String ngayKham, BigDecimal canNangKg, String trieuChung, String chanDoan, String loiDanBacSi, BigDecimal tienKham, BigDecimal tienThuoc, BigDecimal tongVienPhi, String trangThaiPhieu) {
        this.maPhieuKham = maPhieuKham;
        this.maBenhNhi = maBenhNhi;
        this.maBacSi = maBacSi;
        this.ngayKham = ngayKham;
        this.canNangKg = canNangKg;
        this.trieuChung = trieuChung;
        this.chanDoan = chanDoan;
        this.loiDanBacSi = loiDanBacSi;
        this.tienKham = tienKham;
        this.tienThuoc = tienThuoc;
        this.tongVienPhi = tongVienPhi;
        this.trangThaiPhieu = trangThaiPhieu;
    }
}
