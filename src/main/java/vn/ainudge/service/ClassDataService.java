package vn.ainudge.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ainudge.model.Student;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;
import vn.ainudge.repo.ChatLogRepository;
import vn.ainudge.repo.ConversationRepository;
import vn.ainudge.repo.PushSubRepository;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.repo.TaskProgressRepository;
import vn.ainudge.repo.TaskRepository;

/** Các thao tác dữ liệu lớp học (dành cho người quản trị): thêm danh sách lớp, xóa nhiệm vụ / học sinh / dữ liệu. */
@Service
@Transactional
public class ClassDataService {
    private final StudentRepository students;
    private final ConversationRepository convs;
    private final ChatLogRepository logs;
    private final TaskRepository tasks;
    private final TaskProgressRepository progresses;
    private final ProofService proof;
    private final PushSubRepository pushSubs;

    public ClassDataService(StudentRepository students, ConversationRepository convs, ChatLogRepository logs,
                        TaskRepository tasks, TaskProgressRepository progresses, ProofService proof,
                            PushSubRepository pushSubs) {
        this.students = students;
        this.convs = convs;
        this.logs = logs;
        this.tasks = tasks;
        this.progresses = progresses;
        this.proof = proof;
        this.pushSubs = pushSubs;
    }

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";   // bỏ ký tự dễ nhầm: O 0 I 1 L
    private static final SecureRandom RNG = new SecureRandom();

    /** Mật khẩu ngẫu nhiên 6 ký tự, dễ đọc, dễ gõ. */
    public static String newPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    /** Đổi (cấp lại) mật khẩu cho một học sinh. */
    public void resetPassword(Long id) {
        students.findById(id).ifPresent(s -> { s.password = newPassword(); students.save(s); });
    }

    /** Người quản trị tự đặt mật khẩu. Trả về null nếu thành công, hoặc câu báo lỗi. */
    public String setPassword(Long id, String pw) {
        String p = pw == null ? "" : pw.trim();
        if (!p.matches("\\S{4,20}")) return "Mật khẩu phải từ 4 đến 20 ký tự và không có dấu cách.";
        students.findById(id).ifPresent(s -> { s.password = p; students.save(s); });
        return null;
    }

    /** Cấp mật khẩu cho những học sinh chưa có. Trả về số học sinh vừa được cấp. */
    public int fillMissingPasswords() {
        int n = 0;
        for (Student s : students.findAll()) {
            if (s.password == null || s.password.isBlank()) { s.password = newPassword(); students.save(s); n++; }
        }
        return n;
    }

    /** Mỗi dòng một tên (có thể ghi "Tên, mật khẩu, email"). Trả về số học sinh mới được thêm. */
    public int addRoster(String text) {
        int added = 0;
        if (text == null) return 0;
        for (String line : text.split("\\R")) {
            // Dòng có thể là "Tên" hoặc "Tên, mật khẩu"
            int cut = -1;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch == ',' || ch == ';' || ch == '\t') { cut = i; break; }
            }
            String n = (cut < 0 ? line : line.substring(0, cut)).trim().replaceAll("\\s+", " ");
            // phần còn lại: "mật khẩu" hoặc "mật khẩu, email" (hoặc chỉ "email")
            String[] rest = (cut < 0 ? "" : line.substring(cut + 1)).split("[,;\\t]", 2);
            String pw = rest.length > 0 ? rest[0].trim() : "";
            String email = rest.length > 1 ? rest[1].trim() : "";
            if (pw.contains("@") && email.isEmpty()) { email = pw; pw = ""; }
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) email = "";
            if (n.isEmpty()) continue;
            if (n.length() > 60) n = n.substring(0, 60);
            if (students.findByNameIgnoreCase(n).isPresent()) continue;
            Student s = new Student();
            s.name = n;
            s.createdAt = LocalDateTime.now();
            s.password = pw.matches("\\S{4,20}") ? pw : newPassword();
            s.email = email.isEmpty() ? null : email;
            students.save(s);
            added++;
        }
        return added;
    }

    /** Xóa hẳn một nhiệm vụ và tiến độ của nó. */
    public void deleteTask(Long id) {
        List<TaskProgress> ps = progresses.findByTask_Id(id);
        proof.deleteForProgresses(ps);
        progresses.deleteAll(ps);
        proof.deleteTaskImagesOf(id);
        tasks.findById(id).ifPresent(tasks::delete);
    }

    /** Xóa một học sinh và toàn bộ dữ liệu của em đó. */
    public void deleteStudent(Long id) {
        logs.deleteAll(logs.findByConversation_Student_Id(id));
        convs.deleteAll(convs.findByStudent_IdOrderByStartedAtDesc(id));
        pushSubs.deleteAll(pushSubs.findByStudent_Id(id));
        List<TaskProgress> ps = progresses.findByStudent_Id(id);
        proof.deleteForProgresses(ps);
        progresses.deleteAll(ps);
        for (Task t : tasks.findByCreatedBy_Id(id)) { t.createdBy = null; tasks.save(t); }
        students.findById(id).ifPresent(students::delete);
    }

    /** Xóa toàn bộ nhiệm vụ, tiến độ và trò chuyện (GIỮ danh sách học sinh). */
    public void resetData() {
        logs.deleteAll();
        proof.deleteAll();
        progresses.deleteAll();
        convs.deleteAll();
        tasks.deleteAll();
    }
}
