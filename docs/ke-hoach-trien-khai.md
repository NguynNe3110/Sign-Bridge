# Kế hoạch triển khai SignBridge

## Kết luận và nguồn

SignBridge hỗ trợ hội thoại hai chiều trên một điện thoại: ký hiệu/thẻ → câu nói và giọng nói → tóm tắt. MVP phải lưu thẻ và lịch sử sau khi đóng ứng dụng, giữ nguyên thông tin định lượng, hỗ trợ thẻ khi mất mạng.

Nguồn đối chiếu: `Mo_ta_y_tuong.docx` §5–8, §11–15; `luong-xu-ly-chinh.md` §2–7; `phan-cong.md` §3–6; `AGENTS.md`. Nguyên xác nhận phụ trách **TV2** trong phiên làm việc này.

| Quyết định | Trạng thái | Hệ quả triển khai |
|---|---|---|
| Kotlin/Android, package `com.uzuu.signbridge` | Đã xác định từ repo | Không đưa Expo/React Native vào Android; TV2 giữ màn kiểm tra Fragment/XML |
| MVVM-basic, `AppContainer` | Quy ước tại thời điểm bắt đầu | `feature` → usecase/repository interface → `data`; nối tại `core/di` |
| Nguyên = TV2 | Người dùng xác nhận | Triển khai thiết bị, storage, client API và bàn giao cho TV1 |
| `/api/health`, `/api/sign`, `/api/expand`, `/api/summarize` | Đề xuất từ Markdown | Client theo hợp đồng này, backend cần xác nhận; không coi đã có server chạy |
| 4 frame/1,5 giây; 4–16 frame; ngưỡng 0,7 | Đề xuất từ Markdown | Đóng gói thành policy để đổi khi team chốt |
| Nhận diện ký hiệu là đầu vào chính | Quyết định ghi trong tài liệu luồng | Camera trước, có thẻ dự phòng; không tuyên bố nhận diện đạt chất lượng khi chưa đo |
| Chia đôi 180°, màn quản lý thẻ/lịch sử hoàn chỉnh | Phạm vi TV1 | TV2 cung cấp state/event và màn tích hợp tối thiểu; TV1 hoàn thiện trải nghiệm |

Trong tài liệu gốc, camera là “nên có”; tài liệu luồng nâng camera thành đầu vào chính. Kế hoạch giữ camera theo quyết định mới, nhưng ưu tiên đường thẻ/offline luôn sử dụng được. Những chỗ nói “backend repo này” và `expo-*` trong docs cũ không mô tả repo Android hiện tại.

Trong lúc triển khai, luồng setup song song bổ sung Compose/Hilt trên nhánh `setup/tech-stack-compose-hilt`; Nguyên xác nhận tiếp tục phối hợp trên nhánh này. Launcher mới dùng Compose và mở màn kiểm tra TV2 qua Fragment. `AppModule` của luồng setup cung cấp lại dependency từ cùng `AppContainer`; không tạo graph thiết bị/storage thứ hai. Kế hoạch TV2 giữ nguyên các interface/usecase thuần Kotlin để TV1 dùng từ Compose.

## Yêu cầu và nghiệm thu

| ID | Yêu cầu / nguồn | Người thực hiện | Điều kiện nghiệm thu |
|---|---|---|---|
| REQ-01 | 4 ngữ cảnh và thẻ 1–3 lựa chọn; docx §5, §7 | TV1 + TV2 | Given ngữ cảnh, When đổi ngữ cảnh, Then chỉ hiện thẻ thuộc ngữ cảnh đó |
| REQ-02 | SQLite 4 thực thể; docx §11 | TV2 | Given thẻ cá nhân và lượt hội thoại đã lưu, When force-stop rồi mở lại, Then dữ liệu vẫn còn |
| REQ-03 | Cá nhân hóa và usageCount; docx §5.4, §11 | TV2 | Given thẻ cá nhân, When thêm/xóa/gửi, Then dữ liệu hợp lệ được lưu và usageCount tăng đúng lượt |
| REQ-04 | Camera trước; luồng §4.2, §6 | TV2 | Given quyền camera, When giữ 1,5–6 giây, Then lấy frame đúng thứ tự, số lượng bội 4, cạnh ≤640px, JPEG ≤250KB |
| REQ-05 | Ghi âm push-to-talk; docx §5.3, luồng §4.4 | TV2 | Given quyền mic, When giữ và thả, Then gửi m4a ≤60 giây/2MB; hủy khi app ra nền; xóa file tạm |
| REQ-06 | TTS tiếng Việt; docx §5.2 | TV2 | Given câu hợp lệ, When phát, Then báo hoàn tất qua state/callback; thiếu voice thì hiện chữ và báo lỗi |
| REQ-07 | Client 4 API, timeout 10 giây; luồng §4 | TV2 + TV4 | Given server tương thích, When gọi, Then request/response đúng field và lỗi có mã; cancel không bị biến thành lỗi mạng |
| REQ-08 | Offline; docx §6.1 | TV2 | Given mạng lỗi/quá hạn/AI unavailable, When gửi thẻ, Then ghép nguyên văn thành câu mẫu và lưu lượt offline; không giả lập kết quả AI |
| REQ-09 | Tin cậy nhận diện; luồng §7.4 | TV2 + TV3/4 | Given confidence thấp/UNKNOWN, When nhận response, Then không tự phát câu, hiện gloss/thẻ để xác nhận |
| REQ-10 | Tóm tắt và phản hồi nhanh; docx §6 | TV1 + TV2 + TV3/4 | Given response hợp lệ, Then hiển thị transcript/summary/highlights và 3 replies; chọn reply phát trực tiếp, không gọi expand |
| REQ-11 | Chia đôi 180°, chữ lớn, rung; docx §5.1, §6.1 | TV1 | Given hai người đối diện, Then mỗi người đọc đúng chiều; rung khi bắt đầu thu/phát xong |
| REQ-12 | Demo thật; docx §15 | Cả nhóm | Hội thoại ≥3 lượt <30 giây, AI thật không sai số, dữ liệu còn sau force-stop |

