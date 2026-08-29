package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.model.dto.team.TeamMemberRemoveRequest;
import com.spt.learningmanage.service.TeamMembershipTerminationService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;

/**
 * WP5-E real-transaction rollback gate. It runs against the isolated MySQL
 * profile used by the earlier membership-termination work packages.
 */
@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/db/stage1/team_membership_cleanup_mapper_v2_cleanup.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/db/stage1/team_membership_cleanup_mapper_v2_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/db/stage1/team_membership_cleanup_mapper_v2_cleanup.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TeamMembershipTerminationTransactionMySqlTest {

    @Autowired
    private TeamMembershipTerminationService terminationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private TaskAssignmentLogMapper taskAssignmentLogMapper;

    @BeforeEach
    void setActor() {
        UserHolder.set(16001L);
    }

    @AfterEach
    void clearActor() {
        UserHolder.remove();
        Mockito.reset(taskAssignmentLogMapper);
    }

    @Test
    void auditFailureRollsBackTaskUnassignmentAndMembershipTermination() {
        Mockito.when(taskAssignmentLogMapper.batchInsertMembershipTerminationLogs(anyList()))
                .thenThrow(new IllegalStateException("WP5-C forced audit failure"));

        TeamMemberRemoveRequest request = new TeamMemberRemoveRequest();
        request.setTeamId(26001L);
        request.setTargetUserId(16003L);

        assertThrows(IllegalStateException.class,
                () -> terminationService.removeMember(request));

        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT assignee_user_id, assigned_by_user_id, assigned_at "
                        + "FROM task WHERE id = 66001");
        assertEquals(16003L, ((Number) task.get("assignee_user_id")).longValue());
        assertEquals(16001L, ((Number) task.get("assigned_by_user_id")).longValue());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log "
                        + "WHERE action = 'MEMBER_REMOVED' AND task_id IN "
                        + "(66001,66002,66003,66004)", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT is_delete FROM team_member "
                        + "WHERE team_id = 26001 AND user_id = 16003", Integer.class));
    }

    @Test
    void leaveAuditFailureRollsBackRealTaskMutationAndMembershipTermination() {
        Mockito.when(taskAssignmentLogMapper.batchInsertMembershipTerminationLogs(anyList()))
                .thenThrow(new IllegalStateException("WP5-E forced leave audit failure"));
        UserHolder.set(16003L);

        assertThrows(IllegalStateException.class,
                () -> terminationService.leaveTeam(26001L));

        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT assignee_user_id, assigned_by_user_id, assigned_at "
                        + "FROM task WHERE id = 66001");
        assertEquals(16003L, ((Number) task.get("assignee_user_id")).longValue());
        assertEquals(16001L, ((Number) task.get("assigned_by_user_id")).longValue());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log "
                        + "WHERE action = 'MEMBER_LEFT' AND task_id IN "
                        + "(66001,66002,66003,66004)", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT is_delete FROM team_member "
                        + "WHERE team_id = 26001 AND user_id = 16003", Integer.class));
    }

    @Test
    void auditCountMismatchRollsBackBeforeMembershipCas() {
        Mockito.when(taskAssignmentLogMapper.batchInsertMembershipTerminationLogs(anyList()))
                .thenReturn(0);

        assertThrows(Exception.class,
                () -> terminationService.removeMember(removeRequest(26001L, 16003L)));

        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT assignee_user_id, assigned_by_user_id, assigned_at "
                        + "FROM task WHERE id = 66001");
        assertEquals(16003L, ((Number) task.get("assignee_user_id")).longValue());
        assertEquals(16001L, ((Number) task.get("assigned_by_user_id")).longValue());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log "
                        + "WHERE action = 'MEMBER_REMOVED' AND task_id IN "
                        + "(66001,66002,66003,66004)", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT is_delete FROM team_member "
                        + "WHERE team_id = 26001 AND user_id = 16003", Integer.class));
    }

    private TeamMemberRemoveRequest removeRequest(Long teamId, Long targetUserId) {
        TeamMemberRemoveRequest request = new TeamMemberRemoveRequest();
        request.setTeamId(teamId);
        request.setTargetUserId(targetUserId);
        return request;
    }
}
