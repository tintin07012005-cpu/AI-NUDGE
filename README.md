# AI-Nudge Lite (v3)

Web nhắc học sinh bắt đầu và nộp bài đúng hẹn. Ba nhóm người dùng (người quản trị là người có quyền cao nhất):

| Ai | Làm gì | Vào ở đâu |
|----|--------|-----------|
| **Ban cán sự** | Nhập nhiệm vụ trong ngày (có thể gửi kèm nhiều ảnh đề bài), xác nhận bạn nào đã nộp bài | Đăng nhập như học sinh, bấm "📋 Ban cán sự", nhập PIN |
| **Học sinh** | Trò chuyện với trợ lý nhắc nhở (Nudge) để làm và nộp bài, gửi ảnh minh chứng khi bấm "Mình làm xong rồi" (gửi được nhiều ảnh, gửi xong là hoàn tất nhiệm vụ) | http://localhost:8080 , đăng ký bằng email |
| **Người quản trị** | Quyền cao nhất: thêm danh sách lớp, xem tiến độ, học sinh trả lời gì với trợ lý, đánh giá, chỉ định ban cán sự, xóa nhiệm vụ / học sinh, dọn dữ liệu | /teacher/login |

## Mật khẩu mặc định (đổi trong `application.properties`)
- Người quản trị: `giaovien123`
- PIN ban cán sự: `2468`

## Thiết lập lần đầu
1. Sửa `username` / `password` MySQL trong `application.properties`, chạy `mvn spring-boot:run`. Database và bảng tự được tạo.
   (Nếu đã chạy bản v1 và muốn sạch: `DROP DATABASE ainudge;` rồi chạy lại.)
2. **Người quản trị** (/teacher) → kéo xuống "Thêm danh sách lớp", dán danh sách (mỗi dòng một tên). Mỗi em tự có một mật khẩu ngẫu nhiên 6 ký tự, hoặc bạn tự đặt bằng cách viết "Tên, mật khẩu" trên cùng dòng. Sau đó vẫn sửa được ở cột "Mật khẩu" (gõ mật khẩu mới rồi bấm Lưu, hoặc bấm 🎲 để tạo ngẫu nhiên) và tải file Excel "mật khẩu học sinh".
3. **Người quản trị** → bấm "Chọn" ở cột "Ban cán sự" cho các bạn ban cán sự.
4. **Ban cán sự** đăng nhập bằng tên → "📋 Ban cán sự" → nhập PIN → thêm nhiệm vụ (môn, nội dung, hạn nộp).
5. **Học sinh** đăng nhập bằng đúng tên trong danh sách lớp với mật khẩu người quản trị cấp (tên không có trong danh sách thì không vào được) → trợ lý tự liệt kê nhiệm vụ và hỏi.
6. Khi nhận bài, ban cán sự bấm "Xác nhận đã nộp". Người quản trị xem bảng đánh giá.

