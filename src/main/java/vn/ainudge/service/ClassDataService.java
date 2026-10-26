package vn.ainudge.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ainudge.model.ClassRoom;
import vn.ainudge.model.Student;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;
import vn.ainudge.repo.ChatLogRepository;
import vn.ainudge.repo.ClassRoomRepository;
import vn.ainudge.repo.ConversationRepository;
import vn.ainudge.repo.PushSubRepository;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.repo.TaskProgressRepository;
import vn.ainudge.repo.TaskRepository;

/**
 * Các thao tác dữ liệu lớp học (dành cho người quản trị): thêm danh sách lớp,
 * xóa nhiệm vụ / học sinh / dữ liệu.
 */
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
    private final ClassRoomRepository classes;

    public ClassDataService(StudentRepository students, ConversationRepository convs, ChatLogRepository logs,
            TaskRepository tasks, TaskProgressRepository progresses, ProofService proof,
            PushSubRepository pushSubs, ClassRoomRepository classes) {
        this.classes = classes;
        this.students = students;
        this.convs = convs;
        this.logs = logs;
        this.tasks = tasks;
        this.progresses = progresses;
        this.proof = proof;
        this.pushSubs = pushSubs;
    }

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // bỏ ký tự dễ nhầm: O 0 I 1 L
    private static final SecureRandom RNG = new SecureRandom();

    /** Mật khẩu ngẫu nhiên 6 ký tự, dễ đọc, dễ gõ. */
    public static String newPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++)
            sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    /** Đổi (cấp lại) mật khẩu cho một học sinh. */
    public void resetPassword(Long id) {
        students.findById(id).ifPresent(s -> {
            s.password = newPassword();
            students.save(s);
        });
    }

    /**
     * Người quản trị tự đặt mật khẩu. Trả về null nếu thành công, hoặc câu báo lỗi.
     */
    public String setPassword(Long id, String pw) {
        String p = pw == null ? "" : pw.trim();
        if (!p.matches("\\S{4,20}"))
            return "Mật khẩu phải từ 4 đến 20 ký tự và không có dấu cách.";
        students.findById(id).ifPresent(s -> {
            s.password = p;
            students.save(s);
        });
        return null;
    }

    /** Cấp mật khẩu cho những học sinh chưa có. Trả về số học sinh vừa được cấp. */
    public int fillMissingPasswords() {
        int n = 0;
        for (Student s : students.findAll()) {
            if (s.password == null || s.password.isBlank()) {
                s.password = newPassword();
                students.save(s);
                n++;
            }
        }
        return n;
    }

    /** Tạo lớp mới. Trả về null nếu thành công, hoặc câu báo lỗi. */
    public String createClass(String name) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > 60)
            return "Tên lớp cần từ 1 đến 60 ký tự.";
        if (classes.findByNameIgnoreCase(n).isPresent())
            return "Lớp \"" + n + "\" đã có rồi.";
        ClassRoom c = new ClassRoom();
        c.name = n;
        c.createdAt = LocalDateTime.now();
        classes.save(c);
        return null;
    }

    /**
     * Xóa lớp. Không xóa khi còn nhiệm vụ đang giao cho lớp đó (tránh nhiệm vụ bỗng
     * thành "giao cho tất cả").
     */
    public String deleteClass(Long id) {
        ClassRoom c = classes.findById(id).orElse(null);
        if (c == null)
            return null;
        List<Task> all = tasks.findAll();
        for (Task t : all)
            if (t.active && t.classes.stream().anyMatch(x -> x.id.equals(id)))
                return "Lớp \"" + c.name + "\" còn nhiệm vụ đang giao. Hãy đổi lớp được giao hoặc xóa nhiệm vụ trước.";
        for (Task t : all)
            if (t.classes.removeIf(x -> x.id.equals(id)))
                tasks.save(t); // nhiệm vụ đã ẩn: gỡ liên kết cho phép xóa lớp
        for (Student s : students.findByClassRoom_Id(id)) {
            s.classRoom = null;
            students.save(s);
        }
        classes.delete(c);
        return null;
    }

    /** Xếp một học sinh vào lớp (classId = null: bỏ khỏi lớp). */
    public void setStudentClass(Long studentId, Long classId) {
        students.findById(studentId).ifPresent(s -> {
            s.classRoom = classId == null ? null : classes.findById(classId).orElse(null);
            students.save(s);
        });
    }

    /**
     * Đổi các lớp được giao của một nhiệm vụ (danh sách rỗng = tất cả học sinh).
     */
    public void setTaskClasses(Long taskId, List<Long> classIds) {
        tasks.findById(taskId).ifPresent(t -> {
            t.classes.clear();
            if (classIds != null && !classIds.isEmpty())
                t.classes.addAll(new HashSet<>(classes.findAllById(classIds)));
            tasks.save(t);
        });
    }

    public int addRoster(String text) {
        return addRoster(text, null);
    }

    /**
     * Mỗi dòng một tên (có thể ghi "Tên, mật khẩu, email"). Học sinh mới được xếp
     * vào lớp classId (nếu có).
     * Tên đã có mà chưa vào lớp nào thì được ghi danh vào lớp này. Trả về số học
     * sinh mới được thêm.
     */
    public int addRoster(String text, Long classId) {
        ClassRoom cls = classId == null ? null : classes.findById(classId).orElse(null);
        int added = 0;
        if (text == null)
            return 0;
        for (String line : text.split("\\R")) {
            // Dòng có thể là "Tên" hoặc "Tên, mật khẩu"
            int cut = -1;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch == ',' || ch == ';' || ch == '\t') {
                    cut = i;
                    break;
                }
            }
            String n = (cut < 0 ? line : line.substring(0, cut)).trim().replaceAll("\\s+", " ");
            // phần còn lại: "mật khẩu" hoặc "mật khẩu, email" (hoặc chỉ "email")
            String[] rest = (cut < 0 ? "" : line.substring(cut + 1)).split("[,;\\t]", 2);
            String pw = rest.length > 0 ? rest[0].trim() : "";
            String email = rest.length > 1 ? rest[1].trim() : "";
            if (pw.contains("@") && email.isEmpty()) {
                email = pw;
                pw = "";
            }
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
                email = "";
            if (n.isEmpty())
                continue;
            if (n.length() > 60)
                n = n.substring(0, 60);
            Student ex = students.findByNameIgnoreCase(n).orElse(null);
            if (ex != null) {
                if (cls != null && ex.classRoom == null) {
                    ex.classRoom = cls;
                    students.save(ex);
                } // ghi danh bạn đã có tài khoản
                continue;
            }
            Student s = new Student();
            s.name = n;
            s.createdAt = LocalDateTime.now();
            s.password = pw.matches("\\S{4,20}") ? pw : newPassword();
            s.email = email.isEmpty() ? null : email;
            s.classRoom = cls;
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
        for (Task t : tasks.findByCreatedBy_Id(id)) {
            t.createdBy = null;
            tasks.save(t);
        }
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