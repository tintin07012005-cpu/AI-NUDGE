package vn.ainudge.web;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import vn.ainudge.dto.Reply;
import vn.ainudge.dto.TaskChip;
import vn.ainudge.model.Student;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.service.ChatService;
import vn.ainudge.service.NotifyService;
import vn.ainudge.service.PresenceTracker;

@Controller
@RequestMapping("/student")
public class StudentController {
    private final StudentRepository students;
    private final ChatService chat;
    private final NotifyService notify;
    private final PresenceTracker presence;

    /**
     * Mỗi học sinh một khóa: trình duyệt vừa hỏi định kỳ (poll) vừa gửi câu trả lời (answer),
     * nếu hai yêu cầu chạy song song thì yêu cầu này có thể ghi đè kết quả của yêu cầu kia
     * (mất lượt hoãn, nhắc sai giờ, hỏi lại câu cũ). Khóa đặt ở đây để bao trọn cả phần lưu dữ liệu.
     */
    private static final ConcurrentHashMap<Long, Object> LOCKS = new ConcurrentHashMap<>();

    private static Object lockOf(Student s) {
        return LOCKS.computeIfAbsent(s.id, k -> new Object());
    }

    public StudentController(StudentRepository students, ChatService chat, NotifyService notify,
                             PresenceTracker presence) {
        this.students = students;
        this.chat = chat;
        this.notify = notify;
        this.presence = presence;
    }

    private Student current(HttpSession session) {
        return students.findById((Long) session.getAttribute("studentId")).orElseThrow();
    }

    @GetMapping
    public String page(HttpSession session, Model model) {
        Student s = current(session);
        model.addAttribute("name", s.name);
        model.addAttribute("monitor", s.monitor);
        model.addAttribute("hasEmail", s.email != null && !s.email.isBlank());
        return "student";
    }

    /** Trình duyệt gọi khi mở trang: lấy lịch sử + lời chào của trợ lý. */
    @GetMapping("/api/state")
    @ResponseBody
    public Reply state(HttpSession session) {
        Student s = current(session);
        presence.touch(s.id);
        synchronized (lockOf(s)) {
            return chat.open(s, false);
        }
    }

    /** Học sinh bấm một nút trả lời (hoặc gõ chữ / chọn giờ). */
    @PostMapping("/api/answer")
    @ResponseBody
    public Reply answer(@RequestParam String value,
                        @RequestParam(required = false) String label,
                        HttpSession session) {
        Student s = current(session);
        synchronized (lockOf(s)) {
            return chat.answer(s, value, label);
        }
    }

    /** Trình duyệt hỏi mỗi vài giây: có gì cần nhắc không? */
    @GetMapping("/api/poll")
    @ResponseBody
    public Reply poll(HttpSession session) {
        Student s = current(session);
        presence.touch(s.id);
        synchronized (lockOf(s)) {
            return chat.poll(s);
        }
    }

    /** Thanh nhiệm vụ hôm nay. */
    @GetMapping("/api/tasks")
    @ResponseBody
    public List<TaskChip> tasks(HttpSession session) {
        return chat.chips(current(session));
    }

    /** Học sinh gửi ảnh minh chứng (nhiều ảnh) ở bước "làm xong" trong khung chat. */
    @PostMapping("/api/photo")
    @ResponseBody
    public Reply photo(@RequestParam("files") List<MultipartFile> files, HttpSession session) {
        Student s = current(session);
        synchronized (lockOf(s)) {
            return chat.photoDone(s, files);
        }
    }

    /** Khóa công khai để trình duyệt đăng ký thông báo đẩy. */
    @GetMapping("/api/push/key")
    @ResponseBody
    public Map<String, String> pushKey() {
        return Map.of("key", notify.publicKey());
    }

    /** Lưu thiết bị vừa bật thông báo đẩy. */
    @PostMapping("/api/push/subscribe")
    @ResponseBody
    public boolean pushSubscribe(@RequestParam String endpoint, @RequestParam String p256dh,
                                 @RequestParam String auth, HttpSession session) {
        return notify.subscribe(current(session), endpoint, p256dh, auth);
    }

    /** Học sinh chưa có email thì thêm email ở đây để nhận lời nhắc qua email. */
    @PostMapping("/api/email")
    @ResponseBody
    public Map<String, Object> saveEmail(@RequestParam String email, HttpSession session) {
        String em = email == null ? "" : email.trim().toLowerCase();
        if (!em.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || em.length() > 150)
            return Map.of("ok", false, "error", "Email chưa đúng định dạng.");
        Student s = current(session);
        var other = students.findFirstByEmailIgnoreCase(em);
        if (other.isPresent() && !other.get().id.equals(s.id))
            return Map.of("ok", false, "error", "Email này đã được tài khoản khác dùng.");
        s.email = em;
        students.save(s);
        notify.welcomeAsync(s);
        return Map.of("ok", true);
    }
}
