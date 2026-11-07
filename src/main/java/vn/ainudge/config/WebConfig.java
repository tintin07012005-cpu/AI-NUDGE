package vn.ainudge.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Configuration;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.service.RememberService;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Chặn người chưa đăng nhập vào trang học sinh / ban cán sự / người quản trị. */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final StudentRepository students;
    private final RememberService remember;
    /** Nhớ 30 giây là học sinh còn tồn tại, để mỗi lần trang gọi máy chủ không phải hỏi database. */
    private final ConcurrentHashMap<Long, Long> okUntil = new ConcurrentHashMap<>();

    public WebConfig(StudentRepository students, RememberService remember) {
        this.students = students;
        this.remember = remember;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                String path = req.getRequestURI();
                remember.restore(req, res);          // phiên đã mất nhưng còn "thẻ ghi nhớ" -> tự đăng nhập lại
                HttpSession s = req.getSession(false);
                if (path.startsWith("/teacher") && !path.equals("/teacher/login")) {
                    if (s == null || s.getAttribute("teacher") == null) { res.sendRedirect("/teacher/login"); return false; }
                }
                if (path.startsWith("/student") || path.startsWith("/monitor")) {
                    Object sid = s == null ? null : s.getAttribute("studentId");
                    Long sidL = (sid instanceof Long l) ? l : null;
                    long now = System.currentTimeMillis();
                    // chưa đăng nhập, hoặc học sinh đã bị người quản trị xóa khỏi danh sách
                    boolean ok = sidL != null && (now < okUntil.getOrDefault(sidL, 0L) || students.existsById(sidL));
                    if (ok) okUntil.put(sidL, now + 30_000);
                    if (!ok) {
                        if (sidL != null) okUntil.remove(sidL);
                        if (s != null) s.invalidate();
                        remember.forgetStudent(res);
                        if (path.contains("/api/")) res.sendError(401);
                        else res.sendRedirect("/");
                        return false;
                    }
                }
                return true;
            }
        }).addPathPatterns("/teacher/**", "/student/**", "/monitor/**", "/proof/**", "/task-image/**");
    }
}
