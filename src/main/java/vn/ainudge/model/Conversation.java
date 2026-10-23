package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Một lần trò chuyện của học sinh với trợ lý (một "buổi"). */
@Entity
public class Conversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Student student;

    public LocalDateTime startedAt;
    public LocalDateTime lastActivity;

    /** Đang ở bước nào của bộ câu hỏi (HAS_TASK, COUNT, SUBJECT, WHEN, ...) */
    @Column(length = 30)
    public String step;

    /** Nhiệm vụ mà trợ lý đang trao đổi với học sinh */
    public Long currentTaskId;

    // ----- CÁC CỘT CŨ (không còn dùng, giữ lại để không bị lỗi với database đã tạo từ bản trước) -----
    public int taskNo;                 // đang là nhiệm vụ thứ mấy
    public int remaining;              // còn mấy nhiệm vụ
    @Column(length = 60)  public String subject;      // môn
    public LocalDateTime plannedAt;    // giờ hẹn bắt đầu
    @Column(length = 10)  public String plannedLabel; // "19:30"
    @Column(length = 60)  public String place;        // nơi làm
    @Column(length = 100) public String barrier;      // rào cản học sinh chọn gần nhất
    public int postponeCount;          // số lần hoãn của nhiệm vụ hiện tại

    // ----- số liệu tóm tắt cho người quản trị -----
    public int tasksTotal;
    public int tasksStarted;
    public int tasksDone;
    public int postponeTotal;
}
