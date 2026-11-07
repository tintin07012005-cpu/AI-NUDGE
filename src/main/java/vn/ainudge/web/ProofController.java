package vn.ainudge.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.ainudge.model.ProofImage;
import vn.ainudge.model.Student;
import vn.ainudge.model.TaskImage;
import vn.ainudge.repo.ProofImageRepository;
import vn.ainudge.repo.StudentRepository;
import vn.ainudge.repo.StudyGroupRepository;
import vn.ainudge.repo.TaskImageRepository;
import vn.ainudge.service.ProofService;

/**
 * Xem ảnh minh chứng (chỉ người quản trị, ban cán sự đã nhập PIN và chính học
 * sinh gửi ảnh) và ảnh đề bài (mọi người đã đăng nhập).
 */
@Controller
public class ProofController {
    private final ProofImageRepository images;
    private final StudentRepository students;
    private final ProofService proof;
    private final TaskImageRepository taskImages;
    private final StudyGroupRepository studyGroups;

    public ProofController(ProofImageRepository images, StudentRepository students, ProofService proof,
            TaskImageRepository taskImages, StudyGroupRepository studyGroups) {
        this.studyGroups = studyGroups;
        this.images = images;
        this.taskImages = taskImages;
        this.students = students;
        this.proof = proof;
    }

    @GetMapping("/proof/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id, HttpSession session) {
        ProofImage im = images.findById(id).orElse(null);
        if (im == null)
            return ResponseEntity.notFound().build();

        boolean ok = session.getAttribute("teacher") != null;
        if (!ok && session.getAttribute("studentId") != null) {
            Student me = students.findById((Long) session.getAttribute("studentId")).orElse(null);
            if (me != null) {
                boolean owner = im.progress.student.id.equals(me.id);
                boolean monitor = me.monitor && session.getAttribute("monitorOk") != null;
                boolean leader = !owner && !monitor && studyGroups.findByLeader_Id(me.id).stream()
                        .anyMatch(g -> im.progress.student.studyGroups.stream().anyMatch(x -> x.id.equals(g.id)));
                ok = owner || monitor || leader; // tổ trưởng xem ảnh của tổ viên trong tổ mình
            }
        }
        if (!ok)
            return ResponseEntity.status(403).build();

        byte[] data = proof.bytes(im.fileName, im.cloudUrl);
        if (data == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(im.contentType))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(data));
    }

    /**
     * Ảnh đề bài do ban cán sự gửi: mọi học sinh đã đăng nhập và người quản trị đều
     * xem được.
     */
    @GetMapping("/task-image/{id}/file")
    public ResponseEntity<Resource> taskFile(@PathVariable Long id, HttpSession session, HttpServletRequest req) {
        TaskImage im = taskImages.findById(id).orElse(null);
        if (im == null)
            return ResponseEntity.notFound().build();

        boolean ok = session.getAttribute("teacher") != null;
        if (!ok && session.getAttribute("studentId") != null)
            ok = students.existsById((Long) session.getAttribute("studentId"));
        if (!ok)
            return ResponseEntity.status(403).build();

        // Trang chỉ hỏi loại tệp (HEAD) để biết là ảnh hay Word: trả ngay, không tải tệp về
        if ("HEAD".equals(req.getMethod()))
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(im.contentType))
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                    .build();

        byte[] data = proof.bytes(im.fileName, im.cloudUrl);
        if (data == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(im.contentType))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate()) // ảnh đề không bao giờ đổi nội dung
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(data));
    }

    /**
     * Ảnh thu nhỏ của ảnh đề bài: chuyển thẳng sang Cloudinary (đã thu nhỏ, đổi định dạng nhẹ)
     * nên không phải tải ảnh gốc qua máy chủ Render. Tệp Word hoặc tệp cũ thì dùng đường dẫn tệp gốc.
     */
    @GetMapping("/task-image/{id}/thumb")
    public ResponseEntity<Void> taskThumb(@PathVariable Long id, HttpSession session) {
        TaskImage im = taskImages.findById(id).orElse(null);
        if (im == null)
            return ResponseEntity.notFound().build();

        boolean ok = session.getAttribute("teacher") != null;
        if (!ok && session.getAttribute("studentId") != null)
            ok = students.existsById((Long) session.getAttribute("studentId"));
        if (!ok)
            return ResponseEntity.status(403).build();

        String target = "/task-image/" + id + "/file";
        if (im.cloudUrl != null && im.contentType != null && im.contentType.startsWith("image/")
                && im.cloudUrl.contains("/image/upload/"))
            target = im.cloudUrl.replace("/image/upload/", "/image/upload/c_limit,w_360,q_auto,f_auto/");
        return ResponseEntity.status(302)
                .header("Location", target)
                .header("Cache-Control", "private, max-age=86400")
                .build();
    }
}