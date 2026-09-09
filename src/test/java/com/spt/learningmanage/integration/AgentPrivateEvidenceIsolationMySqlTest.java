package com.spt.learningmanage.integration;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.KnowledgeVisibilityTypeEnum;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.job.AgentRunWorker;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.dto.agent.AgentProjectRiskRequest;
import com.spt.learningmanage.model.dto.agent.AgentReportConfirmRequest;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingBatchResult;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchHit;
import com.spt.learningmanage.model.dto.rag.RerankItem;
import com.spt.learningmanage.model.dto.rag.RerankRequest;
import com.spt.learningmanage.model.dto.rag.RerankResult;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.knowledge.KnowledgeDocumentProjection;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.rag.RagCandidate;
import com.spt.learningmanage.service.AgentReportService;
import com.spt.learningmanage.service.AgentRunService;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.service.EmbeddingClient;
import com.spt.learningmanage.service.KnowledgeDocumentFactory;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.RerankClient;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.service.VectorSearchClient;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import com.spt.learningmanage.service.rag.RagCandidateHydrator;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = LearningManageApplication.class, properties = {
        "app.scheduling.enabled=false",
        "ai.agent.enabled=true",
        "ai.agent.worker-enabled=false",
        "ai.agent.tool-calling-enabled=false",
        "ai.agent.overall-timeout-seconds=30",
        "ai.rag.enabled=true",
        "ai.rag.require-completed-backfill=false",
        "ai.rag.vector-score-threshold=0.0",
        "ai.rag.rerank-score-threshold=0.0",
        "ai.rag.question-hmac-secret=private-evidence-isolation-test-secret-32-bytes"
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "STAGE6_AGENT_IT_ENABLED", matches = "true")
class AgentPrivateEvidenceIsolationMySqlTest {
    private static final String PRIVATE_SECRET = "PRIVATE_SECRET_7F92";
    private static final String SAFE_SUMMARY = "TEAM_SAFE_SUMMARY";
    private static final long OWNER_ID = 2_096_910_000_000_001L;
    private static final long AUTHOR_ID = 2_096_910_000_000_002L;
    private static final long READER_ID = 2_096_910_000_000_003L;
    private static final long TEAM_ID = 2_096_910_000_000_010L;
    private static final long PROJECT_ID = 2_096_910_000_000_020L;
    private static final long TASK_ID = 2_096_910_000_000_030L;
    private static final long REVIEW_ID = 2_096_910_000_000_040L;

    @Autowired UserMapper userMapper;
    @Autowired TeamMapper teamMapper;
    @Autowired TeamMemberMapper teamMemberMapper;
    @Autowired ProjectMapper projectMapper;
    @Autowired WeeklyReviewMapper weeklyReviewMapper;
    @Autowired TaskCreationService taskCreationService;
    @Autowired PermissionService permissionService;
    @Autowired KnowledgeDocumentFactory documentFactory;
    @Autowired KnowledgeHashing hashing;
    @Autowired RagCandidateHydrator hydrator;
    @Autowired AgentRunService runService;
    @Autowired AgentRunQueueService queueService;
    @Autowired AgentRunWorker worker;
    @Autowired AgentReportService reportService;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockBean AiModelClient aiModelClient;
    @MockBean EmbeddingClient embeddingClient;
    @MockBean VectorSearchClient vectorSearchClient;
    @MockBean RerankClient rerankClient;

    private final AtomicReference<List<VectorSearchHit>> vectorHits = new AtomicReference<>(List.of());
    private final AtomicReference<String> modelPrompt = new AtomicReference<>("");

    @BeforeEach
    void setUp() {
        cleanup();
        configureDeterministicProviders();

        userMapper.insert(user(OWNER_ID, "private_isolation_owner"));
        userMapper.insert(user(AUTHOR_ID, "private_isolation_author"));
        userMapper.insert(user(READER_ID, "private_isolation_reader"));

        Team team = new Team();
        team.setId(TEAM_ID);
        team.setName("Private evidence isolation team");
        team.setOwnerId(OWNER_ID);
        team.setInviteCode("PRIV0091");
        team.setIsDelete(0);
        teamMapper.insert(team);
        teamMemberMapper.insert(member(OWNER_ID, "OWNER"));
        teamMemberMapper.insert(member(AUTHOR_ID, "MEMBER"));
        teamMemberMapper.insert(member(READER_ID, "MEMBER"));

        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setUserId(OWNER_ID);
        project.setTeamId(TEAM_ID);
        project.setName("Private evidence isolation project");
        project.setStatus(0);
        project.setProgress(BigDecimal.ZERO);
        project.setOrderNo(0);
        project.setIsDelete(0);
        projectMapper.insert(project);

        Task task = new Task();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setTitle("Safe structured task");
        task.setDescription("No private review content");
        task.setStatus(0);
        task.setPriority(2);
        task.setDueDate(LocalDate.now().plusDays(3));
        task.setDeleteSource(0);
        task.setIsDelete(0);
        taskCreationService.createTask(task, ownerScope(), AUTHOR_ID);

        WeeklyReview review = new WeeklyReview();
        review.setId(REVIEW_ID);
        review.setUserId(AUTHOR_ID);
        review.setYear(2026);
        review.setWeekNo(37);
        review.setStartDate(LocalDate.of(2026, 9, 7));
        review.setEndDate(LocalDate.of(2026, 9, 13));
        review.setCompletedTaskCount(0);
        review.setVisibilityScope("PRIVATE");
        review.setFocusProjectId(PROJECT_ID);
        review.setFocusProjectName(project.getName());
        review.setReflection(PRIVATE_SECRET);
        review.setNextPlan("synthetic private next plan");
        weeklyReviewMapper.insert(review);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanup();
        Mockito.reset(aiModelClient, embeddingClient, vectorSearchClient, rerankClient);
    }

