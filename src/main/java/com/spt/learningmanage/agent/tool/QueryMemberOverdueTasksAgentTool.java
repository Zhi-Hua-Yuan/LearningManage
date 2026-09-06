package com.spt.learningmanage.agent.tool;

import com.spt.learningmanage.agent.AgentTool;
import com.spt.learningmanage.agent.EmptyToolArguments;
import com.spt.learningmanage.agent.ToolExecutionContext;
import com.spt.learningmanage.agent.model.TeamOverdueTaskItem;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.mapper.AgentReadMapper;
import com.spt.learningmanage.service.PermissionService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class QueryMemberOverdueTasksAgentTool implements AgentTool<EmptyToolArguments> {
    private final AgentReadMapper readMapper;
    private final PermissionService permissionService;
    private final AgentProperties properties;

    public QueryMemberOverdueTasksAgentTool(AgentReadMapper readMapper,
                                            PermissionService permissionService,
                                            AgentProperties properties) {
        this.readMapper = readMapper;
        this.permissionService = permissionService;
        this.properties = properties;
    }

    @Override public String name() { return "queryMemberOverdueTasks"; }
    @Override public Set<AgentSceneEnum> allowedScenes() { return Set.of(AgentSceneEnum.TEAM_WORKLOAD); }
    @Override public Class<EmptyToolArguments> argumentType() { return EmptyToolArguments.class; }

    @Override
    public Object execute(ToolExecutionContext context, EmptyToolArguments arguments) {
        permissionService.requireTeamWorkloadAnalyze(context.actorUserId(), context.teamId());
        LocalDate today = LocalDate.now();
        Map<Long, String> aliases = new LinkedHashMap<>();
        AtomicInteger memberSequence = new AtomicInteger();
        readMapper.selectTeamMemberMetrics(context.teamId(), today, today.plusDays(7),
                        today.minusDays(30).atStartOfDay())
                .forEach(row -> aliases.put(row.getUserId(), "M" + memberSequence.incrementAndGet()));
        AtomicInteger taskSequence = new AtomicInteger();
        return readMapper.selectTeamOverdueTasks(context.teamId(), today, properties.getProjectTaskLimit()).stream()
                .map(row -> new TeamOverdueTaskItem("O" + taskSequence.incrementAndGet(),
                        aliases.getOrDefault(row.getAssigneeUserId(), "UNASSIGNED"), row.getTitle(),
                        row.getPriority(), row.getDueDate()))
                .toList();
    }
}
