package vn.ainudge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Tiến độ của MỘT học sinh với MỘT nhiệm vụ. */
@Entity
public class TaskProgress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false) public Task task;
    @ManyToOne(optional = false) public Student student;

    /** TODO (chưa làm gì) | PLANNED (đã hẹn giờ) | STARTED (đang làm) | DONE (học sinh báo xong) */
    @Column(length = 12)
    public String status = "TODO";

    public LocalDateTime plannedAt;
    @Column(length = 10)  public String plannedLabel;
    @Column(length = 60)  public String place;
    @Column(length = 100) public String barrier;     // điều khiến chưa bắt đầu
    public int postponeCount;                        // số lần hoãn liên tiếp (để chọn cú hích)
    public int postponeTotal;                        // tổng số lần hoãn (cả ngày, KHÔNG reset; dùng để giới hạn hoãn)
    @Column(columnDefinition = "int default 0 not null")
    public int pauseCount;                           // số lần tạm dừng giữa chừng (KHÔNG reset; dùng để giới hạn nghỉ)
    @Column(length = 10)  public String lastPostponeReason;   // hard | tired | busy | bored | other (lý do hoãn gần nhất)
    @Column(length = 300) public String otherReasons;           // các lý do hoãn do học sinh tự gõ, cách nhau bởi " | "
    @Column(length = 240) public String postponeLog;          // nhật ký lý do hoãn: "hard@21,tired@22" (mã@giờ)
    @Column(length = 200) public String prep;        // cam kết gần nhất của học sinh khi hoãn hoặc tạm dừng
    public boolean needHelp;                         // học sinh bấm "Mình cần được giúp"
    public boolean paused;                           // đang tạm dừng giữa chừng: plannedAt là giờ hẹn LÀM TIẾP (không phải giờ bắt đầu)

    public LocalDateTime startedAt;
    public LocalDateTime doneAt;
    @Column(length = 10)  public String feel;        // easy | ok | hard
    @Column(length = 500) public String proof;       // học sinh tự kể đã làm gì
    public boolean selfSubmitted;                    // học sinh nói đã nộp

    // ----- do BAN CÁN SỰ xác nhận (để đối chứng với lời học sinh) -----
    public boolean confirmed;
    public LocalDateTime confirmedAt;
    @Column(length = 100) public String confirmedBy;

    public boolean deadlineReminded;                 // đã nhắc sát hạn (trong khung chat) chưa
    public boolean deadlinePushed;                   // đã nhắc sát hạn qua thông báo đẩy / email chưa
    public LocalDateTime notifiedFor;                // giờ hẹn mà thông báo đẩy / email đã được gửi (tránh gửi lặp)
}
