package vn.ainudge.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import vn.ainudge.dto.Opt;
import vn.ainudge.model.Conversation;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskProgress;

/**
 * ============================================================
 * BỘ CÂU HỎI VÀ CÚ HÍCH CỦA TRỢ LÝ (SỬA CHỮ Ở FILE NÀY)
 * ============================================================
 * Muốn đổi câu hỏi, đổi lời nhắc, đổi các nút lựa chọn: chỉ cần sửa chữ nằm
 * trong dấu "..." ở dưới.
 * Không cần hiểu phần còn lại.
 */
public class Script {
    /**
     * Chữ trên nút "không biết bắt đầu" (ChatService so khớp chữ này để hiện thêm
     * các nút học trực tuyến).
     */
    public static final String BARRIER_START_LABEL = "Không biết bắt đầu từ đâu";

    private static final Random RND = new Random();
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    /** Chọn ngẫu nhiên 1 câu trong nhiều câu (để trợ lý đỡ nhàm chán). */
    static String pick(String... options) {
        return options[RND.nextInt(options.length)];
    }

    // ------------------------------------------------------------------
    // KHO CÂU TRUYỀN ĐỘNG LỰC (từ file Word "Câu nói truyền động lực").
    // Muốn thêm câu mới: thêm một dòng "...", vào đúng nhóm. Nhớ có dấu phẩy ở cuối
    // dòng (trừ dòng cuối).
    // ------------------------------------------------------------------
    /** Câu chung: dùng ở lời động viên chung. */
    private static final String[] Q_GENERAL = {
            "Thành công không phải là ngẫu nhiên, mà là kết quả của sự chăm chỉ và kiên trì mỗi ngày.",
            "Tiến chậm không sao, miễn là bạn không dừng lại.",
            "Khoảng cách giữa ước mơ và hiện thực được gọi là hành động.",
            "Đừng xấu hổ khi bạn không biết, chỉ xấu hổ khi bạn không học.",
            "Mỗi ngày lười đi một chút, là tự tay bạn đẩy ước mơ mình xa thêm một bước.",
            "Giáo dục là vũ khí mạnh mẽ nhất mà bạn có thể sử dụng để thay đổi thế giới. (Nelson Mandela)",
            "Mỗi ngày tiến một chút cũng là tiến bộ.",
            "Một việc nhỏ được làm ngay sẽ hữu ích hơn một kế hoạch còn để đó.",
            "Bạn đã quay lại với bài học, hãy tiếp tục từ bước đang dang dở.",
            "Tương lai của bạn được quyết định bởi những gì bạn làm hôm nay, chứ không phải ngày mai.",
            "Bạn không cần phải hoàn hảo mỗi ngày, bạn chỉ cần tốt hơn phiên bản của chính mình ngày hôm qua 1% thôi."
    };
    /** Khi gặp bài khó, bí bài. */
    private static final String[] Q_HARD = {
            "Bài toán càng khó, chiến thắng càng vinh quang. Bí bài chỉ là dấu hiệu cho thấy não bạn đang chuẩn bị nâng cấp.",
            "Gặp bài khó không phải để bạn từ bỏ, mà là để bạn biết mình cần học thêm điều gì. Đi hỏi thầy cô, bạn bè cũng là một loại năng lực.",
            "Đừng nhìn vào cả bài toán lớn mà sợ. Hãy bẻ nhỏ nó ra, giải quyết từng dòng, từng bước một.",
            "Những người giỏi nhất cũng từng có lúc không giải được bài. Sự khác biệt là họ chọn không dừng lại.",
            "Gặp bài khó không có nghĩa là bạn không làm được.",
            "Chưa hiểu ở đâu thì hỏi đúng chỗ đó, nhờ hỗ trợ cũng là một năng lực."
    };
    /** Khi chán nản, mệt, muốn bỏ cuộc. */
    private static final String[] Q_TIRED = {
            "Nghỉ ngơi 15 phút nếu bạn mệt, nhưng đừng bỏ cuộc nhé.",
            "Lúc bạn muốn bỏ cuộc nhất, hãy nghĩ đến lý do vì sao bạn đã bắt đầu và nỗ lực đến tận bây giờ.",
            "Cố gắng lúc này có thể mệt mỏi, nhưng hối hận về sau sẽ là một nỗi đau âm ỉ.",
            "Tương lai của bạn được quyết định bởi những gì bạn làm hôm nay, chứ không phải ngày mai.",
            "Bạn không cần phải hoàn hảo mỗi ngày, bạn chỉ cần tốt hơn phiên bản của chính mình ngày hôm qua 1% thôi.",
            "Nghỉ ngơi hợp lý cũng là một phần của việc học.",
            "Tiến chậm không sao; quan trọng là chọn nhịp phù hợp với mình."
    };
    /** Khi mất tập trung, xao nhãng, chán. */
    private static final String[] Q_FOCUS = {
            "Điện thoại có thể lướt cả đời, nhưng cơ hội để bạn bứt phá trong học tập chỉ có vài năm ngắn ngủi.",
            "Tập trung 25 phút chất lượng còn hơn ngồi cả ngày mà tâm trí thả trên mây.",
            "Đừng để những niềm vui nhất thời trước mắt cướp đi tương lai rực rỡ phía sau.",
            "Kỷ luật là cầu nối giữa mục tiêu và thành tựu. Hãy tắt thông báo, bật chế độ tập trung ngay bây giờ!",
            "Bạn không cần có hứng ngay; hãy thử một bước nhỏ trước.",
            "Để lấy lại nhịp, chỉ cần bắt đầu từ một việc đơn giản."
    };
    /** Khi bận việc khác, cần hẹn lại. */
    private static final String[] Q_BUSY = {
            "Một kế hoạch vừa sức giúp bạn dễ quay lại hơn.",
            "Chưa làm được ngay thì chọn một thời điểm thực tế để bắt đầu.",
            "Khoảng cách giữa ước mơ và hiện thực được gọi là hành động.",
            "Một việc nhỏ được làm ngay sẽ hữu ích hơn một kế hoạch còn để đó."
    };

