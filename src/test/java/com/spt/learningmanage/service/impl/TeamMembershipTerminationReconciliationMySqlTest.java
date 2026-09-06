package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.model.dto.team.TeamMemberRemoveRequest;
import com.spt.learningmanage.model.vo.team.TeamMembershipTerminationVO;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WP5-E real MySQL reconciliation gate for successful termination paths. */
@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = {
        "/db/stage1/wp5e_membership_transaction_cleanup.sql",
        "/db/stage1/wp5e_membership_transaction_seed.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/db/stage1/wp5e_membership_transaction_cleanup.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TeamMembershipTerminationReconciliationMySqlTest {

    private static final long TEAM_ID = 28001L;
    private static final long OWNER_ID = 18001L;
    private static final long TARGET_ID = 18003L;
    private static final long COMPLETED_ONLY_ID = 18004L;

    @Autowired
    private TeamMembershipTerminationService terminationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void databaseMustBeIsolatedV3() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"));
        assertEquals(7, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) "
                        + "FROM flyway_schema_history WHERE success = 1", Integer.class));
        UserHolder.remove();
    }

    @AfterEach
    void clearActor() {
        UserHolder.remove();
    }

    @Test
    void removeMemberReconcilesTasksLogsMembershipAndOperationTime() {
        UserHolder.set(OWNER_ID);
        TeamMembershipTerminationVO result = terminationService.removeMember(
                removeRequest(TARGET_ID));

        assertEquals(TEAM_ID, result.getTeamId());
        assertEquals(TARGET_ID, result.getMemberUserId());
        assertEquals("MEMBER_REMOVED", result.getAction());
        assertEquals(2, result.getUnassignedTaskCount());
        assertNotNull(result.getTerminatedAt());

        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT id, assignee_user_id, assigned_by_user_id, assigned_at "
                        + "FROM task WHERE id IN (68001,68002) ORDER BY id");
        assertEquals(2, tasks.size());
        for (Map<String, Object> task : tasks) {
            assertNull(task.get("assignee_user_id"));
            assertEquals(OWNER_ID,
                    ((Number) task.get("assigned_by_user_id")).longValue());
            assertSameSecond(result.getTerminatedAt(), task.get("assigned_at"));
        }

        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT task_id, from_assignee_user_id, to_assignee_user_id, "
                        + "assigned_by_user_id, action, reason, create_time "
                        + "FROM task_assignment_log "
                        + "WHERE task_id IN (68001,68002) "
                        + "AND action = 'MEMBER_REMOVED' ORDER BY task_id");
        assertEquals(2, logs.size());
        for (Map<String, Object> log : logs) {
            assertEquals(TARGET_ID,
                    ((Number) log.get("from_assignee_user_id")).longValue());
            assertNull(log.get("to_assignee_user_id"));
            assertEquals(OWNER_ID,
                    ((Number) log.get("assigned_by_user_id")).longValue());
            assertEquals("MEMBER_REMOVED", log.get("action"));
            assertNull(log.get("reason"));
            assertSameSecond(result.getTerminatedAt(), log.get("create_time"));
        }

        Map<String, Object> member = jdbcTemplate.queryForMap(
                "SELECT is_delete, deleted_at FROM team_member "
                        + "WHERE team_id = 28001 AND user_id = 18003");
        assertEquals(1, ((Number) member.get("is_delete")).intValue());
        assertSameSecond(result.getTerminatedAt(), member.get("deleted_at"));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task t JOIN project p ON p.id = t.project_id "
                        + "JOIN team_member tm ON tm.team_id = p.team_id "
                        + "AND tm.user_id = t.assignee_user_id "
                        + "WHERE p.id = 48001 AND t.status = 0 AND tm.is_delete = 1",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log l "
                        + "LEFT JOIN task t ON t.id = l.task_id "
                        + "WHERE l.task_id IN (68001,68002) AND t.id IS NULL",
                Integer.class));
    }

    @Test
    void leaveTeamReconcilesMemberLeftLogsAndPreservesCompletedHistory() {
        UserHolder.set(TARGET_ID);
        TeamMembershipTerminationVO result = terminationService.leaveTeam(TEAM_ID);

        assertEquals("MEMBER_LEFT", result.getAction());
        assertEquals(2, result.getUnassignedTaskCount());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log "
                        + "WHERE task_id IN (68001,68002) "
                        + "AND action = 'MEMBER_LEFT' "
                        + "AND from_assignee_user_id = 18003 "
                        + "AND to_assignee_user_id IS NULL "
                        + "AND assigned_by_user_id = 18003", Integer.class));

        Map<String, Object> completed = jdbcTemplate.queryForMap(
                "SELECT status, assignee_user_id, completed_at "
                        + "FROM task WHERE id = 68003");
        assertEquals(1, ((Number) completed.get("status")).intValue());
        assertEquals(TARGET_ID,
                ((Number) completed.get("assignee_user_id")).longValue());
        assertNotNull(completed.get("completed_at"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log "
                        + "WHERE task_id = 68003 AND action IN "
                        + "('MEMBER_LEFT','MEMBER_REMOVED')", Integer.class));
    }

    @Test
    void leavingMemberWithOnlyCompletedTasksProducesNoTerminationLog() {
        UserHolder.set(COMPLETED_ONLY_ID);
        TeamMembershipTerminationVO result = terminationService.leaveTeam(TEAM_ID);

        assertEquals(COMPLETED_ONLY_ID, result.getMemberUserId());
        assertEquals(0, result.getUnassignedTaskCount());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log "
                        + "WHERE action = 'MEMBER_LEFT' "
                        + "AND from_assignee_user_id = 18004", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT is_delete FROM team_member "
                        + "WHERE team_id = 28001 AND user_id = 18004", Integer.class));
        assertEquals(COMPLETED_ONLY_ID, jdbcTemplate.queryForObject(
                "SELECT assignee_user_id FROM task WHERE id = 68004", Long.class));
    }

    private TeamMemberRemoveRequest removeRequest(long targetUserId) {
        TeamMemberRemoveRequest request = new TeamMemberRemoveRequest();
        request.setTeamId(TEAM_ID);
        request.setTargetUserId(targetUserId);
        return request;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new AssertionError("expected timestamp but got " + value);
    }

    private void assertSameSecond(LocalDateTime expected, Object actual) {
        LocalDateTime actualSecond = toLocalDateTime(actual).withNano(0);
        long deltaSeconds = Math.abs(Duration.between(
                expected.withNano(0), actualSecond).getSeconds());
        assertTrue(deltaSeconds <= 1,
                "operation timestamp drifted by more than one second: "
                        + expected + " vs " + actualSecond);
    }
}
