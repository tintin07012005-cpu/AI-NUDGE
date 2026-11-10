package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Một nhiệm vụ trong ngày do ban cán sự nhập. */
@Entity
@org.hibernate.annotations.BatchSize(size = 50)
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(length = 60)
    public String subject; // môn

    @Column(length = 200)
    public String title; // nội dung nhiệm vụ

    public LocalDateTime deadline; // hạn nộp
    public LocalDateTime createdAt;

    @ManyToOne
    public Student createdBy; // ban cán sự đã nhập

    public boolean active = true;

    /**
     * Các lớp phải làm nhiệm vụ này. Để trống = tất cả học sinh (cách cũ). Một
     * nhiệm vụ có thể giao cho nhiều lớp.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size = 50)
    @JoinTable(name = "task_class", joinColumns = @JoinColumn(name = "task_id"), inverseJoinColumns = @JoinColumn(name = "class_room_id"))
    public Set<ClassRoom> classes = new HashSet<>();

    /** Chỉ dùng khi giao riêng cho nhóm/tổ; nếu có nhóm thì không tự động giao cả lớp. */
    @ManyToMany(fetch = FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size = 50)
    @JoinTable(name = "task_study_group", joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "study_group_id"))
    public Set<StudyGroup> studyGroups = new HashSet<>();

    /** Ngày giao bài tập (dd/MM) để hiển thị; nhiệm vụ cũ chưa có ngày thì hiện "—". */
    public String assignedDate() {
        return createdAt == null ? "—" : createdAt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
    }

    /** Học sinh này có phải làm nhiệm vụ này không? (trong bất kỳ lớp em tham gia). */
    public boolean assignedTo(Student s) {
        if (s == null) return false;
        if (studyGroups != null && !studyGroups.isEmpty())
            return s.studyGroups != null && s.studyGroups.stream().anyMatch(g ->
                    studyGroups.stream().anyMatch(target -> target.id.equals(g.id)) &&
                    s.isMemberOf(g.classRoom.id));
        if (classes == null || classes.isEmpty()) return true;
        return classes.stream().anyMatch(c -> s.isMemberOf(c.id));
    }

    /** Học sinh này có phải làm nhiệm vụ này không? */
    public boolean visibleTo(Student s) {
        if (s == null) return false;
        if (s.classRoom == null)
            return (studyGroups == null || studyGroups.isEmpty()) && (classes == null || classes.isEmpty());
        if (studyGroups != null && !studyGroups.isEmpty())
            return studyGroups.stream().anyMatch(g -> g.classRoom.id.equals(s.classRoom.id)
                    && s.studyGroups != null && s.studyGroups.stream().anyMatch(m -> m.id.equals(g.id)));
        if (classes == null || classes.isEmpty()) return true;
        return classes.stream().anyMatch(c -> c.id.equals(s.classRoom.id));
    }

    /** Tên các lớp được giao, để hiển thị. */
    public String classNames() {
        if (studyGroups != null && !studyGroups.isEmpty())
            return String.join(", ", studyGroups.stream()
                    .map(g -> g.classRoom.name + " / " + g.name)
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        if (classes == null || classes.isEmpty())
            return "Tất cả";
        return String.join(", ", classes.stream().map(c -> c.name).sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    public List<Long> groupIds() {
        return studyGroups == null ? List.of() : studyGroups.stream().map(g -> g.id).sorted().toList();
    }

    public List<Long> classIds() {
        if (studyGroups != null && !studyGroups.isEmpty())
            return studyGroups.stream().map(g -> g.classRoom.id).distinct()
                    .sorted(Comparator.naturalOrder()).toList();
        return classes == null ? List.of() : classes.stream().map(c -> c.id).sorted(Comparator.naturalOrder()).toList();
    }
}