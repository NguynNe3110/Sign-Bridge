# Ba bố cục hội thoại

Refactor UI ngày 14/11/2026 theo skill Impeccable, giữ theme than/xám và các luồng hiện có. Người dùng cần cả cầm/chuyền máy và đặt máy giữa hai người đối diện.

## Lựa chọn và cách thử

Trong màn hội thoại, bấm biểu tượng chia ô cạnh tên ngữ cảnh để mở **Bố cục hội thoại**.

| Bố cục | Khi dùng | Đánh đổi |
| --- | --- | --- |
| Hội thoại chung — mặc định | Cầm máy, chuyền máy, hai người cùng nhìn. Một vùng đọc, mic và ký hiệu cố định dưới đáy. Có nút Chọn thẻ để tới bảng từ khóa. | Nội dung dài cần cuộn; không còn hai vùng đọc bị giới hạn riêng. |
| Theo vai | Tab Khách/Nhân viên, mỗi bên dùng toàn màn hình, phù hợp khi muốn đọc tập trung. | Phải chuyển tab khi đổi lượt; không tự chuyển tab khi có kết quả mới. |
| Đối diện | Phần nhân viên xoay 180°. Có thể thu gọn/mở rộng vùng nhân viên; khi nằm ngang chuyển sang hai cột. | Màn nhỏ/chữ lớn vẫn cần cuộn từng vùng; Theo vai dễ đọc hơn với câu rất dài. |

Lựa chọn bố cục, tab và kích thước vùng đọc chỉ là UI state bằng `rememberSaveable`, giữ qua tái tạo màn hình. Không lưu thêm cấu hình nghiệp vụ. Nút đổi bố cục/tab/kích thước bị khóa trong lúc đang giữ mic hoặc ký hiệu để tránh tháo composable đang nhận gesture. Đang phát TTS vẫn có thể đổi sang Nhân viên để dùng mic ngắt loa theo logic cũ.

## Phạm vi source

- `feature/conversation/ConversationLayout.kt`: ba lựa chọn bố cục.
- `feature/conversation/ConversationLayouts.kt`: phần trình bày và các callback chung.
- `ConversationScreen.kt`: giữ nguyên kết nối ViewModel, lifecycle, permission và effect; `ConversationContent` chuyển cùng state/event cho các bố cục. Có ba Compose Preview dùng cùng dữ liệu.
- `component/DeafPanel.kt`: tái sử dụng phần hiển thị kết quả và bảng thẻ giữa các bố cục.
- Thêm nhãn UI trong resources và một token chiều cao nút chung trong `ui/theme/Dimens.kt`.

Refactor này không sửa API, repository, use case, ViewModel hay cách ghi/phát âm thanh. Các thay đổi ở các file đó trong working tree thuộc các tác vụ tích hợp/sửa mic trước đó.

## Xác minh giao diện

- Chụp native trên Pixel 4 emulator API 29 với cùng dữ liệu **minh họa**, không gọi backend để tạo ảnh.
- Ba bố cục: 393dp/chữ 100% và 320dp/chữ 130%; kiểm tra tên ngữ cảnh và nút Back có trong hierarchy.
- Kiểm tra vùng nút mic trước và trong thao tác giữ 7 giây ở 320dp/130%: bounding box không thay đổi; đổi bố cục bị vô hiệu hóa.
- Nút Chọn thẻ đưa bảng từ khóa vào viewport dù trước đó có tóm tắt dài.
- Kiểm tra Đối diện ở khổ ngang; nội dung dài nằm trong vùng cuộn có chủ đích.
- Ảnh trong `build/ui-layouts/`: `shared.png`, `focused.png`, `table.png`, các bản `*-compact.png`, `shared-holding-compact.png`, `shared-chips-compact.png`, `table-landscape.png`.
- Màn render tạm đã bỏ khỏi source/APK cuối. Chưa kiểm tra thao tác thật trên điện thoại vật lý hoặc tablet.
- Compile/test/lint được kiểm tra với thư mục trung gian và báo cáo riêng `build/ui-verification/` để tránh xung đột các lượt Gradle dùng chung workspace: 67 test, 0 failure; lint 0 lỗi, 19 cảnh báo. APK giao ở `build/ui-layouts/SignBridge-UI-variants-debug.apk`, đã cài và kiểm tra bảng chọn trên emulator. Không thay cấu hình Gradle production.
