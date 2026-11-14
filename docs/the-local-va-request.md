# Tạo/sửa thẻ local và gửi nội dung lên AI

Thẻ mẫu được khởi tạo vào SQLite khi tạo database lần đầu. Thẻ cá nhân và nội dung sửa cũng nằm trong database này; backend không cần endpoint tạo/sửa thẻ.

Trong **Quản lý thẻ**:

- Nhập nội dung rồi nhấn **Thêm** để tạo thẻ cá nhân trong ngữ cảnh hiện tại.
- Nhấn biểu tượng bút ở thẻ cá nhân hoặc thẻ có sẵn để sửa; chọn **Lưu thay đổi** hoặc **Hủy sửa**.
- Nội dung được chuẩn hóa Unicode NFC, bỏ khoảng trắng hai đầu, giới hạn 1–50 ký tự. Không chấp nhận nội dung trùng trong cùng ngữ cảnh hoặc ký tự điều khiển.
- Sửa giữ nguyên ID, ngữ cảnh, loại thẻ và lượt sử dụng. Lỗi lưu giữ lại bản nháp để thử lại.
- Khi đang lưu, các thao tác thay đổi thẻ được khóa để tránh ghi lặp.

Danh sách hội thoại theo dõi cùng repository local. Nếu sửa một thẻ đang chọn, phần chọn cập nhật nội dung theo ID; nếu xóa, thẻ được bỏ khỏi phần chọn. Sau khi quay lại hội thoại, chọn 1–3 thẻ rồi nhấn **Nói câu này**.

API chỉ nhận nội dung thẻ, không nhận ID SQLite hay bộ thẻ mẫu cố định:

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

- Build debug thành công; 73 unit/integration tests qua; lint 0 lỗi, 19 cảnh báo.
- Bài `localCreateAndEditSendCurrentUtf8ContentToRealHttpClientAfterReopen` dùng SQLite thật và HTTP client thật với MockWebServer, kiểm tra chính xác ba request: tạo mới, sửa khi đang chọn, và gửi sau khi mở lại database. Chỉ ba thao tác gửi phát sinh request; tạo/sửa không gọi mạng.
- Kiểm thử SQLite xác nhận sửa thẻ mẫu và cá nhân vẫn giữ nội dung, ID và số lượt dùng sau khi mở lại; tên trùng và chỉnh sửa không hợp lệ không làm thay đổi bản gốc.
- Emulator Pixel 4/API 29: tạo thẻ, sửa, quay lại hội thoại, gửi đến backend thật và hiển thị câu hoàn chỉnh. Màn sửa được xem ở chiều rộng 320 dp, cỡ chữ 130%, có bàn phím. Emulator không khởi tạo được giọng đọc tiếng Việt nên bước phát âm thanh chưa được xác minh trên thiết bị này.
- Gọi curl qua `scripts/test-backend.ps1`, `CAFE`, `chips=["Sữa hạt ít đá"]` đến `https://tartness-humbling-backboned.ngrok-free.dev/api/expand` nhận HTTP 200:

```json
{"spoken_sentence":"Cho mình một ly sữa hạt ít đá nhé."}
```

Request/response và ảnh kiểm tra lưu trong `build/local-cards/` (artifact local, không đưa vào Git). APK đã kiểm tra: `build/local-cards/SignBridge-local-cards-debug.apk`.
