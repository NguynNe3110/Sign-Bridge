# Thiết kế Kỹ thuật: Cấu hình Tech Stack & Kiến trúc Nền tảng SignBridge
**Ngày lập:** 2026-11-13  
**Trạng thái:** Chờ phê duyệt  
**Mục tiêu:** Cấu hình Jetpack Compose, Material 3, Hilt DI và thiết lập ranh giới làm việc song song cho 2 lập trình viên (TV1 & TV2) trên nền tảng MVVM-basic.

---

## 1. Bối cảnh & Mục đích (Intent & Why)

**SignBridge** là ứng dụng Android giao tiếp hai chiều giữa người khiếm thính và người nghe (nhân viên/cộng đồng):
- **Chiều đi:** Người khiếm thính dùng ngôn ngữ ký hiệu trước camera (hoặc thẻ từ khóa dự phòng) $\rightarrow$ AI nhận diện và tạo câu nói hoàn chỉnh $\rightarrow$ phát loa TTS và hiển thị chữ lớn.
- **Chiều về:** Nhân viên nhấn giữ Mic nói chuyện $\rightarrow$ hệ thống tóm tắt ý chính bằng chữ lớn, từ khóa nổi bật và gợi ý phản hồi nhanh.

Dự án hiện có **2 lập trình viên làm việc song song**:
- **TV1 (UI/UX & Trải nghiệm tiếp cận):** Chịu trách nhiệm toàn bộ giao diện: màn hình chia đôi 180° (Split-Screen cho 2 người đối diện), hệ thống thẻ từ khóa chọn động, hiển thị chữ lớn, rung phản hồi và tương tác người dùng.
- **TV2 (Thiết bị, Dữ liệu & API):** Chịu trách nhiệm CameraX (chụp frame cử chỉ), ghi âm MediaRecorder, TextToSpeech, lưu trữ SQLite cục bộ và gọi 4 API qua OkHttp.

### Vấn đề hiện tại
1. Dự án mới khởi tạo với cấu hình XML và ViewBinding (`activity_main.xml`), **chưa có Jetpack Compose** và **chưa có Hilt DI**.
2. Gradle build đang lỗi `checkDebugAarMetadata` do xung đột giữa `compileSdk` (hiện đặt 36.1) và thư viện `androidx.core:core-ktx:1.19.1` (yêu cầu compile against Android API 37).
3. Thiếu cơ chế Dependency Injection chuẩn (Hilt) để tách rời tầng UI (TV1) khỏi tầng phần cứng/API thật (TV2), dẫn đến nguy cơ xung đột mã nguồn và chặn tiến độ của nhau.

---

## 2. Mục tiêu, Giới hạn & Tiêu chí bài trừ (Goals, Non-goals & Anti-goals)

### Mục tiêu (Goals)
1. **Tích hợp Jetpack Compose & Material 3:** Bổ sung Kotlin Compose Compiler plugin, Compose BOM, Material 3, Activity-Compose và Lifecycle-Runtime-Compose vào `libs.versions.toml` và `app/build.gradle.kts`.
2. **Tích hợp Hilt Dependency Injection:** Cấu hình KSP và Hilt Android. Thiết lập `@HiltAndroidApp` trong `MyApp.kt`, `@AndroidEntryPoint` trong `MainActivity.kt`, và các `@Module` Hilt tại `core/di/`.
3. **Khắc phục lỗi compileSdk/AAR metadata:** Đồng bộ `compileSdk` và phiên bản thư viện để `testDebugUnitTest` và `assembleDebug` build sạch 100%.
4. **Bảo toàn 100% công việc của TV2:** Giữ nguyên toàn bộ mã nguồn `data/local` (SQLite), `data/remote` (OkHttp API), `core/ui/` (CameraX preview, Audio, TTS) và `feature/devicecheck`.
5. **Cung cấp nền tảng UI cho TV1:** Xây dựng `ui/theme/` (Color, Type, Theme theo Material 3) và điểm neo `MainActivity` sẵn sàng cho TV1 dựng giao diện chia đôi 180°.
6. **Kiểm tra tự động:** Chạy thành công toàn bộ unit tests hiện có và bài test build mới.

### Giới hạn (Non-goals)
- Không triển khai chi tiết toàn bộ giao diện người dùng của TV1 trong bước này (đây là phạm vi tiếp theo của TV1).
- Không chuyển đổi SQLite sang Room hay OkHttp sang Retrofit (đã thống nhất giữ SQLite thuần và OkHttp theo lựa chọn của người dùng).
- Không sửa đổi logic thiết bị (CameraX/Audio/TTS) của TV2 trừ khi cần bổ sung Hilt `@Inject`.