    @Test
    void teamProjectRiskExcludesPrivateEvidenceBeforePromptAndSharedReport() {
        KnowledgeDocumentProjection privateProjection = documentFactory.buildDesiredDocuments(
                        new KnowledgeSourceRef(KnowledgeSourceTypeEnum.WEEKLY_REVIEW, REVIEW_ID))
                .stream()
                .filter(value -> value.visibilityType() == KnowledgeVisibilityTypeEnum.PRIVATE)
                .findFirst()
                .orElseThrow();
        assertTrue(privateProjection.canonicalText().contains(PRIVATE_SECRET));
        assertTrue(permissionService.filterReadableWeeklyReviewIds(AUTHOR_ID, List.of(REVIEW_ID))
                .contains(REVIEW_ID));
        assertFalse(permissionService.filterReadableWeeklyReviewIds(READER_ID, List.of(REVIEW_ID))
                .contains(REVIEW_ID));

        VectorSearchHit hit = currentHit(privateProjection);
        List<RagCandidate> selfCandidates = hydrator.hydrate(AUTHOR_ID,
                permissionService.requireProjectView(AUTHOR_ID, PROJECT_ID), List.of(hit));
        assertEquals(1, selfCandidates.size(), "ordinary self retrieval remains available");
        assertTrue(selfCandidates.get(0).text().contains(PRIVATE_SECRET));
        vectorHits.set(List.of(hit));

        UserHolder.set(AUTHOR_ID);
        AgentProjectRiskRequest request = new AgentProjectRiskRequest();
        request.setProjectId(PROJECT_ID);
        request.setClientRequestId("private-evidence-isolation-run");
        var submitted = runService.submitProjectRisk(request);
        var claimed = queueService.claimReady("private-evidence-isolation-worker", 1);
        assertEquals(1, claimed.size());
        worker.process(claimed.get(0));

        var run = runService.getRun(submitted.runId());
        assertEquals("SUCCEEDED", run.status());
        assertNotNull(run.draftId());
        assertFalse(modelPrompt.get().contains(PRIVATE_SECRET));
        String draftPayload = jdbcTemplate.queryForObject(
                "SELECT payload_json FROM ai_draft WHERE draft_id=?", String.class, run.draftId());
        assertNotNull(draftPayload);
        assertFalse(draftPayload.contains(PRIVATE_SECRET));

        AgentReportConfirmRequest confirm = new AgentReportConfirmRequest();
        confirm.setDraftId(run.draftId());
        confirm.setOperationId("private-evidence-isolation-confirm");
        var confirmed = reportService.confirm(confirm);
        String reportId = jdbcTemplate.queryForObject(
                "SELECT report_id FROM ai_analysis_report WHERE id=?", String.class,
                confirmed.getBusinessId());

        UserHolder.set(READER_ID);
        var report = reportService.get(reportId);
        assertEquals(SAFE_SUMMARY, report.summary());
        assertFalse(report.summary().contains(PRIVATE_SECRET));
        assertTrue(report.recommendations().stream()
                .noneMatch(value -> value.contains(PRIVATE_SECRET)));
        assertTrue(report.sources().isEmpty());

        System.out.println("P0_PRIVATE_EVIDENCE_ISOLATION selfRag=true promptSecret=false "
                + "draftSecret=false reportSecret=false readerBSecret=false");
    }

