# Bàn giao phần TV2 của Nguyên

## Phạm vi đã code

Nguyên phụ trách Camera, ghi âm, TTS, SQLite, offline và API client. Phần TV2 dùng MVVM-basic và `AppContainer`; màn `DeviceCheckFragment` là nơi kiểm tra tích hợp để TV1 tham khảo, chưa phải giao diện split-screen cuối cùng. Luồng setup song song đã thêm launcher Compose và Hilt; `AppModule` cung cấp cùng dependency từ `AppContainer`, không nhân đôi database/camera/recorder.

| Phần | Điểm vào | Hành vi |
|---|---|---|
| Thẻ, phiên, lịch sử | `domain/repository/ConversationRepository` | Seed 4 ngữ cảnh; thêm/xóa thẻ cá nhân; lịch sử 2 chiều; transaction turn + usage |
| Gửi thẻ | `SendChipsUseCase(session, chips, previousIncoming)` | 1–3 thẻ, theo thứ tự chọn; API expand; fallback nguyên văn khi network/timeout/unavailable/chưa cấu hình |
| Ký hiệu | `DeviceRepository.startSignCapture/finishSignCapture`, `RecognizeSignUseCase` | Camera trước, frame trong RAM, JPEG ≤640px/250KB, 4–16 và bội 4; không phát khi UNKNOWN/độ tin cậy thấp |
| Audio | `DeviceRepository.startRecording/finishRecording`, `SummarizeSpeechUseCase` | AAC trong MPEG-4 (.m4a), tối đa 60 giây/2MB; xóa file tạm kể cả lỗi/hủy |
| Giọng đọc | `DeviceRepository.speak(sentence)` | Suspend đến khi đọc xong hoặc báo lỗi; locale vi-VN, thiếu voice hiển thị lỗi thay vì báo đã phát |
| Trả lời nhanh | `SpeakQuickReplyUseCase(session, reply)` | Lưu lượt và đọc trực tiếp, không gọi expand |
| Health | `TranslationRepository.checkHealth()` | GET /api/health; không thêm một health request vào mỗi lượt gây tăng độ trễ |

Các interface/model/usecase ở `domain` thuần Kotlin. SQLite/OkHttp/MediaRecorder/CameraX/TTS nằm trong `data`. `core/di/AppContainer` lắp dependency, `MyApp` giữ container. `core/ui/CameraPreviewController` chỉ lắp camera với lifecycle và surface của UI; ViewModel không giữ Activity/PreviewView.

## Nối vào màn TV1

1. Lấy các dependency từ `(application as MyApp).container` tại điểm lắp ghép. Inject repository interface và usecase vào ViewModel của màn hội thoại; không lấy container trong từng composable/view con.
2. UI xin CAMERA khi mở preview và RECORD_AUDIO khi người dùng muốn thu. Khi vừa cấp quyền mic, yêu cầu người dùng nhấn lại, tránh thu sau khi họ đã thả nút.
3. Bind `container.cameraPreview.bind(viewLifecycleOwner, preview.surfaceProvider, preview.display.rotation, callback)` sau khi đã có quyền. Unbind trong `onDestroyView`.
4. UI gửi sự kiện bắt đầu/kết thúc thu, thêm/xóa/chọn thẻ, gửi câu. Xem mẫu `feature/devicecheck/DeviceCheckUiEvent.kt` và `DeviceCheckViewModel.kt`.
5. Collect state bằng `viewLifecycleOwner.repeatOnLifecycle(STARTED)`. Khi rời màn hình, hủy job, `device.cancelCapture()` và `device.stopSpeaking()`; giữ dữ liệu đã lưu. Không dùng ViewModel để xin permission.
6. Hiển thị kết quả cần xác nhận bằng gloss + thẻ gợi ý. Không tự đọc `SignResult.sentence` nếu `needsConfirmation`. Màn kiểm tra cho phép lưu một thẻ gợi ý chưa có; sau đó người dùng chọn và gửi.
7. `speak` hoàn tất mới là tín hiệu “đã phát xong”; TV1 có thể rung tại thời điểm này. State mẫu có `speechCompletedCount`; nếu dùng counter cho hiệu ứng, lấy giá trị ban đầu khi bắt đầu collect để tránh rung lại sau resume. Bắt đầu mic là thời điểm TV1 phát rung báo chiều về.
8. `SpeechSummary` chứa transcript, summary, highlights, iconCode và 3 replies. Mapper bỏ highlight không nằm nguyên văn trong summary, không tự sửa số hoặc tự tóm tắt lại ở mobile. TV1 map iconCode sang icon và hỗ trợ mở transcript đầy đủ.
9. Hiển thị history từ SQLite; camera/audio không được ghi vào MessageTurn. `rawInput` là nhãn thẻ/gloss hoặc transcript; `processedText` là câu/tóm tắt thực nhận.

