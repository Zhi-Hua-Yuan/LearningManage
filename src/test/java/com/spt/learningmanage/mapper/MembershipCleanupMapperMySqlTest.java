package com.spt.learningmanage.mapper;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.query.team.MembershipTaskCleanupRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(scripts = {
        "/db/stage1/team_membership_cleanup_mapper_v2_cleanup.sql",
        "/db/stage1/team_membership_cleanup_mapper_v2_seed.sql"
},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MembershipCleanupMapperMySqlTest {

    @Autowired
    private TeamMemberMapper teamMemberMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskAssignmentLogMapper taskAssignmentLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void databaseMustBeIsolatedV3() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"));
        assertEquals(6, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) "
                        + "FROM flyway_schema_history WHERE success = 1", Integer.class));
    }

    @Test
    void shouldSelectOnlyActiveMembersInRelationshipIdOrder() {
        List<TeamMember> members = teamMemberMapper.selectActiveMembersForUpdate(
                26001L, List.of(16005L, 16004L, 16003L, 16001L, 16002L));

        assertEquals(List.of(36001L, 36002L, 36003L, 36004L),
                members.stream().map(TeamMember::getId).toList());
    }

    @Test
    void shouldLockFrozenTaskCleanupScopeAcrossLifecycleStates() {
        List<MembershipTaskCleanupRow> rows = taskMapper
                .selectIncompleteAssignedTeamTasksForUpdate(26001L, 16003L);

        assertEquals(List.of(66001L, 66002L, 66003L, 66004L),
                rows.stream().map(MembershipTaskCleanupRow::getTaskId).toList());
        assertTrue(rows.stream().allMatch(row -> row.getAssigneeUserId().equals(16003L)));
    }

    @Test
    void shouldBulkUnassignAndBatchInsertTerminationLogs() {
        List<MembershipTaskCleanupRow> rows = taskMapper
                .selectIncompleteAssignedTeamTasksForUpdate(26001L, 16003L);
        List<Long> taskIds = rows.stream().map(MembershipTaskCleanupRow::getTaskId).toList();
        LocalDateTime operationTime = LocalDateTime.of(2026, 8, 29, 15, 0);

        int updated = taskMapper.bulkUnassignIncompleteTeamTasks(
                26001L, 16003L, taskIds, 16001L, operationTime);
        assertEquals(taskIds.size(), updated);

        List<TaskAssignmentLog> logs = taskIds.stream().map(taskId -> {
            TaskAssignmentLog log = new TaskAssignmentLog();
            log.setId(986600L + taskId - 66000L);
            log.setTaskId(taskId);
            log.setFromAssigneeUserId(16003L);
            log.setToAssigneeUserId(null);
            log.setAssignedByUserId(16001L);
            log.setAction("MEMBER_REMOVED");
            log.setReason(null);
            log.setCreateTime(operationTime);
            return log;
        }).toList();

        assertEquals(logs.size(), taskAssignmentLogMapper.batchInsertMembershipTerminationLogs(logs));
        assertEquals(taskIds.size(), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE id IN (66001,66002,66003,66004) "
                        + "AND assignee_user_id IS NULL AND assigned_by_user_id = 16001 "
                        + "AND assigned_at = '2026-08-29 15:00:00'", Integer.class));
        assertEquals(taskIds.size(), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log "
                        + "WHERE task_id IN (66001,66002,66003,66004) "
                        + "AND action = 'MEMBER_REMOVED' AND to_assignee_user_id IS NULL "
                        + "AND assigned_by_user_id = 16001", Integer.class));
        assertEquals(16003L, jdbcTemplate.queryForObject(
                "SELECT assignee_user_id FROM task WHERE id = 66005", Long.class));
        assertEquals(16003L, jdbcTemplate.queryForObject(
                "SELECT assignee_user_id FROM task WHERE id = 66008", Long.class));
    }

    @Test
    void shouldFailSafeForEmptyCollections() {
        assertTrue(teamMemberMapper.selectActiveMembersForUpdate(26001L, List.of()).isEmpty());
        assertEquals(0, taskMapper.bulkUnassignIncompleteTeamTasks(
                26001L, 16003L, List.of(), 16001L,
                LocalDateTime.of(2026, 8, 29, 15, 0)));
        assertEquals(0, taskAssignmentLogMapper.batchInsertMembershipTerminationLogs(List.of()));
    }
}
