package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Một nhiệm vụ trong ngày do ban cán sự nhập. */
@Entity
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(length = 60)
    public String subject;          // môn

    @Column(length = 200)
    public String title;            // nội dung nhiệm vụ

    public LocalDateTime deadline;  // hạn nộp
    public LocalDateTime createdAt;

    @ManyToOne
    public Student createdBy;       // ban cán sự đã nhập

    public boolean active = true;
}
