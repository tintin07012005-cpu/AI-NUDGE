package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Học sinh. Học sinh tự đăng ký bằng email hoặc được người quản trị thêm vào
 * danh sách lớp. Ban cán sự là học sinh có monitor = true.
 */
@Entity
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false, length = 100)
    public String name;

    public LocalDateTime createdAt;

    /**
     * Mật khẩu (học sinh tự đặt khi đăng ký, hoặc người quản trị cấp; người quản
     * trị xem và đổi được). null = chưa cấp, chưa đăng nhập được.
     */
    @Column(length = 20)
    public String password;

    /**
     * Email đăng nhập + nhận lời nhắc (học sinh tự đăng ký, hoặc người quản trị
     * nhập khi thêm danh sách lớp).
     */
    @Column(length = 150)
    public String email;

    /** true = ban cán sự (được nhập nhiệm vụ và xác nhận bài đã nộp) */
    public boolean monitor;

    /**
     * Lớp duy nhất của học sinh (chọn khi đăng ký, hoặc người quản trị xếp lớp).
     * null = chưa vào lớp nào.
     */
    @ManyToOne
    public ClassRoom classRoom;

    /** Nhóm/tổ học sinh tham gia (mỗi nhóm nằm trong một lớp). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "student_study_group", joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "study_group_id"))
    public Set<StudyGroup> studyGroups = new HashSet<>();

    /** Mỗi học sinh chỉ thuộc một lớp (classRoom). Giữ dạng tập hợp để các chỗ cũ vẫn dùng được. */
    public Set<ClassRoom> joinedClasses() {
        return classRoom == null ? Set.of() : Set.of(classRoom);
    }

    public boolean isMemberOf(Long classId) {
        if (classId == null) return false;
        return joinedClasses().stream().anyMatch(c -> classId.equals(c.id));
    }
}
