package com.spt.learningmanage.agent.tool;

import com.spt.learningmanage.agent.AgentTool;
import com.spt.learningmanage.agent.EmptyToolArguments;
import com.spt.learningmanage.agent.ToolExecutionContext;
import com.spt.learningmanage.agent.model.ProjectTaskStats;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.mapper.AgentReadMapper;
import com.spt.learningmanage.model.query.agent.AgentProjectStatsRow;
import com.spt.learningmanage.service.PermissionService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

@Component
public class QueryTaskStatsAgentTool implements AgentTool<EmptyToolArguments> {
    private final AgentReadMapper readMapper;
    private final PermissionService permissionService;

    public QueryTaskStatsAgentTool(AgentReadMapper readMapper, PermissionService permissionService) {
        this.readMapper = readMapper;
        this.permissionService = permissionService;
    }

    @Override public String name() { return "queryTaskStats"; }
    @Override public Set<AgentSceneEnum> allowedScenes() { return Set.of(AgentSceneEnum.PROJECT_RISK); }
    @Override public Class<EmptyToolArguments> argumentType() { return EmptyToolArguments.class; }

    @Override
    public Object execute(ToolExecutionContext context, EmptyToolArguments arguments) {
        permissionService.requireProjectView(context.actorUserId(), context.projectId());
        LocalDate today = LocalDate.now();
        AgentProjectStatsRow row = readMapper.selectProjectStats(
                context.projectId(), today, today.plusDays(7), today.minusDays(30).atStartOfDay());
        long total = value(row.getTotalCount());
        long overdue = value(row.getOverdueCount());
        long open = value(row.getOpenCount());
        long unassigned = value(row.getUnassignedCount());
        String risk = overdue >= 5 || (open > 0 && overdue * 100 / open >= 30) || unassigned >= 5
                ? "HIGH" : overdue > 0 || value(row.getDueNext7DaysCount()) >= 5 ? "MEDIUM" : "LOW";
        return new ProjectTaskStats(total, value(row.getCompletedCount()), open, overdue, unassigned,
                value(row.getDueNext7DaysCount()), value(row.getCompletedLast30DaysCount()),
                risk, "project-risk-v1");
    }

    private long value(Long value) { return value == null ? 0 : value; }
}
