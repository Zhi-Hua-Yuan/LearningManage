package com.spt.learningmanage.integration;

import com.spt.learningmanage.job.DataCleanupWorker;
import com.spt.learningmanage.mapper.AiAdminOperationLogMapper;
import com.spt.learningmanage.mapper.AiCallLogMapper;
import com.spt.learningmanage.mapper.AiDataCleanupItemMapper;
import com.spt.learningmanage.mapper.AiDataCleanupRunMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.dto.ops.CleanupRunCreateRequest;
import com.spt.learningmanage.model.entity.AiCallLog;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.service.CleanupRunQueueService;
import com.spt.learningmanage.service.CleanupRunService;
import com.spt.learningmanage.service.AiOpsQueryService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.scheduling.enabled=false",
        "ai.cleanup.enabled=true",
        "ai.cleanup.schedule-enabled=false",
        "ai.cleanup.body-retention-days=1",
        "ai.cleanup.metadata-retention-days=2"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "STAGE7_CLEANUP_IT_ENABLED", matches = "true")
class DataCleanupLifecycleV8IT {
    private static final long ADMIN_ID = 2_097_700_000_000_001L;
    private static final long CALL_ID = 2_097_700_000_000_010L;

    @Autowired UserMapper userMapper;
    @Autowired AiCallLogMapper callLogMapper;
    @Autowired AiDataCleanupRunMapper runMapper;
    @Autowired AiDataCleanupItemMapper itemMapper;
    @Autowired AiAdminOperationLogMapper adminLogMapper;
    @Autowired CleanupRunService runService;
    @Autowired CleanupRunQueueService queueService;
    @Autowired DataCleanupWorker worker;
    @Autowired AiOpsQueryService opsQueryService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        assertEquals(8, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1",
                Integer.class));
        cleanup();
        User admin = new User();
        admin.setId(ADMIN_ID);
        admin.setAccount("stage7_cleanup_admin");
        admin.setUsername("stage7_cleanup_admin");
        admin.setPassword("not-a-real-password-hash");
        admin.setUserRole("SYSTEM_ADMIN");
        admin.setIsDelete(0);
        userMapper.insert(admin);

        AiCallLog log = new AiCallLog();
        log.setId(CALL_ID);
        log.setUserId(ADMIN_ID);
        log.setScene("stage7-cleanup-it");
        log.setModelName("ci-model");
        log.setRequestedModel("ci-model");
        log.setPromptType("ci-prompt");
        log.setRequestText("sensitive request body");
        log.setResponseText("sensitive response body");
        log.setStatus(1);
        log.setRetryCount(0);
        log.setFallbackUsed(0);
        log.setDegraded(0);
        log.setRequestSanitizationStatus("CLEAN");
        log.setResponseSanitizationStatus("CLEAN");
        log.setErrorSanitizationStatus("CLEAN");
        log.setRequestTruncated(0);
        log.setResponseTruncated(0);
        log.setErrorTruncated(0);
        log.setCreateTime(LocalDateTime.now().minusDays(2));
        log.setUpdateTime(LocalDateTime.now().minusDays(2));
        callLogMapper.insert(log);
        UserHolder.set(ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanup();
    }

    @Test
    void dryRunApprovalPrecedesIdempotentBodyRedaction() {
        var dryRun = runService.submit(request(true, "stage7-cleanup-dry-request"));
        executeOne();
        dryRun = runService.get(dryRun.getRunId());
        assertEquals("SUCCEEDED", dryRun.getStatus());
        assertEquals(1L, dryRun.getEstimatedCount());
        assertEquals(0L, dryRun.getAffectedCount());
        assertEquals("sensitive request body", callLogMapper.selectById(CALL_ID).getRequestText());

        var formalRequest = request(false, "stage7-cleanup-formal-request");
        formalRequest.setApprovedDryRunId(dryRun.getRunId());
        var formal = runService.submit(formalRequest);
        executeOne();
        formal = runService.get(formal.getRunId());
        assertEquals("SUCCEEDED", formal.getStatus());
        assertEquals(1L, formal.getAffectedCount());

        AiCallLog retained = callLogMapper.selectById(CALL_ID);
        assertNotNull(retained);
        assertNull(retained.getRequestText());
        assertNull(retained.getResponseText());
        assertNotNull(retained.getBodyPurgedAt());
        assertTrue(adminLogMapper.selectCount(null) >= 2);
    }

    @Test
    void concurrentSubmissionsCreateExactlyOneActiveRun() throws Exception {
        var start = new CountDownLatch(1);
        var success = new AtomicInteger();
        var conflict = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                String requestId = "stage7-concurrent-cleanup-" + i;
                executor.submit(() -> {
                    UserHolder.set(ADMIN_ID);
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        runService.submit(request(true, requestId));
                        success.incrementAndGet();
                    } catch (com.spt.learningmanage.exception.BusinessException exception) {
                        if (exception.getErrorCode() == com.spt.learningmanage.exception.ErrorCode.CLEANUP_ALREADY_RUNNING) {
                            conflict.incrementAndGet();
                        } else {
                            throw exception;
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        UserHolder.remove();
                    }
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, success.get());
        assertEquals(1, conflict.get());
        assertEquals(1, runMapper.selectCount(null));
    }

    @Test
    void reclaimedLeaseRejectsTheOldExecutionToken() {
        var submitted = runService.submit(request(true, "stage7-lease-reclaim-request"));
        AiDataCleanupRun first = queueService.claimOne("worker-one");
        assertNotNull(first);
        jdbcTemplate.update("UPDATE ai_data_cleanup_run SET lease_until=DATE_SUB(NOW(3), INTERVAL 1 SECOND) "
                + "WHERE run_id=?", submitted.getRunId());
        AiDataCleanupRun second = queueService.claimOne("worker-two");
        assertNotNull(second);
        assertNotEquals(first.getExecutionToken(), second.getExecutionToken());
        assertFalse(queueService.complete(first, "SUCCEEDED", 0, 0, 0, 0, null));
        assertTrue(queueService.complete(second, "SUCCEEDED", 0, 0, 0, 0, null));
    }

    @Test
    void cancelingPendingRunCancelsEveryItem() {
        CleanupRunCreateRequest request = new CleanupRunCreateRequest();
        request.setDryRun(true);
        request.setClientRequestId("stage7-pending-cancel-request");
        var submitted = runService.submit(request);
        var canceled = runService.cancel(submitted.getRunId());
        assertEquals("CANCELED", canceled.status());
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_data_cleanup_item WHERE run_id=? AND status<>'CANCELED'",
                Long.class, submitted.getRunId());
        assertEquals(0L, remaining);
    }

    @Test
    void operationsAggregationAndFailurePaginationStayDatabaseBounded() {
        LocalDateTime now = jdbcTemplate.queryForObject("SELECT NOW(3)", LocalDateTime.class);
        String sql = "INSERT INTO ai_call_log "
                + "(id,user_id,scene,model_name,status,failure_type,cost_time_ms,retry_count,trace_id,create_time,update_time) "
                + "VALUES (?,?,?,?,?,?,?,?,?,NOW(3),NOW(3))";
        List<Object[]> failures = IntStream.range(0, 505)
                .mapToObj(index -> new Object[]{
                        2_097_700_000_100_000L + index, ADMIN_ID, "stage7-ops", "ci-model", 2,
                        "PROVIDER", 10L + index, 0, "stage7-failure-" + index
                }).toList();
        jdbcTemplate.batchUpdate(sql, failures);
        jdbcTemplate.update(sql, 2_097_700_000_199_999L, ADMIN_ID, "stage7-ops", "ci-model", 0,
                null, null, 0, "stage7-running");

        var page = opsQueryService.failures(now.minusHours(1), now.plusHours(1), 6, 100);
        assertEquals(505L, page.getTotal());
        assertEquals(5, page.getRecords().size());
        assertTrue(page.getRecords().stream().allMatch(failure -> "FAILED".equals(failure.getStatus())));

        var overview = opsQueryService.overview(now.minusHours(1), now.plusHours(1));
        assertEquals(506L, overview.getAi().getTotalCount());
        assertEquals(505L, overview.getAi().getStatusCounts().get("FAILED"));
        assertEquals(1L, overview.getAi().getStatusCounts().get("RUNNING"));
        assertNotNull(overview.getAi().getP95DurationMs());
    }

    private void executeOne() {
        AiDataCleanupRun claimed = queueService.claimOne("stage7-cleanup-it-worker");
        assertNotNull(claimed);
        worker.process(claimed);
    }

    private CleanupRunCreateRequest request(boolean dryRun, String requestId) {
        CleanupRunCreateRequest request = new CleanupRunCreateRequest();
        request.setDryRun(dryRun);
        request.setResourceTypes(List.of("AI_CALL_BODY"));
        request.setClientRequestId(requestId);
        return request;
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM ai_data_cleanup_item WHERE run_id IN "
                + "(SELECT run_id FROM ai_data_cleanup_run WHERE initiator_user_id=?)", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM ai_data_cleanup_run WHERE initiator_user_id=?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM ai_admin_operation_log WHERE operator_user_id=?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM ai_call_log WHERE user_id=?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE id=?", ADMIN_ID);
    }
}
