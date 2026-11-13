# Quy ước tổ chức code Kotlin/Android theo MVVM-basic

AI phải dùng tài liệu này khi tạo hoặc sửa code theo base `MVVM-basic`. Mục tiêu là giữ đúng vị trí file, trách nhiệm từng tầng và luồng dữ liệu, đồng thời giữ kiến trúc đơn giản.

## 1. Nguyên tắc bắt buộc

- Tổ chức package ứng dụng thành 5 nhóm: `core`, `data`, `domain`, `feature`, `ui`. Giữ cách viết số ít như `feature`, `repository`, `usecase`, `component`.
- Tổ chức `data` và `domain` dùng chung ở cấp ứng dụng; tổ chức phần màn hình theo từng tính năng trong `feature`.
- Không tự chuyển sang cấu trúc `presentation/data/domain` bên trong từng feature, đổi thành `features`, hoặc tách Gradle module theo tầng/tính năng khi yêu cầu chỉ là phát triển theo base này.
- Với dự án mới, mặc định dùng Jetpack Compose. Với dự án đang dùng Fragment/XML, tiếp tục công nghệ hiện có trong phạm vi tác vụ; chỉ chuyển đổi khi yêu cầu có phần chuyển đổi UI.
- Chỉ tạo file và thư mục thực sự cần cho tính năng. Cây bên dưới là bản đồ vị trí, không phải yêu cầu sinh tất cả file rỗng.
- Đọc code và cấu hình dự án đích trước khi sửa. Tái sử dụng implementation, dependency và cách đặt tên đã có nếu vẫn giữ đúng trách nhiệm của các tầng.
- Khi gặp code cũ lệch quy ước, tổ chức phần thay đổi đúng phạm vi; không tự di chuyển toàn bộ dự án.

## 2. Xác định source root và package

Trong dự án Android, đặt code dưới source root thực tế, thường là:

```text
app/src/main/java/<base-package>/
```

Source root `kotlin/` cũng hợp lệ nếu dự án đang dùng. Thay `<base-package>` bằng package thật, ví dụ `com.example.accountmanager` tương ứng `com/example/accountmanager/`. Đọc `namespace`, manifest và package của source hiện có; không lấy `applicationId` làm căn cứ duy nhất.

`Kotlin_MVVM/` trong bộ mẫu chỉ là thư mục minh họa cấu trúc, không phải tên module hoặc package bắt buộc. Không giữ nguyên các package mẫu `com.example`, `com.uzuu.petrolimex`, `com.uzuu.sobe` khi đưa code vào ứng dụng khác.

## 3. Cây cấu trúc chuẩn

Các tên `Login`, `Register`, `Home` là ví dụ. Thêm tính năng mới theo cùng cách tổ chức.

```text
<base-package>/
├── MyApp.kt
├── core/
│   ├── di/
│   │   └── AppContainer.kt
│   ├── dispatcher/
│   ├── extension/
│   ├── result/
│   └── ui/
├── data/
│   ├── local/
│   │   ├── dao/
│   │   ├── datasource/
│   │   ├── entity/
│   │   └── AppDatabase.kt
│   ├── remote/
│   │   ├── api/
│   │   ├── datasource/
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   ├── response/
│   │   │   └── BaseResponseDto.kt
│   │   ├── AuthInterceptor.kt
│   │   └── RetrofitProvider.kt
│   ├── mapper/
│   ├── repository/
│   └── session/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── feature/
│   ├── MainActivity.kt
│   ├── main/
│   │   └── MainScreen.kt
│   ├── auth/
│   │   ├── login/
│   │   │   ├── LoginScreen.kt
│   │   │   ├── LoginViewModel.kt
│   │   │   ├── LoginUiState.kt
│   │   │   ├── LoginUiEvent.kt
│   │   │   └── LoginUiEffect.kt
│   │   └── register/
│   │       ├── RegisterScreen.kt
│   │       ├── RegisterViewModel.kt
│   │       ├── RegisterUiState.kt
│   │       └── RegisterUiEvent.kt
│   └── home/
│       ├── HomeScreen.kt
│       ├── HomeViewModel.kt
│       ├── HomeUiState.kt
│       └── HomeUiEvent.kt
└── ui/
    ├── adapter/
    ├── component/
    │   ├── button/
    │   │   ├── AppButton.kt
    │   │   └── AppIconButton.kt
    │   ├── input/
    │   │   ├── AppTextField.kt
    │   │   └── AppSearchBar.kt
    │   ├── surface/
    │   │   ├── AppCard.kt
    │   │   └── AppBottomSheet.kt
    │   └── navigation/
    │       ├── AppTopBar.kt
    │       └── AppBottomNav.kt
    ├── dialog/
    ├── icon/
    │   ├── AppIcons.kt
    │   └── VectorIcons.kt
    ├── navigation/
    │   ├── Routes.kt
    │   ├── NavGraphs.kt
    │   └── NavHost.kt
    └── theme/
        ├── AppTheme.kt
        ├── Color.kt
        ├── Dimens.kt
        ├── Shape.kt
        └── Type.kt
```

