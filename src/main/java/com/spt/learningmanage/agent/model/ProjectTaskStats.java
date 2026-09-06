package com.spt.learningmanage.agent.model;

public record ProjectTaskStats(
        long totalCount,
        long completedCount,
        long openCount,
        long overdueCount,
        long unassignedCount,
        long dueNext7DaysCount,
        long completedLast30DaysCount,
        String baselineRiskLevel,
        String ruleVersion
) {
}

