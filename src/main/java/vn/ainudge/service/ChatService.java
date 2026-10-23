package vn.ainudge.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.ainudge.dto.Msg;
import vn.ainudge.dto.Opt;
import vn.ainudge.dto.Reply;
import vn.ainudge.dto.TaskChip;
import vn.ainudge.model.ChatLog;
import vn.ainudge.model.Conversation;
import vn.ainudge.model.Student;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;
import vn.ainudge.repo.ChatLogRepository;
import vn.ainudge.repo.ConversationRepository;
import vn.ainudge.repo.TaskImageRepository;
import vn.ainudge.repo.TaskProgressRepository;
import vn.ainudge.repo.TaskRepository;

/**
 * "Bộ não" của trợ lý: một máy trạng thái đơn giản.
 * Mỗi bước (step) có 1 câu hỏi + các nút; học sinh chọn nút -> chuyển sang bước kế tiếp.
 *
 * PICK (chọn nhiệm vụ do ban cán sự nhập) -> WHEN -> BARRIER -> PLACE -> (WAIT -> DUE) -> DOING
 *      -> PROOF (kể đã làm gì) -> FEEL -> SUBMIT (đã nộp chưa) -> nhiệm vụ tiếp theo hoặc END
 */
@Service
@Transactional
public class ChatService {
    static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final ConversationRepository convs;
    private final ChatLogRepository logs;
    private final TaskRepository tasks;
    private final TaskProgressRepository progresses;
    private final ProofService proof;
    private final TaskImageRepository taskImages;

    @Value("${ainudge.demo:false}")
    private boolean demo;

    public ChatService(ConversationRepository convs, ChatLogRepository logs,
                       TaskRepository tasks, TaskProgressRepository progresses, ProofService proof,
                       TaskImageRepository taskImages) {
        this.taskImages = taskImages;
        this.convs = convs;
        this.logs = logs;
        this.tasks = tasks;
        this.progresses = progresses;
        this.proof = proof;
    }

    // ============================================================
    //  Mở trang: tiếp tục buổi trò chuyện hôm nay hoặc bắt đầu buổi mới
    // ============================================================
    public Reply open(Student s, boolean forceNew) {
        Conversation c = current(s);
        boolean resume = !forceNew && c != null
            && c.startedAt.toLocalDate().equals(LocalDate.now())
            && !"END".equals(c.step);
        if (resume) {
            Conversation fresh = restartIfStale(c, s, new ArrayList<>());
            if (fresh != null) return new Reply(history(fresh), List.of(), options(fresh), input(fresh));
            return new Reply(history(c), List.of(), options(c), input(c));
        }
        c = new Conversation();
        c.student = s;
        c.startedAt = LocalDateTime.now();
        c.lastActivity = c.startedAt;
        c.step = "NO_TASK";
        convs.save(c);

        List<Msg> out = new ArrayList<>();
        bot(c, out, Script.greeting(s.name), "normal");
        intro(c, out, s, true);
        convs.save(c);
        return new Reply(List.of(), out, options(c), input(c));
    }

