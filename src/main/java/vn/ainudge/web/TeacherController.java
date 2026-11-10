package vn.ainudge.web;

import jakarta.servlet.http.HttpSession;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.ainudge.dto.ConvView;
import vn.ainudge.dto.Msg;
import vn.ainudge.dto.ProgressView;
import vn.ainudge.dto.QA;
import vn.ainudge.dto.StudentRow;
import vn.ainudge.dto.TaskRow;
import vn.ainudge.model.ChatLog;
import vn.ainudge.model.ClassRoom;
import vn.ainudge.model.Conversation;
import vn.ainudge.model.Student;
import vn.ainudge.model.StudyGroup;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;
import vn.ainudge.repo.ChatLogRepository;
import vn.ainudge.repo.ClassRoomRepository;
import vn.ainudge.repo.ConversationRepository;
import vn.ainudge.repo.ProofImageRepository;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.repo.StudyGroupRepository;
import vn.ainudge.repo.TaskImageRepository;
import vn.ainudge.repo.TaskProgressRepository;
import vn.ainudge.repo.TaskRepository;
import vn.ainudge.service.ClassDataService;
import vn.ainudge.service.Eval;
import vn.ainudge.service.Script;
import vn.ainudge.service.XlsxWriter;

/**
 * Trang người quản trị: xem nhiệm vụ, học sinh trả lời gì với trợ lý, và đánh
 * giá việc nộp bài.
 */
@Controller
@RequestMapping("/teacher")
public class TeacherController {
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final StudentRepository students;
    private final ConversationRepository convs;
    private final ChatLogRepository logs;
    private final TaskRepository tasks;
    private final TaskProgressRepository progresses;
    private final ClassDataService data;
    private final ProofImageRepository images;
    private final TaskImageRepository taskImages;
    private final vn.ainudge.service.AppCredentialService credentials;
    private final ClassRoomRepository classRooms;
    private final StudyGroupRepository groups;

    public TeacherController(StudentRepository students, ConversationRepository convs, ChatLogRepository logs,
            TaskRepository tasks, TaskProgressRepository progresses, ClassDataService data,
            ProofImageRepository images, TaskImageRepository taskImages,
            vn.ainudge.service.AppCredentialService credentials,
            ClassRoomRepository classRooms, StudyGroupRepository groups) {
        this.groups = groups;
        this.classRooms = classRooms;
        this.credentials = credentials;
        this.taskImages = taskImages;
        this.students = students;
        this.convs = convs;
        this.logs = logs;
        this.tasks = tasks;
        this.progresses = progresses;
        this.data = data;
        this.images = images;
    }

    /** Đổi mật khẩu quản trị viên hoặc PIN của ban cán sự. */
    @PostMapping("/security/change")
    public String changeSecurity(@RequestParam String kind, @RequestParam String oldValue,
            @RequestParam String newValue, @RequestParam String confirmValue,
            jakarta.servlet.http.HttpSession session,
            jakarta.servlet.http.HttpServletResponse response, RedirectAttributes ra) {
        if (session.getAttribute("teacher") == null)
            return "redirect:/teacher/login";
        if (!"admin".equals(kind) && !"monitor".equals(kind)) {
            ra.addFlashAttribute("msg", "Loại thông tin không hợp lệ.");
            return "redirect:/teacher";
        }
        boolean pin = "monitor".equals(kind);
        boolean valid = pin ? newValue.matches("[0-9]{4,12}") : newValue.length() >= 8 && newValue.length() <= 100;
        if (!valid) {
            ra.addFlashAttribute("msg", pin ? "PIN cần từ 4–12 chữ số." : "Mật khẩu quản trị cần từ 8–100 ký tự.");
            return "redirect:/teacher";
        }
        if (!newValue.equals(confirmValue)) {
            ra.addFlashAttribute("msg", "Giá trị nhập lại không khớp.");
            return "redirect:/teacher";
        }
        if (!credentials.change(kind, oldValue, newValue)) {
            ra.addFlashAttribute("msg", "Mật khẩu hoặc PIN hiện tại không đúng.");
            return "redirect:/teacher";
        }
        if (!pin) {
            session.invalidate();
            response.addHeader("Set-Cookie", "ainudge_t=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax");
            return "redirect:/teacher/login?changed=1";
        }
        ra.addFlashAttribute("msg", "Đã đổi PIN ban cán sự. Các lần đăng nhập mới phải dùng PIN mới.");
        return "redirect:/teacher";
    }

    /** Các dòng thống kê hoãn việc (hiển thị ở trang quản trị). */
    public record Stat(String label, long count) {
    }

    public record Postponer(Long id, String name, String className, int total, String reasons) {
    }

    public record HelpReq(Long id, String name, String className, String task) {
    }

