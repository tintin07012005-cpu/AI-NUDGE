# Hiển thị số lượt hoãn / tạm dừng – cập nhật trên bản v25 cú hích hành động

- Giữ nguyên cấu hình MySQL của v25, các câu cú hích, giao diện, Word, quyền quản trị, nhắc hạn và cách tính 3 lượt dùng chung mỗi nhiệm vụ.
- Cả khi bấm **Hoãn một chút** và **Mình tạm dừng**, bot đều hiển thị: tối đa 3 lượt, đã dùng n/3, còn m lượt. Không còn giới hạn thông báo ở riêng lần hoãn đầu.
- Trước khi chọn thời gian nghỉ hoặc hoãn, bot nhắc ngắn gọn số lượt còn lại và thời điểm bị trừ lượt.
- Sau khi chọn giờ nghỉ / giờ hoãn hợp lệ, bot cập nhật số lượt đã dùng và còn lại.
- Bấm nút đổi ý / thử làm ngay / yêu cầu hỗ trợ không bị trừ lượt.
- Lượt dùng thực tế lấy từ `postponeTotal + pauseCount` trong `TaskProgress`, không dựa trên số lần hiện thông báo.

## Cách thử
1. Chạy lại ứng dụng và bắt đầu một hội thoại mới. Khi đang học bấm **Mình tạm dừng**: thấy **đã dùng 0/3, còn 3 lượt** trước khi chọn lý do.
2. Chọn **Chán, mất tập trung**: ở màn chọn thời gian vẫn thấy nhắc **còn 3/3 lượt**.
3. Chọn **10 phút nữa**: hệ thống nhắc **đã dùng 1/3, còn 2 lượt**.
4. Sau khi trở lại học, bấm tạm dừng hoặc hoãn lần nữa: báo số lượt hiện tại (**2 lượt còn lại**).
5. Chọn **Mình không tạm dừng nữa** hoặc làm bước nhỏ: số lượt không giảm.

**Chú ý:** Tin nhắn đã gửi trong hội thoại cũ không tự được chèn lại. Bạn cần bắt đầu luồng mới sau khi khởi động lại bản này để thấy thay đổi.