    // ============================================================
    //  Học sinh trả lời (bấm nút, chọn giờ hoặc gõ chữ)
    // ============================================================
    public Reply answer(Student s, String value, String label) {
        Conversation c = current(s);
        if (c == null || "END".equals(c.step)) return open(s, true);
        List<Msg> stale = new ArrayList<>();
        Conversation fresh = restartIfStale(c, s, stale);               // nhiệm vụ đang làm đã bị xóa/thay: không tiếp tục câu chuyện cũ
        if (fresh != null) return new Reply(List.of(), stale, options(fresh), input(fresh));
        if (value == null) value = "";
        if (label == null || label.isBlank()) label = value;

        String step = c.step;
        log(c, "STUDENT", label, "normal", step);
        List<Msg> out = new ArrayList<>();
        Task t = task(c);
        TaskProgress p = t == null ? null : progressFor(t, s);

        switch (step) {
            case "NO_TASK" -> intro(c, out, s, true);
            case "PICK" -> pick(c, out, s, value);
            case "WHEN", "WHEN2" -> handleWhen(c, out, t, p, step, value);
            case "TIME" -> handleTime(c, out, t, p, value);
            case "BARRIER" -> {
                if ("other".equals(value)) {
                    ask(c, out, "BARRIER_OTHER");                // học sinh tự gõ lý do
                } else {
                    p.barrier = label;
                    bot(c, out, Script.barrierNudge(value), "nudge");
                    ask(c, out, "PLACE");
                }
            }
            case "BARRIER_OTHER" -> {
                String txt = clean(label, 100);
                if (txt.length() < 2) {
                    bot(c, out, "Bạn gõ thêm vài chữ giúp mình nhé.", "normal");
                } else {
                    p.barrier = txt;
                    bot(c, out, Script.barrierNudge("other"), "nudge");
                    ask(c, out, "PLACE");
                }
            }
            case "PLACE" -> {
                if ("other".equals(value)) ask(c, out, "PLACE_OTHER");   // học sinh tự gõ nơi làm bài
                else planned(c, out, t, p, label);
            }
            case "PLACE_OTHER" -> {
                String txt = clean(label, 60);
                if (txt.length() < 2) bot(c, out, "Bạn gõ tên chỗ làm bài giúp mình nhé.", "normal");
                else planned(c, out, t, p, txt);
            }
            case "WAIT" -> { if ("early".equals(value)) startDoing(c, out, p); }
            case "DUE" -> {
                if ("postpone".equals(value)) {
                    if (overdue(t)) {
                        bot(c, out, Script.overdueNoPostpone(t), "nudge");
                    } else if (tooLate(t, LocalDateTime.now().plusMinutes(minPostpone(p)))) {
                        bot(c, out, Script.noMorePostpone(t), "nudge");
                    } else if (postponeBlocked(p)) {
                        bot(c, out, Script.manyPostpones(delays(p)), "normal");
                        bot(c, out, Script.lastBoost(), "boost");
                    } else {
                        askWhy(c, out, t, p, false);      // tin nhắn ngay lúc bấm hoãn: hỏi lý do
                        c.step = "POSTPONE_WHY";
                    }
                } else if ("start".equals(value)) {
                    startDoing(c, out, p);
                } else if ("help".equals(value)) {
                    askHelp(c, out, p);
                }
            }
            case "POSTPONE_WHY" -> {
                if ("back".equals(value)) {
                    startDoing(c, out, p);                                    // học sinh đổi ý, làm luôn
                } else if ("other".equals(value)) {
                    bot(c, out, Script.otherAsk(), "normal");                 // học sinh tự gõ lý do
                    c.step = "POSTPONE_OTHER";
                } else if (Script.isReason(value)) {
                    p.lastPostponeReason = value;
                    addPostponeLog(p, value);
                    bot(c, out, Script.postponeReply(value, p), "nudge"); // một cú hích có hành động, tránh nhắn hai câu trùng ý
                    bot(c, out, "Còn " + Math.max(0, MAX_DELAY - delays(p))
                        + " lượt. Chọn giờ mới sẽ tính 1 lượt.", "normal");
                    c.step = "POSTPONE_PLAN";
                }
            }
            case "POSTPONE_OTHER" -> {
                if ("back".equals(value)) {
                    startDoing(c, out, p);                                    // đổi ý, làm luôn
                } else if (clean(label, 120).length() < 3) {
                    bot(c, out, Script.otherShort(), "normal");
                } else {
                    p.lastPostponeReason = "other";
                    addPostponeLog(p, "other");
                    addOtherReason(p, clean(label, 120));
                    bot(c, out, Script.postponeReply("other", p), "nudge");
                    bot(c, out, "Còn " + Math.max(0, MAX_DELAY - delays(p))
                        + " lượt. Chọn giờ mới sẽ tính 1 lượt.", "normal");
                    c.step = "POSTPONE_PLAN";
                }
            }
            case "POSTPONE_PLAN" -> {
                if ("start".equals(value)) {
                    startDoing(c, out, p);
                    bot(c, out, Script.microStart(p.lastPostponeReason), "nudge");
                } else if ("help".equals(value)) {
                    askHelp(c, out, p);
                } else if (value != null && value.startsWith("wait:")) {
                    int min = parseInt(value.substring(5), 0);
                    LocalDateTime next = LocalDateTime.now().plusMinutes(min);
                    if (min <= 0 || min > 60 || postponeBlocked(p) || overdue(t)) {
                        // giá trị không hợp lệ: bỏ qua
                    } else if (tooLate(t, next)) {
                        bot(c, out, Script.noMorePostpone(t), "nudge");
                    } else {
                        p.postponeCount++; p.postponeTotal++;
                        p.plannedAt = next;
                        p.plannedLabel = next.format(HM);
                        askCommit(c, out, t, p, false);                          // hoãn lần nào cũng phải cam kết
                    }
                }
            }
            case "POSTPONE_COMMIT" -> {
                boolean last = delays(p) >= MAX_DELAY;                           // đã dùng hết lượt: phải gõ cam kết, không có nút chọn nhanh
                String text = clean(label, 150);
                if ("start".equals(value)) {
                    startDoing(c, out, p);                                       // đổi ý, làm luôn
                } else if (last && "prep".equals(value)) {
                    bot(c, out, Script.commitShort(), "normal");
                } else if (!"prep".equals(value) && !meaningfulCommit(text)) {
                    bot(c, out, Script.commitShort(), "normal");
                } else {
                    p.prep = text;
                    bot(c, out, Script.committed(t, p, last), "normal");
                    if (!last) bot(c, out, Script.boost(), "boost");
                    c.step = "WAIT";
                }
            }
            case "DOING" -> {
                if ("done".equals(value)) ask(c, out, "PHOTO");   // làm xong -> gửi ảnh minh chứng ngay trong khung chat
                else if ("stop".equals(value)) {
                    if (pauseBlocked(p)) {
                        bot(c, out, Script.pauseLimit(delays(p)), "normal");
                        bot(c, out, Script.lastBoost(), "boost");    // nghỉ đủ số lần rồi: ở lại bước DOING
                    } else if (tooLate(t, LocalDateTime.now().plusMinutes(10))) {
                        bot(c, out, Script.noPause(t), "nudge");          // sát hạn rồi: không cho nghỉ dài, ở lại bước DOING
                    } else {
                        askWhy(c, out, t, p, true);
                        c.step = "PAUSE_WHY";                              // phải nói lý do rồi mới được chọn nghỉ bao lâu
                    }
                }
            }
            case "PAUSE_WHY" -> {
                if ("back".equals(value)) {
                    bot(c, out, Script.keepGoing(), "normal");
                    c.step = "DOING";
                } else if ("other".equals(value)) {
                    bot(c, out, Script.otherAsk(), "normal");
                    c.step = "PAUSE_OTHER";
                } else if (Script.isReason(value)) {
                    p.lastPostponeReason = value;
                    addPostponeLog(p, value);
                    bot(c, out, Script.pauseReply(value), "nudge"); // thay lời động viên suông bằng gợi ý làm ngay
                    bot(c, out, "Bạn còn " + Math.max(0, MAX_DELAY - delays(p))
                        + " lượt. Chọn giờ nghỉ mới sẽ tính 1 lượt.", "normal");
                    ask(c, out, "PAUSE");
                }
            }
            case "PAUSE_OTHER" -> {
                if ("back".equals(value)) {
                    bot(c, out, Script.keepGoing(), "normal");
                    c.step = "DOING";
                } else if (clean(label, 120).length() < 3) {
                    bot(c, out, Script.otherShort(), "normal");
                } else {
                    p.lastPostponeReason = "other";
                    addPostponeLog(p, "other");
                    addOtherReason(p, clean(label, 120));
                    bot(c, out, Script.pauseReply("other"), "nudge");
                    bot(c, out, "Bạn còn " + Math.max(0, MAX_DELAY - delays(p))
                        + " lượt. Chọn giờ nghỉ mới sẽ tính 1 lượt.", "normal");
                    ask(c, out, "PAUSE");
                }
            }
            case "PAUSE" -> {
                int min = parseInt(value, 0);
                if ("micro".equals(value)) {
                    startDoing(c, out, p);
                    bot(c, out, Script.microStart(p.lastPostponeReason), "nudge");
                } else if ("help".equals(value)) {
                    askHelp(c, out, p); // vẫn giữ quyền chọn thử ngay hoặc nghỉ
                } else if ("back".equals(value)) {
                    bot(c, out, Script.keepGoing(), "normal");
                    c.step = "DOING";
                } else if ("custom".equals(value)) {
                    ask(c, out, "PAUSE_TIME");
                } else if (min > 0 && min <= pauseMax(p)) {
                    pauseUntil(c, out, t, p, LocalDateTime.now().plusMinutes(min), "PAUSE");
                }
            }
            case "PAUSE_TIME" -> {
                LocalDateTime when = null;
                try { when = LocalDate.now().atTime(LocalTime.parse(value.trim())); } catch (Exception e) { /* giờ không hợp lệ */ }
                if (when == null || when.isBefore(LocalDateTime.now())) {
                    bot(c, out, "Giờ này chưa đúng hoặc đã qua rồi, bạn chọn lại giờ giúp mình nhé.", "normal");
                } else if (when.isAfter(LocalDateTime.now().plusMinutes(pauseMax(p)))) {
                    bot(c, out, Script.pauseTooLong(), "nudge");
                } else {
                    pauseUntil(c, out, t, p, when, "PAUSE_TIME");
                }
            }
            case "PHOTO" -> {
                if ("nophoto".equals(value)) ask(c, out, "PROOF");   // không chụp được ảnh -> kể bằng chữ như cũ
            }
            case "PROOF" -> {
                if (label.trim().length() < 3) {
                    bot(c, out, Script.proofShort(), "normal");
                } else {
                    p.proof = label.length() > 480 ? label.substring(0, 480) : label;
                    p.status = "DONE";
                    p.doneAt = LocalDateTime.now();
                    if (p.startedAt != null && Duration.between(p.startedAt, p.doneAt).toMinutes() < 2)
                        bot(c, out, Script.fastDone(), "nudge");
                    ask(c, out, "FEEL");
                }
            }
            case "FEEL" -> {
                p.feel = value;
                bot(c, out, Script.cheer(value), "cheer");
                ask(c, out, "SUBMIT");
            }
            case "SUBMIT" -> {
                if ("yes".equals(value)) {
                    p.selfSubmitted = true;
                    bot(c, out, Script.submitted(), "cheer");
                } else {
                    bot(c, out, Script.submitNudge(t), "nudge");
                }
                intro(c, out, s, false);
            }
            default -> { }
        }
        c.lastActivity = LocalDateTime.now();
        convs.save(c);
        return new Reply(List.of(), out, options(c), input(c));
    }

