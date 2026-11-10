package vn.ainudge.dto;

/** Một mục trong kế hoạch tuần, của riêng học sinh đang đăng nhập. */
public record WeekTask(Long id, String label, String day, String assigned, String deadline,
        String status, String statusLabel, String audience, java.util.List<Long> images) {}
