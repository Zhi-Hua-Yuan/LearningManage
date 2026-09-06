package com.spt.learningmanage.agent.model;

import java.math.BigDecimal;

public record TeamMemberMetricSnapshot(
        Long userId,
        long openTaskCount,
        long overdueOpenCount,
        long dueNext7DaysCount,
        long completedLast30DaysCount,
        long completedWithDueDateLast30Days,
        long onTimeCompletedLast30Days,
        BigDecimal onTimeCompletionRate,
        String workloadRisk,
        String ruleVersion
) {
}