    public record ClassRow(Long id, String name, long students) {
    }

    /** Nhóm nhiệm vụ theo lớp (nhiệm vụ giao nhiều lớp sẽ xuất hiện ở từng lớp). */
    public record TaskGroup(String title, List<TaskRow> tasks) {
    }

    /** Nhóm học sinh theo lớp. */
    public record StudentGroup(String title, List<StudentRow> rows) {
    }

    // ====================== BẢNG ĐIỀU KHIỂN (thanh bên trái, mỗi tab tải riêng) ======================

    /** Thẻ lớp trên trang Tổng quan. */
    public record ClassCard(Long id, String name, long students, long tasks) {
    }

    /** Thông tin phân trang; base là địa chỉ (đã kèm bộ lọc) để ghép số trang vào cuối. */
    public record PageInfo(int page, int pages, int total, int from, int to, int size, String base,
            List<Integer> nums) {
    }

    private static final List<String> TABS = List.of("overview", "tasks", "students", "classes", "hoan", "security",
            "data");

    /** Bỏ dấu tiếng Việt và chữ hoa để gõ "toan" vẫn tìm ra "Toán". */
    private static String fold(String s) {
        if (s == null)
            return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private PageInfo pageInfo(int total, int pageNo, int size, String tab, Long cls, String query) {
        int pages = Math.max(1, (total + size - 1) / size);
        int p = Math.min(Math.max(1, pageNo), pages);
        int from = total == 0 ? 0 : (p - 1) * size + 1;
        int to = Math.min(total, p * size);
        StringBuilder b = new StringBuilder("/teacher?tab=").append(tab).append("&size=").append(size);
        if (cls != null)
            b.append("&cls=").append(cls);
        if (!query.isEmpty())
            b.append("&q=").append(java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8));
        b.append("&page=");
        // các số trang cần hiện: đầu, cuối và 2 trang quanh trang hiện tại; số 0 = dấu "…"
        java.util.TreeSet<Integer> keep = new java.util.TreeSet<>();
        keep.add(1);
        keep.add(pages);
        for (int i = p - 2; i <= p + 2; i++)
            if (i >= 1 && i <= pages)
                keep.add(i);
        List<Integer> nums = new ArrayList<>();
        int prev = 0;
        for (int n : keep) {
            if (prev != 0 && n - prev > 1)
                nums.add(0);
            nums.add(n);
            prev = n;
        }
        return new PageInfo(p, pages, total, from, to, size, b.toString(), nums);
    }

    @GetMapping
    public String home(@RequestParam(required = false) String tab, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Long cls, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer size, HttpSession session, Model model) {
        // Mở trang không kèm tham số (ví dụ sau khi bấm Lưu/Xóa) thì quay lại đúng tab, trang và bộ lọc đang xem
        if (tab == null && page == null && cls == null && q == null && size == null) {
            tab = (String) session.getAttribute("tv.tab");
            page = (Integer) session.getAttribute("tv.page");
            cls = (Long) session.getAttribute("tv.cls");
            q = (String) session.getAttribute("tv.q");
            size = (Integer) session.getAttribute("tv.size");
        }
        if (tab == null || !TABS.contains(tab))
            tab = "overview";
        String query = q == null ? "" : q.trim();
        if (query.length() > 60)
            query = query.substring(0, 60);
        int pageSize = (size != null && (size == 10 || size == 20 || size == 50)) ? size
                : ("tasks".equals(tab) ? 10 : 20);
        int pageNo = page == null || page < 1 ? 1 : page;
        session.setAttribute("tv.tab", tab);
        session.setAttribute("tv.page", pageNo);
        session.setAttribute("tv.cls", cls);
        session.setAttribute("tv.q", query);
        session.setAttribute("tv.size", pageSize);

        model.addAttribute("tab", tab);
        model.addAttribute("title", switch (tab) {
            case "tasks" -> "Nhiệm vụ";
            case "students" -> "Học sinh";
            case "classes" -> "Lớp và tổ / nhóm";
            case "hoan" -> "Hoãn việc và hỗ trợ";
            case "security" -> "Bảo mật tài khoản";
            case "data" -> "Dữ liệu và xuất file";
            default -> "Tổng quan";
        });
        model.addAttribute("cls", cls);
        model.addAttribute("q", query);
        // số liệu nhỏ hiện trên thanh bên trái (đều là truy vấn đếm, rất nhẹ)
        model.addAttribute("total", students.count());
        model.addAttribute("taskCount", tasks.countByActiveTrue());
        model.addAttribute("helpCount", progresses.countByNeedHelpTrue());

        switch (tab) {
            case "overview" -> overviewTab(model);
            case "tasks" -> tasksTab(model, cls, query, pageNo, pageSize);
            case "students" -> studentsTab(model, cls, query, pageNo, pageSize);
            case "classes" -> classesTab(model);
            case "hoan" -> hoanTab(model);
            default -> {
            }
        }
        return "teacher";
    }

