package com.spt.learningmanage.agent.tool;

import com.spt.learningmanage.agent.ProjectHistoryArguments;
import com.spt.learningmanage.agent.ToolExecutionContext;
import com.spt.learningmanage.agent.model.ProjectHistoryToolResult;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.rag.RagCandidate;
import com.spt.learningmanage.model.rag.RagRetrievalOutcome;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.rag.RagRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrieveProjectHistoryAgentToolTest {
    private static final String PRIVATE_SECRET = "PRIVATE_SECRET_7F92";
    private final RagRetrievalService retrievalService = mock(RagRetrievalService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AgentProperties properties = new AgentProperties();
    private final RetrieveProjectHistoryAgentTool tool = new RetrieveProjectHistoryAgentTool(
            retrievalService, permissionService, properties);

    @BeforeEach
    void setUp() {
        properties.setHistoryLimit(1);
    }

    @Test
    void teamProjectFiltersPrivateEvidenceBeforeApplyingHistoryLimit() {
        ProjectAccessScope scope = new ProjectAccessScope(
                7L, 10L, 6L, 20L, TeamRoleEnum.MEMBER);
        when(permissionService.requireProjectView(7L, 10L)).thenReturn(scope);
        when(retrievalService.retrieve(eq(7L), eq(scope), anyString(), eq("trace")))
                .thenReturn(outcome(List.of(
                        candidate(30L, "WEEKLY_REVIEW:30:PRIVATE:10", PRIVATE_SECRET),
                        candidate(31L, "WEEKLY_REVIEW:31:TEAM:10", "TEAM_SHARED_SUMMARY"))));

        ProjectHistoryToolResult result = execute(scope);

        assertEquals(1, result.evidence().size());
        assertEquals(31L, result.evidence().get(0).sourceId());
        assertEquals("TEAM_SHARED_SUMMARY", result.evidence().get(0).text());
        assertEquals("S1", result.evidence().get(0).citationId());
        assertFalse(result.evidence().toString().contains(PRIVATE_SECRET));
    }

    @Test
    void personalProjectKeepsPrivateSelfEvidence() {
        ProjectAccessScope scope = new ProjectAccessScope(7L, 10L, 7L, null, null);
        when(permissionService.requireProjectView(7L, 10L)).thenReturn(scope);
        when(retrievalService.retrieve(eq(7L), eq(scope), anyString(), eq("trace")))
                .thenReturn(outcome(List.of(
                        candidate(30L, "WEEKLY_REVIEW:30:PRIVATE:10", PRIVATE_SECRET))));

        ProjectHistoryToolResult result = execute(scope);

        assertEquals(1, result.evidence().size());
        assertTrue(result.evidence().get(0).text().contains(PRIVATE_SECRET));
    }

    private ProjectHistoryToolResult execute(ProjectAccessScope scope) {
        ToolExecutionContext context = new ToolExecutionContext(
                7L, "run-1", AgentSceneEnum.PROJECT_RISK,
                scope.projectId(), scope.teamId(), scope, "trace", 1, "token", 1L);
        return (ProjectHistoryToolResult) tool.execute(context, new ProjectHistoryArguments(null));
    }

    private RagRetrievalOutcome outcome(List<RagCandidate> candidates) {
        return new RagRetrievalOutcome(candidates, candidates.size(), candidates.size(),
                false, null, "stub-embedding", "stub-rerank");
    }

    private RagCandidate candidate(Long sourceId, String documentKey, String text) {
        return new RagCandidate(
                "candidate-" + sourceId,
                "point-" + sourceId,
                documentKey,
                KnowledgeSourceTypeEnum.WEEKLY_REVIEW,
                sourceId,
                0,
                "Review " + sourceId,
                text,
                "content-hash-" + sourceId,
                "payload-hash-" + sourceId,
                0.9,
                0.95,
                LocalDateTime.of(2026, 9, 9, 9, 0));
    }
}
