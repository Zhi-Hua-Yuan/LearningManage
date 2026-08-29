package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.constant.TaskAssignmentActionEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/db/stage1/task_assignment_d2e_audit_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/db/stage1/task_assignment_d2e_audit_cleanup.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TaskAssignmentAuditReconciliationMySqlTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void databaseMustBeIsolatedV2() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1",
                Integer.class));
    }

    @Test
    void currentAssigneeAndAssignmentHistoryReconcileWithoutAnomaly() {
        long invalidCurrentAssignee = scalar("""
                SELECT COUNT(*)
                FROM task t
                JOIN project p ON p.id = t.project_id AND p.is_delete = 0
                LEFT JOIN team_member member
                  ON member.team_id = p.team_id
                 AND member.user_id = t.assignee_user_id
                 AND member.is_delete = 0
                WHERE t.id IN (63001, 63002)
                  AND t.is_delete = 0
                  AND t.assignee_user_id IS NOT NULL
                  AND ((p.team_id IS NULL AND t.assignee_user_id <> p.user_id)
                    OR (p.team_id IS NOT NULL AND member.id IS NULL))
                """);
        long missingAssignmentHistory = scalar("""
                SELECT COUNT(*)
                FROM task t
                LEFT JOIN task_assignment_log log_entry ON log_entry.task_id = t.id
                WHERE t.id IN (63001, 63002)
                  AND t.is_delete = 0
                  AND t.assignee_user_id IS NOT NULL
                  AND log_entry.id IS NULL
                """);
        long orphanLog = scalar("""
                SELECT COUNT(*)
                FROM task_assignment_log log_entry
                LEFT JOIN task t ON t.id = log_entry.task_id
                WHERE log_entry.task_id IN (63001, 63002)
                  AND t.id IS NULL
                """);

        List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
                SELECT id, from_assignee_user_id, to_assignee_user_id,
                       assigned_by_user_id, action, create_time
                FROM task_assignment_log
                WHERE task_id = 63001
                ORDER BY create_time ASC, id ASC
                """);

        long invalidActionTransition = 0;
        long brokenChain = 0;
        Long previousTo = null;
        for (Map<String, Object> log : logs) {
            Long from = nullableLong(log.get("from_assignee_user_id"));
            Long to = nullableLong(log.get("to_assignee_user_id"));
            TaskAssignmentActionEnum action = TaskAssignmentActionEnum.fromValue(
                    (String) log.get("action"));
            if (!isValidTransition(action, from, to)) {
                invalidActionTransition++;
            }
            if (previousTo != null || from != null) {
                if (!Objects.equals(previousTo, from)) {
                    brokenChain++;
                }
            }
            previousTo = to;
        }

        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT assignee_user_id, assigned_by_user_id, assigned_at "
                        + "FROM task WHERE id = 63001");
        Map<String, Object> latest = logs.get(logs.size() - 1);
        long latestAssigneeMismatch = Objects.equals(
                nullableLong(task.get("assignee_user_id")),
                nullableLong(latest.get("to_assignee_user_id"))) ? 0 : 1;
        long latestActorMismatch = Objects.equals(
                nullableLong(task.get("assigned_by_user_id")),
                nullableLong(latest.get("assigned_by_user_id"))) ? 0 : 1;
        long latestTimeMismatch = task.get("assigned_at").toString()
                .startsWith(latest.get("create_time").toString().substring(0, 16)) ? 0 : 1;

        assertEquals(0, invalidCurrentAssignee);
        assertEquals(0, missingAssignmentHistory);
        assertEquals(0, orphanLog);
        assertEquals(0, invalidActionTransition);
        assertEquals(0, brokenChain);
        assertEquals(0, latestAssigneeMismatch);
        assertEquals(0, latestActorMismatch);
        assertEquals(0, latestTimeMismatch);
        assertFalse(logs.isEmpty());
    }

    private long scalar(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
        assertNotNull(value);
        return value.longValue();
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private boolean isValidTransition(TaskAssignmentActionEnum action, Long from, Long to) {
        if (action == null) {
            return false;
        }
        return switch (action) {
            case INITIAL_ASSIGN, ASSIGN -> from == null && to != null;
            case REASSIGN -> from != null && to != null && !from.equals(to);
            case UNASSIGN, MEMBER_LEFT, MEMBER_REMOVED -> from != null && to == null;
        };
    }
}
