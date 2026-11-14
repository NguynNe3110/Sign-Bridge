# Kiểm tra luồng hội thoại — 14/11/2026

## Cập nhật: gọi thẳng AI Service

Đã đổi URL mặc định Android và script curl sang `https://tartness-humbling-backboned.ngrok-free.dev/` sau khi đọc lại OpenAPI đang chạy (`SignBridge AI Service`, phiên bản 1.2.0). Các kết quả host `scrimmage-legume-unvalued` bên dưới là ghi nhận trước khi đổi kết nối.

- Chọn thẻ: `POST /api/expand`, JSON `context`, `chips`, `previousIncoming`.
- Ghi âm: `POST /api/summarize`, multipart M4A `audio`, `context`, `previousOutgoing`. Endpoint này chạy cả STT và LLM, không cần đi qua Spring gateway.
- `/api/v1/speech/transcribe` là STT độc lập; `/docs` là trang tài liệu. Không có endpoint TTS trong OpenAPI; app vẫn đọc câu bằng TTS Android.
- OpenAPI chưa khai báo `/api/sign`; Android báo dịch vụ chưa hỗ trợ khi endpoint này trả 404.
- Timeout AI tăng lên 30 giây; xử lý `detail=NO_SPEECH_DETECTED` của FastAPI và lỗi tunnel ngrok.
- Không loại bỏ phản hồi chỉ vì quick reply dài hơn 6 từ hoặc tóm tắt dài hơn 15 từ; giữ nguyên nội dung và số liệu dịch vụ trả về.

Kiểm tra trực tiếp trên host AI: health trả `service=SignBridge-AI`, `llm_configured=true`; request thẻ hiệu thuốc trả “Dạ, mình bị đau đầu, lấy giúp mình loại không buồn ngủ và uống sau ăn nhé.”

File âm thanh mẫu `test_audio-text/test.mp3` từ repo AI được gửi đến `/api/summarize`: HTTP 200, transcript “tôi muốn mua một cốc trà sữa trân châu nhưng ít đường năm mươi phần trăm đá.”, thời gian xử lý server 6563 ms. Đây là kiểm tra luồng và hợp đồng API; không khẳng định mọi chi tiết suy diễn của phần tóm tắt đều chính xác. Artifact trong `build/direct-ai/`.

Địa chỉ IPv4 đã lưu trong app hoặc Gradle property riêng vẫn ghi đè URL mặc định; khi kiểm tra cần bảo đảm không trỏ lại gateway cũ.

## Luồng thực tế

- Người khiếm thính chọn 1–3 thẻ → `SendChipsUseCase` → `POST /api/expand` → hiển thị nguyên `spoken_sentence` phía nhân viên và đọc bằng TTS trên điện thoại.
- Nhân viên giữ mic → ghi AAC trong file M4A → thả nút → `POST /api/summarize`, multipart `context`, `audio`, `previousOutgoing` → hiển thị `summary` phía người khiếm thính. Có thể mở **Xem lời nói đầy đủ** để đọc `transcript`.
- App không chuyển giọng nói thành chữ trực tiếp khi đang giữ mic. Việc chuyển đổi diễn ra ở backend sau khi thả nút.
- Nếu không gọi được API tạo câu, app chỉ ghép nguyên văn các thẻ bằng dấu `·` và đánh dấu ngoại tuyến; không tự thêm lời chào.

## Kết quả đối chiếu backend

Backend đang cấu hình: `https://scrimmage-legume-unvalued.ngrok-free.dev`.

`GET /api/health` trả `{"status":"UP"}`. Đây là kiểm tra service đang chạy, chưa chứng minh AI thật đang được sử dụng.

Gọi trực tiếp `POST /api/expand`, không qua UI Android:

| context | chips | spoken_sentence trả về |
| --- | --- | --- |
| CAFE | Không đường, Mang đi | Chào bạn, mình muốn: không đường, mang đi. Cảm ơn bạn! |
| CAFE | Cà phê sữa | Chào bạn, mình muốn: cà phê sữa. Cảm ơn bạn! |

Kiểm tra giữ mic 3,5 giây trên Pixel 4 emulator API 29, chạy với `-no-audio`, không cung cấp lời nói: app nhận và lưu một lượt `INCOMING`, `isOffline=0`, với:

- `transcript`: “Dạ bạn dùng Panadol xanh nha, uống một viên sau ăn, ngày hai lần, hộp này giá ba mươi lăm nghìn.”
- `summary`: “Panadol Xanh • 1 viên sau ăn • Ngày 2 lần • 35.000đ”.

Các phản hồi này cho thấy endpoint có hành vi trả mẫu. Không có source backend trong workspace này để xác nhận implementation hoặc thay đổi cấu hình server. README backend hiện mô tả mặc định `AI_MODE=mock`; cần đối chiếu cấu hình triển khai và gateway AI thực tế, không chỉ đổi cờ nếu gateway thật chưa được triển khai.

## Sửa phía Android

- Cho mic ngắt TTS để nhân viên trả lời ngay; trước đây `isSpeaking` làm `isBusy=true` và vô hiệu hóa mic.
- Hủy cả job chờ TTS khi ngắt, xử lý callback TTS `onStop`, tránh chờ giọng đọc đến timeout.
- Sau khi cấp quyền mic lần đầu, nhắc giữ nút lại; không tự thu khi thao tác xin quyền đã kết thúc.
- Hiện thông báo lỗi cụ thể của thiết bị/API thay vì luôn dùng thông báo chung.
- Phân biệt trạng thái đang ghi và đang chuyển lời nói thành văn bản; cho xem bản chép lời đầy đủ.

## Xác minh

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`: thành công; 58 test, không có test lỗi; lint 0 lỗi, 27 cảnh báo.
- Emulator: đã kiểm tra xin quyền, giữ/thả mic, nhận tóm tắt từ endpoint đang cấu hình và mở bản chép lời.
- Chưa xác minh nhận diện tiếng nói thật, vì endpoint đang có hành vi trả mẫu; chưa thử bản sửa mic trên điện thoại vật lý.
- Ảnh kiểm tra nằm trong `build/conversation-debug/` (artifact cục bộ, không commit).