## Làm sao biết học sinh nói thật?
- Nhiệm vụ do **ban cán sự** nhập, học sinh không thể tự bịa ra nhiệm vụ.
- Báo "làm xong" thì phải **kể lại đã làm gì** (người quản trị xem được).
- Học sinh có thể **gửi ảnh minh chứng** (tối đa 10 ảnh mỗi nhiệm vụ). Ban cán sự xem ảnh ở trang xác nhận, người quản trị xem ở trang chi tiết từng học sinh. Ảnh lưu trong thư mục `uploads/` cạnh chương trình, và chỉ người có quyền mới xem được.
- **Ban cán sự xác nhận** bài đã nộp; kết quả "Nộp sớm / đúng hạn / trễ / chưa nộp" dựa trên xác nhận này.
- **Khi học sinh bấm "Hoãn một chút"**: trợ lý nhắn ngay một câu đồng cảm kèm hạn nộp, rồi hỏi lý do (Bài khó / Đang mệt / Bận việc khác / Chán), hoặc "Mình đổi ý, làm luôn". Mỗi lý do có gợi ý riêng (2 câu dễ trước, nghỉ có hẹn giờ, hẹn lại đúng lúc rảnh, thử 5 phút). Hoãn **ngắn dần**: lần 1 được 10 phút, lần 2 chỉ 5 phút, lần 3 không còn nút hoãn, chỉ còn "Thử 5 phút thôi" và "Mình cần được giúp". Sau khi hoãn, trợ lý cho biết còn bao nhiêu thời gian đến hạn nộp. **Chống hoãn (v19):** mỗi nhiệm vụ chỉ được hoãn hoặc tạm dừng **tổng cộng 3 lượt** (hoãn và nghỉ dùng chung ngân sách, `MAX_DELAY`). Mỗi lượt đều phải: (1) nói lý do (4 lý do có sẵn hoặc tự gõ "Lý do khác", người quản trị xem ở cột "Lý do hoãn"); (2) chọn giờ hẹn; (3) cam kết sẽ làm gì trong lúc chờ và đến giờ làm gì (chọn nhanh hoặc tự gõ). Trước lượt cuối trợ lý cảnh báo mạnh "ĐÂY LÀ LƯỢT CUỐI"; lượt cuối chỉ được hẹn 5 phút (nghỉ tối đa 10 phút), bắt buộc tự gõ cam kết, và hệ thống tự đánh dấu "cần hỗ trợ" để cô và ban cán sự thấy. Hết lượt thì không còn nút hoãn/nghỉ, chỉ còn "Thử 5 phút thôi" và "Mình cần được giúp". Nhiệm vụ đã quá hạn nộp thì không được hoãn, chỉ được hẹn bắt đầu trong 15 phút. Lý do "Bận việc khác" chỉ hẹn tối đa 15 phút. Các con số nằm ở `MAX_DELAY`, `MAX_POSTPONE`, `MAX_PAUSE`, `MAX_PAUSE_MIN` trong `ChatService.java`.
- **Thống kê hoãn việc cho giáo viên**: trang quản trị có bảng lý do hoãn, khung giờ hay hoãn, các bạn hay hoãn nhất và các bạn bấm "Mình cần được giúp". Trang chi tiết học sinh và file Excel có thêm cột "Lý do hoãn".
- **Tạm dừng giữa chừng**: khi học sinh bấm "Mình tạm dừng", trợ lý hỏi "Bạn muốn tạm dừng trong bao lâu / làm tiếp sau bao lâu nữa?" (10, 20, 30 phút, 1 tiếng hoặc chọn giờ cụ thể). Đến giờ đó trang sẽ rung chuông "Hết giờ nghỉ rồi!", và nếu học sinh không mở trang thì gửi thông báo đẩy / email. Có thể bấm "Nghỉ thêm 10 phút" nếu chưa sẵn sàng. Gần hạn nộp thì không cho nghỉ dài (chỉ nhắc làm tiếp). Trang người quản trị hiện trạng thái "Tạm dừng, hẹn làm tiếp".

## Trợ lý hỗ trợ thế nào
Liệt kê nhiệm vụ gần hạn nhất → hỏi giờ bắt đầu (chưa biết thì gợi ý mốc nhỏ; giờ sau hạn nộp thì nhắc chọn sớm hơn)
→ hỏi điều gì cản trở (mỗi lựa chọn có cú hích riêng) → hẹn giờ và nhắc đúng giờ (hoãn từ 2 lần có cú hích "thử 5 phút")
→ **nhắc khi còn dưới 60 phút đến hạn mà chưa xong** → báo xong, kể lại, cảm nhận → hỏi đã nộp cho ban cán sự chưa.

## Sửa câu hỏi / lời nhắc
Chỉ sửa một file: `src/main/java/vn/ainudge/service/Script.java` (chữ trong dấu "...").

