package vn.ainudge.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Ghi nhớ học sinh nào đang mở trang web (trình duyệt hỏi máy chủ mỗi 10 giây). */
@Component
public class PresenceTracker {
    private final Map<Long, Long> seen = new ConcurrentHashMap<>();

    public void touch(Long studentId) { seen.put(studentId, System.currentTimeMillis()); }

    public boolean isActive(Long studentId, long withinMillis) {
        Long t = seen.get(studentId);
        return t != null && System.currentTimeMillis() - t < withinMillis;
    }
}
