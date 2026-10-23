package vn.ainudge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiNudgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiNudgeApplication.class, args);
    }
}
