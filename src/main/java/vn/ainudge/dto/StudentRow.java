package vn.ainudge.dto;

public record StudentRow(Long id, String name, boolean monitor, int sessions, String lastTime,
                         int started, int done, int confirmed,
                         int early, int onTime, int late, int missing) {}
