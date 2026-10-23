package vn.ainudge.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.ainudge.model.Student;
import vn.ainudge.repo.StudentRepository;

/**
 * "Ghi nhớ đăng nhập" giống Facebook / Zalo.
 *
 * Sau khi đăng nhập, trình duyệt giữ một "thẻ" (cookie) có chữ ký, hạn 1 năm và tự gia hạn mỗi lần vào lại.
 * Lần sau mở web (kể cả khi đã tắt trình duyệt hoặc máy chủ khởi động lại) thì vào thẳng, không phải nhập lại.
 * Chỉ khi bấm "Đăng xuất" (hoặc người quản trị đổi mật khẩu của em đó) thì mới phải đăng nhập lại.
 *
 * Thẻ không chứa mật khẩu, chỉ chứa mã học sinh + hạn + chữ ký bí mật nên không thể làm giả.
 * Có 3 thẻ riêng: học sinh (ainudge_s), người quản trị (ainudge_t), mở khóa PIN ban cán sự (ainudge_m).
 */
@Service
public class RememberService {
    private static final String STUDENT = "ainudge_s", ADMIN = "ainudge_t", MONITOR = "ainudge_m";
    private static final long MAX_AGE = 365L * 24 * 3600;   // 1 năm, tự gia hạn mỗi lần vào lại

    private final StudentRepository students;
    private final byte[] key;
    private final AppCredentialService credentials;

    public RememberService(StudentRepository students,
                           @Value("${ainudge.remember-secret:}") String secret,
                           @Value("${ainudge.teacher-password}") String adminPassword,
                           AppCredentialService credentials) {
        this.students = students;
        this.credentials = credentials;
        String base = secret == null || secret.isBlank() ? "ainudge:" + adminPassword : secret.trim();
        this.key = base.getBytes(StandardCharsets.UTF_8);
    }

    // ---------------- ghi nhớ khi đăng nhập ----------------

    public void rememberStudent(HttpServletRequest req, HttpServletResponse res, Student s) {
        set(req, res, STUDENT, String.valueOf(s.id), studentSalt(s));
    }

    public void rememberAdmin(HttpServletRequest req, HttpServletResponse res) {
        set(req, res, ADMIN, "admin", credentials.cookieSalt("admin"));
    }

    public void rememberMonitor(HttpServletRequest req, HttpServletResponse res, Student s) {
        set(req, res, MONITOR, String.valueOf(s.id), credentials.cookieSalt("monitor"));
    }

    // ---------------- quên khi đăng xuất ----------------

    public void forgetAll(HttpServletResponse res) {
        clear(res, STUDENT);
        clear(res, ADMIN);
        clear(res, MONITOR);
    }

    public void forgetStudent(HttpServletResponse res) {
        clear(res, STUDENT);
        clear(res, MONITOR);
    }

    // ---------------- khôi phục đăng nhập khi mở lại web ----------------

    /** Nếu phiên đã mất (tắt trình duyệt, khởi động lại máy chủ...) mà thẻ còn hợp lệ thì đăng nhập lại tự động. */
    public void restore(HttpServletRequest req, HttpServletResponse res) {
        HttpSession ses = req.getSession(false);
        boolean hasStudent = ses != null && ses.getAttribute("studentId") != null;
        boolean hasAdmin = ses != null && ses.getAttribute("teacher") != null;

        if (!hasStudent) {
            String id = read(req, STUDENT, this::studentSaltById);
            Student s = id == null ? null : students.findById(Long.parseLong(id)).orElse(null);
            if (s != null) {
                ses = req.getSession(true);
                ses.setAttribute("studentId", s.id);
                hasStudent = true;
                rememberStudent(req, res, s);      // gia hạn thêm 1 năm
            }
        }
        if (!hasAdmin && read(req, ADMIN, x -> credentials.cookieSalt("admin")) != null) {
            ses = req.getSession(true);
            ses.setAttribute("teacher", true);
            rememberAdmin(req, res);
        }
        if (hasStudent && ses != null && ses.getAttribute("monitorOk") == null) {
            String id = read(req, MONITOR, x -> credentials.cookieSalt("monitor"));
            if (id != null && id.equals(String.valueOf(ses.getAttribute("studentId")))) ses.setAttribute("monitorOk", true);
        }
    }

    // ---------------- chi tiết kỹ thuật ----------------

    /** Mật khẩu của học sinh nằm trong chữ ký: người quản trị đổi mật khẩu thì thẻ cũ hết hiệu lực. */
    private static String studentSalt(Student s) { return s.password == null ? "" : s.password; }

    private String studentSaltById(String id) {
        try {
            return students.findById(Long.parseLong(id)).map(RememberService::studentSalt).orElse(null);
        } catch (NumberFormatException e) { return null; }
    }

    private String sign(String kind, String id, long exp, String salt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] raw = mac.doFinal((kind + "|" + id + "|" + exp + "|" + salt).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Trả về mã (id) trong thẻ nếu thẻ còn hạn và chữ ký đúng, ngược lại trả về null. */
    private String read(HttpServletRequest req, String kind, Function<String, String> saltFor) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (!kind.equals(c.getName()) || c.getValue() == null) continue;
            String[] p = c.getValue().split("\\.", 3);
            if (p.length != 3) return null;
            long exp;
            try { exp = Long.parseLong(p[1]); } catch (NumberFormatException e) { return null; }
            if (exp < Instant.now().getEpochSecond()) return null;
            String salt = saltFor.apply(p[0]);
            if (salt == null) return null;
            byte[] want = sign(kind, p[0], exp, salt).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(want, p[2].getBytes(StandardCharsets.UTF_8)) ? p[0] : null;
        }
        return null;
    }

    private void set(HttpServletRequest req, HttpServletResponse res, String kind, String id, String salt) {
        long exp = Instant.now().getEpochSecond() + MAX_AGE;
        String value = id + "." + exp + "." + sign(kind, id, exp, salt);
        res.addHeader("Set-Cookie", kind + "=" + value + "; Max-Age=" + MAX_AGE + "; Path=/; HttpOnly; SameSite=Lax"
            + (req.isSecure() ? "; Secure" : ""));
    }

    private void clear(HttpServletResponse res, String kind) {
        res.addHeader("Set-Cookie", kind + "=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax");
    }
}
