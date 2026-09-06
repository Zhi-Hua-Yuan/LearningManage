package com.spt.learningmanage.integration;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spt.learningmanage.client.knowledge.KnowledgeRestTransport;
import com.spt.learningmanage.config.QdrantProperties;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.job.KnowledgeIndexWorker;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;
import com.spt.learningmanage.model.dto.knowledge.VectorAccessFilter;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchRequest;
import com.spt.learningmanage.model.dto.rag.RagAskRequest;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.EmbeddingClient;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.RagService;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.service.VectorSearchClient;
import com.spt.learningmanage.service.VectorStoreClient;
import com.spt.learningmanage.service.knowledge.KnowledgeEventQueueService;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import com.spt.learningmanage.service.rag.RagCandidateHydrator;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.scheduling.enabled=false",
        "ai.knowledge-index.worker-enabled=false",
        "ai.rag.enabled=true",
        "ai.rag.require-completed-backfill=false",
        "ai.rag.vector-score-threshold=-1",
        "ai.rag.question-hmac-secret=stage5-integration-hmac-secret-32-characters"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "STAGE5_RAG_IT_ENABLED", matches = "true")
class RagEndToEndIT {
    private static final long USER_ID = 9_950_001L;
    private static final long PROJECT_ID = 9_950_002L;
    private static final long OTHER_USER_ID = 9_950_003L;

    @Autowired UserMapper userMapper;
    @Autowired ProjectMapper projectMapper;
    @Autowired TaskMapper taskMapper;
    @Autowired TaskCreationService taskCreationService;
    @Autowired KnowledgeEventQueueService eventQueueService;
    @Autowired KnowledgeIndexWorker indexWorker;
    @Autowired KnowledgeIndexEventPublisher eventPublisher;
    @Autowired EmbeddingClient embeddingClient;
    @Autowired VectorSearchClient vectorSearchClient;
    @Autowired VectorStoreClient vectorStoreClient;
    @Autowired PermissionService permissionService;
    @Autowired RagCandidateHydrator candidateHydrator;
    @Autowired KnowledgeHashing hashing;
    @Autowired QdrantProperties qdrantProperties;
    @Autowired ObjectMapper objectMapper;
    @Autowired RagService ragService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    private Long taskId;
    private String documentKey;

    @BeforeEach
    void setUp() {
        cleanup();
        vectorStoreClient.ensureCollection();
        userMapper.insert(user(USER_ID, "stage5_rag_user"));
        userMapper.insert(user(OTHER_USER_ID, "stage5_rag_other"));
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setUserId(USER_ID);
        project.setName("Stage 5 RAG integration");
        project.setStatus(0);
        project.setOrderNo(0);
        project.setIsDelete(0);
        projectMapper.insert(project);

        Task task = new Task();
        task.setProjectId(PROJECT_ID);
        task.setTitle("完成权限感知检索");
        task.setDescription("实现 MySQL 二次鉴权、Rerank 和引用校验");
        task.setStatus(0);
        task.setPriority(3);
        task.setDeleteSource(0);
        task.setIsDelete(0);
        taskId = taskCreationService.createTask(task,
                new ProjectAccessScope(USER_ID, PROJECT_ID, USER_ID, null, null), USER_ID);
        documentKey = "TASK:" + taskId + ":PRIVATE:" + PROJECT_ID;
        processOnlyReadyEvent();
        assertFalse(vectorStoreClient.inspectByDocumentKey(documentKey).points().isEmpty(),
                "index worker must publish at least one Qdrant point");
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        if (documentKey != null) {
            vectorStoreClient.deleteByDocumentKey(documentKey);
        }
        cleanup();
    }

    @Test
    void indexedTaskProducesCitedAnswerWithoutPersistingQuestionOrEvidenceInAiLog() {
        UserHolder.set(USER_ID);
        assertRetrievable("这个项目的检索工作进展怎么样？");
        var answer = ragService.ask(request("这个项目的检索工作进展怎么样？"));

        assertEquals("ACTIVE", answer.getStatus());
        assertFalse(answer.getInsufficientEvidence());
        assertEquals(1, answer.getSources().size());
        assertEquals("TASK", answer.getSources().get(0).getSourceType());
        assertEquals(taskId, answer.getSources().get(0).getSourceId());
        assertTrue(answer.getAnswer().contains("[S1]"));
        assertEquals(1, count("ai_rag_query_log"));
        assertEquals(1, count("ai_rag_result"));
        assertEquals(1, resultSourceCount());

        String requestText = jdbcTemplate.queryForObject(
                "SELECT request_text FROM ai_call_log WHERE user_id=? AND scene='rag-project-ask' ORDER BY create_time DESC LIMIT 1",
                String.class, USER_ID);
        String responseText = jdbcTemplate.queryForObject(
                "SELECT response_text FROM ai_call_log WHERE user_id=? AND scene='rag-project-ask' ORDER BY create_time DESC LIMIT 1",
                String.class, USER_ID);
        assertFalse(requestText.contains("这个项目的检索工作进展怎么样"));
        assertFalse(requestText.contains("实现 MySQL 二次鉴权"));
        assertNull(responseText);

        var loaded = ragService.getResult(answer.getRequestId());
        assertEquals(answer.getAnswer(), loaded.getAnswer());
    }

