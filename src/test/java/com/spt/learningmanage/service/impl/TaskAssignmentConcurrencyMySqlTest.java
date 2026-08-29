package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
import com.spt.learningmanage.model.vo.task.TaskAssignVO;
import com.spt.learningmanage.service.TaskAssignmentService;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/db/stage1/permission_mapper_v2_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/db/stage1/permission_mapper_v2_cleanup.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TaskAssignmentConcurrencyMySqlTest {

    @Autowired
    private TaskAssignmentService taskAssignmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setActorMustUseIsolatedV2Database() {
        assertIsolatedV2Database();
        UserHolder.remove();
    }

    @AfterEach
    void clearActor() {
        UserHolder.remove();
    }

    @Test
    void sameExpectedAssigneeAllowsExactlyOneConcurrentWinner() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> toBob = submit(executor, ready, start, 12002L);
            Future<Outcome> toCarol = submit(executor, ready, start, 12003L);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            Outcome first = toBob.get(10, TimeUnit.SECONDS);
            Outcome second = toCarol.get(10, TimeUnit.SECONDS);
            assertEquals(1, (first.success() ? 1 : 0) + (second.success() ? 1 : 0));
            assertEquals(1, (first.conflict() ? 1 : 0) + (second.conflict() ? 1 : 0));

            Long finalAssignee = jdbcTemplate.queryForObject(
                    "SELECT assignee_user_id FROM task WHERE id = 62003", Long.class);
            assertNotNull(finalAssignee);
            assertTrue(finalAssignee.equals(12002L) || finalAssignee.equals(12003L));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task_assignment_log WHERE task_id = 62003", Integer.class));

            var log = jdbcTemplate.queryForMap(
                    "SELECT from_assignee_user_id, to_assignee_user_id, assigned_by_user_id, action "
                            + "FROM task_assignment_log WHERE task_id = 62003");
            assertEquals(12002L, ((Number) log.get("from_assignee_user_id")).longValue());
            assertEquals(finalAssignee, ((Number) log.get("to_assignee_user_id")).longValue());
            assertEquals(12001L, ((Number) log.get("assigned_by_user_id")).longValue());
            assertEquals("REASSIGN", log.get("action"));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Future<Outcome> submit(ExecutorService executor,
                                   CountDownLatch ready,
                                   CountDownLatch start,
                                   Long targetAssignee) {
        return executor.submit(() -> {
            UserHolder.set(12001L);
            ready.countDown();
            try {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                TaskAssignRequest request = new TaskAssignRequest();
                request.setTaskId(62003L);
                request.setAssigneeUserId(targetAssignee);
                request.setExpectedAssigneeUserId(12002L);
                request.setReason("concurrent handoff");
                TaskAssignVO result = taskAssignmentService.assign(request);
                return new Outcome(true, null, result);
            } catch (BusinessException ex) {
                return new Outcome(false, ex.getErrorCode().getCode(), null);
            } finally {
                UserHolder.remove();
            }
        });
    }

    private void assertIsolatedV2Database() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1",
                Integer.class));
    }

    private record Outcome(boolean success, Integer errorCode, TaskAssignVO result) {
        boolean conflict() {
            return Integer.valueOf(50001).equals(errorCode);
        }
    }
}
