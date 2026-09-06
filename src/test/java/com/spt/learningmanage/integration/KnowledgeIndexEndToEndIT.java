package com.spt.learningmanage.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.constant.KnowledgeBackfillStatusEnum;
import com.spt.learningmanage.constant.KnowledgeDocumentStatusEnum;
import com.spt.learningmanage.constant.KnowledgeEventStatusEnum;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.job.KnowledgeBackfillCompletionJob;
import com.spt.learningmanage.job.KnowledgeBackfillJob;
import com.spt.learningmanage.job.KnowledgeIndexWorker;
import com.spt.learningmanage.mapper.AiKnowledgeBackfillRunMapper;
import com.spt.learningmanage.mapper.AiKnowledgeDocumentMapper;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.dto.knowledge.KnowledgeBackfillCreateRequest;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import com.spt.learningmanage.model.entity.AiKnowledgeDocument;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.KnowledgeAdminService;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
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
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "ai.knowledge-index.worker-enabled=false",
        "app.scheduling.enabled=false"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "STAGE4_KNOWLEDGE_IT_ENABLED", matches = "true")
class KnowledgeIndexEndToEndIT {

    private static final long USER_ID = 9_940_001L;
    private static final long PROJECT_ID = 9_940_002L;
    private static final long SECOND_USER_ID = 9_940_003L;
    private static final long TEAM_ID = 9_940_004L;
    private static final long TEAM_MEMBER_ID = 9_940_005L;
    private static final long TEAM_PROJECT_ID = 9_940_006L;
    private static final long REVIEW_ID = 9_940_007L;

    @Autowired private UserMapper userMapper;
    @Autowired private ProjectMapper projectMapper;
    @Autowired private TaskMapper taskMapper;
    @Autowired private WeeklyReviewMapper weeklyReviewMapper;
    @Autowired private TaskCreationService taskCreationService;
    @Autowired private KnowledgeIndexEventPublisher eventPublisher;
    @Autowired private AiKnowledgeIndexEventMapper eventMapper;
    @Autowired private AiKnowledgeBackfillRunMapper backfillRunMapper;
    @Autowired private AiKnowledgeDocumentMapper documentMapper;
    @Autowired private KnowledgeEventQueueService queueService;
    @Autowired private KnowledgeIndexWorker worker;
    @Autowired private KnowledgeBackfillJob backfillJob;
    @Autowired private KnowledgeBackfillCompletionJob backfillCompletionJob;
    @Autowired private KnowledgeAdminService knowledgeAdminService;
    @Autowired private KnowledgeIndexProperties knowledgeIndexProperties;
    @Autowired private EmbeddingProperties embeddingProperties;
    @Autowired private VectorStoreClient vectorStoreClient;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ApplicationContext applicationContext;

    private final List<Long> createdTaskIds = new ArrayList<>();
    private final List<String> createdDocumentKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        assertFalse(applicationContext.containsBean(
                TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME),
                "manual-worker integration context must not register scheduled polling");
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
        UserHolder.remove();
        for (Long createdTaskId : createdTaskIds) {
            vectorStoreClient.deleteByDocumentKey("TASK:" + createdTaskId + ":PRIVATE:" + PROJECT_ID);
        }
        for (String documentKey : createdDocumentKeys) {
            vectorStoreClient.deleteByDocumentKey(documentKey);
        }
        createdTaskIds.clear();
        createdDocumentKeys.clear();
        embeddingProperties.setModel("text-embedding-v4");
        knowledgeIndexProperties.setWorkerEnabled(false);
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
        var payload = vectorStoreClient.inspectByDocumentKey(key).points().get(0).payload();
        assertEquals(Long.toString(USER_ID), payload.get("userId").toString());
        assertTrue(payload.containsKey("sourceVersion"));
        assertTrue(payload.containsKey("updatedAt"));
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

