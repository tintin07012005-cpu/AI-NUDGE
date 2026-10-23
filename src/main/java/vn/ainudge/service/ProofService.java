package vn.ainudge.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.ainudge.model.ProofImage;
import vn.ainudge.model.Student;
import vn.ainudge.model.Task;
import vn.ainudge.model.TaskImage;
import vn.ainudge.model.TaskProgress;
import vn.ainudge.repo.ProofImageRepository;
import vn.ainudge.repo.TaskImageRepository;
import vn.ainudge.repo.TaskProgressRepository;
import vn.ainudge.repo.TaskRepository;

/**
 * Lưu / xóa ảnh minh chứng đã nộp bài. Chỉ nhận ảnh thật (kiểm tra phần đầu
 * file), tên file do hệ thống đặt.
 */
@Service
@Transactional
public class ProofService {
    public static final int MAX_PER_TASK = 10; // ảnh minh chứng của một học sinh cho một nhiệm vụ
    public static final int MAX_TASK_IMAGES = 10; // ảnh hoặc Word (.docx) // ảnh đề bài ban cán sự gửi kèm một nhiệm vụ

    public record Result(int saved, int rejected, String error) {
    }

    private final ProofImageRepository images;
    private final TaskImageRepository taskImages;
    private final TaskRepository tasks;
    private final TaskProgressRepository progresses;
    private final Path dir;
    private final CloudStorage cloud;