`MyApp.kt` và `MainActivity.kt` là các điểm khởi tạo, đặt ở vị trí trên khi tạo mới. `MainScreen.kt` chỉ cần khi có giao diện vỏ ứng dụng, ví dụ màn chứa bottom navigation. Với XML, dùng `<Name>Fragment.kt` và layout trong `res/layout/`; `ui/adapter/` dành cho RecyclerView, không cần tạo trong ứng dụng chỉ dùng Compose.

`LoginUiEffect.kt`, data source, interceptor, `BaseResponseDto.kt` và các thành phần hạ tầng chỉ được tạo khi thực sự có nhu cầu. Không giả định API luôn có response bọc chung.

## 4. Trách nhiệm và ranh giới từng nhóm

| Vị trí | Đặt gì ở đây | Giới hạn |
| --- | --- | --- |
| `core/di` | Lắp ghép dependency, container, module/provider DI | Không xử lý nghiệp vụ |
| `core/dispatcher` | Cung cấp dispatcher cho coroutine khi cần | Không chứa logic màn hình |
| `core/extension` | Extension dùng chung, tên thể hiện rõ mục đích | Không gom mọi helper vào một file |
| `core/result` | Kiểu kết quả/lỗi chung nếu dự án cần | Phần được `domain` dùng phải thuần Kotlin |
| `core/ui` | Hạ tầng UI chung, ví dụ helper lifecycle, lớp nền có nhu cầu thật | Component hiển thị đặt trong `ui/component`, không ở đây |
| `data/local` | Room database, DAO, entity, nguồn dữ liệu cục bộ | Không trả entity thẳng cho UI |
| `data/remote` | Khai báo API, DTO, nguồn dữ liệu mạng, cấu hình client | Không chứa state hoặc điều hướng màn hình |
| `data/mapper` | Chuyển DTO/entity sang domain model và ngược lại khi cần | Không phụ thuộc `feature` hoặc Compose |
| `data/repository` | Implementation của interface trong `domain/repository` | Không để interface nghiệp vụ ở đây |
| `data/session` | Cài đặt lưu/đọc/xóa phiên, token, thông tin đăng nhập | UI không đọc token hoặc storage trực tiếp |
| `domain/model` | Mô hình nghiệp vụ dùng độc lập với nguồn dữ liệu | Không chứa Retrofit/Room annotation hoặc kiểu UI |
| `domain/repository` | Hợp đồng thao tác dữ liệu phục vụ nghiệp vụ | Không phụ thuộc repository implementation hoặc DTO/entity |
| `domain/usecase` | Một hành động/quy tắc nghiệp vụ cụ thể | Không phụ thuộc Android, ViewModel hoặc UI |
| `feature/<...>` | Screen/Fragment, ViewModel, UiState, UiEvent, UiEffect và UI riêng của tính năng | Không chứa API, DAO, repository implementation |
| `ui/component` | Thành phần hiển thị tái sử dụng giữa các tính năng | Nhận dữ liệu/callback; không gọi repository hoặc giữ ViewModel tính năng |
| `ui/dialog`, `ui/icon` | Dialog và biểu tượng dùng chung | Dialog riêng của một màn có thể ở ngay feature đó |
| `ui/navigation` | Route, graph và lắp ghép màn hình vào NavHost | Không chứa màu, typography hoặc logic truy xuất dữ liệu |
| `ui/theme` | Theme, màu, typography, shape, spacing | Không chứa route, API hoặc nghiệp vụ |

`domain` chỉ dùng Kotlin và các thư viện thuần Kotlin phù hợp như coroutines; có thể dùng phần thuần Kotlin của `core/result`. Không import `android.*`, `androidx.*`, Retrofit, Room, `data`, `feature` hoặc `ui` vào `domain`.

## 5. Chiều phụ thuộc và luồng xử lý

Đây là quy ước cần triển khai, không phải khẳng định bộ mẫu đã có một luồng chạy hoàn chỉnh.

