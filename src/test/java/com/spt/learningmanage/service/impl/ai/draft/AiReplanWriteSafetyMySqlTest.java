package com.spt.learningmanage.service.impl.ai.draft;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.service.ai.scene.ListReplanAiService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiReplanWriteSafetyMySqlTest {

    private static final long USER_ID = 9_955_006L;
    private static final long PROJECT_ID = 9_955_006_001L;
    private static final long TASK_ONE_ID = 9_955_006_101L;
    private static final long TASK_TWO_ID = 9_955_006_102L;

    @Autowired
    private ListReplanAiService replanService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        assertIsolatedV3Database();
        cleanup();
        jdbcTemplate.update("""
                        INSERT INTO `user` (id, account, username, password, user_role, is_delete)
                        VALUES (?, 'wp5_replan_user', 'WP5重排用户', 'test-only-password-hash', 'USER', 0)
                        """, USER_ID);
        jdbcTemplate.update("""
                        INSERT INTO project
                        (id, user_id, name, goal, status, order_no, progress, is_delete)
                        VALUES (?, ?, 'WP5重排项目', '验证重排写入安全', 0, 0, 0, 0)
                        """, PROJECT_ID, USER_ID);
        insertTask(TASK_ONE_ID, "任务一", 1);
        insertTask(TASK_TWO_ID, "任务二", 1);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanup();
    }

    @Test
    void confirmAndCancelRaceProducesExactlyOneTerminalState() throws Exception {
        String operationId = "wp5-replan-race";
        insertOperation(operationId);
        insertItem(9_955_006_201L, operationId, TASK_ONE_ID, "任务一", "任务一-新", 1, 2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> confirm = executor.submit(() -> runAsUser(ready, start, () -> {
                try {
                    return replanService.confirmListReplan(PROJECT_ID, operationId);
                } catch (BusinessException exception) {
                    return false;
                }
            }));
            Future<Boolean> cancel = executor.submit(() -> runAsUser(ready, start,
                    () -> replanService.cancelListReplan(operationId)));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            boolean confirmed = confirm.get(20, TimeUnit.SECONDS);
            boolean canceled = cancel.get(20, TimeUnit.SECONDS);
            assertTrue(confirmed ^ canceled);

            Integer status = jdbcTemplate.queryForObject(
                    "SELECT status FROM ai_replan_operation WHERE operation_id = ?",
                    Integer.class, operationId);
            assertTrue(status == AiReplanWriteGuard.CONFIRMED || status == AiReplanWriteGuard.CANCELED);
            String title = jdbcTemplate.queryForObject(
                    "SELECT title FROM task WHERE id = ?", String.class, TASK_ONE_ID);
            assertEquals(confirmed ? "任务一-新" : "任务一", title);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleSecondTaskRollsBackEveryTaskAndKeepsPreviewState() {
        String operationId = "wp5-replan-stale";
        insertOperation(operationId);
        insertItem(9_955_006_211L, operationId, TASK_ONE_ID, "任务一", "任务一-新", 1, 2);
        insertItem(9_955_006_212L, operationId, TASK_TWO_ID, "任务二", "任务二-新", 1, 2);
        jdbcTemplate.update("UPDATE task SET title = '任务二-外部修改' WHERE id = ?", TASK_TWO_ID);

        UserHolder.set(USER_ID);
        assertThrows(BusinessException.class,
                () -> replanService.confirmListReplan(PROJECT_ID, operationId));

        assertEquals("任务一", jdbcTemplate.queryForObject(
                "SELECT title FROM task WHERE id = ?", String.class, TASK_ONE_ID));
        assertEquals("任务二-外部修改", jdbcTemplate.queryForObject(
                "SELECT title FROM task WHERE id = ?", String.class, TASK_TWO_ID));
        assertEquals(AiReplanWriteGuard.PREVIEW, jdbcTemplate.queryForObject(
                "SELECT status FROM ai_replan_operation WHERE operation_id = ?",
                Integer.class, operationId));
    }

    private boolean runAsUser(CountDownLatch ready,
                              CountDownLatch start,
                              CheckedBooleanSupplier work) throws Exception {
        UserHolder.set(USER_ID);
        try {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            return work.getAsBoolean();
        } finally {
            UserHolder.remove();
        }
    }

    private void insertTask(long taskId, String title, int priority) {
        jdbcTemplate.update("""
                        INSERT INTO task
                        (id, project_id, user_id, title, status, priority, is_delete, delete_source)
                        VALUES (?, ?, ?, ?, 0, ?, 0, 0)
                        """, taskId, PROJECT_ID, USER_ID, title, priority);
    }

    private void insertOperation(String operationId) {
        jdbcTemplate.update("""
                        INSERT INTO ai_replan_operation
                        (id, operation_id, user_id, project_id, trace_id, status, expires_at, created_at)
                        VALUES (?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                Math.abs(operationId.hashCode()) + 9_955_000_000L,
                operationId, USER_ID, PROJECT_ID, "r".repeat(32),
                LocalDateTime.now().plusMinutes(10), LocalDateTime.now());
    }

    private void insertItem(long itemId,
                            String operationId,
                            long taskId,
                            String oldTitle,
                            String newTitle,
                            int oldPriority,
                            int newPriority) {
        LocalDateTime snapshot = jdbcTemplate.queryForObject(
                "SELECT update_time FROM task WHERE id = ?", LocalDateTime.class, taskId);
        assertNotNull(snapshot);
        jdbcTemplate.update("""
                        INSERT INTO ai_replan_item
                        (id, operation_id, task_id, old_title, new_title, old_priority, new_priority,
                         confidence, reason, task_snapshot_update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 90, 'WP5测试', ?)
                        """,
                itemId, operationId, taskId, oldTitle, newTitle,
                oldPriority, newPriority, snapshot);
    }

    private void assertIsolatedV3Database() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"),
                "WP5 replan tests must use an isolated test database");
        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1",
                Integer.class));
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM ai_replan_item WHERE operation_id LIKE 'wp5-replan-%'");
        jdbcTemplate.update("DELETE FROM ai_replan_operation WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM task WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM project WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", USER_ID);
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
