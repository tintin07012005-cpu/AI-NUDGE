package vn.ainudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Lưu ảnh / tệp Word lên Cloudinary (gọi thẳng API HTTP, không cần thêm thư
 * viện).
 * Chưa điền cloudinary.cloud-name / api-key / api-secret thì enabled() = false
 * và web tự lưu vào thư mục uploads như cũ.
 */
@Service
public class CloudStorage {
    private static final Logger log = LoggerFactory.getLogger(CloudStorage.class);

    public record Stored(String url, String publicId) {
    }

    private final String cloud;
    private final String key;
    private final String secret;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CloudStorage(@Value("${cloudinary.cloud-name:}") String cloud,
            @Value("${cloudinary.api-key:}") String key,
            @Value("${cloudinary.api-secret:}") String secret) {
        this.cloud = cloud.trim();
        this.key = key.trim();
        this.secret = secret.trim();
        if (enabled())
            log.info("Đã bật lưu ảnh trên Cloudinary (cloud: {}).", this.cloud);
        else
            log.warn("Chưa cấu hình Cloudinary: ảnh sẽ lưu vào thư mục uploads (mất khi Render deploy lại).");
    }

    public boolean enabled() {
        return !cloud.isEmpty() && !key.isEmpty() && !secret.isEmpty();
    }

    private static String resourceType(String contentType) {
        return contentType != null && contentType.startsWith("image/") ? "image" : "raw";
    }

    /**
     * Tải một tệp lên Cloudinary. folder ví dụ "ainudge/proof"; ext là đuôi tệp
     * (jpg, png, docx...).
     */
    public Stored upload(byte[] data, String folder, String ext, String contentType) throws IOException {
        String rt = resourceType(contentType);
        String base = UUID.randomUUID().toString();
        // Tệp "raw" (Word) phải có đuôi trong public_id; ảnh thì Cloudinary tự thêm
        // đuôi vào địa chỉ.
        String publicId = folder + "/" + base + ("raw".equals(rt) ? "." + ext : "");
        long ts = System.currentTimeMillis() / 1000;
        String signature = sha1("public_id=" + publicId + "&timestamp=" + ts + secret);

        String boundary = "----ainudge" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 1024);
        field(out, boundary, "api_key", key);
        field(out, boundary, "timestamp", String.valueOf(ts));
        field(out, boundary, "public_id", publicId);
        field(out, boundary, "signature", signature);
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + base + "." + ext
                + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest
                .newBuilder(URI.create("https://api.cloudinary.com/v1_1/" + cloud + "/" + rt + "/upload"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        HttpResponse<String> res = send(req);
        if (res.statusCode() != 200) {
            log.warn("Cloudinary từ chối tải lên, mã {}: {}", res.statusCode(), res.body());
            throw new IOException("Cloudinary trả mã " + res.statusCode());
        }
        JsonNode n = json.readTree(res.body());
        String url = n.path("secure_url").asText("");
        String pid = n.path("public_id").asText("");
        if (url.isEmpty() || pid.isEmpty())
            throw new IOException("Cloudinary không trả về địa chỉ tệp");
        return new Stored(url, pid);
    }

    /**
     * Xóa một tệp trên Cloudinary (lỗi thì chỉ ghi log, không làm hỏng thao tác xóa
     * trong web).
     */
    public void delete(String publicId, String contentType) {
        if (!enabled() || publicId == null || publicId.isBlank())
            return;
        try {
            long ts = System.currentTimeMillis() / 1000;
            String signature = sha1("public_id=" + publicId + "&timestamp=" + ts + secret);
            String body = "public_id=" + URLEncoder.encode(publicId, StandardCharsets.UTF_8)
                    + "&timestamp=" + ts + "&api_key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&signature=" + signature;
            HttpRequest req = HttpRequest
                    .newBuilder(URI.create(
                            "https://api.cloudinary.com/v1_1/" + cloud + "/" + resourceType(contentType) + "/destroy"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = send(req);
            if (res.statusCode() != 200)
                log.warn("Xóa tệp trên Cloudinary không thành công, mã {}", res.statusCode());
        } catch (Exception e) {
            log.warn("Xóa tệp trên Cloudinary lỗi: {}", e.toString());
        }
    }

    /**
     * Tải nội dung tệp từ Cloudinary về để web phát lại (vẫn kiểm tra đăng nhập như
     * trước). null nếu không lấy được.
     */
    public byte[] fetch(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                log.warn("Không lấy được tệp từ Cloudinary, mã {}", res.statusCode());
                return null;
            }
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("Lấy tệp từ Cloudinary lỗi: {}", e.toString());
            return null;
        }
    }

    private HttpResponse<String> send(HttpRequest req) throws IOException {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Bị ngắt khi gửi lên Cloudinary", e);
        }
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.write(
                ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static String sha1(String s) throws IOException {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}