📌 PHÂN TÍCH KỸ THUẬT: CÁC HƯỚNG LÀM NGÔN NGỮ KÝ HIỆU (VSL) CHO MOBILE APP

Hiện tại trong mã nguồn dự án của chúng ta đang có sẵn 2 công nghệ:
1. Thư mục Vietnamese-Sign-Language-Recognition: Đã có model LSTM (62MB) train sẵn 2.764 từ vựng ký hiệu tiếng Việt bằng tọa độ xương MediaPipe.
2. Backend FastAPI (/api/sign): Đang tích hợp Gemini/GPT Vision nhận diện chuỗi 4-16 frame ảnh qua API.

--------------------------------------------------
🎯 3 HƯỚNG PHÁT TRIỂN ĐỂ LỰA CHỌN:

[ ] LỰA CHỌN A: Dùng Vision LLM Cloud (/api/sign) - Đơn giản nhất cho Mobile
- Cách làm: Mobile chỉ cần mở camera chụp 4 frame JPEG gửi lên Backend /api/sign -> Gemini Vision nhận diện và trả về câu nói.
- Ưu điểm: Phía Mobile code cực kỳ nhàn (chỉ cần chụp ảnh gửi HTTP POST), không phải nhúng model AI nặng vào app.
- Nhược điểm: Phải có kết nối mạng, độ trễ khoảng 1.5s - 2.5s.

[ ] LỰA CHỌN B: Chuyển đổi Model 2.764 từ có sẵn sang TFLite chạy Offline trên App
- Cách làm: Convert file `final_model.keras` (62MB) trong repo sang định dạng `.tflite`, nhúng MediaPipe vào Mobile để app tự nhận diện ký hiệu ngay trên điện thoại không cần mạng.
- Ưu điểm: Tốc độ tức thì (< 100ms), người khiếm thính ra ký hiệu là app nhận ra ngay, không tốn 1 đồng tiền server hay API.
- Nhược điểm: Phía Mobile phải code tích hợp MediaPipe camera và load model TFLite.

[ ] LỰA CHỌN C: Mô hình Lai (Hybrid) - Chuẩn chuyên nghiệp
- Cách làm: Điện thoại nhận diện cử chỉ offline -> ra các từ khóa (VD: ĐAU, ĐẦU, MUA, THUỐC) -> gửi các từ khóa đó lên Backend /api/expand để LLM ghép thành câu lịch sự: "Bán cho cháu thuốc đau đầu với ạ".
- Ưu điểm: Tận dụng được sức mạnh của cả 2: Camera nhận diện mượt mà không lag, câu nói phát ra lại cực kỳ tự nhiên.

👉 Bạn thấy team Mobile nên theo Hướng A (nhẹ cho mobile) hay Hướng B/C (chạy nhanh, chuyên nghiệp)?