    /** Tạo các dòng nhiệm vụ cho một danh sách (tiến độ và ảnh lấy bằng 2 truy vấn chung). */
    private List<TaskRow> taskRowsFor(List<Task> list, List<Student> allStudents) {
        List<Long> ids = list.stream().map(t -> t.id).toList();
        Map<Long, List<TaskProgress>> progByTask = new HashMap<>();
        Map<Long, List<Long>> imgByTask = new HashMap<>();
        if (!ids.isEmpty()) {
            for (TaskProgress p : progresses.findByTask_IdIn(ids))
                progByTask.computeIfAbsent(p.task.id, k -> new ArrayList<>()).add(p);
            for (Object[] pr : taskImages.findIdPairsByTaskIds(ids))
                imgByTask.computeIfAbsent((Long) pr[0], k -> new ArrayList<>()).add((Long) pr[1]);
        }
        List<TaskRow> out = new ArrayList<>();
        for (Task t : list) {
            int st = 0, dn = 0, cf = 0;
            for (TaskProgress p : progByTask.getOrDefault(t.id, List.of())) {
                if (!t.assignedTo(p.student))
                    continue;
                if (p.startedAt != null)
                    st++;
                if ("DONE".equals(p.status))
                    dn++;
                if (p.confirmed)
                    cf++;
            }
            out.add(new TaskRow(t.id, Script.label(t), t.assignedDate(), t.deadline.format(DM),
                    t.createdBy == null ? "—" : t.createdBy.name,
                    (int) allStudents.stream().filter(t::assignedTo).count(), st, dn, cf,
                    imgByTask.getOrDefault(t.id, List.of()), t.classNames(), t.classIds(), t.groupIds()));
        }
        return out;
    }

    // ----- Tab Tổng quan: chỉ vài số đếm và 6 nhiệm vụ mới nhất -----
    private void overviewTab(Model model) {
        List<Student> allStudents = students.findAll();
        List<Task> active = tasks.findByActiveTrueOrderByDeadlineDesc();
        model.addAttribute("today", convs.countStudentsStartedSince(LocalDate.now().atStartOfDay()));
        model.addAttribute("confirmedTotal", progresses.countByConfirmedTrue());
        model.addAttribute("recentTasks", taskRowsFor(active.stream().limit(6).toList(), allStudents));
        List<ClassCard> cards = new ArrayList<>();
        for (ClassRoom c : classRooms.findAllByOrderByNameAsc())
            cards.add(new ClassCard(c.id, c.name, allStudents.stream().filter(x -> x.isMemberOf(c.id)).count(),
                    active.stream().filter(t -> t.classIds().contains(c.id)).count()));
        model.addAttribute("classCards", cards);
        model.addAttribute("noClassCount", allStudents.stream().filter(x -> x.joinedClasses().isEmpty()).count());
    }

    // ----- Tab Nhiệm vụ: lọc theo lớp, tìm theo tên, phân trang -----
    private void tasksTab(Model model, Long cls, String query, int pageNo, int pageSize) {
        List<Student> allStudents = students.findAll();
        String needle = fold(query);
        List<Task> filtered = new ArrayList<>();
        for (Task t : tasks.findByActiveTrueOrderByDeadlineDesc()) {
            if (cls != null && (cls == 0L ? !t.classIds().isEmpty() : !t.classIds().contains(cls)))
                continue;
            if (!needle.isEmpty() && !fold(Script.label(t)).contains(needle))
                continue;
            filtered.add(t);
        }
        PageInfo pi = pageInfo(filtered.size(), pageNo, pageSize, "tasks", cls, query);
        List<Task> slice = filtered.subList(Math.max(0, pi.from() - 1), pi.to());
        model.addAttribute("taskRows", taskRowsFor(slice, allStudents));
        model.addAttribute("pg", pi);
        model.addAttribute("classes", classRooms.findAllByOrderByNameAsc());
        model.addAttribute("groups", groups.findAll());
    }