    // ============================================================
    //  Trình duyệt hỏi định kỳ: có gì cần nhắc không?
    //  (1) đến giờ hẹn bắt đầu  (2) sắp đến hạn nộp mà chưa xong
    // ============================================================
    public Reply poll(Student s) {
        Conversation c = current(s);
        Reply none = new Reply(List.of(), List.of(), List.of(), "");
        if (c == null) return none;
        List<Msg> stale = new ArrayList<>();
        Conversation fresh = restartIfStale(c, s, stale);
        if (fresh != null) return new Reply(List.of(), stale, options(fresh), input(fresh));
        LocalDateTime now = LocalDateTime.now();
        List<Msg> out = new ArrayList<>();

        // (1) đến giờ hẹn
        if (("WAIT".equals(c.step) || "POSTPONE_COMMIT".equals(c.step)) && c.currentTaskId != null) {
            Task t = task(c);
            TaskProgress p = t == null ? null : progressFor(t, s);
            if (p != null && p.plannedAt != null && !p.plannedAt.isAfter(now)) {
                bot(c, out, Script.reminder(t, p), "nudge");
                if (postponeBlocked(p)) {
                    bot(c, out, Script.manyPostpones(delays(p)), "normal");
                    bot(c, out, Script.lastBoost(), "boost");
                } else {
                    bot(c, out, Script.boost(), "boost");
                }
                c.step = "DUE";
            }
        }

        // (2) sắp đến hạn (trong vòng 60 phút) mà chưa xong
        for (Task t : todayTasks()) {
            if (!t.deadline.isAfter(now) || t.deadline.isAfter(now.plusMinutes(60))) continue;
            TaskProgress p = progressFor(t, s);
            if (!"DONE".equals(p.status) && !p.deadlineReminded) {
                p.deadlineReminded = true;
                bot(c, out, Script.deadlineSoon(t, "STARTED".equals(p.status)), "nudge");
            }
        }

        if (out.isEmpty()) return none;
        c.lastActivity = now;
        convs.save(c);
        return new Reply(List.of(), out, options(c), input(c));
    }