    private void configureDeterministicProviders() {
        vectorHits.set(List.of());
        modelPrompt.set("");
        when(embeddingClient.embedQuery(any(), any())).thenReturn(new EmbeddingBatchResult(
                List.of(List.of(0.10f, 0.20f, 0.30f)), "stub-embedding", 3L, 3L,
                "stub-embedding-request"));
        when(vectorSearchClient.query(any())).thenAnswer(invocation -> vectorHits.get());
        when(rerankClient.rerank(any(RerankRequest.class))).thenAnswer(invocation -> {
            RerankRequest request = invocation.getArgument(0);
            List<RerankItem> items = IntStream.range(0, request.candidates().size())
                    .mapToObj(index -> new RerankItem(
                            request.candidates().get(index).candidateId(), index, 0.99 - index * 0.01))
                    .toList();
            return new RerankResult(items, "stub-rerank", 1L, "stub-rerank-request");
        });
        when(aiModelClient.chat(any(AiChatCommand.class))).thenAnswer(invocation -> {
            AiChatCommand command = invocation.getArgument(0);
            String prompt = command.messages().stream()
                    .map(message -> message.content() == null ? "" : message.content())
                    .reduce("", (left, right) -> left + "\n" + right);
            modelPrompt.set(prompt);
            String summary = prompt.contains(PRIVATE_SECRET) ? PRIVATE_SECRET : SAFE_SUMMARY;
            String json = """
                    {"riskLevel":"LOW","summary":"%s","riskItems":[{"category":"HISTORY","severity":"LOW","reason":"%s","impact":"synthetic impact","recommendation":"%s","evidenceIds":[]}],"positiveSignals":[],"insufficientEvidence":false,"citations":[]}
                    """.formatted(summary, summary, summary);
            return new AiChatResult(json, List.of(), "stop", new AiUsage(10, 10, 20),
                    "stub-chat-request", command.requestedModel(), command.requestedModel(),
                    0, false, null);
        });
    }

    private VectorSearchHit currentHit(KnowledgeDocumentProjection projection) {
        Map<String, Object> payload = new LinkedHashMap<>(projection.payload());
        payload.put("documentKey", projection.documentKey());
        payload.put("chunkIndex", 0);
        payload.put("sourceVersion", hashing.contentHash(projection.canonicalText()) + ':'
                + hashing.payloadHash(projection.payload()));
        return new VectorSearchHit("private-isolation-review-point", 0.99, payload);
    }

    private ProjectAccessScope ownerScope() {
        return new ProjectAccessScope(OWNER_ID, PROJECT_ID, OWNER_ID, TEAM_ID, TeamRoleEnum.OWNER);
    }

    private User user(long id, String account) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setUsername(account);
        user.setPassword("not-a-real-password-hash");
        user.setUserRole("USER");
        user.setIsDelete(0);
        return user;
    }

    private TeamMember member(long userId, String role) {
        TeamMember member = new TeamMember();
        member.setTeamId(TEAM_ID);
        member.setUserId(userId);
        member.setRole(role);
        member.setIsDelete(0);
        return member;
    }

    private void cleanup() {
        UserHolder.remove();
        jdbcTemplate.update("DELETE FROM ai_analysis_report_source WHERE report_id IN "
                + "(SELECT report_id FROM ai_analysis_report WHERE creator_user_id IN (?,?,?))",
                OWNER_ID, AUTHOR_ID, READER_ID);
        jdbcTemplate.update("DELETE FROM ai_analysis_report WHERE creator_user_id IN (?,?,?)",
                OWNER_ID, AUTHOR_ID, READER_ID);
        jdbcTemplate.update("DELETE FROM ai_draft_confirm_log WHERE user_id IN (?,?,?)",
                OWNER_ID, AUTHOR_ID, READER_ID);
        jdbcTemplate.update("DELETE FROM ai_draft WHERE user_id IN (?,?,?)",
                OWNER_ID, AUTHOR_ID, READER_ID);
        jdbcTemplate.update("DELETE FROM ai_agent_tool_log WHERE run_id IN "
                + "(SELECT run_id FROM ai_agent_run WHERE user_id IN (?,?,?))",
                OWNER_ID, AUTHOR_ID, READER_ID);
        jdbcTemplate.update("DELETE FROM ai_call_log WHERE user_id IN (?,?,?)",
                OWNER_ID, AUTHOR_ID, READER_ID);
        jdbcTemplate.update("DELETE FROM ai_agent_run WHERE user_id IN (?,?,?)",
                OWNER_ID, AUTHOR_ID, READER_ID);
        jdbcTemplate.update("DELETE FROM ai_knowledge_index_event WHERE source_id IN (?,?)",
                TASK_ID, REVIEW_ID);
        jdbcTemplate.update("DELETE FROM task_assignment_log WHERE task_id=?", TASK_ID);
        jdbcTemplate.update("DELETE FROM weekly_review_task WHERE weekly_review_id=?", REVIEW_ID);
        jdbcTemplate.update("DELETE FROM weekly_review WHERE id=?", REVIEW_ID);
        jdbcTemplate.update("DELETE FROM task WHERE id=?", TASK_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id=?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM team_member WHERE team_id=?", TEAM_ID);
        jdbcTemplate.update("DELETE FROM team WHERE id=?", TEAM_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE id IN (?,?,?)",
                OWNER_ID, AUTHOR_ID, READER_ID);
    }
}
