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
import vn.ainudge.model.StudyGroup;
import vn.ainudge.model.StudyGroup;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;
import vn.ainudge.repo.ChatLogRepository;
import vn.ainudge.repo.ClassRoomRepository;
import vn.ainudge.repo.ConversationRepository;
import vn.ainudge.repo.PushSubRepository;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.repo.StudyGroupRepository;
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
    private final StudyGroupRepository groups;

    public ClassDataService(StudentRepository students, ConversationRepository convs, ChatLogRepository logs,
            TaskRepository tasks, TaskProgressRepository progresses, ProofService proof,
            PushSubRepository pushSubs, ClassRoomRepository classes, StudyGroupRepository groups) {
        this.groups = groups;
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
            if (t.active && (t.classes.stream().anyMatch(x -> x.id.equals(id))
                    || t.studyGroups.stream().anyMatch(g -> g.classRoom.id.equals(id))))
                return "Lớp \"" + c.name + "\" còn nhiệm vụ đang giao. Hãy xóa/đổi đối tượng nhận nhiệm vụ trước.";
        for (Task t : all) {
            boolean removedClass = t.classes.removeIf(x -> x.id.equals(id));
            boolean removedGroup = t.studyGroups.removeIf(g -> g.classRoom.id.equals(id));
            if (removedClass || removedGroup) tasks.save(t);
        }
        for (Student s : students.findAll().stream().filter(x -> x.isMemberOf(id)).toList()) {
            s.classMemberships.removeIf(x -> x.id.equals(id));
            s.studyGroups.removeIf(g -> g.classRoom.id.equals(id));
            if (s.classRoom != null && s.classRoom.id.equals(id))
                s.classRoom = s.classMemberships.stream().findFirst().orElse(null);
            students.save(s);
        }
        groups.deleteAll(groups.findByClassRoom_IdOrderByNameAsc(id));
        classes.delete(c);
        return null;
    }

    /** Thêm lớp, không tạo tài khoản/email mới; lớp mới được chọn làm lớp đang xem. */
    public void setStudentClass(Long studentId, Long classId) {
        if (classId == null) return;
        students.findById(studentId).ifPresent(s -> classes.findById(classId).ifPresent(c -> {
            if (s.classRoom != null) s.classMemberships.add(s.classRoom); // dữ liệu lớp cũ
            s.classMemberships.add(c);
            s.classRoom = c;
            students.save(s);
        }));
    }

    /** Bỏ một lớp; đồng thời bỏ các nhóm/tổ của lớp đó. */
    public void removeStudentClass(Long studentId, Long classId) {
        students.findById(studentId).ifPresent(s -> {
            s.classMemberships.removeIf(c -> c.id.equals(classId));
            s.studyGroups.removeIf(g -> g.classRoom.id.equals(classId));
            if (s.classRoom != null && s.classRoom.id.equals(classId))
                s.classRoom = s.classMemberships.stream().findFirst().orElse(null);
            students.save(s);
        });
    }

    public String createGroup(Long classId, String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 80) return "Tên tổ/nhóm cần từ 1–80 ký tự.";
        ClassRoom c = classes.findById(classId).orElse(null);
        if (c == null) return "Chưa chọn lớp hợp lệ.";
        if (groups.findByClassRoom_IdAndNameIgnoreCase(classId, name).isPresent())
            return "Tên tổ/nhóm này đã tồn tại trong lớp.";
        StudyGroup g = new StudyGroup();
        g.classRoom = c;
        g.name = name;
        groups.save(g);
        return null;
    }

    public String deleteGroup(Long groupId) {
        StudyGroup group = groups.findById(groupId).orElse(null);
        if (group == null) return "Không tìm thấy tổ/nhóm.";
        if (tasks.findAll().stream().anyMatch(t -> t.active && t.studyGroups.stream()
                .anyMatch(g -> g.id.equals(groupId))))
            return "Tổ/nhóm đang có nhiệm vụ. Hãy chuyển hoặc xóa nhiệm vụ trước.";
        for (Task t : tasks.findAll()) {
            if (t.studyGroups.removeIf(g -> g.id.equals(groupId))) tasks.save(t);
        }
        for (Student s : students.findAll()) {
            if (s.studyGroups.removeIf(g -> g.id.equals(groupId))) students.save(s);
        }
        groups.delete(group);
        return null;
    }

    /** Cập nhật các tổ/nhóm của học sinh, chỉ chấp nhận nhóm của lớp đã ghi danh. */
    public String setStudentGroups(Long studentId, List<Long> groupIds) {
        Student s = students.findById(studentId).orElse(null);
        if (s == null) return "Không tìm thấy học sinh.";
        var chosen = groupIds == null ? List.<StudyGroup>of() : groups.findAllById(groupIds);
        if (chosen.stream().anyMatch(g -> !s.isMemberOf(g.classRoom.id)))
            return "Học sinh cần được thêm vào lớp trước khi xếp tổ/nhóm.";
        s.studyGroups.clear();
        s.studyGroups.addAll(chosen);
        students.save(s);
        return null;
    }

    /**
     * Đổi các lớp được giao của một nhiệm vụ (danh sách rỗng = tất cả học sinh).
     */
    public void setTaskClasses(Long taskId, List<Long> classIds) {
        tasks.findById(taskId).ifPresent(t -> {
            t.classes.clear();
            t.studyGroups.clear();
            if (classIds != null && !classIds.isEmpty())
                t.classes.addAll(new HashSet<>(classes.findAllById(classIds)));
            tasks.save(t);
        });
    }

    /** Chuyển nhiệm vụ sang giao riêng một hoặc nhiều nhóm (không giao cả lớp). */
    public String setTaskGroups(Long taskId, List<Long> groupIds) {
        Task t = tasks.findById(taskId).orElse(null);
        if (t == null) return "Không tìm thấy nhiệm vụ.";
        if (groupIds == null || groupIds.isEmpty()) return "Bạn cần chọn ít nhất một tổ/nhóm.";
        var chosen = groups.findAllById(groupIds);
        if (chosen.size() != new java.util.HashSet<>(groupIds).size())
            return "Có tổ/nhóm không còn tồn tại.";
        t.classes.clear();
        t.studyGroups.clear();
        t.studyGroups.addAll(chosen);
        tasks.save(t);
        return null;
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
            Student ex = email.isEmpty() ? null : students.findFirstByEmailIgnoreCase(email).orElse(null);
            if (ex == null) ex = students.findByNameIgnoreCase(n).orElse(null);
            if (ex != null) {
                if (cls != null) {
                    ex.classMemberships.add(cls);
                    if (ex.classRoom == null) ex.classRoom = cls;
                    students.save(ex);
                }
                continue; // cùng email vẫn là một tài khoản
            }
            Student s = new Student();
            s.name = n;
            s.createdAt = LocalDateTime.now();
            s.password = pw.matches("\\S{4,20}") ? pw : newPassword();
            s.email = email.isEmpty() ? null : email;
            s.classRoom = cls;
            if (cls != null) s.classMemberships.add(cls);
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