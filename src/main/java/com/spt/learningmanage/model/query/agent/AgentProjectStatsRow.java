package com.spt.learningmanage.model.query.agent;

import lombok.Data;

@Data
public class AgentProjectStatsRow {
    private Long totalCount;
    private Long completedCount;
    private Long openCount;
    private Long overdueCount;
    private Long unassignedCount;
    private Long dueNext7DaysCount;
    private Long completedLast30DaysCount;
}