## Giới hạn
- Lời nhắc chỉ hiện khi học sinh đang mở trang (nút 🔔 bật thông báo trình duyệt).
- Giờ nộp là giờ ban cán sự bấm xác nhận, nên ban cán sự nên xác nhận ngay khi nhận bài.
- Học sinh tự đăng ký bằng email hoặc đăng nhập bằng tên + mật khẩu do người quản trị cấp. Mật khẩu lưu dạng chữ thường trong CSDL để người quản trị xem lại được, nên chỉ dùng cho lớp học, đừng đặt trùng mật khẩu thật của em. Mã PIN chỉ bảo vệ trang ban cán sự.
- Trợ lý chạy theo luật cố định, chưa dùng mô hình AI ngôn ngữ nên không cần khóa API.

## Đăng ký / đăng nhập, âm thanh và thông báo nhắc

**Tài khoản học sinh** (không cần Google, không cần cấu hình gì thêm):
- Học sinh mở web → tab **Đăng ký** → nhập tên, email, mật khẩu (4–20 ký tự) → vào thẳng trang trò chuyện.
- Lần sau đăng nhập bằng **email + mật khẩu** (hoặc tên trong danh sách lớp + mật khẩu do người quản trị cấp, như trước).
- Email không được trùng; tên không được trùng (nếu trùng, hệ thống nhắc học sinh thêm lớp hoặc chữ phân biệt).
- Người quản trị vẫn xem / đổi / cấp lại mật khẩu của mọi học sinh ở trang quản trị.

**Nhắc nhở:**
- Đang mở web: trang kêu chuông + rung khi đến giờ (nút 🔊 để tắt/bật tiếng).
- Đã tắt web: thông báo đẩy của trình duyệt (học sinh bấm 🔔 một lần). iPhone phải "Thêm vào Màn hình chính" trước (iOS 16.4 trở lên).
- Email: dự phòng cho em chưa bật thông báo đẩy (cần cấu hình `spring.mail.*`). Email lấy từ lúc học sinh đăng ký, hoặc người quản trị ghi trong danh sách lớp: `Tên, mật khẩu, email`.
- Máy chủ phải chạy liên tục thì lời nhắc khi đã tắt web mới được gửi đi.

## Mới trong bản này
- **Giữ đăng nhập như Facebook / Zalo**: đăng nhập một lần, lần sau mở web vào thẳng (giữ 1 năm, tự gia hạn mỗi lần vào; tắt trình duyệt hay khởi động lại máy chủ cũng không mất). Chỉ phải đăng nhập lại khi bấm **Đăng xuất**, hoặc khi người quản trị đổi mật khẩu của em đó. Ban cán sự cũng chỉ nhập PIN một lần. Dòng `ainudge.remember-secret` trong `application.properties` là chuỗi bí mật để ký "thẻ đăng nhập" (đổi chuỗi khác = mọi người đăng nhập lại 1 lần).
- **Đổi chữ "Giáo viên" thành "Người quản trị"** trên toàn bộ giao diện (địa chỉ trang vẫn là `/teacher`).
- **Ban cán sự gửi ảnh đề bài**: khi thêm nhiệm vụ có ô "Ảnh đề bài" (chọn nhiều ảnh, tối đa 10 ảnh, mỗi ảnh 10 MB). Nhiệm vụ đã tạo vẫn thêm / xóa ảnh được ngay ở bảng "Nhiệm vụ gần đây". Học sinh bấm vào ô nhiệm vụ (thanh phía trên khung chat, ô có dấu 🖼️) để xem ảnh đề; trợ lý cũng nhắc khi bạn chọn bài có ảnh. Người quản trị xem ảnh đề ở bảng nhiệm vụ.
- **Xuất file Excel cho từng học sinh**: ở bảng "Học sinh" có nút **⬇ Excel** ở mỗi dòng (và nút trên trang chi tiết của em đó). File có 2 sheet: *Tiến độ nộp bài* (từng nhiệm vụ) và *Câu trả lời* (những gì em đó nói với trợ lý). Các nút xuất cho cả nhóm vẫn còn ở đầu trang.

