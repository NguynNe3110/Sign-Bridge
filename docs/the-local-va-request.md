# Thẻ mặc định, đồng bộ API và nội dung gửi lên AI

Mỗi ngữ cảnh giữ nguyên 15 thẻ mặc định đang có trong code (5 thẻ cũ + 10 thẻ đã bổ sung). Database phiên bản 2 có migration từ phiên bản 1 để máy đã cài app cũng nhận thêm thẻ mà không cần xóa dữ liệu. Các thẻ đã sửa, thẻ cá nhân, lượt dùng và lịch sử được giữ lại.

Tiêu đề dùng `ConversationUiState.MAX_SELECTED_CHIPS`, cùng nguồn với bộ đếm. Hiện giới hạn là 5, nên cả tiêu đề và bộ đếm hiển thị đúng `1–5` / `5/5`.

Backend OpenAPI 1.3.0 hiện công bố:

- `GET /api/chips?context=...&user_id=...`: lấy danh sách.
- `POST /api/chips`: tạo thẻ với `text`, `context`, `user_id`.
- `DELETE /api/chips/{chip_id}?user_id=...`: xóa thẻ cá nhân.
- Các request quản lý thẻ gửi cùng `X-User-Id` được lưu ổn định bởi `UserSessionManager`.

Chưa có PUT/PATCH sửa thẻ trong Swagger đã đọc. Sửa nội dung hiện lưu trên máy và giữ nguyên ID; không xóa thẻ server rồi tạo lại. Khi đồng bộ, cache ghép danh sách server với dữ liệu hiện có, không xóa cả ngữ cảnh. Thẻ trùng tên không thay ID thẻ mặc định; nội dung đã sửa local và số lượt dùng không bị reset. Các thẻ `CUSTOM` kèm trong response ngữ cảnh khác không được chèn nhầm vào ngữ cảnh đó.

Trong **Quản lý thẻ**:

- Nhập nội dung rồi nhấn **Thêm** để tạo thẻ cá nhân trên API và lưu vào cache của ngữ cảnh hiện tại. Nếu gọi API lỗi, giữ bản nháp để thử lại.
- Nhấn biểu tượng bút ở thẻ cá nhân hoặc thẻ có sẵn để sửa; chọn **Lưu thay đổi** hoặc **Hủy sửa**.
- Nội dung được chuẩn hóa Unicode NFC, bỏ khoảng trắng hai đầu, giới hạn 1–50 ký tự. Không chấp nhận nội dung trùng trong cùng ngữ cảnh hoặc ký tự điều khiển.
- Sửa giữ nguyên ID, ngữ cảnh, loại thẻ và lượt sử dụng. Lỗi lưu giữ lại bản nháp để thử lại.
- Khi đang lưu, các thao tác thay đổi thẻ được khóa để tránh ghi lặp.

Danh sách hội thoại theo dõi cùng repository. Cache hiển thị trước khi tải API, và các cập nhật cache không gây thêm request lấy danh sách. Nếu sửa một thẻ đang chọn, phần chọn cập nhật nội dung theo ID; nếu xóa, thẻ được bỏ khỏi phần chọn. Sau khi quay lại hội thoại, chọn 1–5 thẻ rồi nhấn **Nói câu này**.

API mở rộng câu nhận nội dung thẻ, không nhận ID SQLite hay bộ thẻ mẫu cố định:

```http
POST /api/expand
Content-Type: application/json; charset=utf-8
```

```json
{
  "context": "CAFE",
  "chips": ["Sữa hạt ít đá"],
  "previousIncoming": null
}
```

## Kiểm chứng ngày 14/11/2026

- Build debug thành công; 112 unit/integration tests qua; lint 0 lỗi, 22 cảnh báo.
- `ChipDatabaseMigrationTest` dựng database v1 chứa 5 thẻ mỗi nhóm, thẻ cá nhân, thẻ đã sửa và lịch sử. Sau nâng cấp v2 và mở lại, mỗi nhóm có 15 thẻ mặc định, mọi dữ liệu cũ được giữ nguyên.
- Kiểm thử API dùng HTTP client thật + MockWebServer: GET/POST/DELETE, header người dùng, cache khi offline, không mất thẻ mặc định khi đồng bộ, không đổi ID khi trùng tên, và nội dung sửa không bị server ghi đè.
- Bài `localCreateAndEditSendCurrentUtf8ContentToRealHttpClientAfterReopen` tiếp tục kiểm chứng chính xác nội dung JSON mở rộng câu từ thẻ mới, thẻ sửa và thẻ nạp lại, với cấu hình repository local của bài test.
- Emulator Pixel 4/API 29: nhìn thấy tiêu đề `chọn 1–5`, chọn đủ 5 thẻ và chạm thẻ thứ 6 vẫn giữ `5/5`. Đọc SQLite xác nhận schema v2 và đủ 15 ID thẻ mặc định trong từng ngữ cảnh sau đồng bộ backend thật.
- Lần kiểm tra curl trước đó qua `scripts/test-backend.ps1`, `CAFE`, `chips=["Sữa hạt ít đá"]` đến `https://tartness-humbling-backboned.ngrok-free.dev/api/expand` nhận HTTP 200:

```json
{"spoken_sentence":"Cho mình một ly sữa hạt ít đá nhé."}
```

Ảnh kiểm tra và bản sao database emulator lưu trong `build/chips-verification/` (artifact local, không đưa vào Git). APK mới: `build/chips-verification/SignBridge-chips-fix-debug.apk`. Không cần gỡ app hoặc xóa dữ liệu để nhận bộ thẻ bổ sung.
