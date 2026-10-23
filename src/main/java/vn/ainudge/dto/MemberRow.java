package vn.ainudge.dto;

/** Một dòng trong trang xác nhận nộp bài của ban cán sự. */
public record MemberRow(Long studentId, String name, String status, String proof, boolean selfSubmitted,
                        boolean confirmed, String confirmedAt, java.util.List<Long> images) {}
