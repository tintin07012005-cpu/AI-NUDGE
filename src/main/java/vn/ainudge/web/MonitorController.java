package vn.ainudge.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.ainudge.dto.MemberRow;
import vn.ainudge.dto.TaskRow;
import vn.ainudge.model.Student;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;
import vn.ainudge.repo.ProofImageRepository;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.repo.TaskImageRepository;
import vn.ainudge.repo.TaskProgressRepository;
import vn.ainudge.repo.TaskRepository;
import vn.ainudge.service.Eval;
import vn.ainudge.service.ProofService;
import vn.ainudge.service.RememberService;
import vn.ainudge.service.Script;

/** Trang của BAN CÁN SỰ: nhập nhiệm vụ trong ngày và xác nhận bạn nào đã nộp bài. */
@Controller
@RequestMapping("/monitor")
public class MonitorController {
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final vn.ainudge.service.AppCredentialService credentials;

    private final StudentRepository students;
    private final TaskRepository tasks;
    private final TaskProgressRepository progresses;
    private final ProofImageRepository images;
    private final TaskImageRepository taskImages;
    private final ProofService proof;
    private final RememberService remember;

    public MonitorController(StudentRepository students, TaskRepository tasks, TaskProgressRepository progresses,
                             ProofImageRepository images, TaskImageRepository taskImages, ProofService proof,
                             RememberService remember, vn.ainudge.service.AppCredentialService credentials) {
        this.credentials=credentials;
        this.students = students;
        this.tasks = tasks;
        this.progresses = progresses;
        this.images = images;
        this.taskImages = taskImages;
        this.proof = proof;
        this.remember = remember;
    }

    private List<Long> imageIds(Long taskId) {
        return taskImages.findByTask_IdOrderByIdAsc(taskId).stream().map(i -> i.id).toList();
    }

    /** Trả về ban cán sự đang đăng nhập VÀ đã nhập đúng mã PIN, ngược lại trả về null. */
    private Student monitor(HttpSession session) {
        Student s = students.findById((Long) session.getAttribute("studentId")).orElse(null);
        boolean unlocked = session.getAttribute("monitorOk") != null;
        return s != null && s.monitor && unlocked ? s : null;
    }

    /** Nhập mã PIN của ban cán sự (để học sinh khác không thể mạo danh ban cán sự). */
    @PostMapping("/pin")
    public String pin(@RequestParam String pin, HttpServletRequest req, HttpServletResponse res,
                      HttpSession session, Model model) {
        Student s = students.findById((Long) session.getAttribute("studentId")).orElse(null);
        if (s == null || !s.monitor) return "redirect:/student";
        if (!credentials.verify("monitor", pin.trim())) { model.addAttribute("error", "Sai mã PIN."); return "monitor-pin"; }
        session.setAttribute("monitorOk", true);
        remember.rememberMonitor(req, res, s);          // lần sau không phải nhập lại PIN
        return "redirect:/monitor";
    }

    @GetMapping
    public String home(@RequestParam(required = false) String tooBig, HttpSession session, Model model) {
        Student me = students.findById((Long) session.getAttribute("studentId")).orElse(null);
        if (me == null || !me.monitor) return "redirect:/student";
        Student m = monitor(session);
        if (m == null) return "monitor-pin";
        int total = (int) students.count();
        List<TaskRow> rows = new ArrayList<>();
        for (Task t : tasks.findByActiveTrueOrderByDeadlineDesc()) {
            if (t.deadline.isBefore(LocalDate.now().minusDays(1).atStartOfDay())) continue;   // chỉ hiện nhiệm vụ gần đây
            int done = 0, conf = 0;
            for (TaskProgress p : progresses.findByTask_Id(t.id)) { if ("DONE".equals(p.status)) done++; if (p.confirmed) conf++; }
            rows.add(new TaskRow(t.id, Script.label(t), t.deadline.format(DM),
                t.createdBy == null ? "—" : t.createdBy.name, total, 0, done, conf, imageIds(t.id)));
        }
        model.addAttribute("name", m.name);
        model.addAttribute("rows", rows);
        model.addAttribute("defaultDeadline", LocalDate.now().atTime(21, 0).toString());
        if (tooBig != null) model.addAttribute("msg", "Ảnh quá lớn nên chưa gửi được. Mỗi ảnh tối đa 10 MB, mỗi lần gửi tối đa 60 MB.");
        return "monitor";
    }