    public ProofService(ProofImageRepository images, TaskImageRepository taskImages, TaskRepository tasks,
            TaskProgressRepository progresses, CloudStorage cloud,
            @Value("${ainudge.upload-dir:uploads}") String uploadDir) {
        this.cloud = cloud;
        this.images = images;
        this.taskImages = taskImages;
        this.tasks = tasks;
        this.progresses = progresses;
        this.dir = Path.of(uploadDir).toAbsolutePath();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Lưu nhiều ảnh cho một nhiệm vụ của học sinh. */
    public Result save(Student s, Long taskId, List<MultipartFile> files) {
        Task t = tasks.findById(taskId).orElse(null);
        if (t == null || !t.active)
            return new Result(0, 0, "Không tìm thấy nhiệm vụ này.");
        TaskProgress p = progresses.findByTask_IdAndStudent_Id(t.id, s.id).orElseGet(() -> {
            TaskProgress x = new TaskProgress();
            x.task = t;
            x.student = s;
            return progresses.save(x);
        });
        int existing = images.findByProgress_IdOrderByIdAsc(p.id).size();
        int saved = 0, rejected = 0;
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty())
                continue;
            String[] type = sniff(f);
            if (type == null || existing + saved >= MAX_PER_TASK) {
                rejected++;
                continue;
            }
            String[] st = store(f, type, "ainudge/proof");
            if (st == null) {
                rejected++;
                continue;
            }
            ProofImage im = new ProofImage();
            im.progress = p;
            im.fileName = st[0];
            im.cloudUrl = st[1];
            im.contentType = type[1];
            im.uploadedAt = LocalDateTime.now();
            images.save(im);
            saved++;
        }
        String err = null;
        if (saved == 0 && rejected > 0)
            err = "Không gửi được. Chỉ nhận ảnh JPG, PNG, WEBP, GIF và tối đa " + MAX_PER_TASK
                    + " ảnh cho mỗi nhiệm vụ.";
        return new Result(saved, rejected, err);
    }

    /**
     * Ban cán sự gửi ảnh đề bài (nhiều ảnh) kèm một nhiệm vụ. Chỉ nhận ảnh thật,
     * tối đa MAX_TASK_IMAGES ảnh.
     */
    public Result saveTaskImages(Task t, List<MultipartFile> files) {
        if (files == null)
            return new Result(0, 0, "Bạn cần đính kèm ảnh hoặc file Word (.docx).");
        int existing = taskImages.findByTask_IdOrderByIdAsc(t.id).size();
        int saved = 0, rejected = 0;
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty())
                continue;
            String[] type = sniff(f);
            if (type == null && f.getOriginalFilename() != null
                    && f.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".docx")
                    && f.getSize() <= 10L * 1024 * 1024 && validDocx(f))
                type = new String[] { "docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" };
            if (type == null || existing + saved >= MAX_TASK_IMAGES) {
                rejected++;
                continue;
            }
            String[] st = store(f, type, "ainudge/task");
            if (st == null) {
                rejected++;
                continue;
            }
            TaskImage im = new TaskImage();
            im.task = t;
            im.fileName = st[0];
            im.cloudUrl = st[1];
            im.contentType = type[1];
            im.uploadedAt = LocalDateTime.now();
            taskImages.save(im);
            saved++;
        }
        String err = null;
        if (saved == 0 && rejected > 0)
            err = "Không tải được đề. Chỉ nhận ảnh JPG, PNG, WEBP, GIF hoặc Word .docx (mỗi tệp tối đa 10 MB), tối đa "
                    + MAX_TASK_IMAGES + " tệp cho mỗi nhiệm vụ.";
        else if (rejected > 0)
            err = "Đã gửi " + saved + " tệp, còn " + rejected
                    + " tệp không hợp lệ (chỉ nhận ảnh hoặc Word .docx, tối đa " + MAX_TASK_IMAGES + " tệp).";
        if (saved == 0 && err == null)
            err = "Bạn cần đính kèm ít nhất một ảnh hoặc file Word (.docx).";
        return new Result(saved, rejected, err);
    }

    /** Xóa một ảnh đề bài. */
    public void deleteTaskImage(Long imageId) {
        taskImages.findById(imageId).ifPresent(im -> {
            deleteFile(im.fileName, im.cloudUrl, im.contentType);
            taskImages.delete(im);
        });
    }

    /** Xóa toàn bộ ảnh đề của một nhiệm vụ (khi xóa hẳn nhiệm vụ). */
    public void deleteTaskImagesOf(Long taskId) {
        for (TaskImage im : taskImages.findByTask_IdOrderByIdAsc(taskId)) {
            deleteFile(im.fileName, im.cloudUrl, im.contentType);
            taskImages.delete(im);
        }
    }

    public Path path(TaskImage im) {
        return dir.resolve(im.fileName);
    }

    public void deleteForProgresses(Collection<TaskProgress> list) {
        for (TaskProgress p : list)
            for (ProofImage im : images.findByProgress_IdOrderByIdAsc(p.id))
                deleteImage(im);
    }

    public void deleteAll() {
        for (ProofImage im : images.findAll())
            deleteFile(im.fileName, im.cloudUrl, im.contentType);
        images.deleteAll();
        for (TaskImage im : taskImages.findAll())
            deleteFile(im.fileName, im.cloudUrl, im.contentType);
        taskImages.deleteAll();
    }

    public Path path(ProofImage im) {
        return dir.resolve(im.fileName);
    }

    private void deleteImage(ProofImage im) {
        deleteFile(im.fileName, im.cloudUrl, im.contentType);
        images.delete(im);
    }

    private void deleteFile(String name, String cloudUrl, String contentType) {
        if (cloudUrl != null) {
            cloud.delete(name, contentType);
            return;
        } // tệp trên Cloudinary
        try {
            Files.deleteIfExists(dir.resolve(name));
        } catch (IOException ignored) {
        }
    }

    /**
     * Lưu một tệp: lên Cloudinary nếu đã cấu hình, không thì vào thư mục uploads.
     * Trả về {tên/ID, địa chỉ cloud hoặc null}, lỗi thì null.
     */
    private String[] store(MultipartFile f, String[] type, String folder) {
        try {
            if (cloud.enabled()) {
                CloudStorage.Stored st = cloud.upload(f.getBytes(), folder, type[0], type[1]);
                return new String[] { st.publicId(), st.url() };
            }
            String name = UUID.randomUUID() + "." + type[0];
            try (InputStream in = f.getInputStream()) {
                Files.copy(in, dir.resolve(name));
            }
            return new String[] { name, null };
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Đọc nội dung tệp để phát cho người xem (từ Cloudinary hoặc từ thư mục
     * uploads). null nếu không có.
     */
    public byte[] bytes(String fileName, String cloudUrl) {
        try {
            if (cloudUrl != null)
                return cloud.fetch(cloudUrl);
            Path p = dir.resolve(fileName);
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * DOCX là ZIP OpenXML, phải chứa thành phần Word chính, không nhận ZIP chỉ đổi
     * đuôi.
     */
    private static boolean validDocx(MultipartFile file) {
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(file.getInputStream())) {
            boolean contentTypes = false, wordDocument = false;
            int entries = 0;
            java.util.zip.ZipEntry e;
            while ((e = in.getNextEntry()) != null && ++entries <= 5000) {
                String n = e.getName();
                if ("[Content_Types].xml".equals(n))
                    contentTypes = true;
                if ("word/document.xml".equals(n))
                    wordDocument = true;
                in.closeEntry();
            }
            return contentTypes && wordDocument && entries <= 5000;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    /**
     * Nhận dạng ảnh qua vài byte đầu file, không tin phần đuôi tên file do người
     * dùng gửi.
     */
    private static String[] sniff(MultipartFile f) {
        byte[] h = new byte[12];
        int n;
        try (InputStream in = f.getInputStream()) {
            n = in.readNBytes(h, 0, 12);
        } catch (IOException e) {
            return null;
        }
        if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF)
            return new String[] { "jpg", "image/jpeg" };
        if (n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G')
            return new String[] { "png", "image/png" };
        if (n >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8')
            return new String[] { "gif", "image/gif" };
        if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P')
            return new String[] { "webp", "image/webp" };
        return null;
    }
}