    // ----- Tab Học sinh: lọc theo lớp, tìm theo tên, phân trang; chỉ tính số liệu cho học sinh của trang này -----
    private void studentsTab(Model model, Long cls, String query, int pageNo, int pageSize) {
        LocalDateTime now = LocalDateTime.now();
        String needle = fold(query);
        List<Student> filtered = new ArrayList<>();
        for (Student s : students.findAll()) {
            if (cls != null && (cls == 0L ? !s.joinedClasses().isEmpty() : !s.isMemberOf(cls)))
                continue;
            if (!needle.isEmpty() && !fold(s.name).contains(needle))
                continue;
            filtered.add(s);
        }
        filtered.sort(Comparator.comparing((Student s) -> s.name, String.CASE_INSENSITIVE_ORDER));
        PageInfo pi = pageInfo(filtered.size(), pageNo, pageSize, "students", cls, query);
        List<Student> slice = filtered.subList(Math.max(0, pi.from() - 1), pi.to());
        List<Long> ids = slice.stream().map(s -> s.id).toList();

        Map<Long, List<TaskProgress>> progByStudent = new HashMap<>();
        Map<Long, Object[]> convInfo = new HashMap<>();
        if (!ids.isEmpty()) {
            for (TaskProgress p : progresses.findByStudent_IdIn(ids))
                progByStudent.computeIfAbsent(p.student.id, k -> new ArrayList<>()).add(p);
            for (Object[] r : convs.summarizeByStudents(ids))
                convInfo.put((Long) r[0], r);
        }
        List<Task> activeTasks = tasks.findByActiveTrueOrderByDeadlineDesc();
        List<StudentRow> rows = new ArrayList<>();
        Map<Long, String> passwords = new HashMap<>();
        Map<Long, List<Long>> studentClassIds = new HashMap<>();
        Map<Long, List<Long>> studentGroupIds = new HashMap<>();
        for (Student s : slice) {
            Map<Long, TaskProgress> byTask = new HashMap<>();
            for (TaskProgress p : progByStudent.getOrDefault(s.id, List.of()))
                byTask.putIfAbsent(p.task.id, p);
            int started = 0, done = 0, confirmed = 0, early = 0, onTime = 0, late = 0, missing = 0;
            for (Task t : activeTasks) {
                if (!t.assignedTo(s))
                    continue; // nhiệm vụ của lớp khác thì không tính cho em này
                TaskProgress p = byTask.get(t.id);
                if (p != null) {
                    if (p.startedAt != null)
                        started++;
                    if ("DONE".equals(p.status))
                        done++;
                    if (p.confirmed)
                        confirmed++;
                }
                switch (Eval.result(t, p, now)) {
                    case Eval.EARLY -> early++;
                    case Eval.ON_TIME -> onTime++;
                    case Eval.LATE -> late++;
                    case Eval.MISSING -> missing++;
                    default -> {
                    }
                }
            }
            Object[] ci = convInfo.get(s.id);
            int sessions = ci == null ? 0 : ((Number) ci[1]).intValue();
            String last = ci == null || ci[2] == null ? "—" : ((LocalDateTime) ci[2]).format(DT);
            rows.add(new StudentRow(s.id, s.name, s.monitor, sessions, last, started, done, confirmed,
                    early, onTime, late, missing,
                    s.classRoom == null ? null : s.classRoom.id, s.classRoom == null ? "—" : s.classRoom.name));
            passwords.put(s.id, s.password == null ? "" : s.password);
            studentClassIds.put(s.id, s.joinedClasses().stream().map(c -> c.id).toList());
            studentGroupIds.put(s.id, s.studyGroups.stream().map(g -> g.id).toList());
        }
        Map<Long, List<StudyGroup>> groupsByClass = new HashMap<>();
        for (StudyGroup grp : groups.findAll())
            groupsByClass.computeIfAbsent(grp.classRoom.id, k -> new ArrayList<>()).add(grp);
        groupsByClass.values().forEach(l -> l.sort(
                Comparator.comparing((StudyGroup x) -> x.name, String.CASE_INSENSITIVE_ORDER)));
        model.addAttribute("rows", rows);
        model.addAttribute("pg", pi);
        model.addAttribute("passwords", passwords);
        model.addAttribute("studentClassIds", studentClassIds);
        model.addAttribute("studentGroupIds", studentGroupIds);
        model.addAttribute("groupsByClass", groupsByClass);
        model.addAttribute("classes", classRooms.findAllByOrderByNameAsc());
    }

    // ----- Tab Lớp và tổ / nhóm -----
    private void classesTab(Model model) {
        List<Student> allStudents = students.findAll();
        List<ClassRoom> classList = classRooms.findAllByOrderByNameAsc();
        List<ClassRow> classRows = new ArrayList<>();
        for (ClassRoom c : classList)
            classRows.add(new ClassRow(c.id, c.name, allStudents.stream().filter(x -> x.isMemberOf(c.id)).count()));
        List<StudyGroup> allGroups = groups.findAll();
        Map<Long, List<Student>> groupMembers = new HashMap<>();
        for (StudyGroup grp : allGroups)
            groupMembers.put(grp.id, allStudents.stream()
                    .filter(st -> st.studyGroups.stream().anyMatch(x -> x.id.equals(grp.id)))
                    .sorted(Comparator.comparing((Student st) -> st.name, String.CASE_INSENSITIVE_ORDER)).toList());
        model.addAttribute("classes", classList);
        model.addAttribute("classRows", classRows);
        model.addAttribute("groups", allGroups);
        model.addAttribute("groupMembers", groupMembers);
    }

