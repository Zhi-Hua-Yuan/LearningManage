package com.spt.learningmanage.mapper;

import com.spt.learningmanage.LearningManageApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = {
        "/db/stage1/team_membership_cleanup_mapper_v2_cleanup.sql",
        "/db/stage1/team_membership_cleanup_mapper_v2_seed.sql"
},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MembershipCleanupMapperLockMySqlTest {

    @Autowired
    private TeamMemberMapper teamMemberMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSourceTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void databaseMustBeIsolatedV3() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) "
                        + "FROM flyway_schema_history WHERE success = 1", Integer.class));
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (executor != null) {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        jdbcTemplate.update("DELETE FROM task_assignment_log WHERE task_id BETWEEN 66001 AND 66011");
        jdbcTemplate.update("DELETE FROM task WHERE id BETWEEN 66001 AND 66011");
        jdbcTemplate.update("DELETE FROM project WHERE id BETWEEN 46001 AND 46005");
        jdbcTemplate.update("DELETE FROM team_member WHERE id BETWEEN 36001 AND 36007");
        jdbcTemplate.update("DELETE FROM team WHERE id BETWEEN 26001 AND 26002");
        jdbcTemplate.update("DELETE FROM `user` WHERE id BETWEEN 16001 AND 16005");
    }

    @Test
    void memberRowLockBlocksCasUntilLockerCommits() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> locker = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            teamMemberMapper.selectActiveMembersForUpdate(26001L, List.of(16003L));
            locked.countDown();
            await(release);
        }));

        assertTrue(locked.await(10, TimeUnit.SECONDS));
        CountDownLatch contenderStarted = new CountDownLatch(1);
        Future<Integer> contender = executor.submit(() -> {
            contenderStarted.countDown();
            return new TransactionTemplate(transactionManager)
                    .execute(status -> teamMemberMapper.deactivateMembershipCas(
                            36003L, 26001L, 16003L, "MEMBER",
                            java.time.LocalDateTime.of(2026, 8, 29, 15, 0)));
        });

        assertTrue(contenderStarted.await(10, TimeUnit.SECONDS));
        assertFalse(contender.isDone(), "CAS must wait for the active membership lock");
        release.countDown();
        locker.get(10, TimeUnit.SECONDS);
        assertEquals(1, contender.get(10, TimeUnit.SECONDS));
    }

    @Test
    void taskRowLockBlocksUpdatesOnlyForTheFrozenCleanupScope() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> locker = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            taskMapper.selectIncompleteAssignedTeamTasksForUpdate(26001L, 16003L);
            locked.countDown();
            await(release);
        }));

        assertTrue(locked.await(10, TimeUnit.SECONDS));
        CountDownLatch contenderStarted = new CountDownLatch(1);
        Future<Integer> blocked = executor.submit(() -> {
            contenderStarted.countDown();
            return new TransactionTemplate(transactionManager)
                    .execute(status -> taskMapper.bulkUnassignIncompleteTeamTasks(
                            26001L, 16003L, List.of(66001L), 16001L,
                            java.time.LocalDateTime.of(2026, 8, 29, 15, 0)));
        });

        assertTrue(contenderStarted.await(10, TimeUnit.SECONDS));
        assertFalse(blocked.isDone(), "an included task must wait for the cleanup lock");
        release.countDown();
        locker.get(10, TimeUnit.SECONDS);
        assertEquals(1, blocked.get(10, TimeUnit.SECONDS));
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while holding mapper lock", exception);
        }
    }
}
