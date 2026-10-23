package vn.ainudge.service;

import java.time.LocalDateTime;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;

/**
 * Cách "đánh giá" đơn giản dành cho người quản trị.
 * Kết quả dựa trên việc BAN CÁN SỰ xác nhận đã nhận bài (không chỉ dựa vào lời học sinh).
 */
public class Eval {
    public static final String EARLY = "Nộp sớm";
    public static final String ON_TIME = "Nộp đúng hạn";
    public static final String LATE = "Nộp trễ";
    public static final String MISSING = "Chưa nộp";
    public static final String WAITING = "Đang chờ";

    /** Một học sinh với một nhiệm vụ: nộp sớm / đúng hạn / trễ / chưa nộp / đang chờ. */
    public static String result(Task t, TaskProgress p, LocalDateTime now) {
        if (p != null && p.confirmed) {
            LocalDateTime at = p.confirmedAt == null ? now : p.confirmedAt;
            if (at.isAfter(t.deadline)) return LATE;
            if (at.isBefore(t.deadline.minusMinutes(30))) return EARLY;
            return ON_TIME;
        }
        return now.isAfter(t.deadline) ? MISSING : WAITING;
    }

    /** Tên trạng thái dễ đọc. */
    public static String statusLabel(TaskProgress p) {
        if (p == null) return "Chưa vào học";
        return switch (p.status) {
            case "PLANNED" -> p.paused ? "Tạm dừng, hẹn làm tiếp" : "Đã hẹn giờ";
            case "STARTED" -> "Đang làm";
            case "DONE"    -> "Báo đã xong";
            default        -> "Chưa bắt đầu";
        };
    }
}
