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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WP5-F real MySQL gate for termination-vs-mutation and termination-vs-termination races. */
@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/db/stage1/wp5f_membership_termination_concurrency_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/db/stage1/wp5f_membership_termination_concurrency_cleanup.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TeamMembershipTerminationConcurrencyMySqlTest {

    private static final long TEAM_ID = 29001L;
    private static final long OWNER_ID = 19001L;
    private static final long ADMIN_ID = 19002L;
    private static final long MEMBER_ID = 19003L;
    private static final long OTHER_ID = 19004L;
    private static final long PROJECT_ID = 49001L;

    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskAssignmentService taskAssignmentService;
    @Autowired
    private TeamMembershipTerminationService terminationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        UserHolder.remove();
        executor = Executors.newFixedThreadPool(4);
        assertIsolatedV3Database();
    }

    @AfterEach
    void tearDown() throws Exception {
        UserHolder.remove();
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void createAssignedTaskVsMemberRemoveNeverLeavesInactiveAssignee() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Outcome<Long>> create = executor.submit(() -> withActor(OWNER_ID, ready, start, () -> {
            TaskCreateRequest request = new TaskCreateRequest();
            request.setProjectId(PROJECT_ID);
            request.setTitle("WP5F concurrent create");
            request.setAssigneeUserId(MEMBER_ID);
            return taskService.create(request);
        }));
        Future<Outcome<Object>> remove = executor.submit(() -> withActor(ADMIN_ID, ready, start,
                () -> terminationService.removeMember(removeRequest(MEMBER_ID))));

        awaitBoth(ready, start);
        Outcome<Long> createResult = create.get(10, TimeUnit.SECONDS);
        Outcome<Object> removeResult = remove.get(10, TimeUnit.SECONDS);

        assertTrue(createResult.success() || removeResult.success());
        assertNoIncompleteTaskAssignedToInactiveMember();
        if (!removeResult.success()) {
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task WHERE project_id=49001 AND title='WP5F concurrent create'",
                    Integer.class));
        }
    }

    @Test
    void assignmentVsMemberRemoveNeverLeavesInactiveAssignee() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Outcome<TaskAssignVO>> assign = executor.submit(() -> withActor(OWNER_ID, ready, start, () -> {
            TaskAssignRequest request = new TaskAssignRequest();
            request.setTaskId(69003L);
            request.setAssigneeUserId(MEMBER_ID);
            request.setExpectedAssigneeUserId(OTHER_ID);
            request.setReason("WP5F assignment/remove race");
            return taskAssignmentService.assign(request);
        }));
        Future<Outcome<Object>> remove = executor.submit(() -> withActor(ADMIN_ID, ready, start,
                () -> terminationService.removeMember(removeRequest(MEMBER_ID))));

        awaitBoth(ready, start);
        assign.get(10, TimeUnit.SECONDS);
        remove.get(10, TimeUnit.SECONDS);
        assertNoIncompleteTaskAssignedToInactiveMember();
    }

    @Test
    void reopenVsMemberRemoveKeepsEitherValidHistoricalOrOpenState() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Outcome<Object>> reopen = executor.submit(() -> withActor(OWNER_ID, ready, start,
                () -> taskService.changeStatus(reopenRequest(69002L, "wp5f-reopen-remove"))));
        Future<Outcome<Object>> remove = executor.submit(() -> withActor(ADMIN_ID, ready, start,
                () -> terminationService.removeMember(removeRequest(MEMBER_ID))));

        awaitBoth(ready, start);
        Outcome<Object> reopenResult = reopen.get(10, TimeUnit.SECONDS);
        Outcome<Object> removeResult = remove.get(10, TimeUnit.SECONDS);

        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT status, assignee_user_id, completed_at FROM task WHERE id=69002");
        int status = ((Number) task.get("status")).intValue();
        assertTrue(status == 0 || status == 1);
        assertNoIncompleteTaskAssignedToInactiveMember();
        if (!reopenResult.success()) {
            assertEquals(50001, reopenResult.errorCode());
            assertTrue(removeResult.success());
            assertEquals(1, status);
            assertEquals(MEMBER_ID, ((Number) task.get("assignee_user_id")).longValue());
        } else if (removeResult.success()) {
            assertEquals(0, status);
            assertNull(task.get("assignee_user_id"));
            assertNull(task.get("completed_at"));
        }
    }

    @Test
    void concurrentRemovalsProduceOneSuccessAndOneForbidden() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Outcome<Object>> ownerRemove = executor.submit(() -> withActor(OWNER_ID, ready, start,
                () -> terminationService.removeMember(removeRequest(MEMBER_ID))));
        Future<Outcome<Object>> adminRemove = executor.submit(() -> withActor(ADMIN_ID, ready, start,
                () -> terminationService.removeMember(removeRequest(MEMBER_ID))));

        awaitBoth(ready, start);
        Outcome<Object> ownerResult = ownerRemove.get(10, TimeUnit.SECONDS);
        Outcome<Object> adminResult = adminRemove.get(10, TimeUnit.SECONDS);

        assertEquals(1, (ownerResult.success() ? 1 : 0) + (adminResult.success() ? 1 : 0));
        Outcome<Object> failed = ownerResult.success() ? adminResult : ownerResult;
        assertEquals(40300, failed.errorCode());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT is_delete FROM team_member WHERE team_id=29001 AND user_id=19003", Integer.class));
        assertEquals(1, terminationLogCount("MEMBER_REMOVED"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE id=69001 AND assignee_user_id=19003", Integer.class));
    }

    @Test
    void leaveVsRemoveProducesOneSuccessAndOneTerminationLogSet() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Outcome<Object>> leave = executor.submit(() -> withActor(MEMBER_ID, ready, start,
                () -> terminationService.leaveTeam(TEAM_ID)));
        Future<Outcome<Object>> remove = executor.submit(() -> withActor(ADMIN_ID, ready, start,
                () -> terminationService.removeMember(removeRequest(MEMBER_ID))));

        awaitBoth(ready, start);
        Outcome<Object> leaveResult = leave.get(10, TimeUnit.SECONDS);
        Outcome<Object> removeResult = remove.get(10, TimeUnit.SECONDS);

        assertEquals(1, (leaveResult.success() ? 1 : 0) + (removeResult.success() ? 1 : 0));
        Outcome<Object> failed = leaveResult.success() ? removeResult : leaveResult;
        assertEquals(40300, failed.errorCode());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT is_delete FROM team_member WHERE team_id=29001 AND user_id=19003", Integer.class));
        assertEquals(1, terminationLogCount("MEMBER_LEFT") + terminationLogCount("MEMBER_REMOVED"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE id=69001 AND assignee_user_id=19003", Integer.class));
    }

    private TeamMemberRemoveRequest removeRequest(long targetUserId) {
        TeamMemberRemoveRequest request = new TeamMemberRemoveRequest();
        request.setTeamId(TEAM_ID);
        request.setTargetUserId(targetUserId);
        return request;
    }

    private TaskStatusChangeRequest reopenRequest(long taskId, String requestId) {
        TaskStatusChangeRequest request = new TaskStatusChangeRequest();
        request.setTaskId(taskId);
        request.setTargetStatus(0);
        request.setExpectedStatus(1);
        request.setClientRequestId(requestId);
        return request;
    }

    private int terminationLogCount(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log WHERE task_id=69001 AND action=?",
                Integer.class, action);
    }

    private void assertNoIncompleteTaskAssignedToInactiveMember() {
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task t JOIN project p ON p.id=t.project_id "
                        + "JOIN team_member tm ON tm.team_id=p.team_id AND tm.user_id=t.assignee_user_id "
                        + "WHERE p.id=49001 AND t.status=0 AND tm.is_delete=1", Integer.class));
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
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            return Outcome.success(operation.get());
        } catch (BusinessException exception) {
            return Outcome.failure(exception.getErrorCode().getCode());
        } catch (Exception exception) {
            throw new AssertionError("WP5-F concurrent operation failed", exception);
        } finally {
            UserHolder.remove();
        }
    }

    private void assertIsolatedV3Database() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertTrue(database != null && database.matches("(?i).*(?:_test|_ci_).*"));
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
