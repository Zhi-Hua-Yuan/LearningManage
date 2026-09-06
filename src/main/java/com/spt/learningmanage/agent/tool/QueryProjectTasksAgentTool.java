package com.spt.learningmanage.agent.tool;

import com.spt.learningmanage.agent.AgentTool;
import com.spt.learningmanage.agent.EmptyToolArguments;
import com.spt.learningmanage.agent.ToolExecutionContext;
import com.spt.learningmanage.agent.model.ProjectTaskToolItem;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.mapper.AgentReadMapper;
import com.spt.learningmanage.service.PermissionService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class QueryProjectTasksAgentTool implements AgentTool<EmptyToolArguments> {
    private final AgentReadMapper readMapper;
    private final PermissionService permissionService;
    private final AgentProperties properties;

    public QueryProjectTasksAgentTool(AgentReadMapper readMapper,
                                      PermissionService permissionService,
                                      AgentProperties properties) {
        this.readMapper = readMapper;
        this.permissionService = permissionService;
        this.properties = properties;
    }

    @Override public String name() { return "queryProjectTasks"; }
    @Override public Set<AgentSceneEnum> allowedScenes() { return Set.of(AgentSceneEnum.PROJECT_RISK); }
    @Override public Class<EmptyToolArguments> argumentType() { return EmptyToolArguments.class; }

    @Override
    public Object execute(ToolExecutionContext context, EmptyToolArguments arguments) {
        permissionService.requireProjectView(context.actorUserId(), context.projectId());
        AtomicInteger sequence = new AtomicInteger();
        return readMapper.selectProjectTasks(context.projectId(), properties.getProjectTaskLimit()).stream()
                .map(row -> new ProjectTaskToolItem("T" + sequence.incrementAndGet(), row.getTitle(),
                        row.getStatus(), row.getPriority(), row.getDueDate(), row.getAssigneeUserId() != null))
                .toList();
    }
}