    @PostMapping("/task")
    public String create(@RequestParam String subject, @RequestParam String title,
                         @RequestParam String deadline,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         HttpSession session, RedirectAttributes ra) {
        Student m = monitor(session);
        if (m == null) return "redirect:/monitor";
        String ti = title == null ? "" : title.trim();
        if (files == null || files.stream().noneMatch(f -> f != null && !f.isEmpty())) {
            ra.addFlashAttribute("msg", "Chưa có đề bài: hãy chọn ít nhất một ảnh hoặc file Word (.docx).");
            return "redirect:/monitor";
        }
        if (!ti.isEmpty()) {
            Task t = new Task();
            t.subject = subject.trim();
            t.title = ti.length() > 190 ? ti.substring(0, 190) : ti;
            t.deadline = LocalDateTime.parse(deadline);
            t.createdAt = LocalDateTime.now();
            t.createdBy = m;
            t.active = true;
            tasks.save(t);
            // ảnh đề bài (có thể nhiều ảnh) để học sinh biết đó là bài nào
            ProofService.Result r = proof.saveTaskImages(t, files);
            if (r.saved() == 0) {
                tasks.delete(t); // không tạo nhiệm vụ nếu tất cả tệp bị từ chối
                ra.addFlashAttribute("msg", r.error() == null ? "Không tải được tệp đề bài." : r.error());
            } else if (r.error() != null) ra.addFlashAttribute("msg", r.error());
        }
        return "redirect:/monitor";
    }

    /** Thêm ảnh đề cho một nhiệm vụ đã tạo. */
    @PostMapping("/task/{id}/images")
    public String addImages(@PathVariable Long id,
                            @RequestParam(value = "files", required = false) List<MultipartFile> files,
                            HttpSession session, RedirectAttributes ra) {
        if (monitor(session) == null) return "redirect:/monitor";
        Task t = tasks.findById(id).orElse(null);
        if (t != null && t.active) {
            ProofService.Result r = proof.saveTaskImages(t, files);
            ra.addFlashAttribute("msg", r.error() != null ? r.error() : (r.saved() > 0 ? "Đã thêm " + r.saved() + " tệp đề." : "Bạn chưa chọn tệp nào."));
        }
        return "redirect:/monitor";
    }

    /** Xóa một ảnh đề (gửi nhầm). */
    @PostMapping("/task-image/{id}/delete")
    public String deleteImage(@PathVariable Long id, HttpSession session) {
        if (monitor(session) == null) return "redirect:/monitor";
        proof.deleteTaskImage(id);
        return "redirect:/monitor";
    }

    /** "Xóa" nhiệm vụ (ẩn đi, dữ liệu vẫn còn để người quản trị xem lại). */
    @PostMapping("/task/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session) {
        if (monitor(session) == null) return "redirect:/monitor";
        tasks.findById(id).ifPresent(t -> { t.active = false; tasks.save(t); });
        return "redirect:/monitor";
    }

    /** Danh sách cả lớp cho một nhiệm vụ để xác nhận ai đã nộp. */
    @GetMapping("/task/{id}")
    public String roster(@PathVariable Long id, HttpSession session, Model model) {
        if (monitor(session) == null) return "redirect:/monitor";
        Task t = tasks.findById(id).orElseThrow();
        List<MemberRow> rows = new ArrayList<>();
        for (Student s : students.findAll()) {
            TaskProgress p = progresses.findByTask_IdAndStudent_Id(t.id, s.id).orElse(null);
            rows.add(new MemberRow(s.id, s.name, Eval.statusLabel(p),
                p == null || p.proof == null ? "" : p.proof,
                p != null && p.selfSubmitted,
                p != null && p.confirmed,
                p == null || p.confirmedAt == null ? "" : p.confirmedAt.format(DM),
                p == null ? List.of() : images.findByProgress_IdOrderByIdAsc(p.id).stream().map(i -> i.id).toList()));
        }
        rows.sort(Comparator.comparing(MemberRow::name, String.CASE_INSENSITIVE_ORDER));
        model.addAttribute("task", Script.label(t));
        model.addAttribute("deadline", t.deadline.format(DM));
        model.addAttribute("taskId", t.id);
        model.addAttribute("taskImages", imageIds(t.id));
        model.addAttribute("rows", rows);
        return "monitor-task";
    }

    /** Xác nhận (hoặc bỏ xác nhận) học sinh đã nộp bài. */
    @PostMapping("/task/{id}/confirm")
    public String confirm(@PathVariable Long id, @RequestParam Long studentId,
                          @RequestParam boolean value, HttpSession session) {
        Student m = monitor(session);
        if (m == null) return "redirect:/monitor";
        Task t = tasks.findById(id).orElseThrow();
        Student s = students.findById(studentId).orElseThrow();
        TaskProgress p = progresses.findByTask_IdAndStudent_Id(t.id, s.id).orElseGet(() -> {
            TaskProgress x = new TaskProgress();
            x.task = t;
            x.student = s;
            return x;
        });
        p.confirmed = value;
        p.confirmedAt = value ? LocalDateTime.now() : null;
        p.confirmedBy = value ? m.name : null;
        progresses.save(p);
        return "redirect:/monitor/task/" + id;
    }
}
