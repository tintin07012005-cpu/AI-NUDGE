package vn.ainudge.dto;

public record ProgressView(String task, String deadline, String status, String plan, String barrier, String postpone,
                           String proof, String selfSubmitted, String confirmed, String result,
                           java.util.List<Long> images) {}
