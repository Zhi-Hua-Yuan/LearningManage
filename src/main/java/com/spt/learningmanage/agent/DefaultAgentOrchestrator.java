package com.spt.learningmanage.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.agent.model.AgentReportDraftPayload;
import com.spt.learningmanage.agent.model.AgentReportSourcePayload;
import com.spt.learningmanage.agent.model.AgentRiskItem;
import com.spt.learningmanage.agent.model.ProjectHistoryEvidence;
import com.spt.learningmanage.agent.model.ProjectHistoryToolResult;
import com.spt.learningmanage.agent.model.ProjectRiskAnalysis;
import com.spt.learningmanage.agent.model.ProjectTaskStats;
import com.spt.learningmanage.agent.model.TeamManagerAnalysis;
import com.spt.learningmanage.agent.model.TeamMemberMetricSnapshot;
import com.spt.learningmanage.agent.model.TeamPublicAnalysis;
import com.spt.learningmanage.agent.model.TeamWorkloadToolResult;
import com.spt.learningmanage.ai.pipeline.AiChatRoundExecutionCommand;
import com.spt.learningmanage.ai.pipeline.AiExecutionResult;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.constant.AgentOrchestrationModeEnum;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AgentReadMapper;
import com.spt.learningmanage.mapper.AiAgentToolLogMapper;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.BusinessDataVersionService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import com.spt.learningmanage.service.ai.support.AiJsonResponseSanitizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DefaultAgentOrchestrator implements AgentOrchestrator {
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> RISK_CATEGORIES = Set.of(
            "SCHEDULE", "OVERDUE", "WORKLOAD", "UNASSIGNED", "HISTORY", "DATA_GAP");

    private final AgentProperties properties;
    private final AiProperties aiProperties;
    private final RagProperties ragProperties;
    private final PermissionService permissionService;
    private final BusinessDataVersionService versionService;
    private final AgentToolExecutor toolExecutor;
    private final AgentRunQueueService queueService;
    private final AiInvocationPipeline pipeline;
    private final AgentReadMapper readMapper;
    private final AiAgentToolLogMapper toolLogMapper;
    private final ObjectMapper objectMapper;
    private final AiJsonResponseSanitizer responseSanitizer;
    private final AgentToolPolicy toolPolicy;

    public DefaultAgentOrchestrator(AgentProperties properties,
                                    AiProperties aiProperties,
                                    RagProperties ragProperties,
                                    PermissionService permissionService,
                                    BusinessDataVersionService versionService,
                                    AgentToolExecutor toolExecutor,
                                    AgentRunQueueService queueService,
                                    AiInvocationPipeline pipeline,
                                    AgentReadMapper readMapper,
                                    AiAgentToolLogMapper toolLogMapper,
                                    ObjectMapper objectMapper,
                                    AiJsonResponseSanitizer responseSanitizer,
                                    AgentToolPolicy toolPolicy) {
        this.properties = properties;
        this.aiProperties = aiProperties;
        this.ragProperties = ragProperties;
        this.permissionService = permissionService;
        this.versionService = versionService;
        this.toolExecutor = toolExecutor;
        this.queueService = queueService;
        this.pipeline = pipeline;
        this.readMapper = readMapper;
        this.toolLogMapper = toolLogMapper;
        this.objectMapper = objectMapper;
        this.responseSanitizer = responseSanitizer;
        this.toolPolicy = toolPolicy;
    }

    @Override
    public AgentOrchestrationResult orchestrate(AiAgentRun run) {
        AgentSceneEnum scene = AgentSceneEnum.valueOf(run.getScene());
        return scene == AgentSceneEnum.PROJECT_RISK ? projectRisk(run) : teamWorkload(run);
    }

    private AgentOrchestrationResult projectRisk(AiAgentRun run) {
        ProjectAccessScope scope = permissionService.requireProjectView(run.getUserId(), run.getProjectId());
        long startVersion = versionService.projectVersion(run.getProjectId());
        ToolExecutionContext context = context(run, AgentSceneEnum.PROJECT_RISK, scope, startVersion);
        Map<String, AgentToolExecution> outputs = new LinkedHashMap<>();
        ProjectModelOutcome outcome = null;
        boolean partial = false;
        String partialReason = null;

        if (AgentOrchestrationModeEnum.TOOL_CALLING.name().equals(run.getOrchestrationMode())) {
            try {
                outcome = executeProjectToolCalling(run, context, outputs);
            } catch (RuntimeException exception) {
                partial = true;
                partialReason = "Tool Calling 不可用，已切换固定只读工作流";
                run.setOrchestrationMode(AgentOrchestrationModeEnum.FIXED_WORKFLOW.name());
            }
        }
        if (outcome == null) {
            outcome = executeProjectFixed(run, context, outputs);
            partial = partial || outcome.partial();
            partialReason = joinReason(partialReason, outcome.partialReason());
        }

        long endVersion = versionService.projectVersion(run.getProjectId());
        long reportVersion = stableReportVersion(startVersion, endVersion);
        if (endVersion != startVersion) {
            partial = true;
            partialReason = joinReason(partialReason, "分析期间项目数据发生变化，草稿不可确认，请重新分析");
        }
        AgentReportDraftPayload payload = projectPayload(run, reportVersion, outcome.analysis(),
                outputs.get("retrieveProjectHistory"), outcome.modelResult());
        return result(payload, reportVersion, outputs.size(), partial, partialReason, outcome.modelResult());
    }

    private ProjectModelOutcome executeProjectToolCalling(AiAgentRun run,
                                                          ToolExecutionContext context,
                                                          Map<String, AgentToolExecution> outputs) {
        List<AiChatMessage> messages = new ArrayList<>();
        String task = "分析当前项目风险。必须先调用 queryTaskStats 和 queryOverdueTasks。";
        if (ragProperties.isEnabled()) {
            task += "需要历史证据时调用 retrieveProjectHistory。";
        } else {
            task += "当前未提供历史引用工具，最终 citations 以及每个风险项的 evidenceIds 必须是空数组，"
                    + "不得把任务 localId 当作可持久化引用。";
        }
        messages.add(AiChatMessage.user(task));
        List<AiToolDefinition> definitions = projectToolDefinitions();
        boolean corrected = false;
        int round = 1;
        while (round <= 6) {
            checkCanceled(run);
            AiExecutionResult<AiChatResult> execution = pipeline.executeChatRound(new AiChatRoundExecutionCommand(
                    run.getUserId(), aiProperties.getModel(), AiPromptCodeEnum.AGENT_PROJECT_RISK,
                    messages, definitions, AiToolChoice.auto(), 0.0, 2000, run.getTraceId(),
                    "agentRun=" + run.getRunId() + "|round=" + round + "|mode=tool-calling",
                    run.getRunId(), round));
            AiChatResult response = execution.data();
            if (!response.toolCalls().isEmpty()) {
                messages.add(AiChatMessage.assistant(response.content(), response.toolCalls()));
                for (var call : response.toolCalls()) {
                    String name = call.function().name();
                    toolPolicy.requireCallAllowed(AgentSceneEnum.PROJECT_RISK, outputs.keySet(), name);
                    AgentToolExecution tool = runTool(run, context, outputs.size() + 1,
                            call.id(), name, call.function().arguments());
                    outputs.put(name, tool);
                    messages.add(AiChatMessage.tool(call.id(), untrusted(tool.resultJson())));
                }
                round++;
                continue;
            }
            if (!toolPolicy.hasRequired(AgentSceneEnum.PROJECT_RISK, outputs.keySet())) {
                if (corrected) {
                    throw new BusinessException(ErrorCode.TOOL_EXECUTION_FAILED, "模型未调用必需 Tool");
                }
                messages.add(AiChatMessage.user("尚未调用全部必需工具，请先完成 queryTaskStats 和 queryOverdueTasks。"));
                corrected = true;
                round++;
                continue;
            }
            ProjectRiskAnalysis analysis = parseProjectRisk(response.content(), stats(outputs), history(outputs));
            return new ProjectModelOutcome(analysis, execution, false, null);
        }
        throw new BusinessException(ErrorCode.TOOL_CALL_LIMIT_EXCEEDED, "Agent 模型轮次超限");
    }

    private ProjectModelOutcome executeProjectFixed(AiAgentRun run,
                                                    ToolExecutionContext context,
                                                    Map<String, AgentToolExecution> outputs) {
        ensureTool(run, context, outputs, "queryTaskStats", "{}");
        ensureTool(run, context, outputs, "queryOverdueTasks", "{}");
        boolean partial = false;
        String reason = null;
        if (ragProperties.isEnabled()) {
            try {
                ensureTool(run, context, outputs, "retrieveProjectHistory",
                        "{\"query\":\"项目延期、阻塞、风险和最近进展\"}");
            } catch (RuntimeException exception) {
                partial = true;
                reason = "历史检索不可用，使用结构化任务数据完成分析";
            }
        }
        String prompt = toJson(Map.of(
                "taskStats", outputs.get("queryTaskStats").result(),
                "overdueTasks", outputs.get("queryOverdueTasks").result(),
                "history", outputs.containsKey("retrieveProjectHistory")
                        ? outputs.get("retrieveProjectHistory").result() : Map.of("available", false)));
        try {
            AiExecutionResult<AiChatResult> execution = pipeline.executeChatRound(new AiChatRoundExecutionCommand(
                    run.getUserId(), aiProperties.getModel(), AiPromptCodeEnum.AGENT_PROJECT_RISK,
                    List.of(AiChatMessage.user(untrusted(prompt))), List.of(), AiToolChoice.none(),
                    0.0, 2000, run.getTraceId(),
                    "agentRun=" + run.getRunId() + "|mode=fixed-workflow",
                    run.getRunId(), 1));
            return new ProjectModelOutcome(parseProjectRisk(execution.data().content(), stats(outputs), history(outputs)),
                    execution, partial, reason);
        } catch (RuntimeException exception) {
            return new ProjectModelOutcome(deterministicProjectRisk(stats(outputs), history(outputs)),
                    null, true, joinReason(reason, "模型分析不可用，返回确定性风险摘要"));
        }
    }

    private AgentOrchestrationResult teamWorkload(AiAgentRun run) {
        permissionService.requireTeamWorkloadAnalyze(run.getUserId(), run.getTeamId());
        long startVersion = versionService.teamVersion(run.getTeamId());
        ToolExecutionContext context = context(run, AgentSceneEnum.TEAM_WORKLOAD, null, startVersion);
        Map<String, AgentToolExecution> outputs = new LinkedHashMap<>();
        ensureTool(run, context, outputs, "queryTeamMemberWorkload", "{}");
        ensureTool(run, context, outputs, "queryMemberOverdueTasks", "{}");
        TeamWorkloadToolResult workload = (TeamWorkloadToolResult) outputs.get("queryTeamMemberWorkload").result();
        boolean partial = false;
        String partialReason = null;

        AiExecutionResult<AiChatResult> managerCall = null;
        TeamManagerAnalysis manager;
        try {
            managerCall = pipeline.executeChatRound(new AiChatRoundExecutionCommand(
                    run.getUserId(), aiProperties.getModel(), AiPromptCodeEnum.AGENT_TEAM_WORKLOAD_MANAGER,
                    List.of(AiChatMessage.user(untrusted(toJson(Map.of(
                            "members", workload.members(),
                            "overdueTasks", outputs.get("queryMemberOverdueTasks").result()))))),
                    List.of(), AiToolChoice.none(), 0.0, 1500, run.getTraceId(),
                    "agentRun=" + run.getRunId() + "|audience=manager", run.getRunId(), 1));
            manager = parseManager(managerCall.data().content());
        } catch (RuntimeException exception) {
            manager = deterministicManager(workload);
            partial = true;
            partialReason = "管理摘要模型调用失败，已使用确定性摘要";
        }

        TeamPublicAnalysis publicAnalysis;
        Map<String, Object> aggregate = aggregate(workload);
        try {
            AiExecutionResult<AiChatResult> publicCall = pipeline.executeChatRound(new AiChatRoundExecutionCommand(
                    run.getUserId(), aiProperties.getModel(), AiPromptCodeEnum.AGENT_TEAM_WORKLOAD_PUBLIC,
                    List.of(AiChatMessage.user(untrusted(toJson(aggregate)))), List.of(), AiToolChoice.none(),
                    0.0, 800, run.getTraceId(), "agentRun=" + run.getRunId() + "|audience=public",
                    run.getRunId(), 2));
            publicAnalysis = parsePublic(publicCall.data().content());
            if (publicAnalysis.publicSummary().matches("(?s).*\\bM\\d+\\b.*")) {
                throw new IllegalArgumentException("公开摘要包含成员别名");
            }
        } catch (RuntimeException exception) {
            publicAnalysis = deterministicPublic(aggregate);
            partial = true;
            partialReason = joinReason(partialReason, "公开摘要使用确定性隐私降级");
        }

        List<TeamMemberMetricSnapshot> snapshots = memberSnapshots(run.getTeamId(), workload);
        long endVersion = versionService.teamVersion(run.getTeamId());
        long reportVersion = stableReportVersion(startVersion, endVersion);
        if (endVersion != startVersion) {
            partial = true;
            partialReason = joinReason(partialReason, "分析期间团队数据发生变化，草稿不可确认，请重新分析");
        }
        AgentReportDraftPayload payload = new AgentReportDraftPayload(
                run.getRunId(), "TEAM_WORKLOAD", 1, null, run.getTeamId(), reportVersion,
                null, manager.managerSummary(), publicAnalysis.publicSummary(), snapshots,
                manager.recommendations(), List.of(), List.of(), List.of(),
                managerCall == null ? null : managerCall.actualModel(),
                AiPromptCodeEnum.AGENT_TEAM_WORKLOAD_MANAGER.getCode(),
                managerCall == null ? 1 : managerCall.promptVersion(), run.getTraceId(), LocalDateTime.now());
        return result(payload, reportVersion, outputs.size(), partial, partialReason, managerCall);
    }

    private AgentToolExecution ensureTool(AiAgentRun run,
                                          ToolExecutionContext context,
                                          Map<String, AgentToolExecution> outputs,
                                          String name,
                                          String arguments) {
        AgentToolExecution existing = outputs.get(name);
        if (existing != null) {
            return existing;
        }
        AgentToolExecution value = runTool(run, context, outputs.size() + 1, null, name, arguments);
        outputs.put(name, value);
        return value;
    }

    private AgentToolExecution runTool(AiAgentRun run,
                                       ToolExecutionContext context,
                                       int sequence,
                                       String toolCallId,
                                       String name,
                                       String arguments) {
        checkCanceled(run);
        int persistedSequence = toolLogMapper.selectMaxSequence(run.getRunId(), run.getAttemptCount());
        int nextSequence = Math.max(sequence, persistedSequence + 1);
        if (nextSequence > properties.getMaxToolCalls()) {
            throw new BusinessException(ErrorCode.TOOL_CALL_LIMIT_EXCEEDED);
        }
        queueService.updateProgress(run, "TOOL:" + name, nextSequence - 1, context.dataVersion());
        AgentToolExecution value = toolExecutor.execute(run, context, nextSequence, toolCallId, name, arguments);
        queueService.updateProgress(run, "TOOL_COMPLETED:" + name, nextSequence, context.dataVersion());
        return value;
    }

    private ToolExecutionContext context(AiAgentRun run,
                                         AgentSceneEnum scene,
                                         ProjectAccessScope scope,
                                         long dataVersion) {
        return new ToolExecutionContext(run.getUserId(), run.getRunId(), scene,
                run.getProjectId(), run.getTeamId(), scope, run.getTraceId(),
                run.getAttemptCount(), run.getExecutionToken(), dataVersion);
    }

    private void checkCanceled(AiAgentRun run) {
        if (queueService.cancellationRequested(run.getRunId(), run.getExecutionToken())) {
            throw new BusinessException(ErrorCode.AGENT_CANCELED);
        }
        if (!queueService.heartbeat(run)) {
            throw new BusinessException(ErrorCode.AGENT_WORKER_LOST);
        }
    }

    private ProjectTaskStats stats(Map<String, AgentToolExecution> outputs) {
        return (ProjectTaskStats) outputs.get("queryTaskStats").result();
    }

    private ProjectHistoryToolResult history(Map<String, AgentToolExecution> outputs) {
        AgentToolExecution value = outputs.get("retrieveProjectHistory");
        return value != null && value.result() instanceof ProjectHistoryToolResult history
                ? history : new ProjectHistoryToolResult(List.of(), false, null);
    }

    private ProjectRiskAnalysis parseProjectRisk(String raw,
                                                 ProjectTaskStats stats,
                                                 ProjectHistoryToolResult history) {
        try {
            JsonNode root = objectMapper.readTree(responseSanitizer.sanitizeObject(raw));
            String level = root.path("riskLevel").asText();
            String summary = root.path("summary").asText();
            if (!RISK_LEVELS.contains(level) || summary.isBlank()) {
                throw new IllegalArgumentException("风险分析字段缺失");
            }
            List<AgentRiskItem> items = new ArrayList<>();
            for (JsonNode item : root.path("riskItems")) {
                String category = item.path("category").asText();
                String severity = item.path("severity").asText();
                if (!RISK_CATEGORIES.contains(category) || !RISK_LEVELS.contains(severity)) {
                    throw new IllegalArgumentException("风险项枚举非法");
                }
                items.add(new AgentRiskItem(category, severity, item.path("reason").asText(),
                        item.path("impact").asText(), item.path("recommendation").asText(),
                        stringList(item.path("evidenceIds"))));
            }
            Set<String> allowed = history.evidence().stream()
                    .map(ProjectHistoryEvidence::citationId).collect(java.util.stream.Collectors.toSet());
            List<String> citations = stringList(root.path("citations"));
            if (!allowed.containsAll(citations)) {
                throw new IllegalArgumentException("风险引用不存在");
            }
            level = maxRisk(level, stats.baselineRiskLevel());
            return new ProjectRiskAnalysis(level, summary, items,
                    stringList(root.path("positiveSignals")),
                    root.path("insufficientEvidence").asBoolean(false), citations);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "项目风险分析格式异常");
        }
    }

    private ProjectRiskAnalysis deterministicProjectRisk(ProjectTaskStats stats,
                                                         ProjectHistoryToolResult history) {
        List<AgentRiskItem> items = new ArrayList<>();
        if (stats.overdueCount() > 0) {
            items.add(new AgentRiskItem("OVERDUE", stats.baselineRiskLevel(),
                    "存在 " + stats.overdueCount() + " 项逾期任务", "可能影响项目交付节奏",
                    "优先确认逾期任务阻塞原因并重新安排截止时间", List.of()));
        }
        if (stats.unassignedCount() > 0) {
            items.add(new AgentRiskItem("UNASSIGNED", "MEDIUM",
                    "存在 " + stats.unassignedCount() + " 项未分配任务", "任务可能缺少明确负责人",
                    "由项目管理员确认负责人", List.of()));
        }
        return new ProjectRiskAnalysis(stats.baselineRiskLevel(),
                "当前共有 " + stats.openCount() + " 项未完成任务，其中 " + stats.overdueCount() + " 项逾期。",
                items, stats.completedLast30DaysCount() > 0 ? List.of("最近 30 天存在任务完成记录") : List.of(),
                stats.totalCount() == 0, List.of());
    }

    private AgentReportDraftPayload projectPayload(AiAgentRun run,
                                                   long version,
                                                   ProjectRiskAnalysis analysis,
                                                   AgentToolExecution historyExecution,
                                                   AiExecutionResult<AiChatResult> model) {
        List<AgentReportSourcePayload> sources = historyExecution != null
                && historyExecution.result() instanceof ProjectHistoryToolResult history
                ? history.evidence().stream().filter(value -> analysis.citations().contains(value.citationId()))
                    .map(value -> new AgentReportSourcePayload(value.citationId(), value.sourceType(), value.sourceId(),
                            value.documentKey(), value.chunkIndex(), value.contentHash(), value.payloadHash(), value.title())).toList()
                : List.of();
        List<String> recommendations = analysis.riskItems().stream()
                .map(AgentRiskItem::recommendation).filter(value -> value != null && !value.isBlank()).toList();
        return new AgentReportDraftPayload(run.getRunId(), "PROJECT_RISK", 1,
                run.getProjectId(), null, version, analysis.riskLevel(), analysis.summary(),
                analysis.summary(), List.of(), recommendations, analysis.riskItems(),
                analysis.positiveSignals(), sources, model == null ? null : model.actualModel(),
                AiPromptCodeEnum.AGENT_PROJECT_RISK.getCode(), model == null ? 1 : model.promptVersion(),
                run.getTraceId(), LocalDateTime.now());
    }

    private TeamManagerAnalysis parseManager(String raw) {
        try {
            JsonNode root = objectMapper.readTree(responseSanitizer.sanitizeObject(raw));
            String summary = root.path("managerSummary").asText();
            if (summary.isBlank()) throw new IllegalArgumentException();
            return new TeamManagerAnalysis(summary, stringList(root.path("recommendations")));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "团队管理摘要格式异常");
        }
    }

    private TeamPublicAnalysis parsePublic(String raw) {
        try {
            JsonNode root = objectMapper.readTree(responseSanitizer.sanitizeObject(raw));
            String summary = root.path("publicSummary").asText();
            if (summary.isBlank()) throw new IllegalArgumentException();
            return new TeamPublicAnalysis(summary);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "团队公开摘要格式异常");
        }
    }

    private TeamManagerAnalysis deterministicManager(TeamWorkloadToolResult result) {
        long high = result.members().stream().filter(value -> "HIGH".equals(value.workloadRisk())).count();
        return new TeamManagerAnalysis("团队共 " + result.members().size() + " 名成员，其中 " + high
                + " 名成员的规则负载风险为高。", List.of("优先复核逾期任务和未来 7 天集中到期任务"));
    }

    private TeamPublicAnalysis deterministicPublic(Map<String, Object> aggregate) {
        return new TeamPublicAnalysis("团队当前共有 " + aggregate.get("openTaskCount") + " 项未完成任务，"
                + aggregate.get("overdueOpenCount") + " 项已逾期。建议共同确认近期优先级与阻塞事项。");
    }

    private Map<String, Object> aggregate(TeamWorkloadToolResult result) {
        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("memberCount", result.members().size());
        aggregate.put("openTaskCount", result.members().stream().mapToLong(value -> value.openTaskCount()).sum());
        aggregate.put("overdueOpenCount", result.members().stream().mapToLong(value -> value.overdueOpenCount()).sum());
        aggregate.put("dueNext7DaysCount", result.members().stream().mapToLong(value -> value.dueNext7DaysCount()).sum());
        aggregate.put("completedLast30DaysCount", result.members().stream().mapToLong(value -> value.completedLast30DaysCount()).sum());
        return aggregate;
    }

    private List<TeamMemberMetricSnapshot> memberSnapshots(Long teamId, TeamWorkloadToolResult result) {
        LocalDate today = LocalDate.now();
        var rows = readMapper.selectTeamMemberMetrics(teamId, today, today.plusDays(7),
                today.minusDays(30).atStartOfDay());
        List<TeamMemberMetricSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < Math.min(rows.size(), result.members().size()); index++) {
            var row = rows.get(index);
            var metric = result.members().get(index);
            snapshots.add(new TeamMemberMetricSnapshot(row.getUserId(), metric.openTaskCount(),
                    metric.overdueOpenCount(), metric.dueNext7DaysCount(), metric.completedLast30DaysCount(),
                    metric.completedWithDueDateLast30Days(), metric.onTimeCompletedLast30Days(),
                    metric.onTimeCompletionRate(), metric.workloadRisk(), metric.ruleVersion()));
        }
        return List.copyOf(snapshots);
    }

    private List<AiToolDefinition> projectToolDefinitions() {
        JsonNode empty = objectMapper.createObjectNode().put("type", "object")
                .set("properties", objectMapper.createObjectNode());
        JsonNode history = objectMapper.createObjectNode().put("type", "object")
                .set("properties", objectMapper.createObjectNode().set("query",
                        objectMapper.createObjectNode().put("type", "string").put("maxLength", 200)));
        List<AiToolDefinition> definitions = new ArrayList<>(List.of(
                tool("queryProjectTasks", "查询当前项目任务摘要", empty),
                tool("queryOverdueTasks", "查询当前项目逾期任务", empty),
                tool("queryTaskStats", "查询当前项目任务统计", empty)));
        if (ragProperties.isEnabled()) {
            definitions.add(tool("retrieveProjectHistory", "检索当前项目历史证据", history));
        }
        return List.copyOf(definitions);
    }

    private AiToolDefinition tool(String name, String description, JsonNode parameters) {
        return AiToolDefinition.function(new AiFunctionDefinition(name, description, parameters));
    }

    private String untrusted(String value) {
        return "UNTRUSTED_DATA_START\n" + value + "\nUNTRUSTED_DATA_END";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent 上下文序列化失败");
        }
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText()); });
        }
        return List.copyOf(values);
    }

    private String maxRisk(String modelRisk, String baseline) {
        return riskRank(modelRisk) >= riskRank(baseline) ? modelRisk : baseline;
    }

    private int riskRank(String value) {
        return switch (value) { case "HIGH" -> 3; case "MEDIUM" -> 2; default -> 1; };
    }

    private String joinReason(String first, String second) {
        if (first == null || first.isBlank()) return second;
        if (second == null || second.isBlank()) return first;
        return first + "；" + second;
    }

    static long stableReportVersion(long startVersion, long endVersion) {
        return startVersion == endVersion ? endVersion : startVersion;
    }

    private AgentOrchestrationResult result(AgentReportDraftPayload payload,
                                            long version,
                                            int toolCount,
                                            boolean partial,
                                            String partialReason,
                                            AiExecutionResult<AiChatResult> model) {
        return new AgentOrchestrationResult(toJson(payload), version, toolCount, partial, partialReason,
                model == null ? null : model.callLogId(), model == null ? null : model.actualModel(),
                payload.promptCode(), payload.promptVersion());
    }

    private record ProjectModelOutcome(ProjectRiskAnalysis analysis,
                                       AiExecutionResult<AiChatResult> modelResult,
                                       boolean partial,
                                       String partialReason) {
    }
}