```text
Screen/Fragment -> ViewModel -> UseCase (khi cần) -> Repository interface
Repository implementation -> API / DAO / data source / session
Repository implementation -> mapper -> domain model
ViewModel nhận kết quả -> cập nhật UiState -> UI hiển thị lại
```

- `ViewModel` và `UseCase` phụ thuộc interface trong `domain/repository`. DI nối interface với implementation trong `data/repository`.
- `data` phụ thuộc `domain` để thực hiện hợp đồng và trả domain model. `domain` không phụ thuộc `data`.
- UI và ViewModel không gọi trực tiếp Retrofit, DAO, database, token storage hoặc repository implementation.
- `ui/component` và `ui/theme` không phụ thuộc feature cụ thể. Riêng `ui/navigation` được tham chiếu các màn hình để lắp graph.
- `MyApp`, `MainActivity` và `core/di` được tham chiếu các thành phần cần thiết để khởi tạo ứng dụng. Đây là nơi lắp ghép dependency.

### Khi nào cần UseCase?

- Tạo UseCase khi có quy tắc nghiệp vụ, phối hợp nhiều thao tác/repository hoặc hành động cần tái sử dụng ở nhiều ViewModel.
- Với thao tác đơn giản chỉ đọc/ghi qua một repository và chưa có quy tắc riêng, ViewModel có thể gọi repository interface trực tiếp. Đây vẫn là MVVM cơ bản phù hợp base.
- Nếu dự án đích đã thống nhất mọi thao tác qua UseCase, tiếp tục quy ước đó.
- Không tạo hàng loạt UseCase chỉ để bọc một lời gọi; cũng không dồn nghiệp vụ phức tạp vào ViewModel để bỏ qua tầng này.

## 6. Quy ước cho một màn hình

Với màn có state và tương tác, dùng `<Name>Screen.kt`, `<Name>ViewModel.kt`, `<Name>UiState.kt`, `<Name>UiEvent.kt` trong cùng thư mục. Màn tĩnh có thể chỉ cần Screen. Tạo `<Name>UiEffect.kt` khi có hiệu ứng một lần cần truyền từ ViewModel ra UI.

Quy ước mặc định cho code mới:

| Thành phần | Trách nhiệm | Ví dụ |
| --- | --- | --- |
| `UiState` | Dữ liệu hiện tại đủ để render màn hình | email, isLoading, danh sách, lỗi hiển thị |
| `UiEvent` | Hành động từ UI gửi vào ViewModel | EmailChanged, LoginClicked, RetryClicked |
| `UiEffect` | Yêu cầu UI thực hiện hiệu ứng một lần | ShowSnackbar, NavigateHome |

- ViewModel giữ `MutableStateFlow` ở dạng `private`, expose `StateFlow` chỉ đọc và cập nhật bằng state mới, thường qua `copy`.
- UI gửi hành động bằng `viewModel.onEvent(...)` hoặc handler tương đương đã được dùng thống nhất trong dự án.
- ViewModel không giữ `Activity`, `Fragment`, `View`, `NavController` hoặc tham chiếu composable.
- Lưu kết quả cần hiển thị bền vững vào state. Tách hiệu ứng một lần khỏi state render khi cần; xác định rõ hành vi khi UI dừng/khởi động lại. Không coi `SharedFlow` mặc nhiên bảo đảm phát lại hoặc nhận đúng một lần.
- Khi có tác vụ bất đồng bộ, xử lý loading/success/error phù hợp, dùng scope theo vòng đời và không nuốt cancellation.
- Nếu dự án cũ dùng tên `UiEvent` cho chiều ViewModel -> UI, giữ nhất quán phần cũ và phân biệt đầu vào bằng tên khác như `UiAction`. Không dùng cùng một kiểu cho cả hai chiều hoặc tự đổi tên hàng loạt.

### Với Compose

- Trong `<Name>Screen.kt`, tách `<Name>Screen` nhận ViewModel, collect state bằng `collectAsStateWithLifecycle()` và xử lý effect, với `<Name>Content` chỉ nhận state và callback. Có thể để hai composable trong cùng file.
- `<Name>Content` không lấy ViewModel, repository hoặc NavController; nhờ đó có thể preview bằng dữ liệu mẫu.
- Khởi chạy collect effect theo vòng đời phù hợp, ví dụ trong `LaunchedEffect`; không gọi API hoặc navigation trực tiếp trong thân composable khi render.
- Lắp navigation ở `ui/navigation`, truyền callback điều hướng cho màn hình. ViewModel chỉ phát yêu cầu điều hướng khi cần.
- UI riêng của một màn đặt trong cùng feature, có thể tạo thư mục `component/` ngay dưới màn đó khi cần tách nhiều file. UI dùng chung giữa các tính năng đặt trong `ui/component/`.
- Dùng theme/token trong `ui/theme` và component chung đã có. Không lặp lại cùng màu, typography hoặc spacing ở nhiều feature.