> Bản này có thêm bảng `task_image` trong MySQL, chương trình tự tạo khi chạy, không cần làm gì thêm.

## Bản v6
- **Nhắc nhở sống động**: khi đến giờ, màn hình hiện thẻ nhắc có chuông lắc, vòng sóng lan, nút to (Bắt đầu thôi / Hoãn 10 phút), tiếng chuông "ding - dong" kêu lặp ~45 giây cho đến khi học sinh bấm, tiêu đề tab nhấp nháy, điện thoại rung.
- **Thông báo luôn bật**: bỏ nút 🔔 và 🔊. Lần chạm đầu tiên trên trang, trình duyệt sẽ hỏi cho phép thông báo; nếu chưa cho phép sẽ có thanh nhắc phía trên.
- **Tự nhập**: ở câu "Điều gì khiến bạn chưa bắt đầu?" có nút "Lý do khác", ở câu "Bạn làm bài ở đâu?" có nút "Chỗ khác"; bấm vào thì học sinh gõ nội dung của mình (người quản trị xem được).
- **Email**: học sinh đang mở web vẫn nhận email nhắc (trước đây bị bỏ qua). Học sinh chưa có email thấy ô "Thêm email" ở đầu trang; lưu xong hệ thống gửi một thư chào để biết email chạy hay chưa. Đăng ký mới cũng được gửi thư chào.
- Bỏ lựa chọn "1 phút nữa (thử nghiệm)"; `ainudge.demo=false`.
- **Gợi ý học thêm**: ngay ở câu hỏi "Điều gì dễ khiến bạn chưa bắt đầu?" (và nhắc lại nếu học sinh chọn "Không biết bắt đầu từ đâu"), dưới khung chat hiện thêm 5 nút: **Vietjack**, **YouTube**, **Lời giải hay**, **OLM**, **HOC247** (đều có logo; muốn thêm nguồn khác thì bỏ ảnh vào `static/` rồi thêm vào bảng `LOGOS` trong `app.js`). Bấm vào mở tab mới, tìm theo môn và nội dung nhiệm vụ (tìm bằng Google giới hạn trong từng trang như vietjack.com, loigiaihay.com, olm.vn, hoc247.net, và tìm trên YouTube), nên trang Nudge vẫn mở và chuông nhắc vẫn kêu đúng giờ.


**Định hướng của cô (v23):** cú hích phải ngắn gọn, tích cực, tạo động lực và thay đổi nhận thức; không dùng hình thức phạt, không dọa báo cô hay ban cán sự. Bot trò chuyện để học sinh tự thấy việc bắt đầu quan trọng hơn việc hoãn, và cùng cam kết tiến bộ mỗi ngày với Nudge. Các câu truyền động lực ngắn nằm trong hàm `boost()` của `Script.java`, muốn thêm bớt thì sửa ở đó. Hệ thống không còn tự đánh dấu "cần hỗ trợ" khi học sinh hoãn lần cuối; chỉ khi học sinh tự bấm "Mình cần được giúp" thì cô và ban cán sự mới nhận được.

**v24:** ngân sách hoãn/nghỉ là 3 lượt cho mỗi nhiệm vụ; lượt cuối bot nói một câu truyền động lực riêng (hàm `lastBoost()` trong `Script.java`), các lượt trước dùng `boost()`.

**v25:** câu truyền động lực hiện ở khung riêng nổi bật (kind `boost`, nền tím hồng, có ✨). Có 3 nhóm câu trong `Script.java`: `reasonBoost()` (theo lý do học sinh chọn), `boost()` (sau mỗi lần hoãn/nghỉ và khi nhắc giờ), `lastBoost()` (lượt cuối). Bot không còn báo "còn mấy lượt"; đến lượt cuối bot nói "bạn hoãn khá nhiều lần rồi", đưa câu truyền động lực, và sau lượt đó không còn nút hoãn hay tạm dừng.
