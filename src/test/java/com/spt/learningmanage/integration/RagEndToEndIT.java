package com.spt.learningmanage.integration;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.job.KnowledgeIndexWorker;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.dto.rag.RagAskRequest;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.service.RagService;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.service.VectorStoreClient;
import com.spt.learningmanage.service.knowledge.KnowledgeEventQueueService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired VectorStoreClient vectorStoreClient;
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