    /**
     * Học sinh gửi ảnh minh chứng ở bước PHOTO (sau khi bấm "Mình làm xong rồi").
     * Có ảnh hợp lệ thì coi như hoàn tất nhiệm vụ: ghi nhận xong + đã nộp, khen, rồi sang nhiệm vụ kế hoặc kết thúc.
     */
    public Reply photoDone(Student s, List<MultipartFile> files) {
        Conversation c = current(s);
        if (c == null) return open(s, true);
        List<Msg> stale = new ArrayList<>();
        Conversation fresh = restartIfStale(c, s, stale);
        if (fresh != null) return new Reply(List.of(), stale, options(fresh), input(fresh));
        Task t = task(c);
        if (!"PHOTO".equals(c.step) || t == null || !t.active)
            return new Reply(List.of(), List.of(), options(c), input(c));        // không đúng lúc: bỏ qua

        ProofService.Result r = proof.save(s, t.id, files);
        List<Msg> out = new ArrayList<>();
        if (r.saved() == 0) {
            bot(c, out, r.error() != null ? r.error() : "Mình chưa nhận được ảnh nào, bạn thử gửi lại nhé.", "normal");
            return new Reply(List.of(), out, options(c), input(c));
        }

        String said = "📷 Mình đã gửi " + r.saved() + " ảnh minh chứng";
        log(c, "STUDENT", said, "normal", "PHOTO");
        TaskProgress p = progressFor(t, s);
        p.status = "DONE";
        p.doneAt = LocalDateTime.now();
        p.selfSubmitted = true;
        if (p.proof == null || p.proof.isBlank()) p.proof = "Đã gửi " + r.saved() + " ảnh minh chứng";
        progresses.save(p);

        if (p.startedAt != null && Duration.between(p.startedAt, p.doneAt).toMinutes() < 2)
            bot(c, out, Script.fastDone(), "nudge");
        bot(c, out, "Mình đã nhận " + r.saved() + " ảnh minh chứng cho \"" + Script.label(t) + "\"! 🎉 Nhiệm vụ này coi như hoàn tất. "
            + "Ban cán sự sẽ xem ảnh rồi xác nhận bài của bạn."
            + (r.rejected() > 0 ? " (Có " + r.rejected() + " ảnh mình không nhận được, chỉ nhận ảnh JPG, PNG, WEBP, GIF.)" : ""), "cheer");
        intro(c, out, s, false);
        c.lastActivity = LocalDateTime.now();
        convs.save(c);
        return new Reply(List.of(new Msg("STUDENT", said, "normal")), out, options(c), input(c));
    }

    /** Thanh nhiệm vụ hôm nay hiện cho học sinh. */
    public List<TaskChip> chips(Student s) {
        List<TaskChip> list = new ArrayList<>();
        for (Task t : todayTasks()) {
            TaskProgress p = progressFor(t, s);
            String st = p.confirmed ? "CONFIRMED" : p.status;
            list.add(new TaskChip(Script.label(t), t.deadline.format(DM), Script.timeLeft(t), st,
                taskImages.findByTask_IdOrderByIdAsc(t.id).stream().map(i -> i.id).toList()));
        }
        return list;
    }

    // ============================================================
    //  Các hàm phụ
    // ============================================================

    /** Đầu buổi hoặc sau mỗi nhiệm vụ: cho học sinh thấy các nhiệm vụ còn lại và chọn. */
    private void intro(Conversation c, List<Msg> out, Student s, boolean first) {
        c.currentTaskId = null;
        List<Task> today = todayTasks();
        if (today.isEmpty()) {
            bot(c, out, Script.noTasks(), "normal");
            c.step = "NO_TASK";
            return;
        }
        List<TaskProgress> pending = pending(s, today);
        if (pending.isEmpty()) {
            bot(c, out, Script.allDone(), "cheer");
            c.step = "END";
            return;
        }
        bot(c, out, Script.taskList(pending, today.size(), first), "normal");
        ask(c, out, "PICK");
    }

    private void pick(Conversation c, List<Msg> out, Student s, String value) {
        Task t = tasks.findById(parseLong(value)).orElse(null);
        if (t == null || !t.active) {
            bot(c, out, "Mình chưa tìm thấy nhiệm vụ đó, bạn chọn lại nhé.", "normal");
            return;
        }
        c.currentTaskId = t.id;
        TaskProgress p = progressFor(t, s);
        if (taskImages.countByTask_Id(t.id) > 0)
            bot(c, out, "Bài này có đề trong ảnh hoặc tệp Word. Bấm vào ô nhiệm vụ môn học phía trên để xem.", "normal");
        if ("STARTED".equals(p.status)) {
            bot(c, out, Script.resumeDoing(t), "normal");
            c.step = "DOING";
        } else if ("PLANNED".equals(p.status) && p.plannedAt != null) {
            bot(c, out, p.paused ? Script.pauseSummary(t, p) : Script.planSummary(t, p), "normal");
            bot(c, out, Script.waiting(), "normal");
            c.step = "WAIT";
        } else {
            ask(c, out, "WHEN");
        }
    }

    private void handleWhen(Conversation c, List<Msg> out, Task t, TaskProgress p, String step, String value) {
        switch (value) {
            case "custom" -> {
                c.step = "TIME";
                bot(c, out, Script.question("TIME", c, t), "normal");
            }
            case "unknown" -> {
                bot(c, out, Script.unknownWhen(), "nudge");
                ask(c, out, "WHEN2");
            }
            default -> {
                LocalDateTime when = LocalDateTime.now().plusMinutes(parseInt(value, 0));
                if (overdue(t) && when.isAfter(LocalDateTime.now().plusMinutes(15))) {
                    bot(c, out, Script.overdueStartNow(t), "nudge");
                    ask(c, out, step);
                } else if (tooLate(t, when)) {
                    bot(c, out, Script.tooLate(t), "nudge");
                    ask(c, out, step);
                } else {
                    setPlan(p, when);
                    bot(c, out, Script.startTimeQuote(), "boost");
                    ask(c, out, "BARRIER");
                }
            }
        }
    }

    private void handleTime(Conversation c, List<Msg> out, Task t, TaskProgress p, String value) {
        LocalDateTime when = null;
        try {
            when = LocalDate.now().atTime(LocalTime.parse(value.trim()));
        } catch (Exception e) { /* giờ không hợp lệ */ }
        if (when == null || when.isBefore(LocalDateTime.now().minusMinutes(1))) {
            bot(c, out, "Giờ này chưa đúng hoặc đã qua rồi, bạn chọn lại giờ giúp mình nhé.", "normal");
            c.step = "TIME";
            return;
        }
        if (overdue(t) && when.isAfter(LocalDateTime.now().plusMinutes(15))) {
            bot(c, out, Script.overdueStartNow(t), "nudge");
            c.step = "TIME";
            return;
        }
        if (tooLate(t, when)) {
            bot(c, out, Script.tooLate(t), "nudge");
            c.step = "TIME";
            return;
        }
        setPlan(p, when);
        bot(c, out, Script.startTimeQuote(), "boost");
        ask(c, out, "BARRIER");
    }

