# SignBridge Backend (TV4)

Spring Boot 4.1 · Java 21+. Đặc tả API: `docs/luong-xu-ly-chinh.md` mục 4 trong repo AI của TV3 (https://github.com/canhhocit/SignBridge-AI).

## Chạy

### Cấu hình địa chỉ từ app Android

Tại màn chọn ngữ cảnh, chọn **Kết nối máy chủ** → nhập **IPv4 của thiết bị chạy backend** và cổng (mặc định `8080`) → **Lưu và kết nối**. Địa chỉ được lưu trên điện thoại, áp dụng cho các yêu cầu API tiếp theo mà không rebuild hay khởi động lại app.

- Điện thoại thật: nhập IP LAN của laptop/server, hai thiết bị cùng mạng Wi-Fi. Không nhập IP điện thoại trừ khi backend chạy ngay trên điện thoại đó.
- Emulator Android: dùng `10.0.2.2` để gọi backend trên máy tính.
- Không thêm `http://`, `:8080` hoặc `/api` vào ô IPv4; cổng có ô riêng, app tự thêm đường dẫn `/api`.
- HTTP hỗ trợ IPv4 nội bộ/loopback; bật **Dùng HTTPS** nếu server dùng TLS (chứng chỉ cần hợp lệ cho địa chỉ được nhập). Các URL HTTP công cộng vẫn bị chặn ở bản release.
- **Chỉ kiểm tra kết nối** gọi `GET /api/health` với địa chỉ đang nhập và kiểm tra `status=UP`, không thay máy chủ đang dùng. **Lưu và kết nối** áp dụng địa chỉ ngay, kể cả khi backend đang offline; app hiển thị rõ kết quả.
- Nếu không kết nối được: kiểm tra backend đang chạy, đang lắng nghe trên giao diện LAN (không chỉ `127.0.0.1`), cổng và firewall. Nhập IP không tự tạo/chạy backend.

`signbridge.apiBaseUrl` khi build vẫn dùng làm giá trị dự phòng nếu app chưa lưu địa chỉ riêng. Cấu hình đã lưu trong app được ưu tiên.

### Chạy service backend (trong repo backend riêng)

```bash
./mvnw spring-boot:run          # cổng 8080, ai.mode=mock (trả JSON mẫu)
./mvnw test
```

Mobile gọi từ điện thoại cùng wifi: `http://<IP-laptop>:8080/api/...` (xem IP bằng `hostname -I`).

## Endpoint

| Method | Path | Body |
|---|---|---|
| GET | `/api/health` | — |
| POST | `/api/sign` | multipart: `context`, `frames` (4/8/12/16 file JPEG ≤ 250KB), `personalChips` (JSON array), `previousIncoming` |
| POST | `/api/expand` | JSON: `{context, chips[1..5], previousIncoming}` |
| POST | `/api/summarize` | multipart: `context`, `audio` (m4a ≤ 2MB), `previousOutgoing` |

Chế độ mock: gửi `personalChips=["MOCK_LOW"]` lên `/api/sign` để thử luồng confidence thấp
(`glosses` có `UNKNOWN`, `spoken_sentence` rỗng).

## Cấu trúc

```
api/           ApiController (4 endpoint + validate request), ExpandRequest
conversation/  ConversationService (kiểm tra output AI — mục 4.6, 7.4), SignVocabulary (20 ký hiệu)
ai/            AiGateway + MockAiGateway, các record input/output AI
common/        ContextType, ApiException, GlobalExceptionHandler (response lỗi chung)
```

## Cấu hình (biến môi trường)

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `AI_MODE` | `mock` | `mock` hoặc `real` (chưa có RealAiGateway) |
| `AI_STT_BASE_URL` | `http://localhost:8000` | Service STT của TV3 |

Không commit API key — chỉ đọc từ biến môi trường.