### Với Fragment/XML hiện có

- Giữ ViewModel, state và logic nghiệp vụ ở đúng tầng như trên.
- Observe/collect theo vòng đời view của Fragment; giải phóng ViewBinding trong `onDestroyView`.
- Adapter dùng chung đặt tại `ui/adapter`; adapter chỉ phục vụ một màn có thể đặt cùng feature.

## 7. Đặt tên và lắp dependency

- Package viết thường; file/class dùng PascalCase, hàm/biến dùng camelCase.
- Dùng `AuthRepository` cho interface và `AuthRepositoryImpl` cho implementation. Dùng `User`, `UserDto`, `UserEntity` để phân biệt domain model, dữ liệu API và dữ liệu lưu cục bộ.
- DTO request có hậu tố `RequestDto`, response có hậu tố `ResponseDto`, ví dụ `LoginRequestDto`, `LoginResponseDto`.
- Tên UseCase thể hiện một hành động, ví dụ `LoginUseCase`, `ObserveAccountsUseCase`.
- Package của file phải khớp vị trí tương đối dưới source root. Không copy nguyên package từ snippet mẫu.
- Dùng constructor để truyền dependency. Với dự án mới chưa chọn DI, ưu tiên manual DI qua `core/di/AppContainer.kt` theo sơ đồ base. Nếu dự án đã có Hilt/Koin, tiếp tục cách đang dùng và đặt module/provider trong `core/di`.
- Không tự cài thêm framework DI, tạo service locator ở mỗi màn hoặc tạo thêm một hệ DI song song chỉ vì snippet có annotation khác.
- Chỉ có một lớp Application chính được đăng ký trong manifest của ứng dụng. Khi tạo mới, dùng `<base-package>.MyApp` tại package gốc; dự án đã có Application thì tái sử dụng.

## 8. Ví dụ đặt file khi thêm đăng nhập

Đây là ví dụ ánh xạ vị trí, chỉ tạo những phần chức năng thực sự cần:

```text
feature/auth/login/LoginScreen.kt          -> giao diện và kết nối với ViewModel
feature/auth/login/LoginViewModel.kt       -> nhận event, gọi nghiệp vụ, cập nhật state
feature/auth/login/LoginUiState.kt         -> trạng thái form/loading/lỗi
feature/auth/login/LoginUiEvent.kt         -> thay đổi input, nhấn đăng nhập
feature/auth/login/LoginUiEffect.kt        -> điều hướng/snackbar nếu cần
domain/model/User.kt                      -> thông tin người dùng trong ứng dụng
domain/repository/AuthRepository.kt       -> hợp đồng đăng nhập
domain/usecase/LoginUseCase.kt             -> nếu có quy tắc/phối hợp nghiệp vụ riêng
data/repository/AuthRepositoryImpl.kt     -> thực hiện đăng nhập và lưu phiên
data/remote/api/AuthApi.kt                 -> khai báo endpoint đã được xác định
data/remote/dto/request/LoginRequestDto.kt -> dữ liệu gửi API
data/remote/dto/response/LoginResponseDto.kt -> dữ liệu API trả về
data/mapper/UserMapper.kt                  -> chuyển dữ liệu API sang User
data/session/SessionManager.kt             -> cài đặt lưu/đọc phiên nếu cần
core/di/AppContainer.kt                    -> nối dependency nếu dùng manual DI
ui/navigation/Routes.kt                    -> thêm route đăng nhập
ui/navigation/NavHost.kt                   -> gắn màn hình vào luồng điều hướng
```

Nếu có data source để gom thao tác mạng, đặt `AuthRemoteDataSource.kt` ở `data/remote/datasource`. Không bắt buộc tạo lớp trung gian chỉ để chuyển tiếp cùng một lời gọi.

Không đặt `AuthApi` trong `feature/auth/login`, `AuthRepositoryImpl` trong `domain/repository`, `LoginViewModel` trong `ui`, hoặc DTO trong `domain/model`.

## 9. Cách làm việc của AI