Với màn Compose mới, dùng repository/usecase được Hilt cung cấp vào ViewModel và collect state bằng `collectAsStateWithLifecycle()`. Preview có thể dùng `AndroidView` chứa CameraX `PreviewView`, bind theo lifecycle và unbind khi dispose. Không đưa `PreviewView`, `LifecycleOwner` hoặc `NavController` vào ViewModel. Fragment kiểm tra tự xử lý nền kín và system/IME insets để không vẽ chồng màn Compose bên dưới.

## Cấu hình trên máy mỗi người

- JDK 21, Android SDK Platform 37 (compile), minSdk 26, targetSdk 36. Build đầu tiên phát hiện `core-ktx 1.19.1` của repo cần compileSdk 37 nên đã sửa compileSdk tương ứng.
- SDK được Android Studio cấu hình qua `local.properties`; có `local.properties.example` nếu cần tự tạo.
- Base URL backend được đọc từ Gradle property `signbridge.apiBaseUrl`. Xem `gradle.properties.example`; đây là file hướng dẫn, không thay thế file chung của repo.
- Không đặt khóa AI vào Android. TV3/TV4 giữ key ở backend.
- URL mặc định trống: màn hình vẫn chạy SQLite và gửi thẻ offline; camera và ghi âm cần backend để dịch.
- Emulator thường truy cập máy host qua `http://10.0.2.2:8080/`; điện thoại thật cần IP LAN hoặc server HTTPS truy cập được. Đây là ví dụ cấu hình, không phải một backend đang chạy.
- HTTP chỉ được cho phép ở build debug. Release yêu cầu HTTPS. Đổi base URL cần rebuild APK.

Ví dụ build theo URL riêng bằng PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug "-Psignbridge.apiBaseUrl=http://10.0.2.2:8080/"
```

## Quy tắc dữ liệu và lỗi

SQLite bật foreign key, seed một lần khi tạo DB, unique nhãn theo context sau chuẩn hóa NFC/lowercase. Lưu lượt và usageCount là một transaction; xóa thẻ cá nhân không xóa lịch sử. Phiên mới đóng phiên ACTIVE cũ. Không có destructive migration: khi đổi schema phải bổ sung migration và tăng version.

Client theo hợp đồng **đề xuất** trong `luong-xu-ly-chinh.md` §4. Network, timeout, upstream error hoặc thiếu cấu hình mới cho phép fallback thẻ; lỗi 400/413/415 không được che thành thành công offline. Không giả lập summary hoặc sign khi mất mạng. Cancellation được truyền tiếp.

Cadence camera là lấy mẫu tối đa mỗi 375ms từ analyzer. Máy chậm có thể không đủ mẫu: chỉ gửi số frame thực có theo bội 4, lỗi nếu <4, không nhân bản frame để giả đủ. Camera trước hiển thị preview mirror theo CameraX, ảnh upload xoay đúng chiều sensor và không tự mirror. Cần đo tốc độ và độ chính xác ký hiệu trên máy thật.

Giới hạn bảo đảm của TV2: kiểm tra cấu trúc JSON, độ dài, enum, confidence và giữ nguyên nội dung nhận được. Mobile không chứng minh được AI/STT bảo toàn tất cả số liệu hoặc nhận đúng VSL; đó là kiểm thử tích hợp TV3/TV4 với dữ liệu thật.

## Kiểm tra thủ công trước demo

- Mở từng context, thêm thẻ cá nhân, gửi 1–3 thẻ, xác nhận thứ tự và usage; xóa một thẻ mà lịch sử vẫn còn.
- Không cấu hình URL/tắt mạng: gửi thẻ vẫn hiện nguyên văn và được đánh dấu Offline; mic/sign báo lỗi rõ ràng.
- Cho phép/từ chối/vĩnh viễn từ chối từng quyền. Permission dialog không tự bắt đầu mic sau khi thả.
- Giữ camera <1,5 giây, 1,5–6 giây, quá 6 giây; camera trước đủ sáng và tay trong khung; kiểm tra ảnh thật không lộn chiều.
- Giữ mic ngắn, 10 giây, 60 giây; app ra nền/khóa màn hình/xoay khi đang thu; không còn thu tiếp và không còn file audio tạm.
- Server trả đủ 7 mã lỗi; confidence thấp, UNKNOWN, JSON hỏng; không phát loa câu chưa xác nhận.
- Máy có/không có giọng Việt; đọc xong mới báo hoàn tất; phát lỗi vẫn giữ câu và lịch sử.
- Force-stop rồi mở lại: thẻ/lịch sử nguyên vẹn. Không uninstall/clear data vì các thao tác đó cố ý xóa dữ liệu.
- Kết nối backend AI thật, tối thiểu 3 lượt <30 giây, đọc transcript đối chiếu toàn bộ số trong summary.

Chạy kiểm tra tự động:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Test dùng server local giả chỉ để kiểm tra giao thức; không phải bằng chứng AI thật hoặc camera/mic/TTS trên điện thoại đã đạt tiêu chí demo.

Kết quả build/test/lint và các bước đã chạy trên emulator: [ket-qua-kiem-tra-tv2.md](ket-qua-kiem-tra-tv2.md).

