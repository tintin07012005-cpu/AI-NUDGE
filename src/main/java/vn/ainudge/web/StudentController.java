package vn.ainudge.web;

import jakarta.servlet.http.HttpSession;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.ainudge.dto.Reply;
import vn.ainudge.dto.WeekPlan;
import vn.ainudge.dto.WeekTask;
import vn.ainudge.dto.TaskChip;
import vn.ainudge.model.ClassRoom;
import vn.ainudge.model.Student;
import vn.ainudge.model.StudyGroup;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;
import vn.ainudge.repo.ClassRoomRepository;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.repo.StudyGroupRepository;
import vn.ainudge.repo.TaskRepository;
import vn.ainudge.repo.TaskProgressRepository;
import vn.ainudge.repo.TaskImageRepository;
import vn.ainudge.service.ChatService;
import vn.ainudge.service.ClassDataService;
import vn.ainudge.service.NotifyService;
import vn.ainudge.service.PresenceTracker;

@Controller
@RequestMapping("/student")
public class StudentController {
    private final StudentRepository students;
    private final ChatService chat;
    private final NotifyService notify;
    private final PresenceTracker presence;
    private final ClassRoomRepository classRooms;
    private final StudyGroupRepository studyGroups;
    private final ClassDataService data;
    private final TaskRepository tasks;
    private final TaskProgressRepository progresses;
    private final TaskImageRepository taskImages;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE dd/MM", new java.util.Locale("vi", "VN"));
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("HH:mm dd/MM");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Mỗi học sinh một khóa: trình duyệt vừa hỏi định kỳ (poll) vừa gửi câu trả lời
     * (answer),
     * nếu hai yêu cầu chạy song song thì yêu cầu này có thể ghi đè kết quả của yêu
     * cầu kia
     * (mất lượt hoãn, nhắc sai giờ, hỏi lại câu cũ). Khóa đặt ở đây để bao trọn cả
     * phần lưu dữ liệu.
     */
    private static final ConcurrentHashMap<Long, Object> LOCKS = new ConcurrentHashMap<>();

    private static Object lockOf(Student s) {
        return LOCKS.computeIfAbsent(s.id, k -> new Object());
    }

    public StudentController(StudentRepository students, ChatService chat, NotifyService notify,
            PresenceTracker presence, ClassRoomRepository classRooms,
            TaskRepository tasks, TaskProgressRepository progresses, TaskImageRepository taskImages,
            StudyGroupRepository studyGroups, ClassDataService data) {
        this.studyGroups = studyGroups;
        this.data = data;
        this.tasks = tasks;
        this.progresses = progresses;
        this.taskImages = taskImages;
        this.classRooms = classRooms;
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
        List<ClassRoom> classList = classRooms.findAllByOrderByNameAsc();
        model.addAttribute("className", s.classRoom == null ? null : s.classRoom.name);
        model.addAttribute("activeClassId", s.classRoom == null ? null : s.classRoom.id);
        model.addAttribute("availableClasses", classList.stream()
                .filter(c -> !s.isMemberOf(c.id)).toList());
        model.addAttribute("classes", classList);
        model.addAttribute("needClass", s.classRoom == null && !classList.isEmpty());
        List<StudyGroup> classGroups = s.classRoom == null ? List.of()
                : studyGroups.findByClassRoom_IdOrderByNameAsc(s.classRoom.id);
        model.addAttribute("classGroups", classGroups);
        model.addAttribute("isLeader", !studyGroups.findByLeader_Id(s.id).isEmpty());
        model.addAttribute("myGroupId", s.classRoom == null ? null
                : s.studyGroups.stream().filter(g -> g.classRoom.id.equals(s.classRoom.id))
                        .map(g -> g.id).findFirst().orElse(null));
        return "student";
    }

