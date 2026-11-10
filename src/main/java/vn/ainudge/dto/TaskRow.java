package vn.ainudge.dto;

public record TaskRow(Long id, String label, String assigned, String deadline, String creator,
        int total, int started, int done, int confirmed,
        java.util.List<Long> images, String classes, java.util.List<Long> classIds, java.util.List<Long> groupIds) {
}