    @Test
    void initialBackfillAndForcedRebuildAreIdempotentAndReconcileExactly() {
        Long firstTaskId = createTask("Backfill source one");
        Long secondTaskId = createTask("Backfill source two");
        jdbcTemplate.update("DELETE FROM ai_knowledge_index_event WHERE source_id IN (?, ?)",
                firstTaskId, secondTaskId);

        knowledgeIndexProperties.setWorkerEnabled(true);
        grantSystemAdmin();
        try {
            KnowledgeBackfillCreateRequest initial = backfillRequest("stage4-it-initial", "INITIAL");
            var created = knowledgeAdminService.createBackfill(initial);
            var replay = knowledgeAdminService.createBackfill(initial);
            assertEquals(created.getRunId(), replay.getRunId());
            assertTrue(replay.isIdempotentReplay());

            backfillJob.run();
            assertEquals(2, drainAllReadyEvents());
            backfillCompletionJob.monitor();
            assertSuccessfulBackfill(created.getRunId(), 2);
            assertExactlyOnePoint(firstTaskId);
            assertExactlyOnePoint(secondTaskId);
            long afterInitialEmbeddingCalls = embeddingLogCount();
            assertEquals(2, afterInitialEmbeddingCalls);

            var rebuild = knowledgeAdminService.createBackfill(
                    backfillRequest("stage4-it-rebuild", "REBUILD"));
            backfillJob.run();
            assertEquals(2, drainAllReadyEvents());
            backfillCompletionJob.monitor();
            assertSuccessfulBackfill(rebuild.getRunId(), 2);
            assertExactlyOnePoint(firstTaskId);
            assertExactlyOnePoint(secondTaskId);
            assertEquals(afterInitialEmbeddingCalls + 2, embeddingLogCount(),
                    "REBUILD must force one fresh embedding call per document");
        } finally {
            UserHolder.remove();
            knowledgeIndexProperties.setWorkerEnabled(false);
        }
    }

