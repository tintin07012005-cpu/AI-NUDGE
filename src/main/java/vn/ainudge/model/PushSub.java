package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Một thiết bị / trình duyệt của học sinh đã bật thông báo đẩy. */
@Entity
public class PushSub {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false) public Student student;

    @Column(nullable = false, length = 1000) public String endpoint;
    @Column(nullable = false, length = 200)  public String p256dh;
    @Column(nullable = false, length = 100)  public String auth;
    public LocalDateTime createdAt;
}