    // ----- Tab Hoãn việc và hỗ trợ (cần đọc mọi tiến độ nên chỉ tải khi mở tab này) -----
    private void hoanTab(Model model) {
        List<TaskProgress> allProgress = progresses.findAll();
        Map<String, Long> reasonCount = new java.util.LinkedHashMap<>();
        for (String code : new String[] { "hard", "tired", "busy", "bored" })
            reasonCount.put(code, 0L);
        Map<String, Long> bandCount = new java.util.LinkedHashMap<>();
        for (String b : new String[] { "Sáng (5–12h)", "Chiều (12–18h)", "Tối (18–22h)", "Khuya (22–5h)" })
            bandCount.put(b, 0L);
        Map<Long, int[]> perStudent = new HashMap<>();
        Map<Long, String> studentName = new HashMap<>();
        Map<Long, String> studentClass = new HashMap<>();
        Map<Long, Map<String, Integer>> studentReasons = new HashMap<>();
        List<HelpReq> helpReqs = new ArrayList<>();
        long postponeAll = 0;
        for (TaskProgress p : allProgress) {
            postponeAll += p.postponeTotal;
            String clsName = p.student.classRoom == null ? "—" : p.student.classRoom.name;
            if (p.needHelp)
                helpReqs.add(new HelpReq(p.student.id, p.student.name, clsName, Script.label(p.task)));
            if (p.postponeTotal > 0) {
                perStudent.computeIfAbsent(p.student.id, k -> new int[1])[0] += p.postponeTotal;
                studentName.put(p.student.id, p.student.name);
                studentClass.put(p.student.id, clsName);
            }
            for (String[] e : Script.parsePostponeLog(p.postponeLog)) {
                reasonCount.merge(e[0], 1L, Long::sum);
                int h = 0;
                try {
                    h = Integer.parseInt(e[1]);
                } catch (Exception ex) {
                    /* bỏ qua */ }
                String band = h >= 5 && h < 12 ? "Sáng (5–12h)"
                        : h >= 12 && h < 18 ? "Chiều (12–18h)" : h >= 18 && h < 22 ? "Tối (18–22h)" : "Khuya (22–5h)";
                bandCount.merge(band, 1L, Long::sum);
                studentReasons.computeIfAbsent(p.student.id, k -> new java.util.LinkedHashMap<>()).merge(e[0], 1,
                        Integer::sum);
            }
        }
        List<Stat> reasonStats = new ArrayList<>();
        reasonCount.forEach((k, v) -> reasonStats.add(new Stat(Script.reasonLabel(k), v)));
        List<Stat> bandStats = new ArrayList<>();
        bandCount.forEach((k, v) -> bandStats.add(new Stat(k, v)));
        List<Postponer> postponers = new ArrayList<>();
        perStudent.forEach((id, arr) -> {
            StringBuilder sb = new StringBuilder();
            studentReasons.getOrDefault(id, Map.of()).entrySet().stream()
                    .sorted((x, y) -> y.getValue() - x.getValue())
                    .forEach(e -> sb.append(sb.length() == 0 ? "" : ", ").append(Script.reasonLabel(e.getKey()))
                            .append(" ×").append(e.getValue()));
            postponers.add(new Postponer(id, studentName.get(id), studentClass.getOrDefault(id, "—"), arr[0],
                    sb.length() == 0 ? "—" : sb.toString()));
        });
        postponers.sort((x, y) -> y.total() - x.total());
        model.addAttribute("postponeAll", postponeAll);
        model.addAttribute("reasonStats", reasonStats);
        model.addAttribute("bandStats", bandStats);
        model.addAttribute("statRows", Math.max(1, Math.max(reasonStats.size(), bandStats.size())));
        model.addAttribute("postponers", postponers.size() > 10 ? postponers.subList(0, 10) : postponers);
        model.addAttribute("helpReqs", helpReqs);
    }