1. Đọc hướng dẫn dự án, xác định source root, base package, Compose/XML, DI, navigation và code tương tự đang có.
2. Xác định file cần thêm/sửa cùng trách nhiệm của chúng. Với tính năng nhiều tầng, nêu ngắn gọn vị trí dự kiến trước khi triển khai rồi tiếp tục làm trong phạm vi yêu cầu.
3. Tạo/sửa đúng tầng; kết nối dependency và route thực sự cần thiết. Không chỉ tạo các lớp rời nhau rồi coi là hoàn tất tính năng.
4. Tái sử dụng model, repository, mapper và UI chung trước khi tạo bản mới. Không tự bịa endpoint hoặc schema khi chưa có hợp đồng API.
5. Kiểm tra import, package, luồng state/event và wiring. Chạy build/test phù hợp với thay đổi và cấu hình thực tế nếu môi trường cho phép; không báo pass khi chưa chạy.
6. Báo lại những gì đã đổi, vị trí file, kết quả kiểm tra và phần chưa xác minh. Nếu chỉ làm tài liệu/cấu trúc mẫu, nói rõ phạm vi đó.

Checklist trước khi hoàn tất phần code:

- [ ] File mới nằm đúng một trong 5 nhóm hoặc điểm khởi tạo được chỉ định.
- [ ] Màn hình nằm trong `feature`; UI tái sử dụng nằm trong `ui`.
- [ ] Repository interface ở `domain`, implementation ở `data`.
- [ ] DTO/entity không vượt ra làm hợp đồng cho UI hoặc domain.
- [ ] `domain` không phụ thuộc Android hoặc chi tiết triển khai của tầng ngoài.
- [ ] ViewModel không truy cập trực tiếp API, DAO, storage hoặc navigation controller.
- [ ] State, hành động đầu vào và hiệu ứng đầu ra có vai trò rõ ràng.
- [ ] Dependency và navigation đã được nối nếu tính năng cần chúng.
- [ ] Package đúng dự án đích; không sinh class/theme/Application trùng.
- [ ] Không tạo tầng/module/framework ngoài nhu cầu của tác vụ.

## 10. Cách hiểu các file tham khảo trong bộ MVVM-basic

Tài liệu này tổng hợp từ `Kotlin_MVVM_structure.txt`, `MVVM_Read Me.txt`, ghi chú bổ sung về Compose và các thư mục trong `Kotlin_MVVM/`. Bộ mẫu chưa có Gradle/manifest hay một ứng dụng hoàn chỉnh để khẳng định build hoặc luồng chạy.

Các lựa chọn đã thống nhất cho hướng dẫn này, dùng khi nguồn mẫu chưa nhất quán:

- Sơ đồ `.txt` dùng Fragment, nhưng có ghi chú không dùng khi chuyển Compose và README hướng dẫn Compose. Vì vậy code mới mặc định dùng Screen; giữ Fragment cho dự án XML hiện có.
- Sơ đồ `.txt` liệt kê nhầm file theme dưới `ui/navigation`. Dùng `Routes.kt`, `NavHost.kt`, `NavGraphs.kt` theo thư mục thật; đặt file màu/kích thước/theme trong `ui/theme`.
- `core/MyApp.kt` và `data/MyApp.kt` là hai mẫu Application, trong đó mẫu thứ hai còn khai báo package `core`. Không đưa cả hai vào ứng dụng; vị trí chuẩn khi tạo mới là package gốc theo sơ đồ `.txt`.
- `ui/theme/AppTheme.kt` và `ui/theme/Theme.kt` đang có nội dung trùng nhau. Chọn một nơi định nghĩa theme wrapper; mặc định là `AppTheme.kt`. Nếu dự án đã dùng `Theme.kt`, tiếp tục file đó thay vì tạo bản sao.
- `ui/copy/` chứa snippet để tham khảo, có placeholder chưa hoàn thiện và code XML. Đây không phải package để chứa code chức năng mới; không sao chép thư mục này vào source production.
- Một số thư mục chỉ là chỗ trống. Không coi chúng là implementation đã có, cũng không lấy chúng làm lý do tạo mọi file trong cây.
- Quy ước `UiEvent` đầu vào/`UiEffect` đầu ra và mặc định manual DI là lựa chọn được ghi rõ ở trên để xử lý sự khác nhau giữa các snippet; không phải khẳng định toàn bộ mẫu đã triển khai như vậy.

Khi phát triển theo base này, dùng các quy ước đã thống nhất ở trên thay cho việc chép nguyên những phần tham khảo mâu thuẫn.
