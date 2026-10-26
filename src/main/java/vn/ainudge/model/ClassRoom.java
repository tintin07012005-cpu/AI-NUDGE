package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Một lớp học (do người quản trị tạo trước). Học sinh chọn lớp khi đăng ký hoặc
 * được xếp lớp.
 */
@Entity
public class ClassRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false, length = 60)
    public String name;

    public LocalDateTime createdAt;
}