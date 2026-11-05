package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
     * Lớp của học sinh (chọn khi đăng ký, tự ghi danh sau, hoặc người quản trị xếp
     * lớp). null = chưa vào lớp nào.
     */
    @ManyToOne
    public ClassRoom classRoom;

    /** Các lớp đã ghi danh; classRoom phía trên là lớp đang mở (giữ tương thích dữ liệu cũ). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "student_class", joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "class_room_id"))
    public Set<ClassRoom> classMemberships = new HashSet<>();

    /** Nhóm/tổ học sinh tham gia (mỗi nhóm nằm trong một lớp). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "student_study_group", joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "study_group_id"))
    public Set<StudyGroup> studyGroups = new HashSet<>();

    public Set<ClassRoom> joinedClasses() {
        Set<ClassRoom> result = new LinkedHashSet<>();
        if (classRoom != null) result.add(classRoom); // dữ liệu cũ vẫn hoạt động
        if (classMemberships != null) result.addAll(classMemberships);
        return result;
    }

    public boolean isMemberOf(Long classId) {
        if (classId == null) return false;
        return joinedClasses().stream().anyMatch(c -> classId.equals(c.id));
    }
}
