package com.spt.learningmanage.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.constant.KnowledgeDocumentStatusEnum;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.mapper.AiKnowledgeDocumentMapper;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeDocument;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.service.VectorStoreClient;
import com.spt.learningmanage.service.knowledge.KnowledgeEventQueueService;
import com.spt.learningmanage.job.KnowledgeIndexWorker;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "ai.knowledge-index.worker-enabled=false")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "STAGE4_KNOWLEDGE_IT_ENABLED", matches = "true")
class KnowledgeIndexEndToEndIT {

    private static final long USER_ID = 9_940_001L;
    private static final long PROJECT_ID = 9_940_002L;

    @Autowired private UserMapper userMapper;
    @Autowired private ProjectMapper projectMapper;
    @Autowired private TaskMapper taskMapper;
    @Autowired private TaskCreationService taskCreationService;
    @Autowired private KnowledgeIndexEventPublisher eventPublisher;
    @Autowired private AiKnowledgeIndexEventMapper eventMapper;
    @Autowired private AiKnowledgeDocumentMapper documentMapper;
    @Autowired private KnowledgeEventQueueService queueService;
    @Autowired private KnowledgeIndexWorker worker;
    @Autowired private VectorStoreClient vectorStoreClient;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<Long> createdTaskIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanupDatabase();
        vectorStoreClient.ensureCollection();
        User user = new User();
        user.setId(USER_ID);
        user.setAccount("stage4_it_user");
        user.setUsername("stage4-it");
        user.setPassword("not-a-real-password-hash");
        user.setUserRole("USER");
        user.setIsDelete(0);
        userMapper.insert(user);

        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setUserId(USER_ID);
        project.setName("Stage 4 integration");
        project.setStatus(0);
        project.setOrderNo(0);
        project.setIsDelete(0);
        projectMapper.insert(project);
    }

    @AfterEach
    void tearDown() {
        for (Long createdTaskId : createdTaskIds) {
            vectorStoreClient.deleteByDocumentKey("TASK:" + createdTaskId + ":PRIVATE:" + PROJECT_ID);
        }
        createdTaskIds.clear();
        cleanupDatabase();
    }

    @Test
    void createPayloadUpdateAndDeleteConvergeWithoutDuplicatePoints() {
        Task task = new Task();
        task.setProjectId(PROJECT_ID);
        task.setTitle("Build transactional knowledge indexing");
        task.setDescription("Verify MySQL outbox and Qdrant convergence");
        task.setStatus(0);
        task.setPriority(2);
        task.setIsDelete(0);
        task.setDeleteSource(0);
        Long taskId = taskCreationService.createTask(task,
                new ProjectAccessScope(USER_ID, PROJECT_ID, USER_ID, null, null), USER_ID);
        createdTaskIds.add(taskId);

        processOnlyReadyEvent();
        String key = "TASK:" + taskId + ":PRIVATE:" + PROJECT_ID;
        AiKnowledgeDocument indexed = documentMapper.selectByDocumentKey(key);
        assertNotNull(indexed);
        assertEquals(KnowledgeDocumentStatusEnum.INDEXED.name(), indexed.getStatus());
        assertEquals(1, vectorStoreClient.inspectByDocumentKey(key).points().size());
        long embeddingLogs = embeddingLogCount();
        assertEquals(1, embeddingLogs);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, taskId)
                    .set(Task::getStatus, 1));
            eventPublisher.publish(KnowledgeSourceTypeEnum.TASK, taskId,
                    KnowledgeEventTypeEnum.SOURCE_CHANGED);
        });
        processOnlyReadyEvent();
        assertEquals(embeddingLogs, embeddingLogCount(), "payload-only update must not re-embed");
        assertEquals(1, vectorStoreClient.inspectByDocumentKey(key).points().size());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, taskId)
                    .set(Task::getIsDelete, 1));
            eventPublisher.publish(KnowledgeSourceTypeEnum.TASK, taskId,
                    KnowledgeEventTypeEnum.SOURCE_DELETED);
        });
        processOnlyReadyEvent();
        assertEquals(KnowledgeDocumentStatusEnum.DELETED.name(),
                documentMapper.selectByDocumentKey(key).getStatus());
        assertEquals(0, vectorStoreClient.inspectByDocumentKey(key).points().size());
    }

    @Test
    void hundredEventBacklogMeetsTheSixtySecondFreshnessP95() throws Exception {
        int sourceCount = 100;
        for (int index = 0; index < sourceCount; index++) {
            Task task = new Task();
            task.setProjectId(PROJECT_ID);
            task.setTitle("Freshness task " + index);
            task.setDescription("Representative stage4 backlog item " + index);
            task.setStatus(0);
            task.setPriority(index % 4);
            task.setIsDelete(0);
            task.setDeleteSource(0);
            Long created = taskCreationService.createTask(task,
                    new ProjectAccessScope(USER_ID, PROJECT_ID, USER_ID, null, null), USER_ID);
            createdTaskIds.add(created);
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        int processed = 0;
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        try {
            while (processed < sourceCount && System.nanoTime() < deadline) {
                List<AiKnowledgeIndexEvent> claimed = queueService.claimReady("stage4-freshness-it", 20);
                if (claimed.isEmpty()) {
                    Thread.sleep(20);
                    continue;
                }
                List<Future<?>> futures = new ArrayList<>(claimed.size());
                for (AiKnowledgeIndexEvent event : claimed) {
                    futures.add(executor.submit(() -> worker.process(event)));
                }
                for (Future<?> future : futures) {
                    future.get(60, TimeUnit.SECONDS);
                }
                processed += claimed.size();
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(sourceCount, processed, "all representative backlog events must finish");

        List<Long> freshnessMillis = jdbcTemplate.queryForList("""
                SELECT TIMESTAMPDIFF(MICROSECOND, event.create_time, document.indexed_at) DIV 1000
                FROM ai_knowledge_index_event event
                JOIN ai_knowledge_document document
                  ON document.source_type = event.source_type
                 AND document.source_id = event.source_id
                WHERE event.source_type = 'TASK'
                  AND event.source_id >= 9940000
                  AND event.event_type = 'SOURCE_CHANGED'
                  AND event.status = 'SUCCESS'
                  AND document.status = 'INDEXED'
                """, Long.class);
        assertEquals(sourceCount, freshnessMillis.size());
        Collections.sort(freshnessMillis);
        long p95Millis = freshnessMillis.get((int) Math.ceil(sourceCount * 0.95) - 1);
        assertTrue(p95Millis <= 60_000,
                "event-to-index freshness P95 exceeded 60 seconds: " + p95Millis + "ms");
    }

    private void processOnlyReadyEvent() {
        List<AiKnowledgeIndexEvent> events = queueService.claimReady("stage4-it", 10);
        assertEquals(1, events.size());
        worker.process(events.get(0));
        assertEquals("SUCCESS", eventMapper.selectById(events.get(0).getId()).getStatus());
    }

    private long embeddingLogCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_log WHERE user_id=? AND scene='knowledge-index'",
                Long.class, USER_ID);
    }

    private void cleanupDatabase() {
        jdbcTemplate.update("DELETE FROM ai_knowledge_index_event WHERE source_id >= 9940000");
        jdbcTemplate.update("DELETE FROM ai_knowledge_source_lock WHERE source_id >= 9940000");
        jdbcTemplate.update("DELETE FROM ai_knowledge_document WHERE source_id >= 9940000");
        jdbcTemplate.update("DELETE FROM ai_call_log WHERE user_id=? AND scene='knowledge-index'", USER_ID);
        jdbcTemplate.update("DELETE FROM task_assignment_log WHERE task_id >= 9940000");
        jdbcTemplate.update("DELETE FROM task WHERE project_id=?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id=?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE id=?", USER_ID);
    }
}
