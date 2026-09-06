package com.spt.learningmanage.model.query.agent;

import lombok.Data;

@Data
public class AgentTeamMemberMetricsRow {
    private Long userId;
    private Long openTaskCount;
    private Long overdueOpenCount;
    private Long dueNext7DaysCount;
    private Long completedLast30DaysCount;
    private Long completedWithDueDateLast30Days;
    private Long onTimeCompletedLast30Days;
}
