package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Một ảnh đề bài do ban cán sự gửi kèm nhiệm vụ, để học sinh biết đó là bài nào
 * (file nằm trong thư mục uploads).
 */
@Entity
public class TaskImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Task task;

    @Column(nullable = false, length = 80)
    public String fileName; // tên ngẫu nhiên do hệ thống đặt
    @Column(nullable = false, length = 120)
    public String contentType; // image/jpeg ...
    public LocalDateTime uploadedAt;

    /**
     * Địa chỉ tệp trên Cloudinary. Để trống nghĩa là tệp cũ nằm trong thư mục
     * uploads.
     */
    @Column(length = 500)
    public String cloudUrl;
}