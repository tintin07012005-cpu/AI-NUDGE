package vn.ainudge.web;

import jakarta.servlet.http.HttpSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.FileSystemResource;
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
import vn.ainudge.repo.TaskImageRepository;
import vn.ainudge.service.ProofService;

/** Xem ảnh minh chứng (chỉ người quản trị, ban cán sự đã nhập PIN và chính học sinh gửi ảnh) và ảnh đề bài (mọi người đã đăng nhập). */
@Controller
public class ProofController {
    private final ProofImageRepository images;
    private final StudentRepository students;
    private final ProofService proof;
    private final TaskImageRepository taskImages;

    public ProofController(ProofImageRepository images, StudentRepository students, ProofService proof,
                           TaskImageRepository taskImages) {
        this.images = images;
        this.taskImages = taskImages;
        this.students = students;
        this.proof = proof;
    }

    @GetMapping("/proof/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id, HttpSession session) {
        ProofImage im = images.findById(id).orElse(null);
        if (im == null) return ResponseEntity.notFound().build();

        boolean ok = session.getAttribute("teacher") != null;
        if (!ok && session.getAttribute("studentId") != null) {
            Student me = students.findById((Long) session.getAttribute("studentId")).orElse(null);
            if (me != null) {
                boolean owner = im.progress.student.id.equals(me.id);
                boolean monitor = me.monitor && session.getAttribute("monitorOk") != null;
                ok = owner || monitor;
            }
        }
        if (!ok) return ResponseEntity.status(403).build();

        Path p = proof.path(im);
        if (!Files.exists(p)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(im.contentType))
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
            .header("X-Content-Type-Options", "nosniff")
            .body(new FileSystemResource(p));
    }

    /** Ảnh đề bài do ban cán sự gửi: mọi học sinh đã đăng nhập và người quản trị đều xem được. */
    @GetMapping("/task-image/{id}/file")
    public ResponseEntity<Resource> taskFile(@PathVariable Long id, HttpSession session) {
        TaskImage im = taskImages.findById(id).orElse(null);
        if (im == null) return ResponseEntity.notFound().build();

        boolean ok = session.getAttribute("teacher") != null;
        if (!ok && session.getAttribute("studentId") != null)
            ok = students.existsById((Long) session.getAttribute("studentId"));
        if (!ok) return ResponseEntity.status(403).build();

        Path p = proof.path(im);
        if (!Files.exists(p)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(im.contentType))
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
            .header("X-Content-Type-Options", "nosniff")
            .body(new FileSystemResource(p));
    }
}
