package vn.ainudge;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiNudgeApplication {
    /**
     * Toàn bộ logic hẹn giờ / chuông nhắc dùng LocalDateTime.now(), nên ép máy chủ
     * chạy theo giờ Việt Nam.
     */
    public static final String ZONE = "Asia/Ho_Chi_Minh";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE)); // đặt trước khi Spring khởi động
        SpringApplication.run(AiNudgeApplication.class, args);
    }

    @PostConstruct
    void setZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }
}