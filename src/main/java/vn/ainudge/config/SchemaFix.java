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
 * hay giá trị mặc định của cột đã có. Các lỗi đã gặp:
 * 1) content_type quá ngắn: lỗi "Data too long" khi lưu file .docx.
 * 2) task_progress.reminders_disabled bắt buộc có giá trị nhưng không có
 * mặc định, trong khi entity hiện tại không có cột này: lỗi
 * "Field 'reminders_disabled' doesn't have a default value".
 * 3) task_progress có 2 dòng trùng (cùng task_id + student_id) do 2 request
 * cùng tạo: lỗi "Query did not return a unique result: 2 results were
 * returned". Hibernate không tự thêm được unique constraint khi dữ liệu đã
 * trùng, nên ở đây ta dọn trùng rồi tự thêm constraint.
 *
 * Các câu lệnh chạy lại nhiều lần vẫn an toàn (không mất dữ liệu).
 * Nếu lệnh nào lỗi thì chỉ ghi log, không làm app dừng.
 */
@Component
public class SchemaFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaFix.class);

    private static final String UK_NAME = "uk_progress_task_student";

    private final JdbcTemplate jdbc;

    public SchemaFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        exec("ALTER TABLE task_image MODIFY content_type VARCHAR(120) NOT NULL");
        exec("ALTER TABLE proof_image MODIFY content_type VARCHAR(120) NOT NULL");
        exec("ALTER TABLE task_progress ALTER COLUMN reminders_disabled SET DEFAULT 0");
        fixDuplicateProgress();
    }

    /**
     * Dọn các dòng task_progress trùng rồi thêm unique constraint (task_id,
     * student_id).
     */
    private void fixDuplicateProgress() {
        try {
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE table_schema = DATABASE() AND table_name = 'task_progress' "
                            + "AND constraint_name = ?",
                    Integer.class, UK_NAME);
            if (exists != null && exists > 0) {
                return; // đã có constraint, không cần làm gì
            }

            // 1) Chuyển ảnh minh chứng của dòng trùng sang dòng được giữ lại (id nhỏ nhất)
            exec("UPDATE proof_image pi "
                    + "JOIN task_progress dup ON pi.progress_id = dup.id "
                    + "JOIN (SELECT task_id, student_id, MIN(id) keep_id "
                    + "      FROM task_progress GROUP BY task_id, student_id) k "
                    + "  ON k.task_id = dup.task_id AND k.student_id = dup.student_id "
                    + "SET pi.progress_id = k.keep_id "
                    + "WHERE dup.id <> k.keep_id");

            // 2) Xóa các dòng trùng, giữ dòng id nhỏ nhất
            exec("DELETE dup FROM task_progress dup "
                    + "JOIN (SELECT task_id, student_id, MIN(id) keep_id "
                    + "      FROM task_progress GROUP BY task_id, student_id) k "
                    + "  ON k.task_id = dup.task_id AND k.student_id = dup.student_id "
                    + "WHERE dup.id <> k.keep_id");

            // 3) Thêm unique constraint để không bị trùng nữa
            exec("ALTER TABLE task_progress ADD CONSTRAINT " + UK_NAME
                    + " UNIQUE (task_id, student_id)");
        } catch (Exception e) {
            log.warn("SchemaFix: bo qua fixDuplicateProgress ({})", e.getMessage());
        }
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