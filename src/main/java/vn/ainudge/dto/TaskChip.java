package vn.ainudge.dto;

/** Một nhiệm vụ hiện trên thanh nhiệm vụ của học sinh. status: TODO | PLANNED | STARTED | DONE | CONFIRMED. images: ảnh đề bài do ban cán sự gửi. */
public record TaskChip(String label, String deadline, String left, String status, java.util.List<Long> images) {}
