package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
import com.spt.learningmanage.model.dto.task.TaskCreateRequest;
import com.spt.learningmanage.model.dto.task.TaskStatusChangeRequest;
import com.spt.learningmanage.model.dto.team.TeamMemberRemoveRequest;
import com.spt.learningmanage.model.vo.task.TaskAssignVO;
import com.spt.learningmanage.service.TaskAssignmentService;
import com.spt.learningmanage.service.TaskService;
import com.spt.learningmanage.service.TeamMembershipTerminationService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP5-D4 development gate. These tests require an isolated MySQL V2 database;
 * they are intentionally not acceptance evidence until executed with real
 * credentials.
 */
@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/db/stage1/wp5d4_task_membership_concurrency_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/db/stage1/wp5d4_task_membership_concurrency_cleanup.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TaskMembershipTerminationConcurrencyMySqlTest {

    private static final long TEAM_ID = 27001L;
    private static final long OWNER_ID = 17001L;
    private static final long MEMBER_ID = 17003L;
    private static final long TARGET_ID = 17004L;
    private static final long PROJECT_ID = 47001L;

    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskAssignmentService taskAssignmentService;
    @Autowired
    private TeamMembershipTerminationService terminationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSourceTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void databaseMustBeIsolatedV3() {
        assertIsolatedV3Database();
        UserHolder.remove();
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void cleanup() throws Exception {
        UserHolder.remove();
        if (executor != null) {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void createAssignedTaskVsMemberLeaveHasOnlyContractualOutcomes() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Outcome<Long>> create = executor.submit(() -> withActor(OWNER_ID, ready, start, () -> {
            TaskCreateRequest request = new TaskCreateRequest();
            request.setProjectId(PROJECT_ID);
            request.setTitle("D4 concurrent create");
            request.setAssigneeUserId(MEMBER_ID);
            return taskService.create(request);
        }));
        Future<Outcome<Object>> leave = executor.submit(() -> withActor(MEMBER_ID, ready, start, () ->
                terminationService.leaveTeam(TEAM_ID)));

        awaitBoth(ready, start);
        Outcome<Long> createResult = create.get(10, TimeUnit.SECONDS);
        Outcome<Object> leaveResult = leave.get(10, TimeUnit.SECONDS);

        assertTrue(createResult.success() || leaveResult.success());
        assertNoIncompleteTaskAssignedToInactiveMember();
        if (createResult.success()) {
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task_assignment_log l JOIN task t ON t.id=l.task_id "
                            + "WHERE t.project_id=47001 AND l.action='MEMBER_LEFT'", Integer.class));
        } else {
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task WHERE project_id=47001 AND title='D4 concurrent create'",
                    Integer.class));
        }
    }

    @Test
    void assignmentVsMemberLeaveNeverLeavesInactiveAssignee() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Outcome<TaskAssignVO>> assign = executor.submit(() -> withActor(OWNER_ID, ready, start, () -> {
            TaskAssignRequest request = new TaskAssignRequest();
            request.setTaskId(67001L);
            request.setAssigneeUserId(MEMBER_ID);
            request.setExpectedAssigneeUserId(TARGET_ID);
            request.setReason("D4 assignment race");
            return taskAssignmentService.assign(request);
        }));
        Future<Outcome<Object>> leave = executor.submit(() -> withActor(MEMBER_ID, ready, start, () ->
                terminationService.leaveTeam(TEAM_ID)));

        awaitBoth(ready, start);
        Outcome<TaskAssignVO> assignResult = assign.get(10, TimeUnit.SECONDS);
        Outcome<Object> leaveResult = leave.get(10, TimeUnit.SECONDS);

        assertTrue(assignResult.success() || leaveResult.success());
        assertNoIncompleteTaskAssignedToInactiveMember();
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE id=67001 AND status=0 AND assignee_user_id=17003 "
                        + "AND EXISTS (SELECT 1 FROM team_member WHERE team_id=27001 AND user_id=17003 "
                        + "AND is_delete=1)", Integer.class));
    }

    @Test
    void reopenVsMemberLeaveKeepsEitherValidHistoricalOrOpenState() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Outcome<Object>> reopen = executor.submit(() -> withActor(OWNER_ID, ready, start, () -> {
            TaskStatusChangeRequest request = reopenRequest(67002L, "d4-reopen-leave");
            return taskService.changeStatus(request);
        }));
        Future<Outcome<Object>> leave = executor.submit(() -> withActor(MEMBER_ID, ready, start, () ->
                terminationService.leaveTeam(TEAM_ID)));

        awaitBoth(ready, start);
        Outcome<Object> reopenResult = reopen.get(10, TimeUnit.SECONDS);
        Outcome<Object> leaveResult = leave.get(10, TimeUnit.SECONDS);

        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT status, assignee_user_id, completed_at FROM task WHERE id=67002");
        int status = ((Number) task.get("status")).intValue();
        if (reopenResult.success()) {
            assertEquals(0, status);
            assertNull(task.get("assignee_user_id"));
            assertNull(task.get("completed_at"));
        } else {
            assertTrue(leaveResult.success());
            assertEquals(1, status);
            assertEquals(MEMBER_ID, ((Number) task.get("assignee_user_id")).longValue());
            assertNotNull(task.get("completed_at"));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task_status_idempotency "
                            + "WHERE user_id=17001 AND task_id=67002 "
                            + "AND client_request_id='d4-reopen-leave'", Integer.class));
        }
    }

    @Test
    void reopenVsReassignUsesStatusAndAssigneeCas() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Outcome<Object>> reopen = executor.submit(() -> withActor(OWNER_ID, ready, start, () ->
                taskService.changeStatus(reopenRequest(67003L, "d4-reopen-reassign"))));
        Future<Outcome<TaskAssignVO>> reassign = executor.submit(() -> withActor(OWNER_ID, ready, start, () -> {
            TaskAssignRequest request = new TaskAssignRequest();
            request.setTaskId(67003L);
            request.setAssigneeUserId(TARGET_ID);
            request.setExpectedAssigneeUserId(MEMBER_ID);
            request.setReason("D4 reopen/reassign race");
            return taskAssignmentService.assign(request);
        }));

        awaitBoth(ready, start);
        Outcome<Object> reopenResult = reopen.get(10, TimeUnit.SECONDS);
        Outcome<TaskAssignVO> reassignResult = reassign.get(10, TimeUnit.SECONDS);

        assertTrue(reopenResult.success() || reassignResult.success());
        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT status, assignee_user_id, completed_at FROM task WHERE id=67003");
        assertTrue(((Number) task.get("status")).intValue() == 0
                || ((Number) task.get("status")).intValue() == 1);
        if (reassignResult.success()) {
            assertEquals(TARGET_ID, ((Number) task.get("assignee_user_id")).longValue());
        }
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_status_idempotency "
                        + "WHERE user_id=17001 AND task_id=67003 "
                        + "AND client_request_id='d4-reopen-reassign' "
                        + "AND target_status=0 AND changed=0", Integer.class));
    }

    @Test
    void memberLockBlocksCreateQualificationUntilLockerCommits() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> locker = executor.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    jdbcTemplate.queryForList(
                            "SELECT id FROM team_member WHERE team_id=27001 AND user_id=17003 FOR UPDATE");
                    locked.countDown();
                    await(release);
                }));
        assertTrue(locked.await(10, TimeUnit.SECONDS));

        Future<Outcome<Long>> contender = executor.submit(() -> withActor(OWNER_ID, null, null, () -> {
            TaskCreateRequest request = new TaskCreateRequest();
            request.setProjectId(PROJECT_ID);
            request.setTitle("D4 lock create");
            request.setAssigneeUserId(MEMBER_ID);
            return taskService.create(request);
        }));
        assertThrows(TimeoutException.class, () -> contender.get(300, TimeUnit.MILLISECONDS));
        release.countDown();
        locker.get(10, TimeUnit.SECONDS);
        assertTrue(contender.get(10, TimeUnit.SECONDS).success());
    }

    @Test
    void memberLockBlocksAssignmentQualificationUntilLockerCommits() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> locker = executor.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    jdbcTemplate.queryForList(
                            "SELECT id FROM team_member WHERE team_id=27001 AND user_id=17003 FOR UPDATE");
                    locked.countDown();
                    await(release);
                }));
        assertTrue(locked.await(10, TimeUnit.SECONDS));

        Future<Outcome<TaskAssignVO>> contender = executor.submit(() -> withActor(OWNER_ID, null, null, () -> {
            TaskAssignRequest request = new TaskAssignRequest();
            request.setTaskId(67001L);
            request.setAssigneeUserId(MEMBER_ID);
            request.setExpectedAssigneeUserId(TARGET_ID);
            request.setReason("D4 lock assignment");
            return taskAssignmentService.assign(request);
        }));
        assertThrows(TimeoutException.class, () -> contender.get(300, TimeUnit.MILLISECONDS));
        release.countDown();
        locker.get(10, TimeUnit.SECONDS);
        assertTrue(contender.get(10, TimeUnit.SECONDS).success());
    }

    @Test
    void memberLockBlocksReopenQualificationUntilLockerCommits() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> locker = executor.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    jdbcTemplate.queryForList(
                            "SELECT id FROM team_member WHERE team_id=27001 AND user_id=17003 FOR UPDATE");
                    locked.countDown();
                    await(release);
                }));
        assertTrue(locked.await(10, TimeUnit.SECONDS));

        Future<Outcome<Object>> contender = executor.submit(() -> withActor(OWNER_ID, null, null, () ->
                taskService.changeStatus(reopenRequest(67002L, "d4-lock-reopen"))));
        assertThrows(TimeoutException.class, () -> contender.get(300, TimeUnit.MILLISECONDS));
        release.countDown();
        locker.get(10, TimeUnit.SECONDS);
        assertTrue(contender.get(10, TimeUnit.SECONDS).success());
    }

    @Test
    void emptyAssigneeReopenAndAssignmentUseNullSafeCas() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Outcome<Object>> reopen = executor.submit(() -> withActor(OWNER_ID, ready, start, () ->
                taskService.changeStatus(reopenRequest(67004L, "d4-empty-reopen"))));
        Future<Outcome<TaskAssignVO>> assign = executor.submit(() -> withActor(OWNER_ID, ready, start, () -> {
            TaskAssignRequest request = new TaskAssignRequest();
            request.setTaskId(67004L);
            request.setAssigneeUserId(MEMBER_ID);
            request.setExpectedAssigneeUserId(null);
            request.setReason("D4 empty assignee race");
            return taskAssignmentService.assign(request);
        }));

        awaitBoth(ready, start);
        Outcome<Object> reopenResult = reopen.get(10, TimeUnit.SECONDS);
        Outcome<TaskAssignVO> assignResult = assign.get(10, TimeUnit.SECONDS);

        assertTrue(reopenResult.success() || assignResult.success());
        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT status, assignee_user_id, completed_at FROM task WHERE id=67004");
        assertTrue(((Number) task.get("status")).intValue() == 0
                || ((Number) task.get("status")).intValue() == 1);
        if (reopenResult.success()) {
            assertEquals(0, ((Number) task.get("status")).intValue());
            assertNull(task.get("completed_at"));
        }
        if (assignResult.success()) {
            assertEquals(MEMBER_ID, ((Number) task.get("assignee_user_id")).longValue());
        }
    }

    private TaskStatusChangeRequest reopenRequest(long taskId, String requestId) {
        TaskStatusChangeRequest request = new TaskStatusChangeRequest();
        request.setTaskId(taskId);
        request.setTargetStatus(0);
        request.setExpectedStatus(1);
        request.setClientRequestId(requestId);
        return request;
    }

    private void awaitBoth(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
    }

    private <T> Outcome<T> withActor(long actor,
                                     CountDownLatch ready,
                                     CountDownLatch start,
                                     ThrowingSupplier<T> operation) {
        UserHolder.set(actor);
        try {
            if (ready != null) {
                ready.countDown();
            }
            if (start != null) {
                assertTrue(start.await(10, TimeUnit.SECONDS));
            }
            return Outcome.success(operation.get());
        } catch (BusinessException exception) {
            return Outcome.failure(exception.getErrorCode().getCode());
        } catch (Exception exception) {
            throw new AssertionError("D4 concurrent operation failed", exception);
        } finally {
            UserHolder.remove();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while holding team_member lock", exception);
        }
    }

    private void assertNoIncompleteTaskAssignedToInactiveMember() {
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task t JOIN project p ON p.id=t.project_id "
                        + "JOIN team_member tm ON tm.team_id=p.team_id "
                        + "AND tm.user_id=t.assignee_user_id "
                        + "WHERE p.id=47001 AND t.status=0 AND tm.is_delete=1", Integer.class));
    }

    private void assertIsolatedV3Database() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"));
        assertEquals(7, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1",
                Integer.class));
    }

    private record Outcome<T>(boolean success, Integer errorCode, T value) {
        static <T> Outcome<T> success(T value) {
            return new Outcome<>(true, null, value);
        }

        static <T> Outcome<T> failure(Integer errorCode) {
            return new Outcome<>(false, errorCode, null);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