## Thứ tự code

1. **Hợp đồng domain và policy (TV2):** model context/chip/session/turn/result; repository interfaces; validation frame/audio; usecase phối hợp API, lưu lượt và tăng usage. Domain thuần Kotlin.
2. **SQLite (TV2):** `data/local/AppDatabase.kt`, DAO, seed 4 context + thẻ mẫu, transaction khi lưu turn và tăng usage; không xóa lịch sử khi xóa thẻ. Không lưu ảnh/audio dài hạn.
3. **API (TV2 + TV4):** OkHttp cancellable, JSON/multipart theo docs, mapper DTO → domain, lỗi 7 mã + network/configuration; base URL lấy từ Gradle property local, không hardcode địa chỉ máy của Nguyên.
4. **Thiết bị (TV2):** CameraX preview + analyzer lấy frame; MediaRecorder AAC/MPEG-4; TextToSpeech vi-VN; dừng khi background và giải phóng theo lifecycle.
5. **Wiring và bàn giao (TV2 → TV1):** `MyApp`, `core/di/AppContainer`; `feature/devicecheck` có ViewModel/UiState/UiEvent, collect theo lifecycle, chạy được các thao tác thật trên màn kiểm tra tối thiểu.
6. **Giao diện sản phẩm (TV1):** chọn ngữ cảnh → split-screen; quản lý thẻ/lịch sử; trạng thái quyền, thu, xử lý, lỗi, offline; giữ các hợp đồng của TV2.
7. **Tích hợp AI/backend (TV3/4):** chốt schema/model, không đưa API key AI vào APK; xác minh số liệu, độ trễ, thử với người dùng VSL; field evidence theo phân công.

## Luồng state và lỗi

`Idle → CapturingSign/Recording → Processing → Ready/NeedsConfirmation/Error`. Chỉ một thao tác thiết bị/API diễn ra tại một thời điểm. Đổi ngữ cảnh không được trộn session hoặc callback của lượt cũ. Ra nền hủy thu và tác vụ đang chạy; coroutine cancellation phải được truyền tiếp. TTS xong phát trạng thái hoàn tất, TV1 quyết định rung/hiệu ứng theo lifecycle.

Lỗi network/timeout/AI unavailable cho phép gửi **thẻ** bằng câu mẫu offline. Camera lỗi/thiếu quyền dẫn về thẻ. Mic lỗi/không có tiếng nói yêu cầu thử lại; không tạo transcript giả khi offline. HTTP 400/413/415 báo lỗi đầu vào, không che bằng offline. Response ký hiệu không chắc không lưu/phát như một câu đã xác nhận. Lịch sử chỉ lưu các lượt đã có nội dung thực tế.

## Phối hợp và cấu hình

- TV1 nhận model domain, `ConversationRepository`, `DeviceRepository`, `TranslationRepository`, các usecase và ví dụ `DeviceCheckViewModel`; không gọi API/SQLite/MediaRecorder trực tiếp từ UI.
- Quyền CAMERA/RECORD_AUDIO do UI xin đúng lúc; bind preview tại điểm nối lifecycle, ViewModel không giữ Activity/PreviewView/NavController.
- `gradle.properties` chung không chứa đường dẫn JDK/SDK cá nhân; `local.properties` dành cho SDK. `signbridge.apiBaseUrl` nằm trong Gradle properties của người dùng hoặc truyền `-P`; file mẫu không chứa secret.
- Server phát triển HTTP chỉ cho phép trong debug; bản release dùng HTTPS. URL trống nghĩa là chưa cấu hình, thẻ vẫn dùng được offline.
- Phần xác minh cần máy thật: camera trước, quyền bị từ chối, Vietnamese TTS, cử chỉ chuyển động, tiếng ồn, background/rotation, force-stop, mạng thật, thời gian demo.

## Checklist kiểm tra TV2

- Unit test policy: số frame, bội 4, kích thước, thời lượng; UNKNOWN/confidence; fallback đúng loại lỗi; cancel không bị nuốt; lưu và usage đúng một lần; reply không gọi API.
- Integration test SQLite: seed idempotent, thêm/xóa thẻ, transaction, đóng/mở DB giữ nguyên lịch sử.
- Client test: JSON UTF-8, multipart fields, error code và response hỏng với local mock server.
- Chạy build debug, unit tests và lint. Báo chính xác giới hạn nếu không có emulator/điện thoại; không coi test JVM là bằng chứng camera/AI thật hoạt động.

Kết quả thực hiện TV2 và phần còn cần kiểm tra với backend/điện thoại thật: [ket-qua-kiem-tra-tv2.md](ket-qua-kiem-tra-tv2.md).
