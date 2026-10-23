package vn.ainudge.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ainudge.model.AppCredential;
import vn.ainudge.repo.AppCredentialRepository;

/** Mật khẩu quản trị và PIN cán sự lưu băm trong CSDL; mặc định từ application.properties của v25. */
@Service
public class AppCredentialService {
    private final AppCredentialRepository repo;
    private final String fallbackAdmin, fallbackMonitor;
    private final SecureRandom random = new SecureRandom();
    public AppCredentialService(AppCredentialRepository repo,
            @Value("${ainudge.teacher-password}") String fallbackAdmin,
            @Value("${ainudge.monitor-pin}") String fallbackMonitor) {
        this.repo=repo; this.fallbackAdmin=fallbackAdmin; this.fallbackMonitor=fallbackMonitor;
    }
    private String fallback(String kind) { return "admin".equals(kind) ? fallbackAdmin : fallbackMonitor; }
    public boolean verify(String kind, String candidate) {
        if (!"admin".equals(kind) && !"monitor".equals(kind)) return false;
        if (candidate==null) return false;
        AppCredential item=repo.findById(kind).orElse(null);
        if (item==null) return MessageDigest.isEqual(fallback(kind).getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
        try {
            String[] pieces=item.encoded.split(":", 2);
            byte[] salt=Base64.getDecoder().decode(pieces[0]);
            byte[] want=Base64.getDecoder().decode(pieces[1]);
            byte[] got=derive(candidate, salt);
            return MessageDigest.isEqual(want, got);
        } catch(Exception e) {return false;}
    }
    @Transactional
    public boolean change(String kind, String oldValue, String newValue) {
        if (!verify(kind, oldValue) || newValue==null || newValue.isBlank()) return false;
        byte[] salt=new byte[16]; random.nextBytes(salt);
        AppCredential item=repo.findById(kind).orElseGet(AppCredential::new);
        item.name=kind;
        item.encoded=Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(newValue,salt));
        repo.save(item);
        return true;
    }
    /** Khi đổi mật khẩu/PIN, thẻ ghi nhớ cũ tự hết hiệu lực. */
    public String cookieSalt(String kind) {
        return repo.findById(kind).map(a -> a.encoded).orElse(fallback(kind));
    }
    private static byte[] derive(String value, byte[] salt) {
        PBEKeySpec spec=new PBEKeySpec(value.toCharArray(),salt,120_000,256);
        try {return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(Exception e) {throw new IllegalStateException(e);}
        finally {spec.clearPassword();}
    }
}