    /**
     * Chi tiết một học sinh: tiến độ từng nhiệm vụ + các câu trả lời + cuộc trò
     * chuyện.
     */
    @GetMapping("/student/{id}")
    public String student(@PathVariable Long id, Model model) {
        LocalDateTime now = LocalDateTime.now();
        Student s = students.findById(id).orElseThrow();

        List<ProgressView> pv = new ArrayList<>();
        for (Task t : tasks.findByActiveTrueOrderByDeadlineDesc()) {
            if (!t.assignedTo(s))
                continue;
            TaskProgress p = progresses.findByTask_IdAndStudent_Id(t.id, s.id).orElse(null);
            String plan = p == null || p.plannedLabel == null ? "—"
                    : p.plannedLabel + (p.place == null ? "" : " · " + p.place);
            pv.add(new ProgressView(Script.label(t), t.assignedDate(), t.deadline.format(DM), Eval.statusLabel(p), plan,
                    p == null || p.barrier == null ? "—" : p.barrier,
                    p == null || Script.postponeText(p).isEmpty() ? "—" : Script.postponeText(p),
                    p == null || p.proof == null ? "—" : p.proof,
                    p != null && p.selfSubmitted ? "Có" : "Không",
                    p != null && p.confirmed ? "Có (" + p.confirmedBy + ")" : "Chưa",
                    Eval.result(t, p, now),
                    p == null ? List.of()
                            : images.findByProgress_IdOrderByIdAsc(p.id).stream().map(i -> i.id).toList()));
        }

        List<ConvView> views = new ArrayList<>();
        for (Conversation c : convs.findByStudent_IdOrderByStartedAtDesc(id)) {
            List<QA> qa = new ArrayList<>();
            List<Msg> chat = new ArrayList<>();
            for (ChatLog l : logs.findByConversation_IdOrderByIdAsc(c.id)) {
                chat.add(new Msg(l.sender, l.content, l.kind));
                if ("STUDENT".equals(l.sender))
                    qa.add(new QA(Script.stepLabel(l.step), l.content));
            }
            views.add(new ConvView(c.startedAt.format(DT), qa, chat));
        }
        model.addAttribute("student", s);
        model.addAttribute("progress", pv);
        model.addAttribute("convs", views);
        return "teacher-student";
    }

    /** Người quản trị chỉ định (hoặc bỏ) ban cán sự. */
    @PostMapping("/student/{id}/monitor")
    public String toggleMonitor(@PathVariable Long id) {
        Student s = students.findById(id).orElseThrow();
        s.monitor = !s.monitor;
        students.save(s);
        return "redirect:/teacher";
    }

    /** Thêm danh sách lớp: mỗi dòng một tên. */
    @PostMapping("/roster")
    public String roster(@RequestParam String names, @RequestParam(required = false) Long classId,
            RedirectAttributes ra) {
        ra.addFlashAttribute("msg", "Đã thêm " + data.addRoster(names, classId) + " học sinh mới"
                + (classId == null ? "" : " vào lớp đã chọn"));
        return "redirect:/teacher";
    }

    /** Bỏ lớp của riêng một học sinh, không xóa tài khoản. */
    /** Tạo tổ/nhóm thuộc một lớp. */
    @PostMapping("/group")
    public String createGroup(@RequestParam Long classId, @RequestParam String name, RedirectAttributes ra) {
        String err = data.createGroup(classId, name);
        ra.addFlashAttribute("msg", err == null ? "Đã tạo tổ/nhóm." : err);
        return "redirect:/teacher";
    }

    @PostMapping("/group/{id}/delete")
    public String deleteGroup(@PathVariable Long id, RedirectAttributes ra) {
        String err = data.deleteGroup(id);
        ra.addFlashAttribute("msg", err == null ? "Đã xóa tổ/nhóm." : err);
        return "redirect:/teacher";
    }

    /** Phân (hoặc bỏ) tổ trưởng của một tổ. */
    @PostMapping("/group/{id}/leader")
    public String setGroupLeader(@PathVariable Long id, @RequestParam(required = false) Long studentId,
            RedirectAttributes ra) {
        String err = data.setGroupLeader(id, studentId);
        ra.addFlashAttribute("msg", err == null ? "Đã lưu tổ trưởng." : err);
        return "redirect:/teacher";
    }

    /** Xếp một học sinh vào một tổ trong một lớp (mỗi lớp chỉ một tổ). */
    @PostMapping("/student/{id}/group")
    public String setStudentGroup(@PathVariable Long id, @RequestParam Long classId,
            @RequestParam(required = false) Long groupId, RedirectAttributes ra) {
        String err = data.setStudentGroupInClass(id, classId, groupId);
        if (err != null) ra.addFlashAttribute("msg", err);
        return "redirect:/teacher";
    }

    @PostMapping("/student/{id}/groups")
    public String setStudentGroups(@PathVariable Long id,
            @RequestParam(value = "groupIds", required = false) List<Long> groupIds, RedirectAttributes ra) {
        String err = data.setStudentGroups(id, groupIds);
        ra.addFlashAttribute("msg", err == null ? "Đã lưu tổ/nhóm của học sinh." : err);
        return "redirect:/teacher";
    }