    private static String[] concat(String[]... groups) {
        java.util.List<String> all = new java.util.ArrayList<>();
        for (String[] g : groups)
            for (String x : g)
                if (!all.contains(x))
                    all.add(x);
        return all.toArray(new String[0]);
    }

    // Bộ lời động viên từ Word, đã biên tập để gần gũi, không ép học khi mệt.
    private static String lastQuote = "";

    public static synchronized String motivation() {
        String[] quotes = concat(Q_GENERAL, Q_HARD, Q_TIRED, Q_FOCUS);
        String chosen;
        do {
            chosen = quotes[RND.nextInt(quotes.length)];
        } while (quotes.length > 1 && chosen.equals(lastQuote));
        lastQuote = chosen;
        return chosen;
    }

    // Chỉ dùng ở bước hỏi thời điểm bắt đầu: đúng hai câu do người dùng chọn.
    private static String lastStartQuote = "";

    public static synchronized String startTimeQuote() {
        String first = "Việc học như con thuyền nước ngược, không tiến ắt lùi!";
        String second = "Việc hôm nay chớ để ngày mai";
        String selected = first.equals(lastStartQuote) ? second
                : second.equals(lastStartQuote) ? first
                        : (RND.nextBoolean() ? first : second);
        lastStartQuote = selected;
        return selected;
    }

    static String boost() {
        return motivation();
    }

    static String lastBoost() {
        return motivation();
    }

    /**
     * Một cú hích có hành động cụ thể, chọn ngẫu nhiên và tránh trùng câu vừa hiển
     * thị.
     * Cú hích là gợi ý, không được hiểu là học sinh đã thực hiện hành động.
     */
    private static final java.util.Map<String, Integer> lastActionIndex = new java.util.HashMap<>();

    private static synchronized String action(String group, String... choices) {
        int prev = lastActionIndex.getOrDefault(group, -1);
        int index = RND.nextInt(choices.length);
        if (choices.length > 1 && index == prev)
            index = (index + 1 + RND.nextInt(choices.length - 1)) % choices.length;
        lastActionIndex.put(group, index);
        // Ghép một thông điệp ngắn đúng ngữ cảnh với hành động cụ thể.
        // Hai câu tục ngữ ở bước chọn giờ KHÔNG xuất hiện trong các nhóm này.
        String encouragement = situationMotivation(group);
        return encouragement.isEmpty() ? choices[index] : encouragement + " " + choices[index];
    }

    // Mỗi tình huống có thư viện riêng; tránh nhắc hai lần liền cùng một câu trong
    // một nhóm.
    private static final java.util.Map<String, Integer> lastSituationIndex = new java.util.HashMap<>();

    private static synchronized String situationMotivation(String group) {
        String[] options;
        if (group.endsWith("-hard"))
            options = Q_HARD;
        else if (group.endsWith("-bored"))
            options = Q_FOCUS;
        else if (group.endsWith("-tired"))
            options = Q_TIRED;
        else if (group.endsWith("-busy"))
            options = Q_BUSY;
        else
            options = Q_GENERAL;
        int old = lastSituationIndex.getOrDefault(group, -1);
        int index = RND.nextInt(options.length);
        if (index == old)
            index = (index + 1 + RND.nextInt(options.length - 1)) % options.length;
        lastSituationIndex.put(group, index);
        return options[index];
    }

    /**
     * Hành động thật sự nhỏ và có thể làm ngay; không quy chụp học sinh lười biếng.
     */
    public static String microStart(String reason) {
        return switch (reason == null ? "" : reason) {
            case "hard" ->
                "Bước đầu trong 5 phút: chọn 1 câu dễ nhất, gạch dữ kiện và viết dòng giải đầu tiên. Nếu vẫn bí, ghi đúng chỗ chưa hiểu để hỏi thầy cô hoặc bạn bè.";
            case "bored" ->
                "Thử 2 phút: đóng tab gây phân tâm, mở đề và gạch chân yêu cầu đầu tiên. Sau 2 phút, bạn tự quyết định có tiếp tục không.";
            case "tired" ->
                "Nếu đã sẵn sàng, chọn một việc nhẹ trong 2 phút: đọc đề hoặc sắp xếp vở. Nếu còn mệt, bạn vẫn có thể chọn nghỉ ngắn.";
            case "busy" ->
                "Việc đầu tiên: mở đúng đề, đánh dấu phần cần làm và chọn 1 câu để bắt đầu khi có thời gian.";
            default ->
                "Thử một bước trong 5 phút: mở đề, chọn 1 câu và viết dòng đầu tiên. Làm được bước nào thì tính bước đó.";
        };
    }

