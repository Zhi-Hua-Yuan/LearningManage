package com.spt.learningmanage.agent.tool;

import com.spt.learningmanage.agent.AgentTool;
import com.spt.learningmanage.agent.EmptyToolArguments;
import com.spt.learningmanage.agent.ToolExecutionContext;
import com.spt.learningmanage.agent.model.TeamMemberWorkloadMetric;
import com.spt.learningmanage.agent.model.TeamWorkloadToolResult;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.mapper.AgentReadMapper;
import com.spt.learningmanage.service.PermissionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class QueryTeamMemberWorkloadAgentTool implements AgentTool<EmptyToolArguments> {
    private final AgentReadMapper readMapper;
    private final PermissionService permissionService;

    public QueryTeamMemberWorkloadAgentTool(AgentReadMapper readMapper, PermissionService permissionService) {
        this.readMapper = readMapper;
        this.permissionService = permissionService;
    }

    @Override public String name() { return "queryTeamMemberWorkload"; }
    @Override public Set<AgentSceneEnum> allowedScenes() { return Set.of(AgentSceneEnum.TEAM_WORKLOAD); }
    @Override public Class<EmptyToolArguments> argumentType() { return EmptyToolArguments.class; }

    @Override
    public Object execute(ToolExecutionContext context, EmptyToolArguments arguments) {
        permissionService.requireTeamWorkloadAnalyze(context.actorUserId(), context.teamId());
        LocalDate today = LocalDate.now();
        AtomicInteger sequence = new AtomicInteger();
        var metrics = readMapper.selectTeamMemberMetrics(
                        context.teamId(), today, today.plusDays(7), today.minusDays(30).atStartOfDay()).stream()
                .map(row -> {
                    long completedDue = value(row.getCompletedWithDueDateLast30Days());
                    BigDecimal rate = completedDue == 0 ? null : BigDecimal.valueOf(
                                    value(row.getOnTimeCompletedLast30Days()) * 100.0 / completedDue)
                            .setScale(2, RoundingMode.HALF_UP);
                    long overdue = value(row.getOverdueOpenCount());
                    long dueSoon = value(row.getDueNext7DaysCount());
                    String risk = overdue >= 3 || dueSoon >= 8 ? "HIGH"
                            : overdue > 0 || dueSoon >= 4 ? "MEDIUM" : "LOW";
                    return new TeamMemberWorkloadMetric("M" + sequence.incrementAndGet(),
                            value(row.getOpenTaskCount()), overdue, dueSoon,
                            value(row.getCompletedLast30DaysCount()), completedDue,
                            value(row.getOnTimeCompletedLast30Days()), rate, risk, "team-workload-v1");
                }).toList();
        return new TeamWorkloadToolResult(metrics);
    }

    private long value(Long value) { return value == null ? 0 : value; }
}

