package vn.ainudge.dto;

import java.util.List;

public record WeekPlan(String from, String until, int total, int completed,
        int pending, List<WeekTask> tasks) {}
