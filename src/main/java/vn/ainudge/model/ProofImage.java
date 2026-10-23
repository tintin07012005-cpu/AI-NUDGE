package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Một ảnh minh chứng đã nộp bài của học sinh (file nằm trong thư mục uploads, ở đây chỉ lưu tên file). */
@Entity
public class ProofImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false) public TaskProgress progress;

    @Column(nullable = false, length = 80) public String fileName;      // tên ngẫu nhiên do hệ thống đặt
    @Column(nullable = false, length = 30) public String contentType;   // image/jpeg ...
    public LocalDateTime uploadedAt;
}