### Tiêu chí bài trừ (Anti-goals)
- Tuyệt đối không làm gãy các unit test hoặc logic đã chạy được của TV2 trong `feature/devicecheck`.
- Không tạo cấu trúc phá vỡ quy ước 5 nhóm (`core`, `data`, `domain`, `feature`, `ui`) được định nghĩa trong [AGENTS.md](file:///D:/AppData/Code/product/SignBridge/AGENTS.md).
- Không để hai hệ thống DI chạy độc lập gây rối loạn vòng đời đối tượng (AppContainer sẽ được nối qua Hilt Module).

---

## 3. Ranh giới Phân công Song song (TV1 & TV2)

```
                            ┌──────────────────────────────────────────────┐
                            │                 domain/                      │
                            │ (Hợp đồng chung: Models, Repo Interfaces)    │
                            └──────────────▲────────────────▲──────────────┘
                                           │                │
                    [Inject Interface]     │                │  [Implements]
                                           │                │
               ┌───────────────────────────┴───┐       ┌────┴──────────────────────────┐
               │       NHÁNH TV1 (UI)          │       │      NHÁNH TV2 (Thiết bị/Data)│
               ├───────────────────────────────┤       ├───────────────────────────────┤
               │ • feature/conversation/       │       │ • data/local/ (SQLite)        │
               │ • feature/chips/              │       │ • data/remote/ (OkHttp API)   │
               │ • ui/component/               │       │ • core/ui/ (CameraX, Audio)   │
               │ • ui/theme/ (Material3)       │       │ • feature/devicecheck/        │
               └───────────────────────────────┘       └───────────────────────────────┘
                                               ▲       ▲
                                               │       │
                                    ┌──────────┴───────┴──────────┐
                                    │        core/di/ (Hilt)      │
                                    │   (Nối dependency tự động)  │
                                    └─────────────────────────────┘
```

| Thành phần | Trách nhiệm TV1 (UI/UX) | Trách nhiệm TV2 (Thiết bị/Data) | Quy ước tránh xung đột |
|---|---|---|---|
| `domain/` | Đọc Models, Repository Interfaces, UseCases | Cài đặt logic nghiệp vụ, khai báo DTO sang Model | Thống nhất interface trước khi code |
| `feature/` | Màn hình chính (`conversation`, `chips`, v.v.) | Màn hình kiểm tra thiết bị (`devicecheck`) | Nằm ở các package riêng biệt, không chạm file nhau |
| `ui/` | Theme, Component nút bấm, thẻ, dialog chung | Không can thiệp | Thuộc toàn quyền sở hữu của TV1 |
| `data/` | Không can thiệp | SQLite, DAO, API Client, Repository Impl | Thuộc toàn quyền sở hữu của TV2 |
| `core/di/` | Nhận dependency tự động qua `@Inject` | Cung cấp binding implementation trong Module | Cấu hình sẵn bởi bản thiết kế này |

---

## 4. Các phương án kiến trúc & Đánh giá (Approaches)

### Phương án 1: Triển khai trọn gói Compose + Hilt + Fix compileSdk (ĐÃ CHỌN)
- **Mô tả:** Cập nhật đồng bộ Version Catalog (`libs.versions.toml`), kích hoạt Compose Compiler, KSP, Hilt Android, nâng `compileSdk` lên 37 (máy local đã cài sẵn `android-37.0`), tạo Hilt Modules tại `core/di/`, chuyển `MainActivity` sang `ComponentActivity` + Compose `setContent`.
- **Ưu điểm:** Dự án đồng nhất ngay từ đầu, cả TV1 và TV2 đều có thể code trên nền tảng hoàn chỉnh mà không phải refactor lại lần 2.
- **Nhược điểm:** Cần kiểm tra kỹ tính tương thích phiên bản plugin Gradle trong một lần build.

### Phương án 2: Triển khai 2 pha (Compose trước, Hilt sau)
- **Mô tả:** Cấu hình Compose trước với `AppContainer` hiện có; sau đó mới thêm Hilt.
- **Lý do loại bỏ:** Làm tăng thời gian chờ của 2 dev, TV1 sẽ phải viết ViewModel theo cú pháp factory cũ rồi lại phải sửa sang `@HiltViewModel`.

### Phương án 3: Giữ nguyên XML ViewBinding
- **Mô tả:** Tiếp tục dùng Layout XML.
- **Lý do loại bỏ:** UI của SignBridge yêu cầu xoay ngược 180° một nửa màn hình, các danh sách thẻ động và phản hồi rung/âm thanh realtime. Jetpack Compose làm việc này sạch hơn, ít boilerplate hơn và tuân thủ định hướng dự án mới trong `AGENTS.md`.

---

## 5. Thiết kế Kỹ thuật Chi tiết

### 5.1. Cập nhật Gradle & Version Catalog (`gradle/libs.versions.toml`)

1. **Phiên bản (versions):**
   - Kotlin: đồng bộ với Compose Compiler (`2.1.0` hoặc dùng Compose Compiler plugin tích hợp sẵn của Kotlin 2.x).
   - KSP: phiên bản tương thích với Kotlin.
   - Hilt: `2.51.1` (hoặc mới nhất ổn định).
   - Compose BOM: `2024.10.01` (hoặc tương đương).
2. **Thư viện (libraries):**
   - `androidx-compose-bom`: `androidx.compose:compose-bom`
   - `androidx-compose-ui`: `androidx.compose.ui:ui`
   - `androidx-compose-ui-graphics`: `androidx.compose.ui:ui-graphics`
   - `androidx-compose-ui-tooling-preview`: `androidx.compose.ui:ui-tooling-preview`
   - `androidx-compose-ui-tooling`: `androidx.compose.ui:ui-tooling`
   - `androidx-compose-material3`: `androidx.compose.material3:material3`
   - `androidx-activity-compose`: `androidx.activity:activity-compose`
   - `androidx-lifecycle-runtime-compose`: `androidx.lifecycle:lifecycle-runtime-compose`
   - `androidx-hilt-navigation-compose`: `androidx.hilt:hilt-navigation-compose`
   - `hilt-android`: `com.google.dagger:hilt-android`
   - `hilt-compiler`: `com.google.dagger:hilt-android-compiler`
3. **Plugins:**
   - `kotlin-compose`: `{ id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }`
   - `ksp`: `{ id = "com.google.devtools.ksp", version.ref = "ksp" }`
   - `hilt`: `{ id = "com.google.dagger.hilt.android", version.ref = "hilt" }`

### 5.2. Cấu hình `app/build.gradle.kts`
- Nâng `compileSdk` lên 37 (để tương thích hoàn toàn với `core-ktx:1.19.1` và AAR metadata).
- `buildFeatures { compose = true; viewBinding = true; buildConfig = true }` (vẫn giữ viewBinding để TV2 có thể chạy `activity_main.xml` hoặc `devicecheck` nếu cần).
- Bổ sung `ksp(libs.hilt.compiler)` và các dependencies của Compose + Hilt.

### 5.3. Cấu hình Hilt Dependency Injection (`core/di/`) theo Delegation Pattern

Để đảm bảo **không xung đột phần cứng** (Camera2/Mic không bị mở 2 lần) và **không làm hỏng code `DeviceCheckFragment` hiện có của TV2**:
1. **Single Source of Truth:** `AppContainer` trong `MyApp.kt` tiếp tục là nơi quản lý instance duy nhất (Singleton) cho Database, Camera, Audio, Speech và Repositories.
2. `MyApp.kt`:
   ```kotlin
   @HiltAndroidApp
   class MyApp : Application() {
       val container: AppContainer by lazy { AppContainer(this) }
   }
   ```
3. `core/di/AppModule.kt`:
   - `@Module @InstallIn(SingletonComponent::class)`
   - Cung cấp `AppContainer` từ `(context as MyApp).container`.
   - Cung cấp `ConversationRepository`, `TranslationRepository`, `DeviceRepository`, `CameraPreviewController`.
   - Cung cấp các UseCase: `SendChipsUseCase`, `RecognizeSignUseCase`, `SummarizeSpeechUseCase`, `SpeakQuickReplyUseCase`.
4. Nhờ đó:
   - TV2 vẫn dùng `(requireActivity().application as MyApp).container` bình thường trong `DeviceCheckFragment`.
   - TV1 có thể dùng `@HiltViewModel` và inject trực tiếp các repository/usecase mà không cần quan tâm đến khởi tạo phần cứng bên dưới.

### 5.4. Cấu hình Jetpack Compose Theme (`ui/theme/`)
Thiết lập hệ thống Theme chuẩn:
- `Color.kt`: Bảng màu tương phản cao phục vụ người khiếm thính (High-contrast, Accessibility-friendly).
- `Type.kt`: Typography với font size lớn, rõ ràng.
- `Theme.kt`: `SignBridgeTheme` hỗ trợ Dynamic Color (Android 12+) và Dark/Light mode.

### 5.5. Cấu hình Activity & Điểm neo khởi chạy
- `MainActivity` kế thừa `AppCompatActivity` và gắn `@AndroidEntryPoint` (vừa hỗ trợ Jetpack Compose `setContent`, vừa hỗ trợ quản lý Fragment).
- Gắn `setContent { SignBridgeTheme { MainScreen(onOpenDeviceCheck = { ... }) } }`.
- Trong `MainScreen`, cung cấp một nút bấm trên TopBar: **"Kiểm tra thiết bị (TV2)"** để chuyển sang hiển thị `DeviceCheckFragment` khi cần test phần cứng trên máy thật. Nhờ đó, cả TV1 (UI Compose) và TV2 (Phần cứng/Data) đều kiểm thử độc lập được trên cùng một bản build app.

---

## 6. Kế hoạch Xác minh & Nghiệm thu (Verification Plan)

| Bước kiểm tra | Lệnh / Thao tác | Tiêu chí đạt |
|---|---|---|
| 1. Kiểm tra cấu hình Gradle | `./gradlew help` | BUILD SUCCESSFUL |
| 2. Kiểm tra AAR Metadata & SDK | `./gradlew checkDebugAarMetadata` | Không còn lỗi yêu cầu API 37 |
| 3. Chạy toàn bộ Unit Tests | `./gradlew testDebugUnitTest` | Toàn bộ unit test hiện tại của TV2 pass 100% |
| 4. Kiểm tra biên dịch đầy đủ | `./gradlew assembleDebug` | Biên dịch thành công APK debug |
| 5. Kiểm tra tính độc lập | Kiểm tra `feature/devicecheck` và `core/di` | Cả 2 hệ thống DI / UI đều có thể chạy mà không xung đột |
