# Setup Tech Stack: Jetpack Compose + Material 3, Hilt DI & Dual-Dev Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cấu hình hoàn chỉnh Jetpack Compose, Material 3, Hilt Dependency Injection và thiết lập ranh giới kiến trúc song song cho 2 lập trình viên (TV1 & TV2) trên nền tảng MVVM-basic.

**Architecture:** Sử dụng mô hình Hilt Module ủy quyền (delegation pattern) qua `AppContainer` hiện có để bảo toàn 100% logic phần cứng của TV2 (0 xung đột Camera2/Mic/SQLite); thiết lập Jetpack Compose + Material 3 làm giao diện chính cho TV1; `MainActivity` vừa hỗ trợ Compose vừa cung cấp entry point mở `DeviceCheckFragment` của TV2 khi kiểm thử máy thật.

**Tech Stack:** Kotlin 2.x, Android Gradle Plugin 9.2.1, Jetpack Compose (BOM 2024.10.01), Material 3, Google Hilt 2.51.1, KSP, SQLite, OkHttp 4.12, CameraX 1.5.2.

**Spec:** [docs/superpowers/specs/2026-11-13-tech-stack-compose-hilt-design.md](file:///D:/AppData/Code/product/SignBridge/docs/superpowers/specs/2026-11-13-tech-stack-compose-hilt-design.md)

## Global Constraints

- compileSdk: 37 (tương thích hoàn toàn với `core-ktx:1.19.1` và Android API 37 đã cài đặt trên máy).
- Giữ nguyên 5 nhóm thư mục chuẩn theo `AGENTS.md`: `core`, `data`, `domain`, `feature`, `ui`.
- Tuyệt đối không làm gãy test và logic phần cứng hiện có của TV2 trong `data/` và `feature/devicecheck`.
- Không tạo 2 nguồn khởi tạo đối tượng độc lập cho Camera2/Mic/SQLite (tất cả đi qua `AppContainer`).

## Review Focus

- **Xung đột phiên bản KSP / Kotlin / Hilt:** Bản cập nhật `libs.versions.toml` phải sử dụng các phiên bản tương thích chính xác với AGP 9.2.1 và Gradle 9.4.1.
- **Lỗi biên dịch AAR metadata:** Phải kiểm tra `./gradlew checkDebugAarMetadata` trước để xác nhận việc nâng `compileSdk` lên 37 giải quyết triệt để lỗi.
- **Bảo toàn `AppContainer`:** `MyApp.kt` phải duy trì thuộc tính `container: AppContainer` để `DeviceCheckFragment` không bị NullPointer hoặc vỡ tham chiếu.
- **Hỗ trợ Fragment trong Compose Activity:** `MainActivity` phải kế thừa `AppCompatActivity` hoặc `FragmentActivity` để có `supportFragmentManager` khi gọi hiển thị `DeviceCheckFragment`.
- **Hilt ViewModel injection:** Cung cấp đầy đủ bindings trong `AppModule` cho các Repository Interfaces và UseCases để TV1 có thể `@Inject` vào ViewModel.

---

### Task 1: Cập nhật Gradle Version Catalog, Plugins & Khắc phục AAR Metadata

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: Cấu hình Gradle hiện tại (AGP 9.2.1, Gradle 9.4.1, compileSdk 36.1).
- Produces: Gradle build sạch với plugins Compose, KSP, Hilt và dependencies Compose Material 3, Hilt Android; compileSdk được nâng lên 37.

- [ ] **Step 1: Cập nhật `gradle/libs.versions.toml`**
  - Thêm các version: `kotlin = "2.1.0"`, `ksp = "2.1.0-1.0.29"`, `hilt = "2.51.1"`, `composeBom = "2024.10.01"`, `activityCompose = "1.9.3"`, `lifecycleCompose = "2.8.7"`, `hiltNavigationCompose = "1.2.0"`.
  - Khai báo libraries: `androidx-compose-bom`, `androidx-compose-ui`, `androidx-compose-ui-graphics`, `androidx-compose-ui-tooling-preview`, `androidx-compose-ui-tooling`, `androidx-compose-material3`, `androidx-activity-compose`, `androidx-lifecycle-runtime-compose`, `androidx-hilt-navigation-compose`, `hilt-android`, `hilt-compiler`.
  - Khai báo plugins: `kotlin-compose`, `ksp`, `hilt`.

- [ ] **Step 2: Cập nhật `build.gradle.kts` gốc**
  - Thêm các plugin alias: `alias(libs.plugins.kotlin.compose) apply false`, `alias(libs.plugins.ksp) apply false`, `alias(libs.plugins.hilt) apply false`.

- [ ] **Step 3: Cập nhật `app/build.gradle.kts`**
  - Apply các plugin: `alias(libs.plugins.kotlin.compose)`, `alias(libs.plugins.ksp)`, `alias(libs.plugins.hilt)`.
  - Cập nhật `compileSdk`:
    ```kotlin
    compileSdk {
        version = release(37)
    }
    ```
  - Bật Compose trong `buildFeatures`:
    ```kotlin
    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }
    ```
  - Bổ sung dependencies: Compose BOM, Compose UI, Material3, Activity Compose, Lifecycle Compose, Hilt Android, `ksp(libs.hilt.compiler)`.

- [ ] **Step 4: Chạy kiểm tra AAR Metadata và Gradle sync**
  - Chạy: `.\gradlew.bat checkDebugAarMetadata`
  - Kết quả mong đợi: `BUILD SUCCESSFUL` (hết lỗi yêu cầu Android API 37).

- [ ] **Step 5: Commit**
  - Git commit: `build: configure compose, hilt, ksp and update compileSdk to 37`

---

### Task 2: Cấu hình Hilt Dependency Injection (Delegation Bridge qua AppContainer)

**Files:**
- Modify: `app/src/main/java/com/uzuu/signbridge/MyApp.kt`
- Create: `app/src/main/java/com/uzuu/signbridge/core/di/AppModule.kt`
- Test: `app/src/test/java/com/uzuu/signbridge/core/di/AppModuleTest.kt`

**Interfaces:**
- Consumes: `AppContainer` từ `MyApp.kt` quản lý Singletons (`AppDatabase`, `SignCameraDataSource`, `AudioRecorderDataSource`, `SpeechDataSource`, Repositories, UseCases).
- Produces: Hilt `@Provides` methods cung cấp:
  - `ConversationRepository`
  - `TranslationRepository`
  - `DeviceRepository`
  - `CameraPreviewController`
  - `SendChipsUseCase`, `RecognizeSignUseCase`, `SummarizeSpeechUseCase`, `SpeakQuickReplyUseCase`

- [ ] **Step 1: Viết test kiểm tra tính toàn vẹn của Hilt Module**
  - Tạo `app/src/test/java/com/uzuu/signbridge/core/di/AppModuleTest.kt` kiểm tra các provider method của `AppModule` trích xuất đúng dependency từ `AppContainer` mà không tạo đối tượng mới.

- [ ] **Step 2: Cập nhật `MyApp.kt`**
  - Thêm annotation `@HiltAndroidApp`.
  - Giữ nguyên `val container: AppContainer by lazy { AppContainer(this) }`.

- [ ] **Step 3: Tạo `core/di/AppModule.kt`**
  - Khai báo `@Module @InstallIn(SingletonComponent::class) object AppModule`.
  - Cung cấp `provideAppContainer(@ApplicationContext context: Context): AppContainer = (context as MyApp).container`.
  - Cung cấp các repository và usecases ủy quyền trực tiếp từ `container`.

- [ ] **Step 4: Chạy kiểm tra Unit Tests**
  - Chạy: `.\gradlew.bat testDebugUnitTest`
  - Kết quả mong đợi: Toàn bộ unit tests hiện có và test mới đều pass 100%.

- [ ] **Step 5: Commit**
  - Git commit: `feat(di): setup hilt with appcontainer delegation bridge`

---

### Task 3: Xây dựng Jetpack Compose Theme & UI Foundation cho TV1

**Files:**
- Create: `app/src/main/java/com/uzuu/signbridge/ui/theme/Color.kt`
- Create: `app/src/main/java/com/uzuu/signbridge/ui/theme/Type.kt`
- Create: `app/src/main/java/com/uzuu/signbridge/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/uzuu/signbridge/feature/main/MainScreen.kt`

**Interfaces:**
- Consumes: Material 3 color schemes, Typography, Compose `setContent`.
- Produces:
  - `SignBridgeTheme(darkTheme: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit)`
  - `MainScreen(onOpenDeviceCheck: () -> Unit)`

- [ ] **Step 1: Tạo bảng màu `ui/theme/Color.kt`**
  - Khai báo các màu sắc độ tương phản cao phục vụ người khiếm thính (High-contrast, Accessibility): `PrimaryBlue`, `OnPrimaryBlue`, `SurfaceCard`, `HighlightYellow`, v.v.

- [ ] **Step 2: Tạo `ui/theme/Type.kt`**
  - Cấu hình Typography với cỡ chữ lớn, độ đậm rõ ràng phục vụ đọc từ khoảng cách đối diện 180°.

- [ ] **Step 3: Tạo `ui/theme/Theme.kt`**
  - Định nghĩa `@Composable fun SignBridgeTheme` bọc MaterialTheme với Light/Dark ColorScheme và Typography.

- [ ] **Step 4: Tạo `feature/main/MainScreen.kt`**
  - Dựng giao diện Compose khung chính:
    - TopAppBar với tiêu đề "SignBridge" và nút Action IconButton: "Test Thiết Bị (TV2)".
    - Vùng nội dung trung tâm: Thông báo chào mừng sẵn sàng cho TV1 dựng giao diện chia đôi 180° (Split-Screen).
    - Callback `onOpenDeviceCheck: () -> Unit` được gọi khi nhấn vào nút Test Thiết Bị.

- [ ] **Step 5: Chạy unit test kiểm tra compilation**
  - Chạy: `.\gradlew.bat compileDebugKotlin`
  - Kết quả mong đợi: Biên dịch thành công các Composable.

- [ ] **Step 6: Commit**
  - Git commit: `feat(ui): implement jetpack compose theme and main screen foundation`

---

### Task 4: Kết nối `MainActivity` với Jetpack Compose & Hilt

**Files:**
- Modify: `app/src/main/java/com/uzuu/signbridge/MainActivity.kt`
- Modify: `app/src/main/res/layout/activity_main.xml` (dự phòng cho Fragment container)

**Interfaces:**
- Consumes: `SignBridgeTheme`, `MainScreen`, `DeviceCheckFragment`, `@AndroidEntryPoint`.
- Produces: Màn hình chính chạy Compose, hỗ trợ chuyển đổi sang `DeviceCheckFragment` khi cần test phần cứng.

- [ ] **Step 1: Cập nhật `MainActivity.kt`**
  - Thêm annotation `@AndroidEntryPoint`.
  - Giữ kế thừa `AppCompatActivity()`.
  - Trong `onCreate`:
    - Khởi tạo Compose qua `setContent`:
      ```kotlin
      setContent {
          SignBridgeTheme {
              MainScreen(
                  onOpenDeviceCheck = {
                      supportFragmentManager.beginTransaction()
                          .replace(android.R.id.content, DeviceCheckFragment())
                          .addToBackStack(null)
                          .commit()
                  }
              )
          }
      }
      ```

- [ ] **Step 2: Chạy toàn bộ Unit Tests & Build APK**
  - Chạy: `.\gradlew.bat testDebugUnitTest`
  - Chạy: `.\gradlew.bat assembleDebug`
  - Kết quả mong đợi: `BUILD SUCCESSFUL`, tạo ra file APK debug hoàn chỉnh.

- [ ] **Step 3: Commit**
  - Git commit: `feat: integrate compose and hilt in mainactivity with device check navigation`

---

### Task 5: Kiểm tra Tổng thể & Xác minh Cuối cùng

**Files:**
- N/A (Kiểm tra nghiệm thu toàn hệ thống)

- [ ] **Step 1: Chạy toàn bộ test suite**
  - Chạy: `.\gradlew.bat testDebugUnitTest`
  - Kiểm tra xem 100% tests (gồm DeviceCheckViewModelTest, Camera/Storage/Network tests của TV2 và DI test) đều PASS.

- [ ] **Step 2: Đối chiếu checklist `AGENTS.md`**
  - Kiểm tra 5 nhóm: `core`, `data`, `domain`, `feature`, `ui`.
  - Kiểm tra tính độc lập giữa TV1 và TV2.

- [ ] **Step 3: Commit hoàn tất và chuẩn bị bàn giao**
  - Git commit (nếu có thay đổi bổ sung) hoặc xác nhận git status sạch.
