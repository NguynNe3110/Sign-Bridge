# Quy trình Gitflow & Tự động Release APK lên GitHub

Tài liệu này hướng dẫn quy trình nhánh (Gitflow) và cách phát hành (Release) phiên bản ứng dụng **SignBridge** lên GitHub. Mỗi khi tạo và đẩy (push) một tag phiên bản mới, hệ thống GitHub Actions sẽ tự động biên dịch, ký app bằng **debug key** và tạo GitHub Release đính kèm file APK sẵn sàng để tải về cài đặt.

---

## 1. Sơ đồ Gitflow trong SignBridge

```
feature/tv1-ui ───────────┐
                          ├─► integration/tv1-tv2 ──► main ──► [TAG v1.0.0]
feature/tv2-device ───────┘                             │
                                                        ▼
                                              GitHub Actions Trigger:
                                              1. Build APK (Signed by Debug Key)
                                              2. Create GitHub Release
                                              3. Upload SignBridge-v1.0.0.apk
```

- **`main`**: Nhánh chính chứa phiên bản ổn định nhất của sản phẩm. Chỉ merge vào đây khi các tính năng đã qua kiểm thử.
- **`feature/<ten-tinh-nang>`**: Nhánh làm việc độc lập của từng lập trình viên (ví dụ `feature/tv1-mobile-ui`, `feature/tv2-device-camera`).
- **`integration/<ten-dot>`**: Nhánh tạm thời dùng để ghép nối các phần việc của nhiều người trước khi đưa vào `main`.

---

## 2. Các bước Release khi hoàn thành phiên bản

Khi đã sẵn sàng phát hành một phiên bản mới (ví dụ `v1.0.0`):

### Bước 1: Merge code vào nhánh `main`
Đảm bảo nhánh `main` có mã nguồn mới nhất và chạy test thành công:
```bash
git checkout main
git pull origin main
git merge integration/tv1-tv2
./gradlew testDebugUnitTest
git push origin main
```

### Bước 2: Đánh Tag phiên bản mới
Sử dụng tag chuẩn ngữ nghĩa (`vX.Y.Z`):
* `v1.0.0`: Bản phát hành chính thức hoặc mốc quan trọng.
* `v0.1.0`: Bản thử nghiệm ban đầu (MVP / Demo nội bộ).
* `v1.0.1`: Bản vá lỗi nhỏ.

Lệnh tạo tag có chú thích:
```bash
git tag -a v1.0.0 -m "Release SignBridge version 1.0.0"
```

### Bước 3: Đẩy (Push) Tag lên GitHub
```bash
git push origin v1.0.0
```

---

## 3. Quá trình GitHub Actions tự động thực hiện

Sau khi bạn chạy lệnh `git push origin v1.0.0`, GitHub Actions (`.github/workflows/release.yml`) sẽ tự động:

1. **Khởi chạy môi trường Ubuntu** với JDK 21.
2. **Biên dịch và ký APK:** Chạy `./gradlew assembleDebug` (tự động ký bằng Android Debug Keystore).
3. **Đổi tên file:** `app-debug.apk` $\rightarrow$ `SignBridge-v1.0.0.apk`.
4. **Tạo GitHub Release:**
   * Tự động tạo trang Release tại mục **Releases** trên repo GitHub của bạn.
   * Tự động tổng hợp danh sách commit / thay đổi (Release Notes).
   * Đính kèm file `SignBridge-v1.0.0.apk` để bất kỳ ai vào GitHub cũng có thể bấm tải trực tiếp về điện thoại cài đặt ngay.

---

## 4. Xóa hoặc sửa Tag (nếu tạo nhầm)

Nếu cần thu hồi hoặc tạo lại một tag:
```bash
# Xóa tag ở máy cá nhân
git tag -d v1.0.0

# Xóa tag trên GitHub
git push --delete origin v1.0.0
```
