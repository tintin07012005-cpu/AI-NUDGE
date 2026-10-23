# AI-Nudge Lite v25 – bản chỉnh tập trung

Nền tảng: bản `ai-nudge-lite-v25.zip` người dùng tải lên, KHÔNG áp dụng bộ thay câu hỏi Word của v27.

## Thay đổi
- Câu động lực ngắn, tự nhiên hơn, lấy ý từ file Word; chọn ngẫu nhiên, không lặp liền kề.
- Sau khi chọn giờ bắt đầu: bot nói một câu động lực ngẫu nhiên.
- Nút bắt đầu: “Bắt đầu nhé! Cam kết tập trung không sao nhãng!”
- Câu hướng dẫn mở ảnh/Word ở ô nhiệm vụ.
- Quản trị: đổi mật khẩu quản trị hoặc PIN ban cán sự; xác minh giá trị cũ; mật khẩu/PIN mới được băm PBKDF2 trong bảng `app_credential` của MySQL. Đổi mật khẩu quản trị sẽ đăng xuất để đăng nhập lại.
- Học sinh: trạng thái tải 0–100% cho mỗi lượt lấy dữ liệu. Phần trăm chỉ biểu thị tiến độ giao diện (ước lượng đến khi phản hồi trả về), không phải số byte tải thực hoặc tiến độ làm bài.
- Tăng kích thước khung chat trên màn hình máy tính và giữ khung chat cuộn được trên màn hình nhỏ.
- Ban cán sự: bắt buộc đính kèm ít nhất một ảnh hoặc DOCX; xác thực cả trình duyệt/máy chủ; kiểm tra chữ ký định dạng DOCX OpenXML; học sinh xem/tải từ ô nhiệm vụ.
- Giữ nguyên cơ chế nhắc bài trước hạn 60 phút từ v25, rút gọn lời nhắc.

## Cấu hình cần lưu ý
- `src/main/resources/application.properties` giữ nguyên từ bản v25, bao gồm đường dẫn MySQL, để tránh lỗi biến môi trường của bản v27.
- Dự án vẫn yêu cầu MySQL được bật, tài khoản kết nối phải hoạt động và JDK 17.
- Vì cấu hình v25 có chứa thông tin bí mật trong tệp, không đăng ZIP hoặc tệp properties lên GitHub công khai; nên thay đổi các mật khẩu/khóa đã lộ khi triển khai thật.
- Ảnh/Word của mỗi nhiệm vụ nằm trong bảng `task_image` và thư mục `uploads/` như cơ chế ảnh cũ.
- Thẻ ghi nhớ đăng nhập cũ của quản trị/ban cán sự hết hiệu lực sau khi đổi mật khẩu/PIN khi đăng nhập lại; các phiên đang mở trên trình duyệt khác có thể còn tồn tại cho tới khi hết hạn.

## Kiểm thử thủ công đề xuất
1. Khởi động ứng dụng với MySQL của v25, đăng nhập quản trị.
2. Đổi mật khẩu quản trị, đăng nhập lại; thử mật khẩu cũ và mới; đổi PIN rồi đăng nhập cán sự lại.
3. Giao bài không tệp => bị chặn; giao ảnh JPG => thành công; DOCX => thành công; file `.docx` giả => bị từ chối.
4. Học sinh chọn nhiệm vụ có ảnh/DOCX => xem được; chọn giờ => có một câu động lực.
5. Mở Vietjack tab khác, quay lại tab AI-Nudge => chat vẫn mở rộng và có cuộn.
6. Tạo bài hạn còn dưới 60 phút, theo dõi lịch nhắc và không gửi trùng.


## Bổ sung: thông báo lượt hoãn trước lần đầu
- Khi học sinh bấm **Hoãn một chút** lần đầu cho một nhiệm vụ, web nhắc rõ hạn mức **3 lượt hoãn/tạm nghỉ dùng chung** và số lượt còn lại trước khi hỏi lý do.
- Chỉ trừ lượt khi học sinh thực sự chọn thời gian hoãn; xem thông báo hoặc quay lại học không làm mất lượt.
- Nếu đã tạm nghỉ trước đó, số lượt còn lại được tính theo số lượt đã sử dụng.
