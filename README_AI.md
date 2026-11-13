# Dùng MVVM-basic làm hướng dẫn cho AI

[AGENTS.md](AGENTS.md) là bản quy ước đầy đủ, được viết từ cấu trúc và ghi chú hiện có của bạn. Nội dung quy định vị trí file, trách nhiệm từng tầng, chiều phụ thuộc, quy ước màn hình và checklist để AI tự đối chiếu khi làm việc.

## Đưa vào dự án

1. Copy `AGENTS.md` vào thư mục gốc dự án Android cần áp dụng, cùng cấp với `settings.gradle.kts` hoặc `settings.gradle`.
2. Nếu dự án đã có `AGENTS.md`, gộp phần quy ước kiến trúc vào hướng dẫn hiện có và xử lý các điểm mâu thuẫn; không ghi đè những hướng dẫn còn cần dùng.
3. Yêu cầu AI đọc file đó trước khi triển khai. Nếu công cụ của bạn có mục project rules riêng, đưa nội dung quy ước vào mục đó hoặc yêu cầu nó đọc `AGENTS.md`.

Không cần copy nguyên `Kotlin_MVVM/` vào project. AI cần tạo code đúng source root và package thực tế của ứng dụng.

## Prompt có thể dùng ngay

```text
Đọc AGENTS.md ở root dự án trước khi làm việc. Đây là quy ước tổ chức
code theo base MVVM-basic của tôi.

Hãy triển khai: <mô tả tính năng hoặc thay đổi>.

Giữ 5 nhóm core, data, domain, feature, ui theo hướng dẫn. Trước khi sửa,
kiểm tra source root, package, UI và DI đang có; nêu ngắn gọn các file
cần thêm/sửa và trách nhiệm của chúng, rồi triển khai trong phạm vi yêu cầu.
Khi hoàn tất, đối chiếu checklist trong AGENTS.md và báo kết quả kiểm tra.
```

Nếu chưa copy file vào dự án, có thể yêu cầu AI đọc `MVVM-basic/AGENTS.md` ở đường dẫn đầy đủ mà công cụ đó truy cập được, rồi áp dụng cho dự án đích.

## Những lựa chọn mặc định cần biết

- Giữ 5 nhóm ở cấp package ứng dụng; phần màn hình chia theo feature.
- Dự án mới dùng Compose; dự án XML hiện có tiếp tục dùng Fragment trong phạm vi đang làm.
- Repository interface đặt ở `domain/repository`; implementation đặt ở `data/repository`.
- UseCase dùng cho quy tắc/phối hợp nghiệp vụ; thao tác đơn giản có thể để ViewModel gọi repository interface.
- Code mới dùng `UiEvent` cho hành động từ UI và `UiEffect` cho hiệu ứng một lần từ ViewModel.
- Dự án mới chưa có DI dùng `core/di/AppContainer.kt`; dự án đã có Hilt/Koin tiếp tục cách đang dùng.

Các lựa chọn trên được nêu rõ để tránh AI tự chọn khác nhau ở mỗi lần làm. Nếu muốn thay đổi mặc định, sửa mục tương ứng trong `AGENTS.md` và phần tóm tắt này.

Hướng dẫn giúp AI có quy tắc cụ thể để tuân theo; việc đối chiếu thay đổi thực tế với checklist vẫn cần thiết. Bộ tài liệu này chưa tự động kiểm tra kiến trúc bằng script, lint hoặc CI.

Hai file hướng dẫn được thêm trong `MVVM-basic`; chưa áp dụng vào `Account_manager` hoặc sửa các file mẫu ban đầu.