    /** Tạo lớp mới. */
    @PostMapping("/class")
    public String createClass(@RequestParam String name, RedirectAttributes ra) {
        String err = data.createClass(name);
        ra.addFlashAttribute("msg", err == null ? "Đã tạo lớp" : err);
        return "redirect:/teacher";
    }

    /** Xóa lớp (học sinh trong lớp trở thành chưa có lớp). */
    @PostMapping("/class/{id}/delete")
    public String deleteClass(@PathVariable Long id, RedirectAttributes ra) {
        String err = data.deleteClass(id);
        ra.addFlashAttribute("msg", err == null ? "Đã xóa lớp" : err);
        return "redirect:/teacher";
    }

    /** Xếp (hoặc đổi) lớp cho một học sinh. Không chọn lớp = bỏ khỏi lớp. */
    @PostMapping("/student/{id}/class")
    public String setStudentClass(@PathVariable Long id, @RequestParam(required = false) Long classId,
            RedirectAttributes ra) {
        String err = data.setStudentClass(id, classId);
        ra.addFlashAttribute("msg", err == null ? "Đã cập nhật lớp của học sinh." : err);
        return "redirect:/teacher";
    }

    /**
     * Đổi các lớp được giao của một nhiệm vụ. Không chọn lớp nào = giao cho tất cả
     * học sinh.
     */
    @PostMapping("/task/{id}/classes")
    public String setTaskClasses(@PathVariable Long id,
            @RequestParam(value = "classIds", required = false) List<Long> classIds, RedirectAttributes ra) {
        data.setTaskClasses(id, classIds);
        ra.addFlashAttribute("msg", "Đã cập nhật lớp được giao của nhiệm vụ");
        return "redirect:/teacher";
    }

    @PostMapping("/task/{id}/groups")
    public String setTaskGroups(@PathVariable Long id,
            @RequestParam(value = "groupIds", required = false) List<Long> groupIds, RedirectAttributes ra) {
        String err = data.setTaskGroups(id, groupIds);
        ra.addFlashAttribute("msg", err == null ? "Đã giao nhiệm vụ riêng cho tổ/nhóm." : err);
        return "redirect:/teacher";
    }

    /**
     * Người quản trị tự nhập mật khẩu cho một học sinh, hoặc bấm 🎲 để tạo ngẫu
     * nhiên.
     */
    @PostMapping("/student/{id}/password")
    public String setPassword(@PathVariable Long id, @RequestParam(required = false) String password,
            @RequestParam(required = false) String random, RedirectAttributes ra) {
        if (random != null || password == null || password.isBlank()) {
            data.resetPassword(id);
            ra.addFlashAttribute("msg", "Đã tạo mật khẩu ngẫu nhiên mới");
        } else {
            String err = data.setPassword(id, password);
            ra.addFlashAttribute("msg", err == null ? "Đã lưu mật khẩu" : err);
        }
        return "redirect:/teacher";
    }

    /** Cấp mật khẩu cho những bạn chưa có. */
    @PostMapping("/passwords/fill")
    public String fillPasswords(RedirectAttributes ra) {
        ra.addFlashAttribute("msg", "Đã cấp mật khẩu cho " + data.fillMissingPasswords() + " học sinh");
        return "redirect:/teacher";
    }

