package vn.ainudge.web;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.ainudge.dto.MemberRow;
import vn.ainudge.model.Student;
import vn.ainudge.model.StudyGroup;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;
import vn.ainudge.repo.ProofImageRepository;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.repo.StudyGroupRepository;
import vn.ainudge.repo.TaskImageRepository;
import vn.ainudge.repo.TaskProgressRepository;
import vn.ainudge.repo.TaskRepository;
import vn.ainudge.service.Eval;
import vn.ainudge.service.Script;

/**
 * Trang của TỔ TRƯỞNG: xem và xác nhận bài đã nộp của các bạn trong tổ mình
 * phụ trách (cùng việc với ban cán sự nhưng chỉ trong phạm vi tổ). Không cần
 * mã PIN, không xác nhận cho chính mình, không giao nhiệm vụ.
 * Đường dẫn bắt đầu bằng /student nên đã được bộ lọc đăng nhập bảo vệ.
 */
@Controller
@RequestMapping("/student/leader")
public class LeaderController {
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final StudentRepository students;
    private final StudyGroupRepository groups;
    private final TaskRepository tasks;
    private final TaskProgressRepository progresses;
    private final ProofImageRepository images;
    private final TaskImageRepository taskImages;

    public LeaderController(StudentRepository students, StudyGroupRepository groups, TaskRepository tasks,
            TaskProgressRepository progresses, ProofImageRepository images, TaskImageRepository taskImages) {
        this.students = students;
        this.groups = groups;
        this.tasks = tasks;
        this.progresses = progresses;
        this.images = images;
        this.taskImages = taskImages;
    }

    /** Một dòng trong danh sách nhiệm vụ của tổ. */
    public record LeaderTask(Long id, String label, String deadline, int total, int done, int confirmed) {}

    private Student me(HttpSession session) {
        Object id = session.getAttribute("studentId");
        return id == null ? null : students.findById((Long) id).orElse(null);
    }

    /** Các tổ mà học sinh đang làm tổ trưởng (và vẫn là thành viên của tổ). */
    private List<StudyGroup> ledGroups(Student leader) {
        return groups.findByLeader_Id(leader.id).stream()
                .filter(g -> leader.studyGroups.stream().anyMatch(x -> x.id.equals(g.id))
                        && leader.isMemberOf(g.classRoom.id))
                .toList();
    }

    /** Học sinh target có thuộc một trong các tổ do tổ trưởng phụ trách không? */
    private boolean inLedGroup(List<StudyGroup> led, Student target) {
        return led.stream().anyMatch(g -> target.studyGroups.stream().anyMatch(x -> x.id.equals(g.id))
                && target.isMemberOf(g.classRoom.id));
    }

    @GetMapping
    public String home(HttpSession session, Model model) {
        Student leader = me(session);
        if (leader == null)
            return "redirect:/";
        List<StudyGroup> led = ledGroups(leader);
        if (led.isEmpty())
            return "redirect:/student";
        List<Student> members = students.findAll().stream()
                .filter(m -> !m.id.equals(leader.id) && inLedGroup(led, m)).toList();
        List<LeaderTask> rows = new ArrayList<>();
        for (Task t : tasks.findByActiveTrueOrderByDeadlineDesc()) {
            if (t.deadline.isBefore(LocalDate.now().minusDays(1).atStartOfDay()))
                continue; // chỉ hiện nhiệm vụ gần đây
            List<Student> target = members.stream().filter(t::assignedTo).toList();
            if (target.isEmpty())
                continue;
            int done = 0, conf = 0;
            for (Student m : target) {
                TaskProgress p = progresses.findByTask_IdAndStudent_Id(t.id, m.id).orElse(null);
                if (p != null && "DONE".equals(p.status))
                    done++;
                if (p != null && p.confirmed)
                    conf++;
            }
            rows.add(new LeaderTask(t.id, Script.label(t), t.deadline.format(DM), target.size(), done, conf));
        }
        model.addAttribute("name", leader.name);
        model.addAttribute("groupNames", String.join(", ", led.stream()
                .map(g -> g.classRoom.name + " / " + g.name).sorted(String.CASE_INSENSITIVE_ORDER).toList()));
        model.addAttribute("memberCount", members.size());
        model.addAttribute("rows", rows);
        return "leader";
    }

    @GetMapping("/task/{id}")
    public String roster(@PathVariable Long id, HttpSession session, Model model) {
        Student leader = me(session);
        if (leader == null)
            return "redirect:/";
        List<StudyGroup> led = ledGroups(leader);
        if (led.isEmpty())
            return "redirect:/student";
        Task t = tasks.findById(id).orElse(null);
        if (t == null || !t.active)
            return "redirect:/student/leader";
        List<MemberRow> rows = new ArrayList<>();
        for (Student m : students.findAll()) {
            if (m.id.equals(leader.id) || !inLedGroup(led, m) || !t.assignedTo(m))
                continue; // chỉ các bạn trong tổ mình và phải làm nhiệm vụ này
            TaskProgress p = progresses.findByTask_IdAndStudent_Id(t.id, m.id).orElse(null);
            rows.add(new MemberRow(m.id, m.name, Eval.statusLabel(p),
                    p == null || p.proof == null ? "" : p.proof,
                    p != null && p.selfSubmitted,
                    p != null && p.confirmed,
                    p == null || p.confirmedAt == null ? "" : p.confirmedAt.format(DM),
                    p == null ? List.of()
                            : images.findByProgress_IdOrderByIdAsc(p.id).stream().map(i -> i.id).toList()));
        }
        rows.sort(Comparator.comparing(MemberRow::name, String.CASE_INSENSITIVE_ORDER));
        model.addAttribute("task", Script.label(t) + " · Lớp: " + t.classNames());
        model.addAttribute("deadline", t.deadline.format(DM));
        model.addAttribute("taskId", t.id);
        model.addAttribute("taskImages", taskImages.findByTask_IdOrderByIdAsc(t.id).stream().map(i -> i.id).toList());
        model.addAttribute("rows", rows);
        return "leader-task";
    }

    /** Xác nhận (hoặc bỏ xác nhận) một bạn trong tổ đã nộp bài. */
    @PostMapping("/task/{id}/confirm")
    public String confirm(@PathVariable Long id, @RequestParam Long studentId,
            @RequestParam boolean value, HttpSession session) {
        Student leader = me(session);
        if (leader == null)
            return "redirect:/";
        List<StudyGroup> led = ledGroups(leader);
        if (led.isEmpty())
            return "redirect:/student";
        Task t = tasks.findById(id).orElse(null);
        Student target = students.findById(studentId).orElse(null);
        // chặn ở phía máy chủ: đúng tổ, không phải chính mình, đúng bài được giao
        if (t == null || target == null || target.id.equals(leader.id)
                || !inLedGroup(led, target) || !t.assignedTo(target))
            return "redirect:/student/leader/task/" + id;
        TaskProgress p = progresses.findByTask_IdAndStudent_Id(t.id, target.id).orElseGet(() -> {
            TaskProgress np = new TaskProgress();
            np.task = t;
            np.student = target;
            return np;
        });
        p.confirmed = value;
        p.confirmedAt = value ? LocalDateTime.now() : null;
        p.confirmedBy = value ? "Tổ trưởng " + leader.name : null;
        progresses.save(p);
        return "redirect:/student/leader/task/" + id;
    }
}
