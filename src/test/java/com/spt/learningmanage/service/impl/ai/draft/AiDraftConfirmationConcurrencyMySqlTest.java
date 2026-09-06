package com.spt.learningmanage.service.impl.ai.draft;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftConfirmationCommand;
import com.spt.learningmanage.model.dto.ai.draft.TaskBreakdownConfirmationContext;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.service.ai.draft.AiDraftConfirmationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiDraftConfirmationConcurrencyMySqlTest {

    private static final long USER_ID = 9_955_005L;
    private static final long DRAFT_DB_ID = 9_955_005_001L;
    private static final String DRAFT_ID = "wp5-concurrency-draft";

    @Autowired
    private AiDraftConfirmationService confirmationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        assertIsolatedV3Database();
        cleanup();
        jdbcTemplate.update("""
                        INSERT INTO `user` (id, account, username, password, user_role, is_delete)
                        VALUES (?, ?, ?, ?, 'USER', 0)
                        """,
                USER_ID, "wp5_concurrency_user", "WP5并发用户", "test-only-password-hash");
        String payload = """
                {"target":"WP5并发项目","description":"并发确认验收", "milestones":[
                  {"name":"唯一里程碑","tasks":[
                    {"name":"唯一任务","priority":2,"dueDate":"2026-12-31"}
                  ]}
                ]}
                """;
        jdbcTemplate.update("""
                        INSERT INTO ai_draft
                        (id, draft_id, user_id, scene, schema_version, payload_json, input_hash,
                         trace_id, status, expire_at)
                        VALUES (?, ?, ?, 'task-breakdown', 1, ?, 'wp5-hash', ?, 0, ?)
                        """,
                DRAFT_DB_ID, DRAFT_ID, USER_ID, payload, "c".repeat(32),
                LocalDateTime.now().plusMinutes(10));
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void twentyConcurrentConfirmationsProduceExactlyOneBusinessResult() throws Exception {
        int concurrency = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AiDraftConfirmVO>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                int attempt = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return confirmationService.confirm(new AiDraftConfirmationCommand(
                            USER_ID, DRAFT_ID, "wp5-operation-" + attempt,
                            "task-breakdown", new TaskBreakdownConfirmationContext(null, null)));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<AiDraftConfirmVO> results = new ArrayList<>();
            for (Future<AiDraftConfirmVO> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            Long businessId = results.get(0).getBusinessId();
            assertNotNull(businessId);
            assertTrue(results.stream().allMatch(result -> businessId.equals(result.getBusinessId())));
            assertEquals(1, results.stream().filter(result -> !result.getIdempotentReplay()).count());
            assertEquals(19, results.stream().filter(AiDraftConfirmVO::getIdempotentReplay).count());
            assertEquals(1, count("SELECT COUNT(*) FROM project WHERE user_id = ?", USER_ID));
            assertEquals(1, count("SELECT COUNT(*) FROM milestone WHERE user_id = ?", USER_ID));
            assertEquals(1, count("SELECT COUNT(*) FROM task WHERE user_id = ?", USER_ID));
            assertEquals(1, count("SELECT COUNT(*) FROM ai_draft_confirm_log WHERE user_id = ?", USER_ID));
            assertEquals(1, count("SELECT status FROM ai_draft WHERE id = ?", DRAFT_DB_ID));
        } finally {
            executor.shutdownNow();
        }
    }

    private int count(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, Integer.class, argument);
    }

    private void assertIsolatedV3Database() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"),
                "WP5 concurrency test must use an isolated test database");
        assertEquals(6, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1",
                Integer.class));
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM task_assignment_log WHERE assigned_by_user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM task WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM milestone WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM ai_draft_confirm_log WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM ai_draft WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM project WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", USER_ID);
    }
}
