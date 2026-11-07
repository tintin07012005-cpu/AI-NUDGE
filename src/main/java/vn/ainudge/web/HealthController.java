package vn.ainudge.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ainudge.repo.StudentRepository;

/**
 * Trang kiểm tra "còn sống": UptimeRobot gọi vào đây để giữ máy chủ Render
 * và cả cơ sở dữ liệu luôn thức (trang chủ thì không chạm tới database).
 */
@RestController
public class HealthController {
    private final StudentRepository students;

    public HealthController(StudentRepository students) {
        this.students = students;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        try {
            students.count();
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            return ResponseEntity.status(503).body("db down");
        }
    }
}
