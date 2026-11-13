# Sign-Bridge

Ứng dụng Android Kotlin hỗ trợ giao tiếp hai chiều giữa người khiếm thính và nhân viên dịch vụ.

## Tài liệu và phần việc

- [Kế hoạch triển khai và yêu cầu nghiệm thu](docs/ke-hoach-trien-khai.md)
- [Phân công nhóm](docs/phan-cong.md) — Nguyên phụ trách TV2.
- [Bàn giao TV2 cho TV1](docs/ban-giao-tv2.md)
- [Luồng và API đề xuất](docs/luong-xu-ly-chinh.md)
- [Quy ước MVVM-basic](AGENTS.md)

## Mở project

1. Dùng Android Studio với JDK 21, cài Android SDK Platform 37. Project compile SDK 37, target SDK 36, chạy từ Android 8.0/API 26.
2. Mở thư mục project, chọn đường dẫn SDK và Gradle Sync. Android Studio thường tạo `local.properties`; nếu cần tự tạo, copy `local.properties.example` và sửa đường dẫn SDK của bạn.
3. Chạy `app`, chọn **Mở bảng kiểm tra phần cứng (TV2)** từ màn Compose. Màn kiểm tra cho phép thêm/xóa thẻ, gửi thẻ offline và xem lịch sử local ngay khi chưa có backend. Giao diện split-screen cuối cùng thuộc phần TV1.
4. Để thử AI thật, cấu hình `signbridge.apiBaseUrl` trong Gradle properties của người dùng (xem `gradle.properties.example`) hoặc truyền `-P` khi build. Backend phải hỗ trợ hợp đồng trong docs; repo này không chứa server AI.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Camera/mic xin quyền khi sử dụng. TTS cần giọng tiếng Việt trên thiết bị. Bản debug cho phép HTTP để thử server local; bản release yêu cầu HTTPS. Không commit `local.properties`, cache/build, khóa ký hoặc secrets; các file `.example` chỉ chứa giá trị minh họa.
