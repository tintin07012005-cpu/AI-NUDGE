package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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
}