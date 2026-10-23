package vn.ainudge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import vn.ainudge.model.PushSub;
import vn.ainudge.model.Student;
import vn.ainudge.repo.PushSubRepository;

/**
 * Gửi lời nhắc ra ngoài trang web: thông báo đẩy của trình duyệt (kể cả khi đã tắt web),
 * và email (dự phòng khi học sinh chưa bật thông báo đẩy, hoặc luôn gửi nếu ainudge.mail.always=true).
 */
@Service
public class NotifyService {
    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    public record Sent(int push, boolean mail) { }

    private final PushSubRepository subs;
    private final ObjectProvider<JavaMailSender> mail;
    private final ObjectMapper json;

    private final String vapidPublic;
    private final String vapidPrivate;
    private final String vapidSubject;
    private final String mailFrom;
    private final String publicUrl;
    private final boolean mailAlways;

    private PushService push;     // null nếu chưa cấu hình khóa

    public NotifyService(PushSubRepository subs, ObjectProvider<JavaMailSender> mail, ObjectMapper json,
                         @Value("${ainudge.vapid-public:}") String vapidPublic,
                         @Value("${ainudge.vapid-private:}") String vapidPrivate,
                         @Value("${ainudge.vapid-subject:mailto:admin@example.com}") String vapidSubject,
                         @Value("${ainudge.mail.from:}") String mailFrom,
                         @Value("${ainudge.public-url:}") String publicUrl,
                         @Value("${ainudge.mail.always:false}") boolean mailAlways) {
        this.subs = subs;
        this.mail = mail;
        this.json = json;
        this.vapidPublic = vapidPublic.trim();
        this.vapidPrivate = vapidPrivate.trim();
        this.vapidSubject = vapidSubject.trim();
        this.mailFrom = mailFrom.trim();
        this.publicUrl = publicUrl.trim();
        this.mailAlways = mailAlways;
    }

    @PostConstruct
    void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) Security.addProvider(new BouncyCastleProvider());
        if (mail.getIfAvailable() == null) log.warn("Chưa cấu hình spring.mail.*: sẽ KHÔNG gửi được email nhắc nhở.");
        else log.info("Email nhắc nhở đã được cấu hình (gửi từ {}).", mailFrom.isEmpty() ? "tài khoản mặc định" : mailFrom);
        if (vapidPublic.isEmpty() || vapidPrivate.isEmpty()) {
            log.warn("Chưa cấu hình ainudge.vapid-public / vapid-private: thông báo đẩy sẽ không hoạt động.");
            return;
        }
        try {
            push = new PushService(vapidPublic, vapidPrivate, vapidSubject);
        } catch (Exception e) {
            log.warn("Khóa thông báo đẩy không hợp lệ: {}", e.toString());
        }
    }

    /** Khóa công khai để trình duyệt đăng ký nhận thông báo đẩy ("" nếu chưa cấu hình). */
    public String publicKey() { return push == null ? "" : vapidPublic; }

    /** Lưu thiết bị vừa bật thông báo đẩy của học sinh. */
    public boolean subscribe(Student s, String endpoint, String p256dh, String auth) {
        if (endpoint == null || p256dh == null || auth == null) return false;
        if (endpoint.isBlank() || p256dh.isBlank() || auth.isBlank() || endpoint.length() > 1000) return false;
        PushSub sub = subs.findFirstByEndpoint(endpoint).orElseGet(PushSub::new);
        sub.student = s;
        sub.endpoint = endpoint;
        sub.p256dh = p256dh;
        sub.auth = auth;
        if (sub.createdAt == null) sub.createdAt = LocalDateTime.now();
        subs.save(sub);
        return true;
    }

    /**
     * Gửi một lời nhắc cho học sinh.
     * pageOpen = true: học sinh đang mở web (trang tự hiện lời nhắc + chuông) nên không gửi thông báo đẩy nữa, chỉ gửi email.
     */
    public Sent send(Student s, String title, String body, boolean pageOpen) {
        int ok = 0;
        if (!pageOpen) for (PushSub sub : subs.findByStudent_Id(s.id)) if (sendPush(sub, title, body)) ok++;
        boolean mailed = false;
        boolean hasEmail = s.email != null && !s.email.isBlank();
        if (hasEmail && (ok == 0 || mailAlways)) mailed = sendMail(s.email.trim(), title, body);
        return new Sent(ok, mailed);
    }

    /** Email chào mừng gửi ngầm khi học sinh đăng ký / lưu email, để biết ngay email có chạy không. */
    public void welcomeAsync(Student s) {
        if (s == null || s.email == null || s.email.isBlank()) return;
        String to = s.email.trim();
        String name = s.name;
        CompletableFuture.runAsync(() -> sendMail(to, "✅ AI-Nudge đã bật nhắc nhở qua email",
            "Chào " + name + "! Từ giờ Nudge sẽ nhắc bạn qua email khi đến giờ làm bài và khi sắp đến hạn nộp. Chúc bạn học tốt!"));
    }

    private boolean sendPush(PushSub sub, String title, String body) {
        if (push == null) return false;
        try {
            byte[] payload = json.writeValueAsString(Map.of("title", title, "body", body, "url", "/student"))
                .getBytes(StandardCharsets.UTF_8);
            var res = push.send(new Notification(sub.endpoint, sub.p256dh, sub.auth, payload));
            int code = res.getStatusLine().getStatusCode();
            if (code == 404 || code == 410) { subs.delete(sub); return false; }    // thiết bị đã hủy đăng ký
            if (code < 200 || code >= 300) log.warn("Thông báo đẩy bị từ chối, mã {}", code);
            return code >= 200 && code < 300;
        } catch (Exception e) {
            log.warn("Gửi thông báo đẩy lỗi: {}", e.toString());
            return false;
        }
    }

    private boolean sendMail(String to, String title, String body) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) return false;                // chưa cấu hình spring.mail.*
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            if (!mailFrom.isEmpty()) m.setFrom(mailFrom);
            m.setTo(to);
            m.setSubject(title);
            m.setText(body + (publicUrl.isEmpty() ? "" : "\n\nMở AI-Nudge: " + publicUrl));
            sender.send(m);
            return true;
        } catch (Exception e) {
            log.warn("Gửi email lỗi: {}", e.toString());
            return false;
        }
    }
}