    /** Giờ bắt đầu sát/sau hạn nộp (chỉ kiểm tra khi nhiệm vụ chưa quá hạn). */
    private boolean tooLate(Task t, LocalDateTime when) {
        return t.deadline.isAfter(LocalDateTime.now()) && !when.isBefore(t.deadline);
    }

    private void setPlan(TaskProgress p, LocalDateTime when) {
        p.plannedAt = when;
        p.plannedLabel = when.format(HM);
        p.postponeCount = 0;
    }

    /** Đã biết nơi làm bài: chốt kế hoạch, vào làm luôn nếu chọn "Ngay bây giờ", không thì chờ đến giờ. */
    private void planned(Conversation c, List<Msg> out, Task t, TaskProgress p, String place) {
        p.place = place;
        p.status = "PLANNED";
        bot(c, out, Script.planSummary(t, p), "normal");
        if (Duration.between(LocalDateTime.now(), p.plannedAt).toSeconds() < 30) {
            startDoing(c, out, p);
        } else {
            bot(c, out, Script.waiting(), "normal");
            c.step = "WAIT";
        }
    }

    private String clean(String v, int max) {
        String x = v == null ? "" : v.trim().replaceAll("\\s+", " ");
        return x.length() > max ? x.substring(0, max) : x;
    }

    /** Tối đa số lần hoãn của MỘT nhiệm vụ trong cả ngày (không reset); sau đó chỉ còn "làm 5 phút" hoặc xin giúp đỡ. */
    private static final int MAX_POSTPONE = 3;

    /** Tối đa số lần tạm dừng giữa chừng của một nhiệm vụ, và mỗi lần nghỉ tối đa bao nhiêu phút. */
    private static final int MAX_PAUSE = 3;
    private static final int MAX_PAUSE_MIN = 30;

    /** Hoãn và tạm dừng dùng CHUNG một ngân sách: tổng cộng tối đa MAX_DELAY lượt cho mỗi nhiệm vụ. */
    private static final int MAX_DELAY = 3;

    private static int delays(TaskProgress p) {
        return p == null ? 0 : p.postponeTotal + p.pauseCount;
    }

    private static boolean postponeBlocked(TaskProgress p) {
        return p != null && (p.postponeTotal >= MAX_POSTPONE || delays(p) >= MAX_DELAY);
    }

    /** Số phút tối đa cho một lần nghỉ: lượt cuối chỉ được 10 phút. */
    private static int pauseMax(TaskProgress p) {
        return delays(p) >= MAX_DELAY - 1 ? 10 : MAX_PAUSE_MIN;
    }

    private static boolean pauseBlocked(TaskProgress p) {
        return p != null && (p.pauseCount >= MAX_PAUSE || delays(p) >= MAX_DELAY);
    }

    /** Nhiệm vụ đã quá hạn nộp: không cho hoãn nữa. */
    private static boolean overdue(Task t) {
        return t != null && !t.deadline.isAfter(LocalDateTime.now());
    }

    /** Lần hoãn đầu được 10 phút, lần sau chỉ 5 phút (ngắn dần). */
    private static int minPostpone(TaskProgress p) {
        return delays(p) < MAX_DELAY - 1 ? 10 : 5;                 // lượt cuối chỉ được hẹn 5 phút
    }

    /** Ghi lý do hoãn kèm giờ trong ngày ("hard@21") để thống kê cho giáo viên. */
    private static void addPostponeLog(TaskProgress p, String reason) {
        String e = reason + "@" + LocalDateTime.now().getHour();
        String cur = p.postponeLog == null ? "" : p.postponeLog;
        if (cur.length() + e.length() + 1 > 240) return;     // đủ chỗ cho khoảng 35 lần, đủ dùng
        p.postponeLog = cur.isEmpty() ? e : cur + "," + e;
    }

    /** Lưu lý do học sinh tự gõ để người quản trị xem được. */
    private static void addOtherReason(TaskProgress p, String text) {
        String cur = p.otherReasons == null ? "" : p.otherReasons;
        String add = text.replace("|", "/").replace("\n", " ");
        if (cur.length() + add.length() + 3 > 300) return;
        p.otherReasons = cur.isEmpty() ? add : cur + " | " + add;
    }

    private void askHelp(Conversation c, List<Msg> out, TaskProgress p) {
        p.needHelp = true;
        bot(c, out, "Mình đã đánh dấu bạn cần hỗ trợ. Bạn có thể hỏi thầy cô hoặc bạn bè về đúng câu đang vướng; trong lúc đó hãy chọn nghỉ hoặc thử một bước nhỏ.", "nudge");
    }

    private void startDoing(Conversation c, List<Msg> out, TaskProgress p) {
        boolean resume = p.paused;                       // quay lại sau khi tạm dừng
        p.paused = false;
        p.prep = null;                                   // bắt đầu rồi thì bỏ lời hứa cũ
        p.status = "STARTED";
        if (p.startedAt == null) p.startedAt = LocalDateTime.now();
        bot(c, out, resume ? Script.resumed() : Script.doing(), "nudge");
        c.step = "DOING";
    }

    /** Học sinh tạm dừng giữa chừng: hẹn giờ làm tiếp (dùng lại cơ chế nhắc đúng giờ như lúc hẹn giờ bắt đầu). */
    private void pauseUntil(Conversation c, List<Msg> out, Task t, TaskProgress p, LocalDateTime when, String stepOnError) {
        if (tooLate(t, when)) {
            bot(c, out, Script.tooLate(t), "nudge");
            c.step = stepOnError;
            return;
        }
        // Mỗi yêu cầu nghỉ đã xác nhận chỉ được tính một lượt; bảo vệ cả trường hợp
        // trình duyệt gửi lại câu trả lời trong lúc nhiệm vụ đã chuyển sang PLANNED.
        if (pauseBlocked(p) || p.paused || !"STARTED".equals(p.status)) {
            bot(c, out, "Lượt nghỉ này đã được ghi nhận hoặc bạn đã dùng hết lượt. Không trừ thêm lượt nào.", "normal");
            c.step = p.paused ? "WAIT" : "DOING";
            return;
        }
        p.status = "PLANNED";
        p.paused = true;
        p.pauseCount++;
        p.plannedAt = when;
        p.plannedLabel = when.format(HM);
        p.postponeCount = 0;
        p.notifiedFor = null;
        askCommit(c, out, t, p, true);                           // nghỉ cũng phải cam kết
    }

