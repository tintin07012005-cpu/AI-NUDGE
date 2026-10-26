package vn.ainudge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sửa lệch cấu trúc bảng cho các DB tạo từ phiên bản code khác.
 *
 * spring.jpa.hibernate.ddl-auto=update chỉ thêm bảng/cột mới, không đổi độ dài
 * hay giá trị mặc định của cột đã có. Hai lỗi đã gặp:
 * 1) content_type quá ngắn: lỗi "Data too long" khi lưu file .docx.
 * 2) task_progress.reminders_disabled bắt buộc có giá trị nhưng không có
 * mặc định, trong khi entity hiện tại không có cột này: lỗi
 * "Field 'reminders_disabled' doesn't have a default value".
 *
 * Các câu lệnh chạy lại nhiều lần vẫn an toàn (không mất dữ liệu).
 * Nếu lệnh nào lỗi thì chỉ ghi log, không làm app dừng.
 */
@Component
public class SchemaFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaFix.class);

    private final JdbcTemplate jdbc;

    public SchemaFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        exec("ALTER TABLE task_image MODIFY content_type VARCHAR(120) NOT NULL");
        exec("ALTER TABLE proof_image MODIFY content_type VARCHAR(120) NOT NULL");
        exec("ALTER TABLE task_progress ALTER COLUMN reminders_disabled SET DEFAULT 0");
    }

    private void exec(String sql) {
        try {
            jdbc.execute(sql);
            log.info("SchemaFix: da chay: {}", sql);
        } catch (Exception e) {
            log.warn("SchemaFix: bo qua [{}] ({})", sql, e.getMessage());
        }
    }
}