    public static String reasonBoost(String reason) {
        // Giữ phương thức để các nơi khác vẫn tương thích; không tạo khẩu hiệu chung
        // chung.
        return microStart(reason);
    }

    // ------------------------------------------------------------------
    // 0. TÊN NHIỆM VỤ VÀ THỜI GIAN CÒN LẠI
    // ------------------------------------------------------------------
    public static String label(Task t) {
        return t.subject + " - " + t.title;
    }

    public static String timeLeft(Task t) {
        long m = Duration.between(LocalDateTime.now(), t.deadline).toMinutes();
        if (m < 0)
            return "đã quá hạn " + human(-m);
        return "còn " + human(m);
    }

    static String human(long minutes) {
        if (minutes < 60)
            return Math.max(minutes, 1) + " phút";
        long h = minutes / 60, m = minutes % 60;
        if (h >= 24)
            return (h / 24) + " ngày";
        return m == 0 ? h + " giờ" : h + " giờ " + m + " phút";
    }

    // ------------------------------------------------------------------
    // 1. CÂU HỎI CHO TỪNG BƯỚC
    // ------------------------------------------------------------------
    public static String question(String step, Conversation c, Task t) {
        String name = t == null ? "" : label(t);
        return switch (step) {
            case "PICK" -> "Bạn muốn bắt đầu với nhiệm vụ nào trước? (Mình gợi ý chọn nhiệm vụ sắp đến hạn nhất)";
            case "WHEN" -> "Bạn muốn bắt đầu từ lúc nào? Hạn nộp: " + t.deadline.format(DM) + " (" + timeLeft(t) + ").";
            case "WHEN2" -> "Bạn thử chọn một mốc thật nhỏ nhé. Mốc nào hợp với bạn?";
            case "TIME" -> "Bạn muốn bắt đầu lúc mấy giờ? Chọn giờ bên dưới nhé.";
            case "BARRIER" -> "Điều gì đang làm bạn khó bắt đầu? Bạn chọn một lý do, mình sẽ gợi ý cách xử lý phù hợp.";
            case "PAUSE" ->
                "Bạn muốn thử một bước nhỏ trước, xem hướng dẫn nếu đang bí, hay chọn giờ nghỉ? Bạn là người quyết định.";
            case "PAUSE_TIME" -> "Bạn muốn làm tiếp lúc mấy giờ? Hãy chọn giờ ở ô bên dưới.";
            case "POSTPONE_WHY" -> "Điều gì đang làm bạn muốn hoãn vậy?";
            case "POSTPONE_PLAN" -> "Bạn chọn cách nào nhé?";
            case "PLACE" -> "Bạn sẽ làm bài ở đâu?";
            case "PLACE_OTHER" ->
                "Bạn sẽ làm bài ở đâu? Gõ tên chỗ đó vào ô bên dưới nhé (ví dụ: nhà bà, quán cà phê, thư viện trường).";
            case "BARRIER_OTHER" ->
                "Cảm ơn bạn đã nói thật. Điều gì khiến bạn chưa bắt đầu? Bạn gõ vài chữ vào ô bên dưới nhé.";
            case "PHOTO" ->
                "Nếu có ảnh bài đã làm, bạn có thể gửi để ban cán sự đối chiếu. Nếu không gửi được, hãy chọn cách mô tả bài làm.";
            case "PROOF" -> "Bạn có thể mô tả ngắn phần đã làm (ví dụ: đã xong 8/10 câu).";
            case "FEEL" -> "Bạn thấy nhiệm vụ này thế nào?";
            case "SUBMIT" -> "Bạn đã nộp bài cho ban cán sự chưa?";
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // 2. CÁC NÚT LỰA CHỌN CHO TỪNG BƯỚC (Opt(chữ trên nút, giá trị))
    // (nút của bước PICK và DUE được tạo trong ChatService)
    // ------------------------------------------------------------------
    public static List<Opt> options(String step, boolean demo) {
        List<Opt> o = new ArrayList<>();
        switch (step) {
            case "NO_TASK" -> o.add(new Opt("Kiểm tra lại nhiệm vụ", "refresh"));
            case "WHEN" -> {
                o.add(new Opt("Ngay bây giờ", "0"));
                o.add(new Opt("15 phút nữa", "15"));
                o.add(new Opt("30 phút nữa", "30"));
                o.add(new Opt("Chọn giờ cụ thể", "custom"));
                o.add(new Opt("Mình chưa biết", "unknown"));
            }
            case "WHEN2" -> {
                o.add(new Opt("30 phút nữa", "30"));
                o.add(new Opt("1 tiếng nữa", "60"));
                o.add(new Opt("Chọn giờ cụ thể", "custom"));
            }
            case "BARRIER" -> {
                o.add(new Opt("Quên mất", "forget"));
                o.add(new Opt("Bài quá nhiều hoặc quá khó", "big"));
                o.add(new Opt(BARRIER_START_LABEL, "start"));
                o.add(new Opt("Bị điện thoại / mạng xã hội cuốn", "phone"));
                o.add(new Opt("Mệt hoặc chán", "tired"));
                o.add(new Opt("Lý do khác (mình tự nhập)", "other"));
                o.add(new Opt("Không có vấn đề gì", "none"));
            }
            case "PLACE" -> {
                for (String s : new String[] { "Bàn học", "Phòng khách", "Phòng riêng", "Thư viện / quán" })
                    o.add(new Opt(s, s));
                o.add(new Opt("Chỗ khác (mình tự nhập)", "other"));
            }
            case "WAIT" -> o.add(new Opt("Mình bắt đầu sớm rồi", "early"));
            case "DOING" -> {
                o.add(new Opt("Mình làm xong rồi", "done"));
                o.add(new Opt("Mình tạm dừng", "stop"));
            }
            case "PHOTO" -> o.add(new Opt("Mình không chụp được ảnh", "nophoto"));
            case "FEEL" -> {
                o.add(new Opt("Dễ", "easy"));
                o.add(new Opt("Vừa sức", "ok"));
                o.add(new Opt("Khó", "hard"));
            }
            case "SUBMIT" -> {
                o.add(new Opt("Mình nộp rồi", "yes"));
                o.add(new Opt("Chưa nộp", "no"));
            }
            case "END" -> o.add(new Opt("Kiểm tra lại nhiệm vụ", "restart"));
            default -> {
            }
        }
        return o;
    }

    // ------------------------------------------------------------------
    // 3. CÁC CÚ HÍCH VÀ LỜI NÓI
    // ------------------------------------------------------------------
    public static String greeting(String name) {
        return "Chào " + name + "! Mình là Nudge, người bạn đồng hành giúp bạn bắt đầu bài vở và nộp đúng hẹn. "
                + "Mình không giải bài, không chấm điểm, không phạt. Mình chỉ trò chuyện để cùng bạn tiến bộ mỗi ngày nhé 💪";
    }

    public static String noTasks() {
        return "Hiện chưa có nhiệm vụ nào do ban cán sự nhập cho hôm nay. Khi có nhiệm vụ mới, bạn bấm nút bên dưới để mình kiểm tra lại nhé.";
    }

    public static String allDone() {
        return "Bạn đã hoàn thành các nhiệm vụ hiện có. Bạn có thể kiểm tra lại khi ban cán sự thêm nhiệm vụ mới.";
    }

    /** Danh sách nhiệm vụ còn lại, gần hạn nhất ở trên. */
    public static String taskList(List<TaskProgress> pending, int total, boolean first) {
        StringBuilder sb = new StringBuilder();
        sb.append(first ? "Hôm nay lớp có " + total + " nhiệm vụ, bạn còn " + pending.size() + " nhiệm vụ chưa xong:\n"
                : "Bạn còn " + pending.size() + " nhiệm vụ chưa xong, mình cùng làm tiếp nhé:\n");
        int i = 1;
        for (TaskProgress p : pending) {
            sb.append(i++).append(". ").append(label(p.task)).append(" - hạn ").append(p.task.deadline.format(DM))
                    .append(" (").append(timeLeft(p.task)).append(")\n");
        }
        return sb.toString().trim();
    }

    public static String resumeDoing(Task t) {
        return "Bạn đang làm dở \"" + label(t) + "\". Xong thì bấm nút bên dưới để báo mình nhé.";
    }

    /** Cú hích theo "điều khiến học sinh chưa bắt đầu". */
    public static String barrierNudge(String value) {
        return switch (value) {
            case "forget" -> "Bạn có thể đặt nhắc giờ hoặc ghi chú ngắn để dễ nhớ nhiệm vụ.";
            case "big" -> "Bài nhiều thì chia nhỏ nhé. Thử làm một câu hoặc 5 phút trước.";
            case "start" ->
                "Hãy đọc câu đầu tiên và ghi ra những gì đề bài cho. Nếu cần, bạn có thể xem hướng dẫn từ các nguồn học tập bên dưới.";
            case "phone" -> "Nếu điện thoại làm bạn phân tâm, thử tắt thông báo trong lúc học nhé.";
            case "tired" -> "Nếu đang mệt, bạn có thể nghỉ ngắn và chọn giờ tiếp tục.";
            case "other" -> "Bạn có thể thử một bước nhỏ hoặc điều chỉnh giờ bắt đầu.";
            default -> "Bạn đã có kế hoạch. Giờ mình chọn nơi học phù hợp nhé.";
        };
    }

    public static String unknownWhen() {
        return "Chưa chọn được giờ cũng không sao. Bạn có thể chọn một mốc dự kiến và đổi lại khi cần.";
    }

    /** Học sinh chọn giờ bắt đầu sát hoặc sau hạn nộp. */
    public static String tooLate(Task t) {
        return "Giờ đó đã sát hoặc sau hạn nộp (" + t.deadline.format(DM)
                + ") mất rồi. Bạn chọn mốc sớm hơn để còn kịp làm và nộp nhé.";
    }

    public static String noMorePostpone(Task t) {
        return "Hoãn thêm nữa là sát hạn nộp (" + t.deadline.format(DM)
                + ") mất rồi. Mình cùng bắt đầu ngay nhé, chỉ cần 5 phút đầu thôi.";
    }

    public static String planSummary(Task t, TaskProgress p) {
        String where = p.place == null ? "" : ", tại " + p.place.toLowerCase();
        return "Kế hoạch của bạn: làm \"" + label(t) + "\" lúc " + p.plannedLabel + where
                + ". Hạn nộp là " + t.deadline.format(DM) + " (" + timeLeft(t)
                + "). Đến giờ đó, bạn chỉ cần ngồi vào chỗ và mở bài ra là xong bước đầu tiên.";
    }

    public static String waiting() {
        return "Mình sẽ nhắc bạn đúng giờ. Bạn nhớ để trang này mở nhé.";
    }

    public static String reminder(Task t, TaskProgress p) {
        String base = reminderBase(t, p);
        if (!p.paused && p.prep != null && !p.prep.isBlank())
            base += " Cam kết của bạn: \"" + p.prep + "\". Mình cùng làm nhé!";
        return base;
    }

    /** Hoãn lần đầu xong: nhắc lại giờ, hạn nộp và việc nhỏ đã hứa. */
    public static String committed(Task t, TaskProgress p, boolean last) {
        if (last)
            return "Mình ghi nhận cam kết của bạn: \"" + p.prep + "\". Đến " + p.plannedLabel + " mình cùng làm \""
                    + label(t) + "\" nhé. Mình tin bạn!";
        return "Chốt nhé: \"" + p.prep + "\". Đến " + p.plannedLabel + " mình cùng làm \"" + label(t) + "\" ("
                + timeLeft(t) + " đến hạn nộp).";
    }

    private static String reminderBase(Task t, TaskProgress p) {
        if (p.paused)
            return pick(
                    "Hết giờ nghỉ rồi! Mình cùng quay lại với \"" + label(t) + "\" nhé.",
                    "Nghỉ đủ rồi, mình quay lại thôi: \"" + label(t) + "\" (" + timeLeft(t)
                            + " đến hạn nộp). Làm tiếp từ chỗ đã dừng nhé!");
        return pick(
                "Đến giờ rồi! Mình cùng bắt đầu \"" + label(t) + "\" nhé, chỉ 2 câu đầu thôi.",
                "\"" + label(t) + "\" đang chờ bạn (" + timeLeft(t)
                        + " đến hạn nộp). Mở bài ra và làm phần đầu tiên nhé!");
    }

    public static String manyPostpones(int n) {
        return "Bạn đã đổi giờ vài lần. Nếu muốn, mình thử 5 phút đầu hoặc nhờ hỗ trợ nhé.";
    }

    // ---------------- khi học sinh bấm hoãn ----------------

    public static boolean isReason(String v) {
        return "hard".equals(v) || "tired".equals(v) || "busy".equals(v) || "bored".equals(v) || "other".equals(v);
    }

    public static String reasonLabel(String code) {
        return switch (code) {
            case "hard" -> "Bài khó";
            case "tired" -> "Đang mệt";
            case "busy" -> "Bận việc khác";
            case "bored" -> "Chán, chưa muốn làm";
            case "other" -> "Lý do khác";
            default -> code;
        };
    }

    /**
     * Tin nhắn NGAY khi học sinh vừa bấm hoãn: đồng cảm, nói rõ hạn nộp, rồi hỏi lý
     * do.
     */
    public static String postponeAsk(Task t, TaskProgress p) {
        return "Bạn muốn hoãn vì lý do gì? Hạn nộp: " + t.deadline.format(DM) + ".";
    }

    /**
     * Cảnh báo mạnh khi học sinh sắp dùng lượt hoãn/nghỉ CUỐI CÙNG của nhiệm vụ.
     */
    public static String lastWarning(Task t, boolean pause) {
        return "Bạn sắp hết lượt hoãn/nghỉ. Hãy chọn giờ quay lại phù hợp nhé. "
                + "(Hạn nộp " + t.deadline.format(DM) + ", " + timeLeft(t) + ".)";
    }

    /** Câu hỏi lý do sau lời cảnh báo và câu truyền động lực ở lượt cuối. */
    public static String lastWhy() {
        return "Nếu vẫn cần hẹn lại lần cuối này, cho mình biết điều gì đang cản bạn nhé.";
    }

    /** Học sinh bấm tạm dừng: hỏi lý do trước. */
    public static String pauseAsk(Task t, TaskProgress p) {
        return "Bạn muốn tạm nghỉ vì lý do gì?";
    }

    public static String pauseReply(String reason) {
        return switch (reason) {
            case "hard" -> action("pause-hard",
                    "Bí ở một câu không có nghĩa là bí cả bài. Khoanh đúng chỗ chưa hiểu, xem một ví dụ tương tự trên VietJack hoặc YouTube, rồi tự thử lại trong 5 phút. Mình để nguồn tham khảo bên dưới nhé.",
                    "Mình thử gỡ nút thắt thay vì bỏ cả bài nhé: chọn một câu, ghi điều đã biết và điều cần tìm. Nếu vẫn vướng, mở hướng dẫn liên quan rồi tự làm lại bước đầu.",
                    "Bạn không phải giải ngay mọi câu. Thử đọc lời giải mẫu của đúng dạng đang bí, đóng tab và tự viết bước đầu. Nếu vẫn chưa hiểu, đánh dấu để hỏi thầy cô hoặc bạn bè.");
            case "tired" -> action("pause-tired",
                    "Bạn mệt thì nghỉ thật sự cũng được. Uống nước, đi lại một chút, chọn giờ quay lại. Khi trở lại, chỉ cần đọc một yêu cầu đầu tiên.",
                    "Đừng ép mình làm cả bài khi đã mệt. Hẹn một khoảng nghỉ vừa đủ; trước khi nghỉ, đánh dấu chỗ đang làm để không phải bắt đầu lại từ đầu.",
                    "Mệt không đồng nghĩa với thiếu cố gắng. Nếu muốn, bạn nghỉ ngắn rồi quay lại với một câu dễ trước nhé.");
            case "busy" -> action("pause-busy",
                    "Có việc cần xử lý thì cứ chọn giờ quay lại thực tế. Hãy đánh dấu câu đang dở; sau đó bạn chỉ việc mở đúng chỗ và tiếp tục.",
                    "Mình không cần hứa làm thật nhiều. Trước khi đi, ghi lại bước tiếp theo bằng một dòng; lúc quay lại sẽ đỡ mất đà.");
            case "bored" -> action("pause-bored",
                    "Đang mất tập trung à? Thử một cuộc hẹn với chính mình: đóng tab gây xao nhãng, đặt 2 phút và chỉ gạch chân yêu cầu đầu tiên. Hết 2 phút bạn quyết định tiếp.",
                    "Không cần ép mình tập trung cả tiếng. Bạn thử 2 phút không thông báo, làm một thao tác nhỏ với đề. Có đà rồi hãy tăng thời gian.",
                    "Thử đổi nhịp một chút: đứng dậy, uống nước, rồi quay lại với đúng một câu. Nếu vẫn khó tập trung, chọn nghỉ có hẹn giờ nhé.");
            default -> action("pause-other",
                    "Mình chưa cần làm hết ngay. Bạn chọn một thao tác nhỏ có thể thực hiện trong 2 phút, hoặc nghỉ và hẹn một giờ quay lại phù hợp.",
                    "Nếu muốn nghỉ, hãy đánh dấu chỗ đang làm. Khi quay lại, mình tiếp tục từ đúng bước đó nhé.");
        };
    }

    /**
     * Phản hồi sau khi muốn hoãn: nhắc hành động nhỏ và cho học sinh quyền lựa
     * chọn.
     */
    public static String postponeReply(String reason, TaskProgress p) {
        return switch (reason) {
            case "hard" -> action("postpone-hard",
                    "Bài khó có thể chia nhỏ: thử 5 phút làm 1 câu dễ nhất, hoặc ghi chính xác chỗ chưa hiểu để nhờ hỗ trợ. Bạn chọn thử ngay hay đổi giờ?",
                    "Thay vì làm cả bài, thử khoanh 1 câu, viết dữ kiện và bước đầu tiên trong 5 phút. Bạn muốn thử không?");
            case "tired" -> action("postpone-tired",
                    "Nếu đang mệt, bạn có thể nghỉ ngắn rồi hẹn giờ quay lại. Khi bắt đầu, chỉ cần đọc 1 câu đầu tiên.",
                    "Bạn có thể chọn nghỉ 10 phút, uống nước và quay lại vào giờ đã hẹn. Không cần ép mình làm ngay.");
            case "busy" -> action("postpone-busy",
                    "Bạn có việc khác thì chọn một giờ bắt đầu khả thi. Trước lúc đó, để sẵn đề và đánh dấu phần cần làm.",
                    "Thử xác định thời gian mình rảnh lại và hẹn giờ. Khi quay về, bắt đầu bằng 1 câu đã chọn trước.");
            case "bored" -> action("postpone-bored",
                    "Thử chỉ 2 phút: tắt thông báo, mở đề và gạch chân yêu cầu đầu tiên. Sau đó bạn có thể quyết định có tiếp tục.",
                    "Không cần ép mình học thật lâu: thử mở đề, đọc 1 câu trong 2 phút. Bạn chọn bắt đầu ngay hay hẹn lại?");
            default -> action("postpone-other",
                    "Bạn muốn thử mở đề và làm 1 bước trong 5 phút, hay chọn giờ bắt đầu phù hợp hơn?",
                    "Thử chia việc còn lại thành bước nhỏ nhất: đọc đề, chọn 1 câu, rồi viết dòng đầu tiên.");
        };
    }

    /**
     * Sau khi chọn thời gian hoãn: nhắc lại giờ, và cho biết sau lần hoãn còn bao
     * nhiêu thời gian.
     */
    public static String postponed(Task t, TaskProgress p, int minutes) {
        long left = Duration.between(p.plannedAt, t.deadline).toMinutes();
        String conLai = left > 0 ? " Sau lần hoãn này bạn còn khoảng " + human(left) + " để làm bài." : "";
        return "Ok, mình sẽ nhắc lại lúc " + p.plannedLabel + " (" + human(minutes) + " nữa)." + conLai
                + " Lúc đó bạn chỉ cần mở bài ra và làm 2 câu đầu thôi nhé.";
    }

    /** Nhiệm vụ đã quá hạn nộp: không cho hoãn nữa. */
    public static String overdueNoPostpone(Task t) {
        return "Nhiệm vụ đã quá hạn (" + t.deadline.format(DM)
                + "). Bạn xem lại yêu cầu nộp và có thể nhờ hỗ trợ nếu cần.";
    }

    /** Chọn giờ bắt đầu quá xa trong khi nhiệm vụ đã quá hạn. */
    public static String overdueStartNow(Task t) {
        return "Bài này đã quá hạn nộp (" + t.deadline.format(DM)
                + ") rồi, nên mình hẹn bắt đầu trong 15 phút nữa thôi nhé. Bạn chọn mốc gần hơn giúp mình.";
    }

    /** Hết số lần tạm dừng. */
    public static String pauseLimit(int n) {
        return "Bạn đã tạm nghỉ vài lần. Mình có thể chọn một bước nhỏ để làm tiếp. "
                + "Mệt hoặc bí quá thì bạn bấm \"Mình cần được giúp\" nhé.";
    }

    public static String pauseTooLong() {
        return "Nghỉ lâu dễ quên bài lắm. Bạn chọn mốc ngắn hơn nhé.";
    }

    /** Lần hoãn cuối: hỏi cam kết bằng một câu. */
    public static String commitAsk(Task t, TaskProgress p, long minutes, boolean last, boolean pause) {
        String what = pause ? "nghỉ" : "hoãn";
        String cam = "Bạn chọn một việc cụ thể trong " + human(minutes) + " " + what
                + " này và bước đầu khi quay lại với \"" + label(t) + "\" nhé. ";
        if (last)
            return "Ok, mình hẹn lại lúc " + p.plannedLabel + " (" + human(minutes) + " nữa). Đây là lượt " + what
                    + " cuối nên mình hẹn ngắn thôi. "
                    + cam
                    + "Bạn gõ vài chữ, ví dụ: \"Mình uống nước 5 phút rồi làm 3 câu đầu\". Muốn làm luôn thì bấm \"Thôi, mình làm luôn\".";
        return "Ok, mình hẹn lại lúc " + p.plannedLabel + " (" + human(minutes) + " nữa). " + cam
                + "Bạn bấm chọn nhanh một việc bên dưới hoặc tự gõ nhé.";
    }

    public static String otherAsk() {
        return "Bạn gõ lý do của mình vào ô bên dưới nhé (viết ngắn thôi cũng được).";
    }

    public static String otherShort() {
        return "Bạn viết rõ hơn một chút giúp mình nhé, ví dụ: \"Mình đang chờ bạn gửi file\".";
    }

    public static String commitShort() {
        return "Mình cần một hành động cụ thể hơn 'cố lên' nhé. Ví dụ: 'Mình uống nước 5 phút, rồi mở đề làm câu 1'.";
    }

    public static String helpNoted() {
        return "Mình đã ghi nhận yêu cầu hỗ trợ. Trong lúc chờ, nếu muốn, bạn có thể làm phần dễ trước.";
    }

    /** Các mục nhật ký lý do hoãn dạng "hard@21,tired@22" -> [mã, giờ]. */
    public static java.util.List<String[]> parsePostponeLog(String log) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        if (log == null || log.isBlank())
            return out;
        for (String e : log.split(",")) {
            String[] x = e.trim().split("@");
            if (x.length == 2 && isReason(x[0]))
                out.add(x);
        }
        return out;
    }

    /**
     * Tóm tắt cho trang quản trị: "Bài khó ×2, Đang mệt ×1 · cần hỗ trợ" ("" nếu
     * chưa hoãn lần nào).
     */
    public static String postponeText(TaskProgress p) {
        if (p == null)
            return "";
        java.util.Map<String, Integer> cnt = new java.util.LinkedHashMap<>();
        for (String code : new String[] { "hard", "tired", "busy", "bored", "other" })
            cnt.put(code, 0);
        for (String[] e : parsePostponeLog(p.postponeLog))
            cnt.merge(e[0], 1, Integer::sum);
        StringBuilder sb = new StringBuilder();
        for (var e : cnt.entrySet())
            if (e.getValue() > 0)
                sb.append(sb.length() == 0 ? "" : ", ").append(reasonLabel(e.getKey())).append(" ×")
                        .append(e.getValue());
        if (p.otherReasons != null && !p.otherReasons.isBlank())
            sb.append(" (khác: ").append(p.otherReasons).append(")");
        if (p.needHelp)
            sb.append(sb.length() == 0 ? "" : " · ").append("cần hỗ trợ");
        return sb.toString();
    }

    /** Nhắc khi sắp đến hạn mà chưa xong. */
    public static String deadlineSoon(Task t, boolean started) {
        return "⏰ Nhiệm vụ \"" + label(t) + "\" còn chưa đầy 1 tiếng là đến hạn (" + t.deadline.format(DM)
                + "). Bạn kiểm tra tiến độ và nộp bài nếu đã hoàn thành nhé.";
    }

    public static String doing() {
        return "Bắt đầu nhé! Cam kết tập trung không sao nhãng!";
    }

    public static String stopped() {
        return "Không sao, nghỉ một chút cũng được, ai làm bài lâu cũng cần nghỉ.";
    }

    /** Đã chọn giờ làm tiếp sau khi tạm dừng. */
    public static String pauseSummary(Task t, TaskProgress p) {
        return "Ok, bạn nghỉ đến " + p.plannedLabel + ". Đến giờ đó mình sẽ nhắc bạn làm tiếp \"" + label(t)
                + "\" (hạn nộp "
                + t.deadline.format(DM) + ", " + timeLeft(t)
                + "). Bạn nhớ để trang này mở nhé, nếu đóng trang mình sẽ nhắc qua thông báo hoặc email.";
    }

    /** Gần hạn nộp quá nên không hẹn nghỉ thêm. */
    public static String noPause(Task t) {
        return "Sắp đến hạn nộp (" + t.deadline.format(DM) + ", " + timeLeft(t)
                + ") rồi nên mình không hẹn nghỉ dài nữa. Bạn làm tiếp cho xong rồi bấm nút bên dưới nhé.";
    }

    public static String keepGoing() {
        return "Được, mình tiếp tục từ bước nhỏ nhất. Xong bài thì bấm nút hoàn thành nhé.";
    }

    /** Học sinh quay lại làm tiếp sau khi tạm dừng. */
    public static String resumed() {
        return pick(
                "Chào mừng bạn quay lại! Bạn đã làm được một phần rồi, giờ chỉ cần làm tiếp thôi. Xong thì bấm nút bên dưới nhé.",
                "Nghỉ khỏe rồi, mình làm tiếp nhé! Cứ tiếp tục từ chỗ bạn đã dừng lại. Xong thì bấm nút bên dưới để báo mình.");
    }

    public static String proofShort() {
        return "Bạn kể thêm một chút nhé, chỉ cần một câu ngắn thôi.";
    }

    public static String fastDone() {
        return "Bạn làm nhanh thật! Nếu còn câu nào làm chưa kỹ thì kiểm tra lại một lượt trước khi nộp nhé.";
    }

    public static String cheer(String feel) {
        return switch (feel) {
            case "easy" -> "Nghe có vẻ không khó với bạn. Giỏi lắm!";
            case "hard" ->
                "Dù khó mà bạn vẫn làm tới cùng, thật đáng khen! Phần nào chưa hiểu, bạn có thể hỏi cô hoặc bạn bè nhé.";
            default -> "Làm tới nơi là điều không dễ. Mình rất ghi nhận bạn!";
        };
    }

    public static String submitted() {
        return "Đã ghi nhận bạn báo nộp sớm là nhẹ đầu nhất. Ban cán sự sẽ xác nhận bài của bạn.";
    }

    public static String submitNudge(Task t) {
        return "Bạn nhớ nộp cho ban cán sự trước " + t.deadline.format(DM) + " (" + timeLeft(t)
                + ") nhé. Nộp ngay bây giờ thì khỏi phải nhớ nữa!";
    }

    // ------------------------------------------------------------------
    // 4. TÊN HIỂN THỊ CỦA TỪNG BƯỚC (dùng ở trang người quản trị)
    // ------------------------------------------------------------------
    public static String stepLabel(String step) {
        if (step == null)
            return "";
        return switch (step) {
            case "NO_TASK" -> "Kiểm tra lại nhiệm vụ";
            case "PICK" -> "Chọn nhiệm vụ";
            case "WHEN", "WHEN2" -> "Khi nào bắt đầu?";
            case "TIME" -> "Giờ bắt đầu cụ thể";
            case "BARRIER", "BARRIER_OTHER" -> "Điều khiến chưa bắt đầu";
            case "PLACE", "PLACE_OTHER" -> "Nơi làm bài";
            case "WAIT" -> "Bắt đầu sớm hơn giờ hẹn";
            case "DUE" -> "Phản hồi khi được nhắc";
            case "DOING" -> "Tình trạng làm bài";
            case "PAUSE", "PAUSE_TIME" -> "Tạm dừng: hẹn giờ làm tiếp";
            case "PAUSE_WHY" -> "Lý do muốn tạm dừng";
            case "PAUSE_OTHER" -> "Lý do tạm dừng khác (tự gõ)";
            case "POSTPONE_WHY" -> "Lý do muốn hoãn";
            case "POSTPONE_OTHER" -> "Lý do hoãn khác (tự gõ)";
            case "POSTPONE_PLAN" -> "Cách xử lý khi hoãn";
            case "POSTPONE_COMMIT" -> "Cam kết khi hoãn lần cuối";
            case "PROOF" -> "Học sinh kể đã làm gì";
            case "FEEL" -> "Cảm nhận về nhiệm vụ";
            case "SUBMIT" -> "Đã nộp cho ban cán sự chưa?";
            case "END" -> "Kết thúc";
            case "PHOTO" -> "Gửi ảnh minh chứng";
            default -> step;
        };
    }
}