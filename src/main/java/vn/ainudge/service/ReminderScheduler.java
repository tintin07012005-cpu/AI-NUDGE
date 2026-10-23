package vn.ainudge.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.ainudge.model.Student;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.repo.TaskProgressRepository;
import vn.ainudge.repo.TaskRepository;

/**
 * Chạy nền mỗi 30 giây, nhắc học sinh KHÔNG mở trang web: (1) đến giờ hẹn bắt đầu, (2) sắp đến hạn nộp.
 * Học sinh đang mở trang thì trang tự nhắc (có tiếng chuông), nên ở đây chỉ gửi thêm email (không gửi thông báo đẩy để khỏi nhắc trùng).
 */
@Component
public class ReminderScheduler {
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("HH:mm dd/MM");
    private static final long ACTIVE_MS = 25_000;

    private final TaskRepository tasks;
    private final TaskProgressRepository progresses;
    private final StudentRepository students;
    private final NotifyService notify;
    private final PresenceTracker presence;

    public ReminderScheduler(TaskRepository tasks, TaskProgressRepository progresses, StudentRepository students,
                             NotifyService notify, PresenceTracker presence) {
        this.tasks = tasks;
        this.progresses = progresses;
        this.students = students;
        this.notify = notify;
        this.presence = presence;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now();

        // (1) đến giờ hẹn bắt đầu (chỉ nhắc nếu vừa đến giờ trong 2 giờ qua)
        for (TaskProgress p : progresses.findByStatusAndPlannedAtLessThanEqual("PLANNED", now)) {
            if (p.plannedAt == null || p.plannedAt.isBefore(now.minusHours(2))) continue;
            if (p.plannedAt.equals(p.notifiedFor) || !p.task.active) continue;
            p.notifiedFor = p.plannedAt;                      // đánh dấu đã xử lý (dù gửi hay bỏ qua)
            progresses.save(p);
            boolean open = presence.isActive(p.student.id, ACTIVE_MS);     // đang mở web: trang tự nhắc, chỉ gửi thêm email
            if (p.paused)
                notify.send(p.student, "▶️ Hết giờ nghỉ rồi, làm tiếp nhé!",
                    "Bạn đang làm dở \"" + Script.label(p.task) + "\" và hẹn làm tiếp lúc " + p.plannedLabel
                    + ". Mở lên và làm tiếp từ chỗ đã dừng nhé.", open);
            else
                notify.send(p.student, "⏰ Đến giờ làm bài rồi!",
                    "Bạn hẹn làm \"" + Script.label(p.task) + "\" lúc " + p.plannedLabel
                    + ". Mở lên và bắt đầu 2 câu đầu tiên thôi nhé.", open);
        }

        // (2) sắp đến hạn nộp (trong 60 phút) mà chưa xong
        for (Task t : tasks.findByActiveTrueOrderByDeadlineDesc()) {
            if (!t.deadline.isAfter(now) || t.deadline.isAfter(now.plusMinutes(60))) continue;
            for (Student s : students.findAll()) {
                TaskProgress p = progresses.findByTask_IdAndStudent_Id(t.id, s.id).orElse(null);
                if (p != null && ("DONE".equals(p.status) || p.deadlinePushed)) continue;
                boolean open = presence.isActive(s.id, ACTIVE_MS);
                if (p == null) { p = new TaskProgress(); p.task = t; p.student = s; }
                p.deadlinePushed = true;
                progresses.save(p);
                notify.send(s, "⏳ Sắp đến hạn nộp",
                    "\"" + Script.label(t) + "\" hạn nộp lúc " + t.deadline.format(DM)
                    + ". Bạn kiểm tra tiến độ và nộp bài nếu đã hoàn thành nhé.", open);
            }
        }
    }
}