    @Test
    void visibilityAndMembershipContractionRemoveUnauthorizedReviewPoints() {
        createTeamReviewFixture();
        String privateKey = "WEEKLY_REVIEW:" + REVIEW_ID + ":PRIVATE:" + TEAM_PROJECT_ID;
        String teamKey = "WEEKLY_REVIEW:" + REVIEW_ID + ":TEAM:" + TEAM_PROJECT_ID;
        createdDocumentKeys.add(privateKey);
        createdDocumentKeys.add(teamKey);

        publishReviewChange();
        processOnlyReadyEvent();
        assertEquals(1, vectorStoreClient.inspectByDocumentKey(privateKey).points().size());
        assertEquals(1, vectorStoreClient.inspectByDocumentKey(teamKey).points().size());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            weeklyReviewMapper.update(null, new LambdaUpdateWrapper<WeeklyReview>()
                    .eq(WeeklyReview::getId, REVIEW_ID)
                    .set(WeeklyReview::getVisibilityScope, "PRIVATE")
                    .set(WeeklyReview::getTeamId, null));
            eventPublisher.publish(KnowledgeSourceTypeEnum.WEEKLY_REVIEW, REVIEW_ID,
                    KnowledgeEventTypeEnum.ACCESS_CHANGED);
        });
        processOnlyReadyEvent();
        assertEquals(1, vectorStoreClient.inspectByDocumentKey(privateKey).points().size());
        assertEquals(0, vectorStoreClient.inspectByDocumentKey(teamKey).points().size());
        assertEquals(KnowledgeDocumentStatusEnum.DELETED.name(),
                documentMapper.selectByDocumentKey(teamKey).getStatus());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            weeklyReviewMapper.update(null, new LambdaUpdateWrapper<WeeklyReview>()
                    .eq(WeeklyReview::getId, REVIEW_ID)
                    .set(WeeklyReview::getVisibilityScope, "TEAM")
                    .set(WeeklyReview::getTeamId, TEAM_ID));
            eventPublisher.publish(KnowledgeSourceTypeEnum.WEEKLY_REVIEW, REVIEW_ID,
                    KnowledgeEventTypeEnum.ACCESS_CHANGED);
        });
        processOnlyReadyEvent();
        assertEquals(1, vectorStoreClient.inspectByDocumentKey(teamKey).points().size());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("UPDATE team_member SET is_delete=1, deleted_at=NOW() WHERE id=?",
                    TEAM_MEMBER_ID);
            eventPublisher.publish(KnowledgeSourceTypeEnum.WEEKLY_REVIEW, REVIEW_ID,
                    KnowledgeEventTypeEnum.ACCESS_CHANGED);
        });
        processOnlyReadyEvent();
        assertEquals(0, vectorStoreClient.inspectByDocumentKey(privateKey).points().size());
        assertEquals(0, vectorStoreClient.inspectByDocumentKey(teamKey).points().size());
        assertEquals(KnowledgeDocumentStatusEnum.DELETED.name(),
                documentMapper.selectByDocumentKey(privateKey).getStatus());
        assertEquals(KnowledgeDocumentStatusEnum.DELETED.name(),
                documentMapper.selectByDocumentKey(teamKey).getStatus());
    }

    @Test
    void transientAndPermanentEmbeddingFailuresAreAuditableAndRecoverable() {
        Long retryTaskId = createTask("Retryable embedding failure");
        embeddingProperties.setModel("stub-status-429");
        AiKnowledgeIndexEvent retryEvent = claimOnlyReadyEvent();
        worker.process(retryEvent);
        AiKnowledgeIndexEvent waiting = eventMapper.selectById(retryEvent.getId());
        assertEquals(KnowledgeEventStatusEnum.RETRY_WAIT.name(), waiting.getStatus());
        assertEquals("RATE_LIMIT", waiting.getFailureType());
        assertNotNull(taskMapper.selectById(retryTaskId),
                "provider failure must not roll back committed business data");

        embeddingProperties.setModel("text-embedding-v4");
        jdbcTemplate.update("UPDATE ai_knowledge_index_event SET next_attempt_at=DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE id=?",
                retryEvent.getId());
        AiKnowledgeIndexEvent retried = claimOnlyReadyEvent();
        worker.process(retried);
        assertEquals(KnowledgeEventStatusEnum.SUCCESS.name(),
                eventMapper.selectById(retryEvent.getId()).getStatus());
        assertExactlyOnePoint(retryTaskId);

        Long deadTaskId = createTask("Permanent embedding protocol failure");
        embeddingProperties.setModel("stub-missing-model");
        AiKnowledgeIndexEvent deadEvent = claimOnlyReadyEvent();
        worker.process(deadEvent);
        AiKnowledgeIndexEvent dead = eventMapper.selectById(deadEvent.getId());
        assertEquals(KnowledgeEventStatusEnum.DEAD.name(), dead.getStatus());
        assertEquals("EMBEDDING_PROTOCOL", dead.getFailureType());
        assertNotNull(taskMapper.selectById(deadTaskId));

        grantSystemAdmin();
        try {
            assertTrue(knowledgeAdminService.replayEvent(deadEvent.getId()));
        } finally {
            UserHolder.remove();
        }
        embeddingProperties.setModel("text-embedding-v4");
        AiKnowledgeIndexEvent replayed = claimOnlyReadyEvent();
        worker.process(replayed);
        assertEquals(KnowledgeEventStatusEnum.SUCCESS.name(),
                eventMapper.selectById(deadEvent.getId()).getStatus());
        assertExactlyOnePoint(deadTaskId);
    }

    private Long createTask(String title) {
        Task task = new Task();
        task.setProjectId(PROJECT_ID);
        task.setTitle(title);
        task.setDescription("Stage 4 final acceptance source");
        task.setStatus(0);
        task.setPriority(1);
        task.setIsDelete(0);
        task.setDeleteSource(0);
        Long taskId = taskCreationService.createTask(task,
                new ProjectAccessScope(USER_ID, PROJECT_ID, USER_ID, null, null), USER_ID);
        createdTaskIds.add(taskId);
        return taskId;
    }

    private KnowledgeBackfillCreateRequest backfillRequest(String runKey, String runType) {
        KnowledgeBackfillCreateRequest request = new KnowledgeBackfillCreateRequest();
        request.setRunKey(runKey);
        request.setRunType(runType);
        request.setSourceScope("TASK");
        request.setBatchSize(100);
        return request;
    }

    private void assertSuccessfulBackfill(Long runId, long expected) {
        AiKnowledgeBackfillRun run = backfillRunMapper.selectById(runId);
        assertEquals(KnowledgeBackfillStatusEnum.SUCCEEDED.name(), run.getStatus());
        assertEquals(expected, run.getDiscoveredCount());
        assertEquals(expected, run.getEnqueuedCount());
        assertEquals(expected, run.getSuccessCount());
        assertEquals(0L, run.getDeadCount());
    }

    private void assertExactlyOnePoint(Long taskId) {
        String key = "TASK:" + taskId + ":PRIVATE:" + PROJECT_ID;
        assertEquals(1, vectorStoreClient.inspectByDocumentKey(key).points().size());
    }

    private int drainAllReadyEvents() {
        int processed = 0;
        while (true) {
            List<AiKnowledgeIndexEvent> events = queueService.claimReady("stage4-backfill-it", 50);
            if (events.isEmpty()) {
                return processed;
            }
            for (AiKnowledgeIndexEvent event : events) {
                worker.process(event);
                assertEquals(KnowledgeEventStatusEnum.SUCCESS.name(),
                        eventMapper.selectById(event.getId()).getStatus());
                processed++;
            }
        }
    }

    private AiKnowledgeIndexEvent claimOnlyReadyEvent() {
        List<AiKnowledgeIndexEvent> events = queueService.claimReady("stage4-failure-it", 10);
        assertEquals(1, events.size());
        return events.get(0);
    }

    private void grantSystemAdmin() {
        jdbcTemplate.update("UPDATE `user` SET user_role='SYSTEM_ADMIN' WHERE id=?", USER_ID);
        UserHolder.set(USER_ID);
    }

    private void createTeamReviewFixture() {
        User member = new User();
        member.setId(SECOND_USER_ID);
        member.setAccount("stage4_it_member");
        member.setUsername("stage4-it-member");
        member.setPassword("not-a-real-password-hash");
        member.setUserRole("USER");
        member.setIsDelete(0);
        userMapper.insert(member);
        jdbcTemplate.update("INSERT INTO team(id,name,owner_id,invite_code,is_delete) VALUES(?,?,?,?,0)",
                TEAM_ID, "Stage 4 team", USER_ID, "stage4-it-team");
        jdbcTemplate.update("INSERT INTO team_member(id,team_id,user_id,role,is_delete) VALUES(?,?,?,?,0)",
                TEAM_MEMBER_ID, TEAM_ID, SECOND_USER_ID, "MEMBER");

        Project project = new Project();
        project.setId(TEAM_PROJECT_ID);
        project.setUserId(USER_ID);
        project.setTeamId(TEAM_ID);
        project.setName("Stage 4 team project");
        project.setStatus(0);
        project.setOrderNo(0);
        project.setIsDelete(0);
        projectMapper.insert(project);

        WeeklyReview review = new WeeklyReview();
        review.setId(REVIEW_ID);
        review.setUserId(SECOND_USER_ID);
        review.setYear(2026);
        review.setWeekNo(36);
        review.setStartDate(LocalDate.of(2026, 8, 31));
        review.setEndDate(LocalDate.of(2026, 9, 6));
        review.setCompletedTaskCount(1);
        review.setVisibilityScope("TEAM");
        review.setTeamId(TEAM_ID);
        review.setFocusProjectId(TEAM_PROJECT_ID);
        review.setFocusProjectName("Stage 4 team project");
        review.setSharedSummary("Team-visible progress only");
        review.setReflection("Private reflection");
        review.setNextPlan("Private next plan");
        weeklyReviewMapper.insert(review);
    }

    private void publishReviewChange() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publish(KnowledgeSourceTypeEnum.WEEKLY_REVIEW, REVIEW_ID,
                        KnowledgeEventTypeEnum.SOURCE_CHANGED));
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
        jdbcTemplate.update("DELETE FROM ai_knowledge_backfill_run WHERE run_key LIKE 'stage4-it-%'");
        jdbcTemplate.update("DELETE FROM ai_call_log WHERE user_id=? AND scene='knowledge-index'", USER_ID);
        jdbcTemplate.update("DELETE FROM task_assignment_log WHERE task_id >= 9940000");
        jdbcTemplate.update("DELETE FROM weekly_review_task WHERE weekly_review_id=?", REVIEW_ID);
        jdbcTemplate.update("DELETE FROM weekly_review WHERE id=?", REVIEW_ID);
        jdbcTemplate.update("DELETE FROM task WHERE project_id=?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM task WHERE project_id=?", TEAM_PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id=?", TEAM_PROJECT_ID);
        jdbcTemplate.update("DELETE FROM team_member WHERE team_id=?", TEAM_ID);
        jdbcTemplate.update("DELETE FROM team WHERE id=?", TEAM_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id=?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE id=?", SECOND_USER_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE id=?", USER_ID);
    }
}