    /**
     * Thông báo hạn mức ở MỌI lần bấm Hoãn hoặc Tạm dừng (không chỉ lần hoãn đầu tiên).
     * Hai thao tác dùng chung 3 lượt cho mỗi nhiệm vụ. Chỉ trừ khi chọn thời gian hẹn.
     */
    private static String delayQuota(TaskProgress p, boolean afterScheduling) {
        int remaining = Math.max(0, MAX_DELAY - delays(p));
        if (afterScheduling) {
            return "Đã hẹn giờ. Còn " + remaining + "/" + MAX_DELAY + " lượt hoãn/nghỉ.";
        }
        return "Bạn còn " + remaining + "/" + MAX_DELAY
            + " lượt hoãn/nghỉ. Chỉ tính 1 lượt khi chọn giờ mới.";
    }

    private void askWhy(Conversation c, List<Msg> out, Task t, TaskProgress p, boolean pause) {
        // Luôn hiện trước câu hỏi lý do, cả hoãn khi chưa bắt đầu lẫn tạm dừng khi đang làm.
        bot(c, out, delayQuota(p, false), "normal");
        if (MAX_DELAY - delays(p) <= 1) {
            bot(c, out, Script.lastWarning(t, pause), "normal");
            bot(c, out, Script.lastBoost(), "boost");
            bot(c, out, Script.lastWhy(), "normal");
        } else {
            bot(c, out, pause ? Script.pauseAsk(t, p) : Script.postponeAsk(t, p), "normal");
        }
    }

