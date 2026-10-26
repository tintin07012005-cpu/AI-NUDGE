package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Một nhiệm vụ trong ngày do ban cán sự nhập. */
@Entity
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
    @JoinTable(name = "task_class", joinColumns = @JoinColumn(name = "task_id"), inverseJoinColumns = @JoinColumn(name = "class_room_id"))
    public Set<ClassRoom> classes = new HashSet<>();

    /** Học sinh này có phải làm nhiệm vụ này không? */
    public boolean visibleTo(Student s) {
        if (classes == null || classes.isEmpty())
            return true;
        if (s == null || s.classRoom == null)
            return false;
        for (ClassRoom c : classes)
            if (c.id.equals(s.classRoom.id))
                return true;
        return false;
    }

    /** Tên các lớp được giao, để hiển thị. */
    public String classNames() {
        if (classes == null || classes.isEmpty())
            return "Tất cả";
        return String.join(", ", classes.stream().map(c -> c.name).sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    public List<Long> classIds() {
        return classes == null ? List.of() : classes.stream().map(c -> c.id).sorted(Comparator.naturalOrder()).toList();
    }
}