    @Test
    void currentMysqlChangeMakesPersistedAnswerStaleBeforeVectorReconciliation() {
        UserHolder.set(USER_ID);
        assertRetrievable("检索任务进展怎么样？");
        var answer = ragService.ask(request("检索任务进展怎么样？"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, taskId)
                    .set(Task::getDescription, "内容已经更新，旧回答不再代表当前事实"));
            eventPublisher.publish(KnowledgeSourceTypeEnum.TASK, taskId,
                    KnowledgeEventTypeEnum.SOURCE_CHANGED);
        });

        var stale = ragService.getResult(answer.getRequestId());
        assertEquals("STALE", stale.getStatus());
        assertNull(stale.getAnswer());
    }

    @Test
    void outsiderIsDeniedBeforeAnyRagAuditRowIsCreated() {
        UserHolder.set(OTHER_USER_ID);
        long before = count("ai_rag_query_log");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ragService.ask(request("能看到这个项目吗？")));

        assertEquals(ErrorCode.FORBIDDEN_ERROR, exception.getErrorCode());
        assertEquals(before, count("ai_rag_query_log"));
    }

    private RagAskRequest request(String question) {
        RagAskRequest request = new RagAskRequest();
        request.setQuestion(question);
        request.setProjectId(PROJECT_ID);
        return request;
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

    private void processOnlyReadyEvent() {
        List<AiKnowledgeIndexEvent> events = eventQueueService.claimReady("stage5-rag-it", 10);
        assertEquals(1, events.size());
        indexWorker.process(events.get(0));
    }

    private void assertRetrievable(String question) {
        ProjectAccessScope scope = permissionService.requireProjectView(USER_ID, PROJECT_ID);
        var embedding = embeddingClient.embedQuery(question,
                new EmbeddingCallContext(USER_ID, "stage5-rag-it-diagnostic",
                        List.of(hashing.sha256(question))));
        var hits = vectorSearchClient.query(new VectorSearchRequest(
                embedding.vectors().get(0),
                new VectorAccessFilter(PROJECT_ID, USER_ID, scope.teamId()),
                100, -1));
        var snapshot = vectorStoreClient.inspectByDocumentKey(documentKey);
        assertFalse(hits.isEmpty(), () -> "permission-filtered Qdrant query returned no hits; snapshot="
                + snapshot.points() + "; probes=" + searchProbes(embedding.vectors().get(0)));
        var candidates = candidateHydrator.hydrate(USER_ID, scope, hits);
        assertFalse(candidates.isEmpty(), () -> "MySQL hydration/authorization removed all hits; hits="
                + hits + "; snapshot=" + snapshot.points());
    }

    private String searchProbes(List<Float> vector) {
        return "unfiltered=" + rawSearch(vector, null)
                + "; project=" + rawSearch(vector, match("projectId", PROJECT_ID))
                + "; visibility=" + rawSearch(vector, match("visibilityType", "PRIVATE"))
                + "; owner=" + rawSearch(vector, match("ownerUserId", USER_ID))
                + "; combined=" + rawSearch(vector, combinedAccessFilter());
    }

    private String rawSearch(List<Float> vector, ObjectNode filter) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("vector", objectMapper.valueToTree(vector));
        body.put("limit", 10);
        body.put("with_payload", true);
        body.put("with_vector", false);
        if (filter != null) {
            ObjectNode effectiveFilter = filter;
            if (!filter.has("must")) {
                effectiveFilter = objectMapper.createObjectNode();
                effectiveFilter.putArray("must").add(filter);
            }
            body.set("filter", effectiveFilter);
        }
        var response = new KnowledgeRestTransport(qdrantProperties.getBaseUrl(),
                qdrantProperties.getApiKey(), 3000, 10000).exchange(
                HttpMethod.POST,
                "/collections/" + qdrantProperties.getAlias() + "/points/search",
                body.toString(), false);
        return response.statusCode() + ":" + response.body();
    }

    private ObjectNode combinedAccessFilter() {
        ObjectNode filter = objectMapper.createObjectNode();
        filter.putArray("must")
                .add(match("projectId", PROJECT_ID))
                .add(match("visibilityType", "PRIVATE"))
                .add(match("ownerUserId", USER_ID));
        return filter;
    }

    private ObjectNode match(String key, Object value) {
        ObjectNode condition = objectMapper.createObjectNode();
        condition.put("key", key);
        ObjectNode match = condition.putObject("match");
        if (value instanceof Number number) {
            match.put("value", number.longValue());
        } else {
            match.put("value", String.valueOf(value));
        }
        return condition;
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE user_id=" + USER_ID, Long.class);
    }

    private long resultSourceCount() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ai_rag_result_source source
                JOIN ai_rag_result result ON result.id=source.result_id
                WHERE result.user_id=?
                """, Long.class, USER_ID);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM ai_rag_result_source WHERE result_id IN (SELECT id FROM ai_rag_result WHERE user_id IN (?,?))",
                USER_ID, OTHER_USER_ID);
        jdbcTemplate.update("DELETE FROM ai_rag_result WHERE user_id IN (?,?)", USER_ID, OTHER_USER_ID);
        jdbcTemplate.update("DELETE FROM ai_rag_query_log WHERE user_id IN (?,?)", USER_ID, OTHER_USER_ID);
        jdbcTemplate.update("DELETE FROM ai_call_log WHERE user_id IN (?,?)", USER_ID, OTHER_USER_ID);
        jdbcTemplate.update("DELETE FROM ai_knowledge_index_event WHERE source_id >= 9950000");
        jdbcTemplate.update("DELETE FROM ai_knowledge_source_lock WHERE source_id >= 9950000");
        jdbcTemplate.update("DELETE FROM ai_knowledge_document WHERE source_id >= 9950000");
        jdbcTemplate.update("DELETE FROM task_assignment_log WHERE task_id >= 9950000");
        jdbcTemplate.update("DELETE FROM task WHERE project_id=?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id=?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE id IN (?,?)", USER_ID, OTHER_USER_ID);
    }
}