    /** Cam kết phải chỉ ra việc làm cụ thể; không lấy khẩu hiệu thay cho kế hoạch. */
    private static boolean meaningfulCommit(String text) {
        if (text == null || text.isBlank()) return false;
        String t = java.text.Normalizer.normalize(text.toLowerCase(java.util.Locale.ROOT),
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd').trim();
        if (t.length() < 12) return false;
        String[] vague = {"co len", "ok", "oke", "duoc", "dong y", "quyet tam", "se co gang", "khong bo cuoc", "hoc bai", "lam bai"};
        for (String v : vague) if (t.equals(v)) return false;
        String[] actions = {"doc ", "viet ", "lam ", "giai ", "mo ", "xem ", "ghi ", "chon ",
                "uống ", "uong ", "nghi ", "hoi ", "soan ", "on ", "tat ", "cat ", "di ", "quay lai", "vuon vai"};
        for (String action : actions) if (t.contains(action)) return true;
        return t.length() >= 26; // các kế hoạch cụ thể viết tự do vẫn được phép
    }

    /** Hỏi cam kết sau khi đã hẹn giờ (dùng chung cho hoãn và tạm dừng). */
    private void askCommit(Conversation c, List<Msg> out, Task t, TaskProgress p, boolean pause) {
        // Đã lưu giờ hẹn và trừ đúng 1 lượt: cập nhật lại số lượt để học sinh biết.
        bot(c, out, delayQuota(p, true), "normal");
        long mins = Math.max(1, (Duration.between(LocalDateTime.now(), p.plannedAt).getSeconds() + 30) / 60);
        bot(c, out, Script.commitAsk(t, p, mins, delays(p) >= MAX_DELAY, pause), "nudge");
        c.step = "POSTPONE_COMMIT";
    }

    /**
     * Nếu nhiệm vụ đang làm dở đã bị xóa/ẩn (hoặc trước đó chưa có nhiệm vụ mà nay đã có),
     * thì KHÔNG tiếp tục câu chuyện cũ: mở cuộc trò chuyện mới với danh sách nhiệm vụ hiện tại.
     * Trả về cuộc trò chuyện mới (tin nhắn mới được thêm vào out), hoặc null nếu không có gì thay đổi.
     */
    private Conversation restartIfStale(Conversation c, Student s, List<Msg> out) {
        String why = null;
        if (c.currentTaskId != null) {
            Task t = tasks.findById(c.currentTaskId).orElse(null);
            if (t == null || !t.active)
                why = "Nhiệm vụ bạn đang làm đã bị xóa hoặc thay đổi, nên mình bắt đầu lại với danh sách nhiệm vụ mới nhé.";
        } else if ("NO_TASK".equals(c.step) && !todayTasks().isEmpty()) {
            why = "Có nhiệm vụ mới rồi nhé!";
        }
        if (why == null) return null;
        Conversation n = new Conversation();
        n.student = s;
        n.startedAt = LocalDateTime.now();
        n.lastActivity = n.startedAt;
        n.step = "NO_TASK";
        convs.save(n);
        bot(n, out, why, "normal");
        intro(n, out, s, false);
        convs.save(n);
        return n;
    }

    /** Chuyển sang bước mới và để trợ lý hỏi câu hỏi của bước đó. */
    private void ask(Conversation c, List<Msg> out, String step) {
        c.step = step;
        String q = Script.question(step, c, task(c));
        if (q != null) bot(c, out, q, "normal");
    }

    private void bot(Conversation c, List<Msg> out, String text, String kind) {
        log(c, "BOT", text, kind, c.step);
        out.add(new Msg("BOT", text, kind));
    }

    private void log(Conversation c, String sender, String text, String kind, String step) {
        ChatLog l = new ChatLog();
        l.conversation = c;
        l.createdAt = LocalDateTime.now();
        l.sender = sender;
        l.content = text;
        l.kind = kind;
        l.step = step;
        logs.save(l);
    }

    // ---------- nút bấm ----------
    private List<Opt> options(Conversation c) {
        if ("PICK".equals(c.step)) {
            List<Opt> o = new ArrayList<>();
            for (TaskProgress p : pending(c.student, todayTasks()))
                o.add(new Opt(Script.label(p.task) + " (hạn " + p.task.deadline.format(HM) + ")", String.valueOf(p.task.id)));
            return o;
        }
        if ("DUE".equals(c.step)) {
            Task t = task(c);
            TaskProgress p = t == null ? null : progressFor(t, c.student);
            List<Opt> o = new ArrayList<>();
            boolean many = postponeBlocked(p) || overdue(t);   // hết lượt hoãn hoặc đã quá hạn
            boolean resume = p != null && p.paused;
            o.add(new Opt(resume ? "Làm tiếp thôi" : many ? "Thử 5 phút thôi" : "Bắt đầu thôi", "start"));
            if (!many && (t == null || !tooLate(t, LocalDateTime.now().plusMinutes(minPostpone(p)))))
                o.add(new Opt(resume ? "Nghỉ thêm một chút" : "Hoãn một chút", "postpone"));
            if (many && !p.needHelp) o.add(new Opt("Mình cần được giúp", "help"));
            return o;
        }
        if ("POSTPONE_WHY".equals(c.step)) {
            List<Opt> o = new ArrayList<>();
            o.add(new Opt("Bài khó", "hard"));
            o.add(new Opt("Đang mệt", "tired"));
            o.add(new Opt("Đang bận việc khác", "busy"));
            o.add(new Opt("Chán, chưa muốn làm", "bored"));
            o.add(new Opt("Lý do khác (mình tự gõ)", "other"));
            o.add(new Opt("Mình đổi ý, làm luôn", "back"));
            return o;
        }
        if ("POSTPONE_PLAN".equals(c.step)) {
            Task t = task(c);
            TaskProgress p = t == null ? null : progressFor(t, c.student);
            String r = p == null || p.lastPostponeReason == null ? "" : p.lastPostponeReason;
            int n = delays(p);
            LocalDateTime now = LocalDateTime.now();
            List<Opt> o = new ArrayList<>();
            o.add(new Opt(switch (r) {
                case "hard" -> "Thử làm 1 câu trong 5 phút";
                case "bored" -> "Thử tập trung 2 phút";
                case "tired" -> "Mình đã sẵn sàng làm tiếp";
                case "busy" -> "Mình đã rảnh, bắt đầu nhé";
                default -> "Thử làm bước đầu tiên";
            }, "start"));
            int[] mins = "busy".equals(r) ? (n < MAX_DELAY - 1 ? new int[]{10, 15} : new int[]{5}) : (n == 0 ? new int[]{10} : new int[]{5});
            for (int m : mins) {
                if (t != null && tooLate(t, now.plusMinutes(m))) continue;
                String label = "busy".equals(r) ? (m >= 60 ? "1 tiếng nữa" : m + " phút nữa")
                    : "tired".equals(r) ? "Nghỉ " + m + " phút" : "Vẫn hoãn " + m + " phút";
                o.add(new Opt(label, "wait:" + m));
            }
            if ("hard".equals(r)) {
                if (p != null && !p.needHelp) o.add(new Opt("Mình cần được giúp", "help"));
                o.addAll(helpLinks(c, false));
            }
            return o;
        }
        if ("POSTPONE_OTHER".equals(c.step)) {
            return new ArrayList<>(List.of(new Opt("Mình đổi ý, làm luôn", "back")));
        }
        if ("POSTPONE_COMMIT".equals(c.step)) {
            Task tc = task(c);
            TaskProgress pc = tc == null ? null : progressFor(tc, c.student);
            List<Opt> o = new ArrayList<>();
            if (delays(pc) < MAX_DELAY) {                                        // lượt cuối: không có nút chọn nhanh, phải tự gõ
                o.add(new Opt("Mở sẵn sách và bài ra bàn", "prep"));
                o.add(new Opt("Cất điện thoại xa tầm tay", "prep"));
                o.add(new Opt("Làm trước 1 câu dễ nhất", "prep"));
                o.add(new Opt("Uống nước, vươn vai", "prep"));
            }
            o.add(new Opt("Thôi, mình làm luôn", "start"));
            return o;
        }
        if ("PAUSE_WHY".equals(c.step)) {
            List<Opt> o = new ArrayList<>();
            o.add(new Opt("Đang mệt", "tired"));
            o.add(new Opt("Bí bài, mình cần nghỉ ngơi một chút để suy nghĩ", "hard"));
            o.add(new Opt("Có việc gấp phải làm", "busy"));
            o.add(new Opt("Chán, mất tập trung", "bored"));
            o.add(new Opt("Lý do khác (mình tự gõ)", "other"));
            o.add(new Opt("Mình làm tiếp luôn", "back"));
            return o;
        }
        if ("PAUSE_OTHER".equals(c.step)) {
            return new ArrayList<>(List.of(new Opt("Mình làm tiếp luôn", "back")));
        }
        if ("PAUSE".equals(c.step)) {
            Task t = task(c);
            List<Opt> o = new ArrayList<>();
            TaskProgress pp = t == null ? null : progressFor(t, c.student);
            String reason = pp == null || pp.lastPostponeReason == null ? "" : pp.lastPostponeReason;
            o.add(new Opt(switch (reason) {
                case "hard" -> "Thử 1 câu dễ trong 5 phút";
                case "bored" -> "Thử tập trung 2 phút";
                case "tired" -> "Mình đã sẵn sàng làm tiếp";
                case "busy" -> "Mình có thể làm tiếp rồi";
                default -> "Thử một bước nhỏ ngay";
            }, "micro"));
            if ("hard".equals(reason)) {
                if (pp != null && !pp.needHelp) o.add(new Opt("Mình cần được hỗ trợ", "help"));
                o.addAll(helpLinks(c, false));
            }
            int[] mins = pauseMax(pp) <= 10 ? new int[]{5, 10} : new int[]{10, 20, 30};   // lượt cuối: nghỉ ngắn
            for (int m : mins)
                if (t == null || !tooLate(t, LocalDateTime.now().plusMinutes(m)))
                    o.add(new Opt(m + " phút nữa", String.valueOf(m)));
            o.add(new Opt("Chọn giờ cụ thể", "custom"));
            o.add(new Opt("Mình không tạm dừng nữa", "back"));
            return o;
        }
        List<Opt> o = new ArrayList<>(Script.options(c.step, demo));
        if ("DOING".equals(c.step)) {                                          // hết lượt nghỉ giữa chừng: ẩn nút tạm dừng
            Task td = task(c);
            TaskProgress pd = td == null ? null : progressFor(td, c.student);
            if (pauseBlocked(pd)) o.removeIf(x -> "stop".equals(x.value()));
        }
        if ("WHEN".equals(c.step) || "WHEN2".equals(c.step)) {                 // nhiệm vụ đã quá hạn: chỉ được hẹn rất gần
            Task tt = task(c);
            if (overdue(tt)) {
                o.clear();
                o.add(new Opt("Ngay bây giờ", "0"));
                o.add(new Opt("15 phút nữa", "15"));
            }
        }
        if ("WAIT".equals(c.step)) {                                           // đang nghỉ giữa chừng: đổi chữ nút cho đúng
            Task t = task(c);
            TaskProgress p = t == null ? null : progressFor(t, c.student);
            if (p != null && p.paused) { o.clear(); o.add(new Opt("Mình làm tiếp sớm rồi", "early")); }
        }
        if ("PLACE".equals(c.step)) {
            Task tt = task(c);
            TaskProgress pp = tt == null ? null : progressFor(tt, c.student);
            if (pp != null && (Script.BARRIER_START_LABEL.equals(pp.barrier)
                    || "Bài quá nhiều hoặc quá khó".equals(pp.barrier))) o.addAll(0, helpLinks(c, false));
        }
        return o;
    }

    /**
     * Hiện thêm nút mở Vietjack / YouTube / Lời giải hay / OLM / HOC247 (tab mới) ngay ở câu hỏi lý do chưa bắt đầu (onlyIfStuck = false),
     * và nhắc lại ở câu hỏi nơi làm bài nếu học sinh chọn "Không biết bắt đầu từ đâu" (onlyIfStuck = true).
     * tìm theo môn và nội dung nhiệm vụ. Giá trị nút có dạng "url:loại:địa chỉ" và chỉ là liên kết, không gửi về máy chủ.
     */
    private List<Opt> helpLinks(Conversation c, boolean onlyIfStuck) {
        Task t = task(c);
        if (t == null) return List.of();
        if (onlyIfStuck && !Script.BARRIER_START_LABEL.equals(progressFor(t, c.student).barrier)) return List.of();
        String q = (t.subject + " " + t.title).trim();
        List<Opt> links = new ArrayList<>();
        links.add(new Opt("Học trên Vietjack", "url:vj:" + googleSite("vietjack.com", q)));
        links.add(new Opt("Xem trên YouTube", "url:yt:https://www.youtube.com/results?search_query="
                + URLEncoder.encode(q + " bài giảng", StandardCharsets.UTF_8)));
        links.add(new Opt("Lời giải hay", "url:lgh:" + googleSite("loigiaihay.com", q)));
        links.add(new Opt("Học trên OLM", "url:olm:" + googleSite("olm.vn", q)));
        links.add(new Opt("Học trên HOC247", "url:h247:" + googleSite("hoc247.net", q)));
        return links;
    }

    /** Tìm trong một trang cụ thể qua Google (site:...), tránh phụ thuộc vào ô tìm kiếm riêng của từng trang. */
    private static String googleSite(String site, String q) {
        return "https://www.google.com/search?q=" + URLEncoder.encode("site:" + site + " " + q, StandardCharsets.UTF_8);
    }

    private String input(Conversation c) {
        if ("TIME".equals(c.step) || "PAUSE_TIME".equals(c.step)) return "time";
        if ("PROOF".equals(c.step) || "PLACE_OTHER".equals(c.step) || "BARRIER_OTHER".equals(c.step) || "POSTPONE_COMMIT".equals(c.step) || "POSTPONE_OTHER".equals(c.step) || "PAUSE_OTHER".equals(c.step)) return "text";
        if ("PHOTO".equals(c.step)) return "photo";
        return "";
    }

    // ---------- dữ liệu ----------
    /** Nhiệm vụ của hôm nay (kể cả cái đã quá hạn trong ngày), gần hạn nhất ở trên. */
    private List<Task> todayTasks() {
        return tasks.findByActiveTrueAndDeadlineAfterOrderByDeadlineAsc(LocalDate.now().atStartOfDay());
    }

    private List<TaskProgress> pending(Student s, List<Task> today) {
        List<TaskProgress> list = new ArrayList<>();
        for (Task t : today) {
            TaskProgress p = progressFor(t, s);
            if (!"DONE".equals(p.status)) list.add(p);
        }
        return list;
    }

    /** Lấy tiến độ của học sinh với nhiệm vụ; chưa có thì tạo mới. */
    private TaskProgress progressFor(Task t, Student s) {
        return progresses.findByTask_IdAndStudent_Id(t.id, s.id).orElseGet(() -> {
            TaskProgress p = new TaskProgress();
            p.task = t;
            p.student = s;
            return progresses.save(p);
        });
    }

    private Task task(Conversation c) {
        return c.currentTaskId == null ? null : tasks.findById(c.currentTaskId).orElse(null);
    }

    private List<Msg> history(Conversation c) {
        List<Msg> list = new ArrayList<>();
        for (ChatLog l : logs.findByConversation_IdOrderByIdAsc(c.id)) list.add(new Msg(l.sender, l.content, l.kind));
        return list;
    }

    private Conversation current(Student s) {
        return convs.findFirstByStudent_IdOrderByStartedAtDesc(s.id).orElse(null);
    }

    private int parseInt(String v, int fallback) {
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return fallback; }
    }

    private Long parseLong(String v) {
        try { return Long.parseLong(v.trim()); } catch (Exception e) { return -1L; }
    }
}
