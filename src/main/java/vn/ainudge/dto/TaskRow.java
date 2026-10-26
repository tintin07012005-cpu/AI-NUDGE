package vn.ainudge.dto;

public record TaskRow(Long id, String label, String deadline, String creator,
        int total, int started, int done, int confirmed,
        java.util.List<Long> images, String classes, java.util.List<Long> classIds) {
}