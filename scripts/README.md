# Test backend bằng curl trên Windows

Chạy từ thư mục gốc project. Script dùng `curl.exe`, tương thích Windows PowerShell 5.1. URL mặc định gọi thẳng AI: `https://tartness-humbling-backboned.ngrok-free.dev`. `/docs` chỉ là trang Swagger, không phải base URL để gửi request.

Dịch vụ AI hiện có `/api/expand` và `/api/summarize` tương thích request của Android. `/api/summarize` tự chạy STT rồi tóm tắt. Swagger hiện chưa khai báo `/api/sign`; bài test Sign chỉ dùng khi dịch vụ đích hỗ trợ endpoint này. Đọc câu qua loa vẫn dùng TTS của Android, không có endpoint TTS trên dịch vụ này.

## Health + tạo câu từ thẻ

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-backend.ps1
```

Không có file media, script chạy health và expand, báo **SKIP** cho summarize/sign.

Đổi request để đối chiếu câu trả về có thực sự thay đổi theo đầu vào:

```powershell
.\scripts\test-backend.ps1 -Endpoint Expand -Context CAFE -Chips 'Cà phê sữa', 'Mang đi' -PreviousIncoming 'Bạn uống gì?'
.\scripts\test-backend.ps1 -Endpoint Expand -Context PHARMACY -Chips 'Đau đầu', 'Không buồn ngủ'
```

Request JSON mặc định được in ra và lưu nguyên văn:

```json
{
  "context": "PHARMACY",
  "chips": ["Đau đầu", "Không buồn ngủ", "Uống sau ăn"],
  "previousIncoming": null
}
```

## Giọng nói → văn bản và tóm tắt

Dùng bản ghi M4A thật, dài tối đa 60 giây, dung lượng tối đa 2 MB:

```powershell
.\scripts\test-backend.ps1 -Endpoint Summarize -Context PHARMACY -AudioPath 'C:\test\nhan-vien.m4a' -PreviousOutgoing 'Mình muốn mua thuốc giảm đau đầu.'
```

Request multipart: `context=PHARMACY`, `audio=@nhan-vien.m4a` (tên upload `reply.m4a`, MIME `audio/mp4` như app), và `previousOutgoing` nếu được truyền. Curl tự tạo boundary. Không giả lập file âm thanh bằng file text.

## Ký hiệu → câu nói

Truyền 4/8/12/16 ảnh JPEG theo thứ tự thời gian. Mỗi ảnh tối đa 250 KB, cạnh dài tối đa 640px; script kiểm tra số lượng, đuôi file và kích thước byte, không kiểm tra độ phân giải/nội dung media.

```powershell
.\scripts\test-backend.ps1 -Endpoint Sign -Context PHARMACY -Frames '.\test\f1.jpg', '.\test\f2.jpg', '.\test\f3.jpg', '.\test\f4.jpg' -PersonalChips 'Dị ứng penicillin' -PreviousIncoming 'Bạn đau đầu lâu chưa?'
```

Request multipart lặp lại field `frames` theo đúng thứ tự, kèm `context`, `personalChips` dưới dạng JSON array và `previousIncoming` nếu được truyền.

## Tùy chọn và kết quả

```powershell
# Chỉ tạo/in request, không gọi mạng. Media vẫn phải tồn tại nếu được chỉ định.
.\scripts\test-backend.ps1 -DryRun

# URL mới: chỉ truyền origin, không thêm /api.
.\scripts\test-backend.ps1 -BaseUrl 'http://localhost:8080' -TimeoutSeconds 10
```

- Mỗi lần chạy lưu riêng trong `build/backend-curl/<timestamp>-<id>/`.
- Có `expand.request.json`, các field text UTF-8, và từng thư mục endpoint chứa `request.txt`, `request.curl.ps1`, `response.json`, `response.headers.txt`, `curl.stderr.txt`, `statistics.txt`.
- Có thể chạy lại chính xác một request bằng `& '<đường dẫn>/request.curl.ps1'` khi các file body/media vẫn còn.
- In HTTP status, thời gian thực hiện, response nguyên văn; `results.json` ghi kết quả kiểm tra cơ bản.
- Exit code `1` nếu curl/HTTP/field response cơ bản lỗi. **PASS không xác nhận AI thật**, không đánh giá chất lượng câu/chép lời; cần so sánh với nội dung thẻ và bản ghi thật.
- Các file request/response có thể chứa lời nói và nội dung thẻ; `build/` là artifact cục bộ, không commit.
