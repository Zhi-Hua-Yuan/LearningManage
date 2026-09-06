package com.spt.learningmanage.agent.tool;

import com.spt.learningmanage.agent.AgentTool;
import com.spt.learningmanage.agent.ProjectHistoryArguments;
import com.spt.learningmanage.agent.ToolExecutionContext;
import com.spt.learningmanage.agent.model.ProjectHistoryEvidence;
import com.spt.learningmanage.agent.model.ProjectHistoryToolResult;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.model.rag.RagRetrievalOutcome;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.rag.RagRetrievalService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RetrieveProjectHistoryAgentTool implements AgentTool<ProjectHistoryArguments> {
    private final RagRetrievalService retrievalService;
    private final PermissionService permissionService;
    private final AgentProperties properties;

    public RetrieveProjectHistoryAgentTool(RagRetrievalService retrievalService,
                                           PermissionService permissionService,
                                           AgentProperties properties) {
        this.retrievalService = retrievalService;
        this.permissionService = permissionService;
        this.properties = properties;
    }

    @Override public String name() { return "retrieveProjectHistory"; }
    @Override public Set<AgentSceneEnum> allowedScenes() { return Set.of(AgentSceneEnum.PROJECT_RISK); }
    @Override public Class<ProjectHistoryArguments> argumentType() { return ProjectHistoryArguments.class; }

    @Override
    public Object execute(ToolExecutionContext context, ProjectHistoryArguments arguments) {
        var scope = permissionService.requireProjectView(context.actorUserId(), context.projectId());
        String query = arguments.query() == null || arguments.query().isBlank()
                ? "项目延期、阻塞、风险和最近进展" : arguments.query().trim();
        RagRetrievalOutcome outcome = retrievalService.retrieve(
                context.actorUserId(), scope, query, context.traceId());
        AtomicInteger sequence = new AtomicInteger();
        var evidence = outcome.candidates().stream().limit(properties.getHistoryLimit())
                .map(value -> new ProjectHistoryEvidence(
                        "S" + sequence.incrementAndGet(), value.sourceType().name(), value.sourceId(),
                        value.documentKey(), value.chunkIndex(), value.contentHash(), value.payloadHash(),
                        value.title(), value.text(), value.finalScore(), value.sourceUpdatedAt()))
                .toList();
        return new ProjectHistoryToolResult(evidence, outcome.degraded(), outcome.degradationReason());
    }
}