    /** Tải danh sách tên + mật khẩu (Excel) để in / gửi cho từng em. */
    @GetMapping("/accounts.xlsx")
    public ResponseEntity<byte[]> exportAccounts() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("Học sinh", "Lớp", "Mật khẩu"));
        List<Student> list = new ArrayList<>(students.findAll());
        list.sort(Comparator.comparing((Student x) -> x.name, String.CASE_INSENSITIVE_ORDER));
        for (Student s : list)
            rows.add(row(s.name, s.classRoom == null ? "" : s.classRoom.name, s.password));
        return xlsx("Mật khẩu", "mat-khau-hoc-sinh.xlsx", rows);
    }

    @PostMapping("/task/{id}/delete")
    public String deleteTask(@PathVariable Long id, RedirectAttributes ra) {
        data.deleteTask(id);
        ra.addFlashAttribute("msg", "Đã xóa nhiệm vụ");
        return "redirect:/teacher";
    }

    @PostMapping("/student/{id}/delete")
    public String deleteStudent(@PathVariable Long id, RedirectAttributes ra) {
        data.deleteStudent(id);
        ra.addFlashAttribute("msg", "Đã xóa học sinh");
        return "redirect:/teacher";
    }

    /** Xóa toàn bộ nhiệm vụ, tiến độ, trò chuyện (giữ danh sách học sinh). */
    @PostMapping("/reset")
    public String reset(RedirectAttributes ra) {
        data.resetData();
        ra.addFlashAttribute("msg", "Đã xóa toàn bộ nhiệm vụ và trò chuyện");
        return "redirect:/teacher";
    }

    /** Tải câu trả lời của học sinh với trợ lý (file Excel). */
    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportAnswers() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("Thời gian", "Học sinh", "Câu hỏi", "Trả lời"));
        for (ChatLog l : logs.findBySenderOrderByIdAsc("STUDENT"))
            rows.add(row(l.createdAt.format(DT), l.conversation.student.name, Script.stepLabel(l.step), l.content));
        return xlsx("Câu trả lời", "tra-loi-hoc-sinh.xlsx", rows);
    }

    private static final String[] PROGRESS_HEAD = { "Học sinh", "Nhiệm vụ", "Ngày giao", "Hạn nộp", "Trạng thái", "Giờ hẹn",
            "Điều khiến chưa bắt đầu", "Số lần hoãn", "Lý do hoãn / cần hỗ trợ", "Bắt đầu lúc", "Báo xong lúc",
            "Học sinh kể", "Học sinh báo đã nộp",
            "Số ảnh minh chứng", "Ban cán sự xác nhận", "Kết quả" };

    /** Các dòng tiến độ (mỗi học sinh x mỗi nhiệm vụ), dòng đầu là tiêu đề. */
    private List<List<String>> progressRows(List<Student> list) {
        LocalDateTime now = LocalDateTime.now();
        List<List<String>> rows = new ArrayList<>();
        rows.add(row(PROGRESS_HEAD));
        List<Student> all = new ArrayList<>(list);
        all.sort(Comparator.comparing((Student x) -> x.name, String.CASE_INSENSITIVE_ORDER));
        for (Task t : tasks.findByActiveTrueOrderByDeadlineDesc()) {
            for (Student s : all) {
                if (!t.assignedTo(s))
                    continue;
                TaskProgress p = progresses.findByTask_IdAndStudent_Id(t.id, s.id).orElse(null);
                rows.add(row(s.name, Script.label(t), t.createdAt == null ? "" : t.createdAt.format(DT), t.deadline.format(DT), Eval.statusLabel(p),
                        p == null ? "" : p.plannedLabel,
                        p == null ? "" : p.barrier,
                        String.valueOf(p == null ? 0 : p.postponeTotal),
                        Script.postponeText(p),
                        p == null || p.startedAt == null ? "" : p.startedAt.format(DT),
                        p == null || p.doneAt == null ? "" : p.doneAt.format(DT),
                        p == null ? "" : p.proof,
                        p != null && p.selfSubmitted ? "Có" : "Không",
                        String.valueOf(p == null ? 0 : images.findByProgress_IdOrderByIdAsc(p.id).size()),
                        p != null && p.confirmed ? "Có" : "Chưa",
                        Eval.result(t, p, now)));
            }
        }
        return rows;
    }

    /** Tải bảng tiến độ và kết quả nộp bài của CẢ NHÓM (file Excel). */
    @GetMapping("/progress.xlsx")
    public ResponseEntity<byte[]> exportProgress() {
        return xlsx("Tiến độ nộp bài", "tien-do-nop-bai.xlsx", progressRows(students.findAll()));
    }

    /**
     * Tải file Excel của MỘT học sinh: sheet 1 là tiến độ từng nhiệm vụ, sheet 2 là
     * các câu em ấy trả lời với trợ lý.
     */
    @GetMapping("/student/{id}/export.xlsx")
    public ResponseEntity<byte[]> exportStudent(@PathVariable Long id) {
        Student s = students.findById(id).orElseThrow();

        List<List<String>> answers = new ArrayList<>();
        answers.add(row("Thời gian", "Học sinh", "Câu hỏi", "Trả lời"));
        List<ChatLog> mine = new ArrayList<>(logs.findByConversation_Student_Id(id));
        mine.sort(Comparator.comparing((ChatLog l) -> l.id));
        for (ChatLog l : mine)
            if ("STUDENT".equals(l.sender))
                answers.add(row(l.createdAt == null ? "" : l.createdAt.format(DT), s.name, Script.stepLabel(l.step),
                        l.content));

        Map<String, List<List<String>>> sheets = new java.util.LinkedHashMap<>();
        sheets.put("Tiến độ nộp bài", progressRows(List.of(s)));
        sheets.put("Câu trả lời", answers);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment()
                                .filename("hoc-sinh-" + s.name.trim().replaceAll("[^\\p{L}\\p{N}]+", "-") + ".xlsx",
                                        java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(XlsxWriter.build(sheets));
    }

    private static List<String> row(String... values) {
        List<String> r = new ArrayList<>();
        for (String v : values)
            r.add(v == null ? "" : v);
        return r;
    }

    private ResponseEntity<byte[]> xlsx(String sheet, String fileName, List<List<String>> rows) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(XlsxWriter.build(sheet, rows));
    }
}