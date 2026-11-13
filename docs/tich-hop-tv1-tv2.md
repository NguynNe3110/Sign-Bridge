# Tích hợp TV1 và TV2

## Phạm vi

Nhánh tích hợp `integration/tv1-tv2` được tạo từ `origin/feature/tv1-mobile-ui` (178deb8), rồi merge `origin/main` (3909761). Không sửa lịch sử hoặc push lên nhánh TV1/main trong quá trình xử lý local.

Giữ nguyên bố cục split-screen 180°, theme, màn chọn context, quản lý thẻ và navigation của TV1. Giữ SQLite, schema v1, ID String, API client, camera, audio, TTS và usecase của TV2. Chỉ sửa các điểm hợp đồng/wiring cần để hai phần chạy cùng nhau. Không ghép thêm nhánh setup local chưa có trên main.

## Các conflict và cách chọn

| File Git báo conflict | Cách giải quyết |
|---|---|
| `app/build.gradle.kts` | Kết hợp Compose TV1 với CameraX/OkHttp/coroutines/test/buildConfig/viewBinding TV2; giữ compileSdk 37 và URL cấu hình local |
| `gradle/libs.versions.toml` | Một catalog; giữ dependency Compose/navigation TV1 và thiết bị/storage TV2; lifecycle thống nhất 2.10.0 |
| `AndroidManifest.xml` | Một Application; giữ quyền camera/mic/internet của TV2 và rung của TV1; backup dữ liệu vẫn tắt |
| `MyApp.kt` | Dùng một Application và một `AppContainer(applicationContext)` |
| `core/di/AppContainer.kt` | Một graph thật của TV2, bổ sung factory cho ViewModel TV1; không dùng fake/in-memory ở production |
| `domain/repository/ConversationRepository.kt` | Giữ hợp đồng storage TV2, bổ sung observeChips để hai màn cùng nhận cập nhật SQLite |
| `res/layout/activity_main.xml` | Giữ như tài nguyên của bộ kiểm tra Fragment cũ; entry sản phẩm vẫn là Compose MainActivity TV1 |

Git không báo conflict với các file khác tên nhưng khai báo cùng class. Các định nghĩa QuickChip, MessageTurn, SignResult và Highlight trùng đã được gộp về model TV2. `ContextType`, `SummaryResult` và `TurnDirection` của TV1 là typealias tới model chung, không tạo lớp nghiệp vụ thứ hai. Các ID UI/test dùng String theo SQLite. Các mã icon/highlight vẫn đúng chuỗi API.

## Wiring sau khi ghép

- ConversationViewModel TV1 gọi usecase TV2; không gọi repository giả hoặc tự lưu lượt lần hai.
- SendChipsUseCase giữ một nơi xử lý API/fallback/lưu lượt/tăng usage trong transaction. Thẻ gợi ý chưa có trong DB được gửi theo nhãn đã xác nhận; chỉ tăng usage cho ID thực sự có, không tự thêm thẻ cá nhân.
- ChipManageViewModel dùng cùng ConversationRepository SQLite. Observe nhận cập nhật sau thêm/xóa/gửi; quay lại màn hội thoại không cần một kho thẻ thứ hai.
- Camera/mic permission và preview lifecycle nằm ở ConversationDevices trong feature; ViewModel chỉ giữ interface và state.
- Preview nhỏ chỉ mở khi người dùng muốn ghi ký hiệu. Cấp quyền xong cần nhấn giữ lại; không tự thu sau khi đã thả nút.
- Nhấn-giữ bị hủy không được gửi audio/frame. Ra nền/đi màn khác hủy job, camera/audio capture và TTS. Giới hạn thu ký hiệu 6 giây, audio 60 giây theo TV2.
- Câu offline được đánh dấu rõ, giữ nguyên thẻ; lỗi TTS không làm mất câu/lịch sử và không phát rung “đã đọc xong”.
- Các fake của TV1 đã bỏ khỏi src/main. Test doubles chỉ nằm trong src/test.
- DeviceCheckFragment cũ là bộ kiểm tra nội bộ, không thay thế hoặc tạo thêm luồng sản phẩm cạnh UI TV1.

## Kiểm tra

Lệnh:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

Kết quả: build PASS; **45 tests PASS**, bao gồm 4 test tích hợp ViewModel TV1 với SQLite thật; lint 0 errors, 24 warnings. Không xóa test TV1: cập nhật fixture sang hợp đồng thật; test xóa thẻ được đổi sang thẻ cá nhân vì TV2 bảo vệ thẻ seed.

Đã chạy trên emulator Pixel_4 Android 10/API 29 read-only:

- UI chọn context và split-screen giữ bố cục TV1, không crash.
- Chọn thẻ → gửi offline: đúng một lượt OUTGOING, isOffline=1, usageCount=1 trong SQLite.
- Thêm thẻ ở màn quản lý → quay lại hội thoại: nhận thẻ mới qua cùng repository.
- Camera trước: xin quyền, hiện preview; giữ 3 giây lấy/nén/validate ảnh rồi báo thiếu server, không giả lập nhận diện.
- Mic: xin quyền không tự ghi; giữ 3 giây xử lý audio rồi báo thiếu server, thư mục audio tạm rỗng.
- Bấm Home khi đang thu mic: có file trong lúc thu, ra nền thì file được xóa; không có AndroidRuntime crash.

Chưa có backend AI thật nên chưa xác minh chất lượng VSL/STT/tóm tắt hoặc tốc độ hội thoại. TTS thành công cần giọng Việt trên thiết bị thật. Đây không phải báo cáo sản phẩm đã đạt nghiệm thu end-to-end.

## Cách cập nhật PR

Sau khi review nhánh tích hợp, có hai cách; chọn một:

1. Cập nhật PR TV1 đang có bằng fast-forward (không force):
   `git push origin integration/tv1-tv2:feature/tv1-mobile-ui`.
2. Push nhánh tích hợp rồi tạo PR mới vào main:
   `git push -u origin integration/tv1-tv2`. PR này thay thế PR TV1 cũ.

Nhánh tích hợp chứa cả hai lịch sử. Kiểm tra lại main/TV1 có commit mới trước khi push; nếu có, merge nhánh mới và kiểm tra lại, không force-push đè việc của người khác.

## Ranh giới để làm tiếp

| Người phụ trách | Phạm vi tự sửa | Điểm cần phối hợp |
|---|---|---|
| TV1 | feature/context, feature/chip và phần render feature/conversation, ui/component, ui/theme, ui/icon, navigation | Không tự tạo repository implementation, model/ID/schema thay thế TV2 |
| TV2 (Nguyên) | data, domain repository/usecase/model, thiết bị, SQLite/API, core/di | Báo trước khi đổi chữ ký interface hoặc state/event mà UI đang dùng |
| Hai bên | MainActivity, MyApp, AppContainer, Gradle, manifest, hợp đồng domain, wiring ConversationViewModel | Chốt người sửa trước; cập nhật main vào nhánh trước khi bắt đầu thay file chung |

Mỗi PR nên ghi các file chung bị chạm và thay đổi hợp đồng. Tách nhánh giúp giữ lịch sử độc lập; thống nhất hợp đồng mới giúp tránh sửa lại khi merge.
