const API_GOC = window.location.origin.startsWith("http")
    ? window.location.origin + "/api"
    : "http://localhost:8080/api";
let nguoiDungHienTai = null;
let tokenPhien = "";
let danhSachBenhNhiTam = [];
let danhSachBenhNhiDayDu = [];
let danhSachThuocTam = [];
let danhSachPhieuKhamTam = [];
let boHenTimKiem = null;
let maYeuCauTimKiem = 0;

// Làm sạch chuỗi trước khi ghép vào innerHTML để chống Stored XSS
function escapeHTML(chuoi) {
    if (chuoi === null || chuoi === undefined) return "";
    return String(chuoi)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function taoHeaderAuth() {
    return {
        "Content-Type": "application/json; charset=UTF-8",
        "Authorization": "Bearer " + tokenPhien
    };
}

function hienThongBao(noiDung, loai) {
    let hop = document.getElementById("hop-thong-bao-noi");
    if (!hop) {
        alert(noiDung);
        return;
    }
    let the = document.createElement("div");
    the.className = "thong-bao-noi" + (loai ? " " + loai : "");
    the.innerText = noiDung;
    hop.appendChild(the);
    setTimeout(function() {
        if (the.parentNode) the.parentNode.removeChild(the);
    }, 3500);
}

function kiemTraLoiPhien(phanHoi) {
    if (!nguoiDungHienTai) return true;
    if (phanHoi.status !== 401) return false;
    let tb = "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.";
    hienThongBao(tb, "loi");
    dangXuat();
    return true;
}

// =========================================================================
// ĐĂNG NHẬP, PHÂN QUYỀN RBAC & ĐIỀU HƯỚNG TAB
// =========================================================================

async function xuLyDangNhap() {
    let tenDangNhap = document.getElementById("o-ten-dang-nhap").value.trim();
    let matKhau = document.getElementById("o-mat-khau").value;
    let theThongBao = document.getElementById("thong-bao-dang-nhap");

    if (tenDangNhap === "" || matKhau.trim() === "") {
        theThongBao.innerText = "Vui lòng nhập đầy đủ tên tài khoản và mật khẩu.";
        return;
    }

    try {
        let phanHoi = await fetch(API_GOC + "/dang-nhap", {
            method: "POST",
            headers: { "Content-Type": "application/json; charset=UTF-8" },
            body: JSON.stringify({ tenDangNhap: tenDangNhap, matKhau: matKhau })
        });
        let ketQua = await phanHoi.json();

        if (ketQua.thanhCong === true) {
            if (ketQua.vaiTro === "PHU_HUYNH") {
                if (ketQua.token) {
                    try {
                        await fetch(API_GOC + "/dang-xuat", {
                            method: "POST",
                            headers: { "Authorization": "Bearer " + ketQua.token }
                        });
                    } catch (loi) {
                        // Bỏ qua lỗi mạng khi thu hồi phiên đăng nhập nhầm cổng
                    }
                }
                theThongBao.innerText = "Tài khoản Phụ huynh vui lòng đăng nhập tại Cổng tra cứu Phụ huynh.";
                return;
            }
            nguoiDungHienTai = ketQua;
            tokenPhien = ketQua.token || "";
            theThongBao.innerText = "";
            document.getElementById("man-hinh-dang-nhap").classList.add("an-di");
            document.getElementById("man-hinh-chinh").classList.remove("an-di");
            document.getElementById("nhan-nguoi-dung").innerText = ketQua.hoTen + " (" + ketQua.vaiTro + ")";
            phanQuyenGiaoDienTheoVaiTro(ketQua.vaiTro);
            if (ketQua.phaiDoiMatKhau === true) {
                datTrangThaiBatBuocDoiMatKhau(true);
                chuyenTab(5);
                let oMkCu = document.getElementById("mk-cu");
                if (oMkCu) oMkCu.focus();
                hienThongBao("Vui lòng đổi mật khẩu tạm trước khi sử dụng các chức năng khác.", "loi");
            } else {
                datTrangThaiBatBuocDoiMatKhau(false);
                chuyenTab(1);
                khoiTaoDuLieuBanDau();
            }
        } else {
            theThongBao.innerText = ketQua.thongBao;
        }
    } catch (loi) {
        theThongBao.innerText = "Không kết nối được máy chủ Java (cổng 8080). Hãy chạy chay_he_thong.bat trước.";
    }
}

function phanQuyenGiaoDienTheoVaiTro(vaiTro) {
    let laQuanTri = vaiTro === "QUAN_TRI";
    let coQuyenKham = laQuanTri || vaiTro === "BAC_SI";
    let coQuyenThongKe = laQuanTri || vaiTro === "LE_TAN";

    let nhomChiQuanTri = [
        document.getElementById("khung-quan-ly-tk-admin"),
        document.getElementById("nhom-pk-bac-si"),
        document.getElementById("o-tk-so-ca-tich-luy"),
        document.getElementById("o-tk-tong-tich-luy")
    ];
    for (let i = 0; i < nhomChiQuanTri.length; i++) {
        if (nhomChiQuanTri[i]) {
            nhomChiQuanTri[i].classList.toggle("an-di", !laQuanTri);
        }
    }

    let khungThongKe = document.getElementById("khung-thong-ke-doanh-thu");
    if (khungThongKe) {
        khungThongKe.classList.toggle("an-di", !coQuyenThongKe);
    }

    // Ẩn cảnh báo dị ứng, biểu mẫu khám kê toa và nhập thuốc đối với Lễ tân
    let nhomChuyenMon = [
        document.getElementById("hop-canh-bao-benh-su"),
        document.getElementById("khung-buoc-1-bac-si"),
        document.getElementById("khung-buoc-2-bac-si"),
        document.getElementById("khung-nhap-thuoc")
    ];
    for (let i = 0; i < nhomChuyenMon.length; i++) {
        if (nhomChuyenMon[i]) {
            nhomChuyenMon[i].classList.toggle("an-di", !coQuyenKham);
        }
    }
}

function datTrangThaiBatBuocDoiMatKhau(batBuoc) {
    for (let i = 1; i <= 4; i++) {
        let nut = document.getElementById("nut-tab-" + i);
        if (nut) {
            nut.disabled = batBuoc;
            nut.classList.toggle("an-di", batBuoc);
        }
    }
    let khuThongKe = document.getElementById("khung-thong-ke-doanh-thu");
    if (khuThongKe) {
        let coQuyenThongKe = nguoiDungHienTai && (nguoiDungHienTai.vaiTro === "QUAN_TRI" || nguoiDungHienTai.vaiTro === "LE_TAN");
        khuThongKe.classList.toggle("an-di", batBuoc || !coQuyenThongKe);
    }
    let khungAdmin = document.getElementById("khung-quan-ly-tk-admin");
    if (khungAdmin && batBuoc) {
        khungAdmin.classList.add("an-di");
    }
}

async function dangXuat() {
    if (boHenTimKiem) clearTimeout(boHenTimKiem);
    boHenTimKiem = null;
    ++maYeuCauTimKiem;
    let tokenCanHuy = tokenPhien;
    nguoiDungHienTai = null;
    tokenPhien = "";
    danhSachBenhNhiTam = [];
    danhSachBenhNhiDayDu = [];
    danhSachThuocTam = [];
    danhSachPhieuKhamTam = [];
    if (tokenCanHuy) {
        try {
            await fetch(API_GOC + "/dang-xuat", {
                method: "POST",
                headers: { "Authorization": "Bearer " + tokenCanHuy }
            });
        } catch (loi) {
            // Bỏ qua lỗi mạng khi đăng xuất khỏi giao diện
        }
    }
    xoaTrangFormBenhNhi();
    xoaTrangFormThuoc();
    ["o-mat-khau", "o-tim-benh-nhi", "lh-ly-do", "pk-trieu-chung", "pk-chan-doan",
     "pk-loi-dan", "tt-lieu-luong", "mk-cu", "mk-moi", "mk-xac-nhan"].forEach(function(id) {
        document.getElementById(id).value = "";
    });
    document.getElementById("lh-gio-hen").value = "09:00";
    document.getElementById("pk-can-nang").value = "14.5";
    document.getElementById("pk-tien-kham").value = "100000";
    document.getElementById("tt-so-luong").value = "10";
    document.getElementById("thong-bao-dang-nhap").innerText = "";
    document.getElementById("nhan-nguoi-dung").innerText = "";
    document.getElementById("tk-so-ca").innerText = "0 ca";
    document.getElementById("tk-so-ca-tich-luy").innerText = "0 ca";
    ["tk-tien-kham", "tk-tien-thuoc", "tk-tong-cong", "tk-tong-tich-luy"].forEach(function(id) {
        document.getElementById(id).innerText = "0 đ";
    });
    [
        "bang-benh-nhi", "bang-lich-hen", "bang-phieu-kham", "bang-thuoc", "bang-tai-khoan",
        "lh-ma-benh-nhi", "lh-ma-bac-si", "pk-ma-benh-nhi", "pk-ma-bac-si",
        "tt-ma-phieu", "tt-ma-thuoc", "noi-dung-phieu-in"
    ].forEach(function(id) {
        document.getElementById(id).innerHTML = "";
    });
    document.getElementById("hop-canh-bao-benh-su").innerText =
        "Chọn bệnh nhi bên dưới để kiểm tra tiền sử bệnh lý và cảnh báo dị ứng thuốc trước khi kê toa.";
    dongKhungIn();
    anHopCapTaiKhoanMoi();
    datTrangThaiBatBuocDoiMatKhau(false);
    document.getElementById("man-hinh-chinh").classList.add("an-di");
    document.getElementById("man-hinh-dang-nhap").classList.remove("an-di");
}

function chuyenTab(soTab) {
    if (nguoiDungHienTai && nguoiDungHienTai.phaiDoiMatKhau === true && soTab !== 5) return;
    dongKhungIn();
    document.getElementById("noi-dung-phieu-in").innerHTML = "";
    for (let i = 1; i <= 5; i++) {
        document.getElementById("tab-" + i).classList.add("an-di");
        document.getElementById("nut-tab-" + i).classList.remove("dang-chon");
    }
    document.getElementById("tab-" + soTab).classList.remove("an-di");
    document.getElementById("nut-tab-" + soTab).classList.add("dang-chon");
    if (soTab === 5 && (!nguoiDungHienTai || nguoiDungHienTai.phaiDoiMatKhau !== true)) {
        taiThongKeVaTaiKhoan();
    }
}

function khoiTaoDuLieuBanDau() {
    let d = new Date();
    let homNay = d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
    let gioiHan18Nam = (d.getFullYear() - 18) + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
    let oNgaySinh = document.getElementById("bn-ngay-sinh");
    if (oNgaySinh) {
        oNgaySinh.min = gioiHan18Nam;
        oNgaySinh.max = homNay;
    }
    let oNgayHen = document.getElementById("lh-ngay-hen");
    if (oNgayHen) {
        oNgayHen.min = homNay;
        oNgayHen.value = homNay;
    }
    taiDanhSachBenhNhi();
    taiDanhSachThuoc();
    taiDanhSachLichHen();
    taiDanhSachPhieuKham();
    taiThongKeVaTaiKhoan();
}

// =========================================================================
// HỒ SƠ BỆNH NHI & PHỤ HUYNH (1 SĐT - NHIỀU BÉ)
// =========================================================================

function timKiemBenhNhiDebounce() {
    if (boHenTimKiem) clearTimeout(boHenTimKiem);
    boHenTimKiem = setTimeout(function() {
        taiDanhSachBenhNhi(false);
    }, 250);
}

function coCanhBaoTienSu(tienSu) {
    let ts = String(tienSu || "").trim().toLowerCase();
    if (ts === "" || ts === "chưa ghi nhận" || ts === "không có" || ts === "không") return false;
    if (ts.startsWith("không có dị ứng thuốc") || ts.startsWith("không dị ứng")) return false;
    return true;
}

async function taiDanhSachBenhNhi(capNhatDanhSachChon) {
    if (capNhatDanhSachChon === undefined) capNhatDanhSachChon = true;
    let maLuot = ++maYeuCauTimKiem;
    try {
        let tuKhoa = document.getElementById("o-tim-benh-nhi").value.trim();
        let phanHoi = await fetch(API_GOC + "/benh-nhi?tuKhoa=" + encodeURIComponent(tuKhoa), { headers: taoHeaderAuth() });
        if (kiemTraLoiPhien(phanHoi)) return;
        let ketQua = await phanHoi.json();
        if (maLuot !== maYeuCauTimKiem) return;

        danhSachBenhNhiTam = ketQua;
        let bangHTML = "";

        let laQuanTri = nguoiDungHienTai && nguoiDungHienTai.vaiTro === "QUAN_TRI";
        let coQuyenKham = nguoiDungHienTai && (nguoiDungHienTai.vaiTro === "BAC_SI" || nguoiDungHienTai.vaiTro === "QUAN_TRI");
        for (let i = 0; i < danhSachBenhNhiTam.length; i++) {
            let b = danhSachBenhNhiTam[i];
            let nutKham = coQuyenKham
                ? "<button class='nut-bam nut-nho nut-xanh-la' onclick='khamNgayMotNutBam(" + b.maBenhNhi + ")'>Khám bệnh</button>"
                : "";
            let nutXoa = laQuanTri
                ? "<button class='nut-bam nut-nho nut-do' onclick='xoaBenhNhi(" + b.maBenhNhi + ")'>Xóa</button>"
                : "";
            let lopTienSu = coCanhBaoTienSu(b.tienSuBenh) ? " class='chu-canh-bao'" : "";
            bangHTML += "<tr>"
                     + "<td><b>#" + b.maBenhNhi + "</b></td>"
                     + "<td><b>" + escapeHTML(b.hoTenBe) + "</b></td>"
                     + "<td>" + escapeHTML(b.ngaySinh) + "</td>"
                     + "<td>" + escapeHTML(b.gioiTinh) + "</td>"
                     + "<td>" + escapeHTML(b.hoTenPhuHuynh) + "<span class='chu-phu chu-nhan'>" + escapeHTML(b.soDienThoai) + "</span></td>"
                     + "<td" + lopTienSu + ">" + escapeHTML(b.tienSuBenh || "Chưa ghi nhận") + "</td>"
                     + "<td><div class='o-nhom-nut'>"
                     + nutKham
                     + "<button class='nut-bam nut-nho nut-vang' onclick='chonBenhNhiDeSua(" + b.maBenhNhi + ")'>Sửa</button>"
                     + nutXoa
                     + "</div></td></tr>";
        }
        document.getElementById("bang-benh-nhi").innerHTML = bangHTML;

        if (capNhatDanhSachChon || tuKhoa === "") {
            if (tuKhoa === "") {
                danhSachBenhNhiDayDu = danhSachBenhNhiTam;
            } else {
                let phDayDu = await fetch(API_GOC + "/benh-nhi?tuKhoa=", { headers: taoHeaderAuth() });
                if (phDayDu.ok) danhSachBenhNhiDayDu = await phDayDu.json();
            }
            let oChonHTML = "";
            for (let i = 0; i < danhSachBenhNhiDayDu.length; i++) {
                let b = danhSachBenhNhiDayDu[i];
                oChonHTML += "<option value='" + b.maBenhNhi + "'>#" + b.maBenhNhi + " - " + escapeHTML(b.hoTenBe) + " (" + escapeHTML(b.soDienThoai) + ")</option>";
            }
            document.getElementById("lh-ma-benh-nhi").innerHTML = oChonHTML;
            document.getElementById("pk-ma-benh-nhi").innerHTML = oChonHTML;
            hienThiCanhBaoBenhSu();
        }
    } catch (loi) {
        console.log("Lỗi tải danh sách bệnh nhi: " + loi);
    }
}

function anHopCapTaiKhoanMoi() {
    let hop = document.getElementById("hop-cap-tk-phu-huynh");
    if (hop) {
        hop.innerHTML = "";
        hop.classList.add("an-di");
    }
}

async function themMoiBenhNhi() {
    let duLieuGui = {
        hoTenBe: document.getElementById("bn-ten-be").value.trim(),
        ngaySinh: document.getElementById("bn-ngay-sinh").value,
        gioiTinh: document.getElementById("bn-gioi-tinh").value,
        hoTenPhuHuynh: document.getElementById("bn-ten-ph").value.trim(),
        soDienThoai: document.getElementById("bn-sdt").value.trim(),
        diaChi: document.getElementById("bn-dia-chi").value.trim(),
        tienSuBenh: document.getElementById("bn-tien-su").value.trim()
    };

    if (duLieuGui.hoTenBe === "" || duLieuGui.ngaySinh === "" || duLieuGui.hoTenPhuHuynh === "" || duLieuGui.soDienThoai === "") {
        hienThongBao("Vui lòng nhập đầy đủ họ tên bé, ngày sinh, họ tên phụ huynh và số điện thoại.", "loi");
        return;
    }

    try {
        let phanHoi = await fetch(API_GOC + "/benh-nhi", {
            method: "POST",
            headers: taoHeaderAuth(),
            body: JSON.stringify(duLieuGui)
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        if (kq.thanhCong) {
            if (kq.taiKhoanMoi && kq.taiKhoanMoi.matKhauTam) {
                let hop = document.getElementById("hop-cap-tk-phu-huynh");
                if (hop) {
                    hop.innerHTML =
                        "<div><b>Tài khoản Cổng phụ huynh mới (chỉ hiển thị một lần):</b> "
                        + "SĐT đăng nhập: <code>" + escapeHTML(kq.taiKhoanMoi.tenDangNhap) + "</code> | "
                        + "Mật khẩu tạm: <code>" + escapeHTML(kq.taiKhoanMoi.matKhauTam) + "</code>. "
                        + "Nhắc phụ huynh đổi mật khẩu ở lần đăng nhập đầu tiên.</div>"
                        + "<button class='nut-bam nut-nho nut-phu' onclick='anHopCapTaiKhoanMoi()'>Đã ghi nhận</button>";
                    hop.classList.remove("an-di");
                }
            } else {
                anHopCapTaiKhoanMoi();
            }
            xoaTrangFormBenhNhi();
            taiDanhSachBenhNhi();
            taiThongKeVaTaiKhoan();
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối máy chủ khi thêm bệnh nhi.", "loi");
    }
}

function chonBenhNhiDeSua(maBenhNhi) {
    for (let i = 0; i < danhSachBenhNhiTam.length; i++) {
        let b = danhSachBenhNhiTam[i];
        if (b.maBenhNhi === maBenhNhi) {
            document.getElementById("bn-ma").value = b.maBenhNhi;
            document.getElementById("bn-ten-be").value = b.hoTenBe;
            document.getElementById("bn-ngay-sinh").value = b.ngaySinh;
            document.getElementById("bn-gioi-tinh").value = b.gioiTinh;
            document.getElementById("bn-ten-ph").value = b.hoTenPhuHuynh;
            document.getElementById("bn-sdt").value = b.soDienThoai;
            // Khóa SĐT khi sửa để không làm gãy liên kết tài khoản đăng nhập của phụ huynh
            document.getElementById("bn-sdt").readOnly = true;
            document.getElementById("bn-dia-chi").value = b.diaChi;
            document.getElementById("bn-tien-su").value = b.tienSuBenh;
            break;
        }
    }
}

async function capNhatBenhNhi() {
    let maBN = document.getElementById("bn-ma").value;
    if (maBN === "") {
        hienThongBao("Vui lòng chọn nút 'Sửa' ở dòng bệnh nhi cần cập nhật trước.", "loi");
        return;
    }
    let duLieuSua = {
        maBenhNhi: parseInt(maBN),
        hoTenBe: document.getElementById("bn-ten-be").value.trim(),
        ngaySinh: document.getElementById("bn-ngay-sinh").value,
        gioiTinh: document.getElementById("bn-gioi-tinh").value,
        tienSuBenh: document.getElementById("bn-tien-su").value.trim(),
        hoTenPhuHuynh: document.getElementById("bn-ten-ph").value.trim(),
        diaChi: document.getElementById("bn-dia-chi").value.trim()
    };
    if (duLieuSua.hoTenBe === "" || duLieuSua.ngaySinh === "" || duLieuSua.hoTenPhuHuynh === "") {
        hienThongBao("Vui lòng nhập đầy đủ họ tên bé, ngày sinh và họ tên phụ huynh.", "loi");
        return;
    }
    try {
        let phanHoi = await fetch(API_GOC + "/benh-nhi", {
            method: "PUT",
            headers: taoHeaderAuth(),
            body: JSON.stringify(duLieuSua)
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        if (kq.thanhCong) {
            xoaTrangFormBenhNhi();
            taiDanhSachBenhNhi();
            taiDanhSachLichHen();
            taiDanhSachPhieuKham();
            taiThongKeVaTaiKhoan();
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối máy chủ khi cập nhật bệnh nhi.", "loi");
    }
}

async function xoaBenhNhi(maBenhNhi) {
    if (!confirm("Xác nhận xóa hồ sơ bệnh nhi #" + maBenhNhi + "?")) return;
    try {
        let phanHoi = await fetch(API_GOC + "/benh-nhi?maBenhNhi=" + maBenhNhi, { method: "DELETE", headers: taoHeaderAuth() });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        taiDanhSachBenhNhi();
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi xóa bệnh nhi.", "loi");
    }
}

function xoaTrangFormBenhNhi() {
    document.getElementById("bn-ma").value = "";
    document.getElementById("bn-ten-be").value = "";
    document.getElementById("bn-ngay-sinh").value = "";
    document.getElementById("bn-gioi-tinh").value = "Nam";
    document.getElementById("bn-ten-ph").value = "";
    document.getElementById("bn-sdt").value = "";
    document.getElementById("bn-sdt").readOnly = false;
    document.getElementById("bn-dia-chi").value = "";
    document.getElementById("bn-tien-su").value = "";
}

function khamNgayMotNutBam(maBenhNhi) {
    chuyenTab(3);
    document.getElementById("pk-ma-benh-nhi").value = maBenhNhi;
    hienThiCanhBaoBenhSu();
}

// =========================================================================
// LỊCH HẸN KHÁM
// =========================================================================

async function taiDanhSachLichHen() {
    try {
        let phanHoi = await fetch(API_GOC + "/lich-hen", { headers: taoHeaderAuth() });
        if (kiemTraLoiPhien(phanHoi)) return;
        let danhSach = await phanHoi.json();
        let html = "";
        for (let i = 0; i < danhSach.length; i++) {
            let l = danhSach[i];
            let thaoTacLich = l.trangThai === "Chờ khám"
                ? "<div class='o-nhom-nut'>"
                  + "<button class='nut-bam nut-nho nut-xanh-la' onclick=\"doiTrangThaiLich(" + l.maLichHen + ", 'Đã khám')\">Đã khám</button>"
                  + "<button class='nut-bam nut-nho nut-do' onclick=\"doiTrangThaiLich(" + l.maLichHen + ", 'Đã hủy')\">Hủy hẹn</button>"
                  + "</div>"
                : "<span class='chu-phu'>Đã kết thúc</span>";
            html += "<tr>"
                  + "<td>#" + l.maLichHen + "</td>"
                  + "<td><b>" + escapeHTML(l.hoTenBe) + "</b></td>"
                  + "<td>" + escapeHTML(l.hoTenBacSi) + "</td>"
                  + "<td>" + escapeHTML(l.ngayHen) + "</td>"
                  + "<td class='chu-nhan'>" + escapeHTML(l.gioHen) + "</td>"
                  + "<td>" + escapeHTML(l.lyDoKham) + "</td>"
                  + "<td><b>" + escapeHTML(l.trangThai) + "</b></td>"
                  + "<td>" + thaoTacLich + "</td></tr>";
        }
        document.getElementById("bang-lich-hen").innerHTML = html;
    } catch (loi) {
        console.log("Lỗi tải lịch hẹn: " + loi);
    }
}

async function datLichHenMoi() {
    let duLieu = {
        maBenhNhi: parseInt(document.getElementById("lh-ma-benh-nhi").value),
        maBacSi: parseInt(document.getElementById("lh-ma-bac-si").value),
        ngayHen: document.getElementById("lh-ngay-hen").value,
        gioHen: document.getElementById("lh-gio-hen").value,
        lyDoKham: document.getElementById("lh-ly-do").value.trim()
    };
    try {
        let phanHoi = await fetch(API_GOC + "/lich-hen", {
            method: "POST",
            headers: taoHeaderAuth(),
            body: JSON.stringify(duLieu)
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        if (kq.thanhCong) {
            taiDanhSachLichHen();
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi đặt lịch hẹn.", "loi");
    }
}

async function doiTrangThaiLich(maLichHen, trangThaiMoi) {
    try {
        let phanHoi = await fetch(API_GOC + "/lich-hen", {
            method: "PUT",
            headers: taoHeaderAuth(),
            body: JSON.stringify({ maLichHen: maLichHen, trangThai: trangThaiMoi })
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        taiDanhSachLichHen();
    } catch (loi) {
        hienThongBao("Lỗi khi đổi trạng thái lịch hẹn.", "loi");
    }
}

// =========================================================================
// KHÁM BỆNH, KÊ TOA, IN PHIẾU & THU TIỀN
// =========================================================================

function hienThiCanhBaoBenhSu() {
    let maBN = parseInt(document.getElementById("pk-ma-benh-nhi").value, 10);
    let hopCanhBao = document.getElementById("hop-canh-bao-benh-su");
    let dsTraCuu = danhSachBenhNhiDayDu.length > 0 ? danhSachBenhNhiDayDu : danhSachBenhNhiTam;
    for (let i = 0; i < dsTraCuu.length; i++) {
        if (dsTraCuu[i].maBenhNhi === maBN) {
            let b = dsTraCuu[i];
            hopCanhBao.innerHTML = "<b>Cảnh báo tiền sử &amp; dị ứng: " + escapeHTML(b.hoTenBe) + "</b> (Sinh: " + escapeHTML(b.ngaySinh) + ")<br><b>Tiền sử / Dị ứng:</b> " + escapeHTML(b.tienSuBenh || "Chưa ghi nhận") + " | <b>Phụ huynh:</b> " + escapeHTML(b.hoTenPhuHuynh) + " (" + escapeHTML(b.soDienThoai) + ")";
            return;
        }
    }
    hopCanhBao.innerText = "Chọn bệnh nhi bên dưới để kiểm tra tiền sử bệnh lý và cảnh báo dị ứng thuốc trước khi kê toa.";
}

async function taoPhieuKhamMoi() {
    let maBacSiGui = 0;
    if (nguoiDungHienTai && nguoiDungHienTai.vaiTro === "BAC_SI") {
        maBacSiGui = nguoiDungHienTai.maTaiKhoan;
    } else {
        maBacSiGui = parseInt(document.getElementById("pk-ma-bac-si").value, 10);
    }
    if (!maBacSiGui) {
        hienThongBao("Vui lòng chọn bác sĩ phụ trách khám.", "loi");
        return;
    }

    let oCanNang = document.getElementById("pk-can-nang");
    let oTienKham = document.getElementById("pk-tien-kham");
    let rawCanNang = oCanNang.value.trim();
    let rawTienKham = oTienKham.value.trim();
    if ((oCanNang.validity && oCanNang.validity.badInput) || rawCanNang === "") {
        hienThongBao("Vui lòng nhập cân nặng hợp lệ của bé (> 0 kg và <= 200 kg).", "loi");
        return;
    }
    let canNangKg = Number(rawCanNang);
    if (!Number.isFinite(canNangKg) || canNangKg <= 0 || canNangKg > 200) {
        hienThongBao("Cân nặng thực tế của bé phải lớn hơn 0 kg và nhỏ hơn hoặc bằng 200 kg.", "loi");
        return;
    }
    if (oTienKham.validity && oTienKham.validity.badInput) {
        hienThongBao("Tiền công khám không hợp lệ (chỉ nhập số từ 0 trở lên).", "loi");
        return;
    }
    let tienKhamGui = null;
    if (rawTienKham !== "") {
        let tkSo = Number(rawTienKham);
        if (!Number.isFinite(tkSo) || tkSo < 0) {
            hienThongBao("Tiền công khám không được là số âm.", "loi");
            return;
        }
        tienKhamGui = tkSo;
    }

    let trieuChung = document.getElementById("pk-trieu-chung").value.trim();
    let chanDoan = document.getElementById("pk-chan-doan").value.trim();
    if (trieuChung === "" || chanDoan === "") {
        hienThongBao("Vui lòng nhập đầy đủ triệu chứng lâm sàng và chẩn đoán.", "loi");
        return;
    }

    let duLieu = {
        maBenhNhi: parseInt(document.getElementById("pk-ma-benh-nhi").value, 10),
        maBacSi: maBacSiGui,
        canNangKg: canNangKg,
        trieuChung: trieuChung,
        chanDoan: chanDoan,
        loiDanBacSi: document.getElementById("pk-loi-dan").value.trim()
    };
    if (tienKhamGui !== null) {
        duLieu.tienKham = tienKhamGui;
    }
    try {
        let phanHoi = await fetch(API_GOC + "/phieu-kham", {
            method: "POST",
            headers: taoHeaderAuth(),
            body: JSON.stringify(duLieu)
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        if (kq.thanhCong) {
            await taiDanhSachPhieuKham();
            document.getElementById("tt-ma-phieu").value = kq.maPhieuKham;
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi tạo phiếu khám.", "loi");
    }
}

function tuDongDienCachDung() {
    let maThuocChon = parseInt(document.getElementById("tt-ma-thuoc").value);
    for (let i = 0; i < danhSachThuocTam.length; i++) {
        if (danhSachThuocTam[i].maThuoc === maThuocChon) {
            document.getElementById("tt-lieu-luong").value = danhSachThuocTam[i].cachDungMacDinh;
            break;
        }
    }
}

async function themThuocVaoToa() {
    let maPhieuVal = document.getElementById("tt-ma-phieu").value;
    if (!maPhieuVal) {
        hienThongBao("Hiện chưa có phiếu khám chờ kê toa. Hãy tạo phiếu khám ở Bước 1 trước.", "loi");
        return;
    }
    let oSoLuong = document.getElementById("tt-so-luong");
    let rawSoLuong = oSoLuong.value.trim();
    if ((oSoLuong.validity && oSoLuong.validity.badInput) || rawSoLuong === "" || !/^-?\d+$/.test(rawSoLuong)) {
        hienThongBao("Số lượng thuốc kê phải là số nguyên lớn hơn 0.", "loi");
        return;
    }
    let soLuong = parseInt(rawSoLuong, 10);
    if (soLuong <= 0) {
        hienThongBao("Số lượng thuốc kê phải lớn hơn 0.", "loi");
        return;
    }
    let duLieu = {
        maPhieuKham: parseInt(maPhieuVal, 10),
        maThuoc: parseInt(document.getElementById("tt-ma-thuoc").value, 10),
        soLuong: soLuong,
        lieuLuongDung: document.getElementById("tt-lieu-luong").value.trim()
    };
    try {
        let phanHoi = await fetch(API_GOC + "/toa-thuoc", {
            method: "POST",
            headers: taoHeaderAuth(),
            body: JSON.stringify(duLieu)
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        if (kq.thanhCong) {
            await taiDanhSachPhieuKham();
            taiDanhSachThuoc();
            xemVaInPhieuKham(duLieu.maPhieuKham);
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi kê thuốc vào toa.", "loi");
    }
}

async function taiDanhSachPhieuKham() {
    try {
        let phanHoi = await fetch(API_GOC + "/phieu-kham", { headers: taoHeaderAuth() });
        if (kiemTraLoiPhien(phanHoi)) return;
        danhSachPhieuKhamTam = await phanHoi.json();
        let bangHTML = "";
        let oChonPhieuHTML = "";

        let coTheThuTien = nguoiDungHienTai && (nguoiDungHienTai.vaiTro === "LE_TAN" || nguoiDungHienTai.vaiTro === "QUAN_TRI");
        for (let i = 0; i < danhSachPhieuKhamTam.length; i++) {
            let p = danhSachPhieuKhamTam[i];
            let nutThuTien = "";
            if (p.trangThaiPhieu !== "Đã thanh toán") {
                if (coTheThuTien) {
                    nutThuTien = "<button class='nut-bam nut-nho nut-vang' onclick='thuTienPhieu(" + p.maPhieuKham + ")'>Thu tiền</button>";
                }
                oChonPhieuHTML += "<option value='" + p.maPhieuKham + "'>Phiếu #" + p.maPhieuKham + " - " + escapeHTML(p.hoTenBe) + " (" + p.canNangKg + " kg)</option>";
            }
            let tenBacSiHienThi = String(p.hoTenBacSi || "").trim();
            let nhanBacSi = /^bs(\.|\s)/i.test(tenBacSiHienThi)
                ? escapeHTML(tenBacSiHienThi)
                : ("BS: " + escapeHTML(tenBacSiHienThi));
            bangHTML += "<tr>"
                     + "<td><b>#" + p.maPhieuKham + "</b><span class='chu-phu'>" + escapeHTML(p.ngayKham) + "</span></td>"
                     + "<td><b>" + escapeHTML(p.hoTenBe) + "</b><span class='chu-phu'>" + nhanBacSi + "</span></td>"
                     + "<td class='chu-nhan'>" + p.canNangKg + " kg</td>"
                     + "<td><b>" + escapeHTML(p.chanDoan) + "</b><span class='chu-phu'>Triệu chứng: " + escapeHTML(p.trieuChung) + "</span><span class='chu-phu'>Dặn dò: " + escapeHTML(p.loiDanBacSi || "Không có") + "</span></td>"
                     + "<td>" + p.tienKham.toLocaleString("vi-VN") + " đ</td>"
                     + "<td>" + p.tienThuoc.toLocaleString("vi-VN") + " đ</td>"
                     + "<td class='chu-tong-tien'>" + p.tongVienPhi.toLocaleString("vi-VN") + " đ</td>"
                     + "<td><b>" + escapeHTML(p.trangThaiPhieu) + "</b></td>"
                     + "<td><div class='o-nhom-nut'>"
                     + "<button class='nut-bam nut-nho nut-xanh-la' onclick='xemVaInPhieuKham(" + p.maPhieuKham + ")'>Xem &amp; In</button>"
                     + nutThuTien
                     + "</div></td></tr>";
        }
        document.getElementById("bang-phieu-kham").innerHTML = bangHTML;
        document.getElementById("tt-ma-phieu").innerHTML = oChonPhieuHTML;
    } catch (loi) {
        console.log("Lỗi tải danh sách phiếu khám: " + loi);
    }
}

async function xemVaInPhieuKham(maPhieuKham) {
    let phieu = null;
    for (let i = 0; i < danhSachPhieuKhamTam.length; i++) {
        if (danhSachPhieuKhamTam[i].maPhieuKham === maPhieuKham) {
            phieu = danhSachPhieuKhamTam[i];
            break;
        }
    }
    if (!phieu) return;

    try {
        let phanHoiToa = await fetch(API_GOC + "/toa-thuoc?maPhieuKham=" + maPhieuKham, { headers: taoHeaderAuth() });
        if (kiemTraLoiPhien(phanHoiToa)) return;
        let dsThuocTrongToa = await phanHoiToa.json();

        let coTheSuaToa = phieu.trangThaiPhieu !== "Đã thanh toán"
            && nguoiDungHienTai
            && (nguoiDungHienTai.vaiTro === "BAC_SI" || nguoiDungHienTai.vaiTro === "QUAN_TRI");
        let cotThaoTacHeader = coTheSuaToa ? "<th class='khong-in'>Thao tác</th>" : "";
        let soCotBang = coTheSuaToa ? 7 : 6;

        let htmlToa = "<div class='khung-bang'><table><thead><tr><th>STT</th><th>Tên thuốc</th><th>Số lượng</th><th>Đơn giá lúc kê</th><th>Thành tiền</th><th>Cách dùng theo cân nặng</th>" + cotThaoTacHeader + "</tr></thead><tbody>";
        for (let i = 0; i < dsThuocTrongToa.length; i++) {
            let t = dsThuocTrongToa[i];
            let cotXoa = coTheSuaToa
                ? "<td class='khong-in'><button class='nut-bam nut-nho nut-do' onclick='boThuocKhoiToa(" + t.maToaThuoc + ", " + maPhieuKham + ")'>Bỏ thuốc</button></td>"
                : "";
            htmlToa += "<tr><td>" + (i + 1) + "</td><td><b>" + escapeHTML(t.tenThuoc) + "</b></td><td>" + t.soLuong + " " + escapeHTML(t.donViTinh) + "</td>"
                    + "<td>" + t.donGiaLucKe.toLocaleString("vi-VN") + " đ</td><td><b>" + t.thanhTien.toLocaleString("vi-VN") + " đ</b></td>"
                    + "<td>" + escapeHTML(t.lieuLuongDung) + "</td>" + cotXoa + "</tr>";
        }
        if (dsThuocTrongToa.length === 0) {
            htmlToa += "<tr><td colspan='" + soCotBang + "'>Chưa kê đơn thuốc cho phiếu khám này.</td></tr>";
        }
        htmlToa += "</tbody></table></div>";

        let noiDungHTML = "<p><b>Mã phiếu khám:</b> #" + phieu.maPhieuKham + " | <b>Ngày giờ khám:</b> " + escapeHTML(phieu.ngayKham) + "</p>"
                        + "<p><b>Bệnh nhi:</b> " + escapeHTML(phieu.hoTenBe) + " | <b>Cân nặng lúc khám:</b> <span class='chu-nhan'>" + phieu.canNangKg + " kg</span></p>"
                        + "<p><b>Bác sĩ khám:</b> " + escapeHTML(phieu.hoTenBacSi) + "</p>"
                        + "<p><b>Triệu chứng:</b> " + escapeHTML(phieu.trieuChung) + "</p>"
                        + "<p><b>Chẩn đoán:</b> <span class='chu-canh-bao'>" + escapeHTML(phieu.chanDoan) + "</span></p>"
                        + "<p><b>Lời dặn của bác sĩ:</b> " + escapeHTML(phieu.loiDanBacSi || "Không có") + "</p>"
                        + "<h4 class='tieu-de-muc tieu-de-don-in'>Chi tiết đơn thuốc</h4>" + htmlToa
                        + "<p class='tong-ket-dot-kham'>"
                        + "Tiền khám: <b>" + phieu.tienKham.toLocaleString("vi-VN") + " VNĐ</b> | "
                        + "Tiền thuốc: <b>" + phieu.tienThuoc.toLocaleString("vi-VN") + " VNĐ</b> | "
                        + "Tổng viện phí: <span class='chu-tong-tien'>" + phieu.tongVienPhi.toLocaleString("vi-VN") + " VNĐ</span> (" + escapeHTML(phieu.trangThaiPhieu) + ")</p>";

        document.getElementById("noi-dung-phieu-in").innerHTML = noiDungHTML;
        document.getElementById("khung-in-phieu").classList.remove("an-di");
        document.getElementById("khung-in-phieu").scrollIntoView({ behavior: "smooth" });
    } catch (loi) {
        hienThongBao("Lỗi khi tải chi tiết toa thuốc để xem trước.", "loi");
    }
}

async function boThuocKhoiToa(maToaThuoc, maPhieuKham) {
    if (!confirm("Xác nhận bỏ dòng thuốc #" + maToaThuoc + " khỏi toa và hoàn lại tồn kho?")) return;
    try {
        let phanHoi = await fetch(API_GOC + "/toa-thuoc?maToaThuoc=" + maToaThuoc, {
            method: "DELETE",
            headers: taoHeaderAuth()
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        if (kq.thanhCong) {
            await taiDanhSachPhieuKham();
            taiDanhSachThuoc();
            xemVaInPhieuKham(maPhieuKham);
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi bỏ thuốc khỏi toa.", "loi");
    }
}

function dongKhungIn() {
    document.getElementById("khung-in-phieu").classList.add("an-di");
}

async function thuTienPhieu(maPhieuKham) {
    if (!confirm("Xác nhận đã thu đủ tiền viện phí cho Phiếu khám #" + maPhieuKham + "?")) return;
    try {
        let phanHoi = await fetch(API_GOC + "/phieu-kham", {
            method: "PUT",
            headers: taoHeaderAuth(),
            body: JSON.stringify({ maPhieuKham: maPhieuKham })
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        await taiDanhSachPhieuKham();
        taiThongKeVaTaiKhoan();
        let khungIn = document.getElementById("khung-in-phieu");
        if (kq.thanhCong && khungIn && !khungIn.classList.contains("an-di")) {
            xemVaInPhieuKham(maPhieuKham);
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi thu tiền viện phí.", "loi");
    }
}

// =========================================================================
// DANH MỤC THUỐC & TỒN KHO
// =========================================================================

async function taiDanhSachThuoc() {
    try {
        let phanHoi = await fetch(API_GOC + "/thuoc", { headers: taoHeaderAuth() });
        if (kiemTraLoiPhien(phanHoi)) return;
        danhSachThuocTam = await phanHoi.json();
        let bangHTML = "";
        let oChonThuocHTML = "";
        let laLeTan = nguoiDungHienTai && nguoiDungHienTai.vaiTro === "LE_TAN";

        for (let i = 0; i < danhSachThuocTam.length; i++) {
            let t = danhSachThuocTam[i];
            let nutThaoTac = laLeTan
                ? "<span class='chu-phu'>Chỉ xem</span>"
                : "<div class='o-nhom-nut'>"
                  + "<button class='nut-bam nut-nho nut-vang' onclick='chonThuocDeSua(" + t.maThuoc + ")'>Sửa</button>"
                  + "<button class='nut-bam nut-nho nut-do' onclick='xoaThuoc(" + t.maThuoc + ")'>Xóa</button>"
                  + "</div>";
            bangHTML += "<tr>"
                     + "<td>#" + t.maThuoc + "</td>"
                     + "<td><b>" + escapeHTML(t.tenThuoc) + "</b></td>"
                     + "<td>" + escapeHTML(t.donViTinh) + "</td>"
                     + "<td><b>" + t.donGia.toLocaleString("vi-VN") + " đ</b></td>"
                     + "<td class='chu-nhan'>" + t.soLuongTon + "</td>"
                     + "<td>" + escapeHTML(t.cachDungMacDinh) + "</td>"
                     + "<td>" + nutThaoTac + "</td></tr>";
            oChonThuocHTML += "<option value='" + t.maThuoc + "'>" + escapeHTML(t.tenThuoc) + " (" + t.donGia.toLocaleString("vi-VN") + "đ | Tồn: " + t.soLuongTon + " " + escapeHTML(t.donViTinh) + ")</option>";
        }
        document.getElementById("bang-thuoc").innerHTML = bangHTML;
        document.getElementById("tt-ma-thuoc").innerHTML = oChonThuocHTML;
        let oLieuLuong = document.getElementById("tt-lieu-luong");
        if (oLieuLuong && oLieuLuong.value.trim() === "") {
            tuDongDienCachDung();
        }
    } catch (loi) {
        console.log("Lỗi tải danh mục thuốc: " + loi);
    }
}

function xoaTrangFormThuoc() {
    document.getElementById("th-ma").value = "";
    document.getElementById("th-ten").value = "";
    document.getElementById("th-don-vi").value = "";
    document.getElementById("th-don-gia").value = "";
    document.getElementById("th-ton-kho").value = "";
    document.getElementById("th-cach-dung").value = "";
}

function docDuLieuFormThuoc() {
    let tenThuoc = document.getElementById("th-ten").value.trim();
    let donViTinh = document.getElementById("th-don-vi").value.trim();
    let rawDonGia = document.getElementById("th-don-gia").value.trim();
    let rawTonKho = document.getElementById("th-ton-kho").value.trim();
    let cachDungMacDinh = document.getElementById("th-cach-dung").value.trim();

    if (tenThuoc === "" || donViTinh === "") {
        hienThongBao("Vui lòng nhập đầy đủ Tên thuốc và Đơn vị tính.", "loi");
        return null;
    }
    let donGia = Number(rawDonGia);
    if (rawDonGia === "" || !Number.isFinite(donGia) || donGia <= 0) {
        hienThongBao("Đơn giá bán phải là số lớn hơn 0 đồng.", "loi");
        return null;
    }
    if (rawTonKho === "" || !/^-?\d+$/.test(rawTonKho)) {
        hienThongBao("Số lượng tồn kho phải là số nguyên từ 0 trở lên.", "loi");
        return null;
    }
    let soLuongTon = parseInt(rawTonKho, 10);
    if (soLuongTon < 0) {
        hienThongBao("Số lượng tồn kho không được nhỏ hơn 0.", "loi");
        return null;
    }
    return {
        tenThuoc: tenThuoc,
        donViTinh: donViTinh,
        donGia: donGia,
        soLuongTon: soLuongTon,
        cachDungMacDinh: cachDungMacDinh
    };
}

async function themThuocMoi() {
    let duLieu = docDuLieuFormThuoc();
    if (!duLieu) return;
    try {
        let phanHoi = await fetch(API_GOC + "/thuoc", {
            method: "POST",
            headers: taoHeaderAuth(),
            body: JSON.stringify(duLieu)
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        if (kq.thanhCong) {
            xoaTrangFormThuoc();
            taiDanhSachThuoc();
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi thêm thuốc.", "loi");
    }
}

function chonThuocDeSua(maThuoc) {
    for (let i = 0; i < danhSachThuocTam.length; i++) {
        let t = danhSachThuocTam[i];
        if (t.maThuoc === maThuoc) {
            document.getElementById("th-ma").value = t.maThuoc;
            document.getElementById("th-ten").value = t.tenThuoc;
            document.getElementById("th-don-vi").value = t.donViTinh;
            document.getElementById("th-don-gia").value = t.donGia;
            document.getElementById("th-ton-kho").value = t.soLuongTon;
            document.getElementById("th-cach-dung").value = t.cachDungMacDinh;
            break;
        }
    }
}

async function capNhatThuoc() {
    let maThuoc = document.getElementById("th-ma").value;
    if (maThuoc === "") {
        hienThongBao("Vui lòng chọn nút 'Sửa' ở dòng thuốc cần cập nhật trước.", "loi");
        return;
    }
    let duLieu = docDuLieuFormThuoc();
    if (!duLieu) return;
    duLieu.maThuoc = parseInt(maThuoc, 10);
    try {
        let phanHoi = await fetch(API_GOC + "/thuoc", {
            method: "PUT",
            headers: taoHeaderAuth(),
            body: JSON.stringify(duLieu)
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        if (kq.thanhCong) {
            xoaTrangFormThuoc();
            taiDanhSachThuoc();
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi sửa thuốc.", "loi");
    }
}

async function xoaThuoc(maThuoc) {
    if (!confirm("Xác nhận xóa thuốc mã #" + maThuoc + "?")) return;
    try {
        let phanHoi = await fetch(API_GOC + "/thuoc?maThuoc=" + maThuoc, { method: "DELETE", headers: taoHeaderAuth() });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        if (kq.thanhCong) {
            taiDanhSachThuoc();
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi xóa thuốc.", "loi");
    }
}

// =========================================================================
// THỐNG KÊ CUỐI NGÀY & MỞ KHÓA TÀI KHOẢN
// =========================================================================

async function taiThongKeVaTaiKhoan() {
    try {
        let vaiTroHienTai = nguoiDungHienTai ? nguoiDungHienTai.vaiTro : "";
        let laQuanTri = vaiTroHienTai === "QUAN_TRI";
        if (laQuanTri || vaiTroHienTai === "LE_TAN") {
            let phanHoiTK = await fetch(API_GOC + "/thong-ke", { headers: taoHeaderAuth() });
            if (kiemTraLoiPhien(phanHoiTK)) return;
            if (phanHoiTK.ok) {
                let tk = await phanHoiTK.json();
                document.getElementById("tk-so-ca").innerText = tk.soCaDaThu + " ca";
                document.getElementById("tk-tien-kham").innerText = tk.tongTienKham.toLocaleString("vi-VN") + " VNĐ";
                document.getElementById("tk-tien-thuoc").innerText = tk.tongTienThuoc.toLocaleString("vi-VN") + " VNĐ";
                document.getElementById("tk-tong-cong").innerText = tk.tongDoanhThu.toLocaleString("vi-VN") + " VNĐ";
                if (laQuanTri) {
                    document.getElementById("tk-so-ca-tich-luy").innerText = (tk.soCaTichLuy || 0) + " ca";
                    document.getElementById("tk-tong-tich-luy").innerText = (tk.tongDoanhThuTichLuy || 0).toLocaleString("vi-VN") + " VNĐ";
                }
            }
        }

        let phanHoiDS = await fetch(API_GOC + "/tai-khoan", { headers: taoHeaderAuth() });
        if (kiemTraLoiPhien(phanHoiDS)) return;
        let dsTK = await phanHoiDS.json();
        let bangHTML = "";
        let oChonBacSiHTML = "";

        for (let i = 0; i < dsTK.length; i++) {
            let u = dsTK[i];
            if (laQuanTri) {
                let nhanKhoa = u.trangThaiKhoa === 1
                    ? "<span class='nhan-trang-thai trang-thai-khoa'>"
                      + (u.khoaDenLuc ? ("Tạm khóa đến " + escapeHTML(u.khoaDenLuc)) : "Đã khóa")
                      + "</span>"
                    : "<span class='nhan-trang-thai trang-thai-mo'>Hoạt động</span>";
                let nutThaoTacTK = (u.trangThaiKhoa === 1 || u.soLanSai > 0)
                    ? "<button class='nut-bam nut-nho nut-xanh-la' onclick='moKhoaTaiKhoan(" + u.maTaiKhoan + ")'>Mở khóa</button>"
                    : "<span class='chu-phu'>Bình thường</span>";
                bangHTML += "<tr>"
                         + "<td>#" + u.maTaiKhoan + "</td>"
                         + "<td><b>" + escapeHTML(u.tenDangNhap) + "</b></td>"
                         + "<td>" + escapeHTML(u.hoTen) + "</td>"
                         + "<td><b>" + escapeHTML(u.vaiTro) + "</b></td>"
                         + "<td>" + u.soLanSai + " lần</td>"
                         + "<td>" + nhanKhoa + "</td>"
                         + "<td>" + nutThaoTacTK + "</td>"
                         + "</tr>";
            }
            if (u.vaiTro === "BAC_SI") {
                oChonBacSiHTML += "<option value='" + u.maTaiKhoan + "'>" + escapeHTML(u.hoTen) + " (#" + u.maTaiKhoan + ")</option>";
            }
        }
        document.getElementById("bang-tai-khoan").innerHTML = laQuanTri ? bangHTML : "";
        document.getElementById("lh-ma-bac-si").innerHTML = oChonBacSiHTML;
        let oChonPkBacSi = document.getElementById("pk-ma-bac-si");
        if (oChonPkBacSi) oChonPkBacSi.innerHTML = oChonBacSiHTML;
    } catch (loi) {
        console.log("Lỗi tải thống kê và tài khoản: " + loi);
    }
}

async function moKhoaTaiKhoan(maTaiKhoan) {
    try {
        let phanHoi = await fetch(API_GOC + "/tai-khoan", {
            method: "POST",
            headers: taoHeaderAuth(),
            body: JSON.stringify({ maTaiKhoan: maTaiKhoan })
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        taiThongKeVaTaiKhoan();
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi mở khóa tài khoản.", "loi");
    }
}

async function doiMatKhauCaNhan() {
    let matKhauCu = document.getElementById("mk-cu").value;
    let matKhauMoi = document.getElementById("mk-moi").value;
    let xacNhan = document.getElementById("mk-xac-nhan").value;

    if (!matKhauCu || !matKhauMoi || !xacNhan) {
        hienThongBao("Vui lòng nhập đầy đủ mật khẩu hiện tại và mật khẩu mới.", "loi");
        return;
    }
    if (matKhauMoi.trim() === "" || matKhauMoi !== matKhauMoi.trim()) {
        hienThongBao("Mật khẩu mới không được chứa khoảng trắng ở đầu hoặc cuối.", "loi");
        return;
    }
    if (matKhauMoi.length < 6) {
        hienThongBao("Mật khẩu mới phải có tối thiểu 6 ký tự.", "loi");
        return;
    }
    if (matKhauMoi !== xacNhan) {
        hienThongBao("Mật khẩu mới nhập lại chưa khớp.", "loi");
        return;
    }

    try {
        let phanHoi = await fetch(API_GOC + "/doi-mat-khau", {
            method: "POST",
            headers: taoHeaderAuth(),
            body: JSON.stringify({ matKhauCu: matKhauCu, matKhauMoi: matKhauMoi })
        });
        if (kiemTraLoiPhien(phanHoi)) return;
        let kq = await phanHoi.json();
        hienThongBao(kq.thongBao, kq.thanhCong ? "thanh-cong" : "loi");
        if (kq.thanhCong) {
            document.getElementById("mk-cu").value = "";
            document.getElementById("mk-moi").value = "";
            document.getElementById("mk-xac-nhan").value = "";
            if (nguoiDungHienTai && nguoiDungHienTai.phaiDoiMatKhau) {
                nguoiDungHienTai.phaiDoiMatKhau = false;
                phanQuyenGiaoDienTheoVaiTro(nguoiDungHienTai.vaiTro);
                datTrangThaiBatBuocDoiMatKhau(false);
                khoiTaoDuLieuBanDau();
            }
        }
    } catch (loi) {
        hienThongBao("Lỗi kết nối khi đổi mật khẩu.", "loi");
    }
}

document.addEventListener("DOMContentLoaded", function() {
    ["o-ten-dang-nhap", "o-mat-khau"].forEach(function(id) {
        let el = document.getElementById(id);
        if (el) {
            el.addEventListener("keydown", function(e) {
                if (e.key === "Enter") xuLyDangNhap();
            });
        }
    });
});