    /** Đăng ký thêm lớp bằng tài khoản hiện có; không tạo tài khoản/email khác. */
    /** Chọn lớp lần đầu, hoặc chuyển sang lớp khác (mỗi học sinh chỉ một lớp). */
    @PostMapping("/class")
    public String joinClass(@RequestParam Long classId, HttpSession session, RedirectAttributes ra) {
        Student s = current(session);
        synchronized (lockOf(s)) {
            String err = data.setStudentClass(s.id, classId);
            ra.addFlashAttribute("msg", err == null ? "Đã cập nhật lớp của bạn." : err);
            if (err == null) {
                Student fresh = current(session);
                if (fresh.classRoom != null) chat.open(fresh, true); // khởi tạo cuộc trò chuyện cho lớp mới
            }
        }
        return "redirect:/student";
    }

    /** Học sinh tự chọn/đổi tổ-nhóm của mình trong lớp đang xem (các lớp khác giữ nguyên). */
    @PostMapping("/groups")
    public String saveMyGroups(@RequestParam(required = false) Long groupId, HttpSession session) {
        Student s = current(session);
        synchronized (lockOf(s)) {
            data.setMyGroup(s.id, groupId);
        }
        return "redirect:/student";
    }

    /** Kế hoạch tuần có trạng thái thật của RIÊNG học sinh, theo lớp đang mở. */
    @GetMapping("/api/week")
    @ResponseBody
    public WeekPlan week(HttpSession session) {
        Student s = current(session);
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        List<WeekTask> rows = new java.util.ArrayList<>();
        int completed = 0;
        // Chỉ lấy nhiệm vụ có thể thuộc về học sinh này, rồi lấy tiến độ và ảnh bằng một truy vấn cho cả tuần
        List<Long> groupIds = s.studyGroups.isEmpty() ? List.of(-1L) : s.studyGroups.stream().map(g -> g.id).toList();
        Long classId = s.classRoom == null ? -1L : s.classRoom.id;
        List<Task> weekTasks = tasks.findForStudentBetween(monday.atStartOfDay(), monday.plusDays(7).atStartOfDay(),
                classId, groupIds).stream().filter(t -> t.visibleTo(s)).toList();
        java.util.Map<Long, TaskProgress> progressByTask = new java.util.HashMap<>();
        java.util.Map<Long, List<Long>> imagesByTask = new java.util.HashMap<>();
        if (!weekTasks.isEmpty()) {
            List<Long> ids = weekTasks.stream().map(t -> t.id).toList();
            for (TaskProgress p : progresses.findByStudent_IdAndTask_IdIn(s.id, ids))
                progressByTask.putIfAbsent(p.task.id, p);
            for (Object[] row : taskImages.findIdPairsByTaskIds(ids))
                imagesByTask.computeIfAbsent((Long) row[0], k -> new java.util.ArrayList<>()).add((Long) row[1]);
        }
        for (Task t : weekTasks) {
            TaskProgress progress = progressByTask.get(t.id);
            String status = progress == null ? "TODO" : progress.confirmed ? "CONFIRMED" : progress.status;
            if (status == null) status = "TODO";
            if ("DONE".equals(status) || "CONFIRMED".equals(status)) completed++;
            String label = switch (status) {
                case "CONFIRMED" -> "Đã nộp · được xác nhận";
                case "DONE" -> "Đã hoàn thành";
                case "STARTED" -> "Đang làm";
                case "PLANNED" -> "Đã lên lịch";
                default -> "Chưa hoàn thành";
            };
            rows.add(new WeekTask(t.id, t.subject + " · " + t.title,
                    t.deadline.format(DAY), t.assignedDate(), t.deadline.format(DM), status, label,
                    t.classNames(), imagesByTask.getOrDefault(t.id, List.of())));
        }
        return new WeekPlan(monday.format(DATE), sunday.format(DATE),
                rows.size(), completed, rows.size() - completed, rows);
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

    /**
     * Học sinh gửi ảnh minh chứng (nhiều ảnh) ở bước "làm xong" trong khung chat.
     */
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