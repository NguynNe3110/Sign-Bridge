# Kết quả kiểm tra TV2

Ngày kiểm tra: 13/11/2026. Phạm vi: phần thiết bị, SQLite, client API, usecase và màn tích hợp TV2 của Nguyên; đã phối hợp với launcher Compose/Hilt trên nhánh `setup/tech-stack-compose-hilt`.

## Kiểm tra tự động

Lệnh đã chạy thành công trên cấu hình mới:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

- Build debug: **PASS**.
- Test toàn repo: **26 tests, 0 failures, 0 errors, 0 skipped** tại lần kiểm tra này.
- Trong đó 22 test TV2: 8 usecase/policy, 3 SQLite, 8 client API, 3 ViewModel. Các test còn lại gồm test mẫu và test của luồng setup Compose/Hilt.
- Lint: **0 errors, 25 warnings**. Các cảnh báo gồm phiên bản dependency/target SDK, resource chưa dùng, gợi ý KTX và overdraw. Nền kín của Fragment là có chủ đích để che ComposeView nằm dưới khi mở bảng kiểm tra; không bỏ nền để làm mất cảnh báo rồi gây chồng nội dung.
- Detector Impeccable trên màn TV2: không có finding tại lần chạy cuối.

Các tình huống đã kiểm tra bằng test: fallback chỉ với lỗi cho phép; không nuốt cancellation; không lưu ký hiệu UNKNOWN như câu đã xác nhận; context không khớp bị chặn; thẻ/lịch sử/usage còn sau đóng và mở database; transaction rollback khi tăng usage thất bại; bảo vệ thẻ seed và chặn trùng nhãn; JSON UTF-8; multipart đúng field/thứ tự; đủ 7 mã lỗi; JSON/confidence sai; timeout khi đọc body; HTTPS release; double-submit; background cancel; đổi context lỗi không tái dùng session cũ; hủy nhấn-giữ rồi thả không gửi audio.

## Kiểm tra chạy thật trên emulator

Môi trường: AVD Pixel_4, Android 10/API 29, chạy nền read-only; bật camera front/back mô phỏng khi kiểm tra ảnh. Không thay cấu hình hay lưu snapshot vào AVD gốc.

| Tình huống | Quan sát |
|---|---|
| Mở app, SQLite seed | Màn TV2 mở được, có thẻ theo context, không crash |
| Thêm và gửi thẻ khi chưa cấu hình backend | Lưu thẻ, tạo lượt OUTGOING đánh dấu Offline, usageCount tăng 1 |
| Force-stop rồi mở lại | Thẻ, lượt hội thoại và usageCount vẫn còn trong SQLite; thẻ có trong UI |
| Camera trước mô phỏng, giữ 3 giây | Preview có ảnh; thu/nén/validate frame hoàn tất và đến bước báo thiếu URL server; chưa kiểm tra nhận diện AI thật |
| Cấp quyền mic | Không tự bắt đầu thu sau khi cấp quyền; cache audio vẫn trống trước lần giữ tiếp theo |
| Giữ mic 3 giây | Tạo và đọc audio, validate xong đến bước báo thiếu URL; thư mục audio tạm trở lại rỗng |
| Đang ghi mic rồi bấm Home | Có file tạm khi đang thu; sau ra nền thư mục rỗng và không tạo lượt hội thoại giả |
| Kéo trên nút nhấn-giữ để cuộn | Hủy thu, không gửi khi thả; cache rỗng, UI báo đã hủy |
| Replay trên emulator không có bộ đọc Việt | Báo không khởi tạo được giọng đọc, không báo đã phát xong; giữ nguyên câu/lịch sử |
| Cỡ chữ 100% và 130% | Đã xem ảnh render; nhãn nhập thẻ được rút ngắn và tách hướng dẫn 50 ký tự để không bị cắt |
| Compose → TV2 → Back | Sau sửa nền/insets, không còn vẽ chồng; Back quay lại màn Compose; không có AndroidRuntime crash |

Ảnh và log QA nằm trong `app/build/qa/` (được Git ignore); không cần các file này để build hoặc chạy test.

## Chưa được xác minh

- Backend thật chưa được cung cấp/cấu hình. API client hiện theo hợp đồng đề xuất trong docs; test giao thức dùng MockWebServer.
- Chưa đánh giá độ chính xác VSL, STT/tóm tắt giữ số, tốc độ AI, hoặc hội thoại 3 lượt dưới 30 giây.
- Chưa nghe giọng Việt/TTS hoàn tất trên điện thoại thật và chưa đo cadence camera trên nhiều thiết bị. Emulator chỉ xác minh đường lỗi TTS và pipeline media trước bước gọi backend.
- Giao diện hội thoại chia đôi 180°, rung và trải nghiệm sản phẩm hoàn chỉnh thuộc TV1; bảng TV2 là màn kiểm tra và ví dụ nối dependency/state/event.
