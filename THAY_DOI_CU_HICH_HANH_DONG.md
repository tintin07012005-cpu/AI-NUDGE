# Bổ sung cú hích hành động (trên bản v25 có nhắc lượt hoãn)

- Giữ nguyên cấu hình MySQL, đăng nhập, giao bài, Word, bộ câu động viên ngẫu nhiên và giới hạn 3 lượt hoãn/nghỉ dùng chung.
- Khi học sinh chọn lý do nghỉ/hoãn, chỉ hiển thị **một cú hích ngắn phù hợp với lý do** thay vì hai tin nhắn chung chung liên tiếp.
- Mỗi nhóm lý do có nhiều cách diễn đạt, chọn ngẫu nhiên và tránh lặp liền câu trước trong cùng nhóm.
- Cú hích có hành động kiểm chứng được: 1 câu trong 5 phút (bí bài), 2 phút mở đề (mất tập trung), hẹn nghỉ 10 phút (mệt), hoặc xác định giờ quay lại (bận).
- Ở màn hình chọn thời gian nghỉ thêm nút **Thử 1 câu dễ trong 5 phút** (hoặc nút tương ứng với lý do). Bấm nút này thật sự đưa hội thoại về bước **DOING** và hiển thị chỉ dẫn bắt đầu, **không tự nhận đã hoàn thành**.
- Nếu bí bài có nút **Mình cần được hỗ trợ**: chỉ ghi nhận cờ cần hỗ trợ trong ứng dụng, không tự gửi thông báo tới giáo viên. Học sinh vẫn có thể chọn nghỉ hoặc học tiếp.
- Rút gọn câu hỏi chọn thời lượng nghỉ: không dùng câu dài mang tính trách móc.

## Kiểm thử thủ công
1. Học sinh bấm **Mình tạm dừng → Bí bài, cần nghỉ để nghĩ**: một cú hích cụ thể, sau đó có nút thử 1 câu, cần hỗ trợ, nghỉ 10/20/30 phút.
2. Bấm thử 1 câu: trạng thái trở lại **DOING**, không trừ lượt nghỉ.
3. Bấm 10 phút: tiếp tục đi qua luồng hẹn giờ nghỉ, dùng 1 lượt như cũ.
4. Chọn lý do **Mệt**: hiện gợi ý nghỉ, không thúc ép học ngay.
5. Chọn **Hoãn một chút → Bài khó**: có lời gợi ý cụ thể và nút làm thử 1 câu trong 5 phút.
