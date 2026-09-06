package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.mapper.PermissionQueryMapper;
import com.spt.learningmanage.model.permission.TaskCapabilities;
import com.spt.learningmanage.service.PermissionService;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {
                LearningManageApplication.class,
                PermissionServiceBatchMySqlTest.QueryCountTestConfiguration.class
        }
)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(
        scripts = "/db/stage1/permission_mapper_v2_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class PermissionServiceBatchMySqlTest {

    private static final long ACTOR_ID = 12003L;
    private static final List<Long> BATCH_TASK_IDS = LongStream.rangeClosed(63001L, 63100L)
            .boxed()
            .toList();

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PermissionQueryCountInterceptor queryCountInterceptor;

    @BeforeEach
    void prepareIsolatedV3BatchFixture() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertTrue(database != null && database.matches("(?i).*(?:_test|_ci_).*"),
                "batch permission tests must use an isolated test database");
        Integer currentVersion = jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) "
                        + "FROM flyway_schema_history WHERE success = 1",
                Integer.class
        );
        assertEquals(6, currentVersion);

        String insertSql = "INSERT INTO task "
                + "(id, project_id, user_id, title, status, priority, is_delete, "
                + "create_time, update_time, assignee_user_id) "
                + "VALUES (?, 42002, 12001, ?, 0, 0, 0, "
                + "'2026-01-01 00:00:00', '2026-01-01 00:00:00', 12003)";
        jdbcTemplate.batchUpdate(insertSql, BATCH_TASK_IDS, BATCH_TASK_IDS.size(),
                (statement, taskId) -> {
                    statement.setLong(1, taskId);
                    statement.setString(2, "WP4C batch task " + taskId);
                });
        queryCountInterceptor.reset();
    }

    @Test
    void oneHundredReadableTasksUseExactlyOneActorAndOneBatchQuery() {
        Set<Long> readable = permissionService.filterReadableTaskIds(ACTOR_ID, BATCH_TASK_IDS);

        assertEquals(BATCH_TASK_IDS, readable.stream().toList());
        assertEquals(1, queryCountInterceptor.actorQueries());
        assertEquals(1, queryCountInterceptor.taskQueries());
        assertEquals(0, queryCountInterceptor.projectQueries());
    }

    @Test
    void capabilitiesUseTheSameTwoQueryBudget() {
        Map<Long, TaskCapabilities> capabilities =
                permissionService.resolveTaskCapabilities(ACTOR_ID, BATCH_TASK_IDS);

        assertEquals(100, capabilities.size());
        assertTrue(capabilities.get(63001L).canEditContent());
        assertTrue(capabilities.get(63001L).canChangeStatus());
        assertEquals(1, queryCountInterceptor.actorQueries());
        assertEquals(1, queryCountInterceptor.taskQueries());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class QueryCountTestConfiguration {

        @Bean
        PermissionQueryCountInterceptor permissionQueryCountInterceptor() {
            return new PermissionQueryCountInterceptor();
        }
    }

    @Intercepts({
            @Signature(
                    type = Executor.class,
                    method = "query",
                    args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
            )
    })
    static class PermissionQueryCountInterceptor implements Interceptor {

        private final AtomicInteger actorQueries = new AtomicInteger();
        private final AtomicInteger projectQueries = new AtomicInteger();
        private final AtomicInteger taskQueries = new AtomicInteger();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            String id = statement.getId();
            if (id.equals(PermissionQueryMapper.class.getName() + ".selectActorPermissionRow")) {
                actorQueries.incrementAndGet();
            } else if (id.equals(PermissionQueryMapper.class.getName() + ".selectProjectPermissionRows")) {
                projectQueries.incrementAndGet();
            } else if (id.equals(PermissionQueryMapper.class.getName() + ".selectTaskPermissionRows")) {
                taskQueries.incrementAndGet();
            }
            return invocation.proceed();
        }

        @Override
        public Object plugin(Object target) {
            return Plugin.wrap(target, this);
        }

        void reset() {
            actorQueries.set(0);
            projectQueries.set(0);
            taskQueries.set(0);
        }

        int actorQueries() {
            return actorQueries.get();
        }

        int projectQueries() {
            return projectQueries.get();
        }

        int taskQueries() {
            return taskQueries.get();
        }
    }
}
