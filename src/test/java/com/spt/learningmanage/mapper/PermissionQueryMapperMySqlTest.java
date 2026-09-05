package com.spt.learningmanage.mapper;

import com.spt.learningmanage.model.permission.ActorPermissionRow;
import com.spt.learningmanage.model.permission.ProjectPermissionRow;
import com.spt.learningmanage.model.permission.TaskPermissionRow;
import com.spt.learningmanage.model.permission.TeamMemberPermissionRow;
import com.spt.learningmanage.model.permission.WeeklyReviewPermissionRow;
import com.spt.learningmanage.LearningManageApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(
        scripts = "/db/stage1/permission_mapper_v2_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class PermissionQueryMapperMySqlTest {

    private static final LocalDateTime DELETED_TEAM_AT = LocalDateTime.of(2026, 1, 2, 0, 0);

    @Autowired
    private PermissionQueryMapper permissionQueryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void databaseMustBeAnIsolatedV3Database() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*") ,
                "permission Mapper integration tests must use an isolated test database");

        Integer currentVersion = jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) "
                        + "FROM flyway_schema_history WHERE success = 1",
                Integer.class
        );
        assertEquals(4, currentVersion,
                "permission Mapper integration tests require the V3 schema");
    }

    @Test
    void actorQueryShouldReadActiveDeletedAndMissingUsers() {
        ActorPermissionRow active = permissionQueryMapper.selectActorPermissionRow(12001L);
        assertEquals(12001L, active.getActorUserId());
        assertEquals("USER", active.getActorSystemRole());
        assertEquals(0, active.getActorIsDelete());

        ActorPermissionRow systemAdmin = permissionQueryMapper.selectActorPermissionRow(12004L);
        assertEquals("SYSTEM_ADMIN", systemAdmin.getActorSystemRole());

        ActorPermissionRow deleted = permissionQueryMapper.selectActorPermissionRow(12005L);
        assertEquals(1, deleted.getActorIsDelete());

        assertNull(permissionQueryMapper.selectActorPermissionRow(12999L));
    }

    @Test
    void projectQueryShouldPreserveLifecycleAndMembershipFacts() {
        Map<Long, ProjectPermissionRow> rows = permissionQueryMapper
                .selectProjectPermissionRows(12003L, List.of(42001L, 42002L, 42003L, 42004L))
                .stream()
                .collect(Collectors.toMap(ProjectPermissionRow::getProjectId, Function.identity()));

        assertEquals(4, rows.size());
        assertNull(rows.get(42001L).getTeamId());

        ProjectPermissionRow activeTeamProject = rows.get(42002L);
        assertEquals(22001L, activeTeamProject.getTeamId());
        assertEquals(12001L, activeTeamProject.getTeamOwnerUserId());
        assertEquals("MEMBER", activeTeamProject.getActorTeamRole());
        assertEquals(0, activeTeamProject.getActorMembershipIsDelete());

        ProjectPermissionRow deletedProject = rows.get(42003L);
        assertEquals(1, deletedProject.getProjectIsDelete());
        assertNotNull(deletedProject.getProjectDeletedAt());

        ProjectPermissionRow deletedTeamProject = rows.get(42004L);
        assertEquals(22002L, deletedTeamProject.getTeamId());
        assertEquals(1, deletedTeamProject.getTeamIsDelete());
        assertEquals(DELETED_TEAM_AT, deletedTeamProject.getTeamDeletedAt());
    }

    @Test
    void taskQueryShouldKeepCreatorAssigneeAndJoinedLifecycleFactsDistinct() {
        Map<Long, TaskPermissionRow> rows = permissionQueryMapper
                .selectTaskPermissionRows(12002L, List.of(62001L, 62002L, 62003L, 62005L))
                .stream()
                .collect(Collectors.toMap(TaskPermissionRow::getTaskId, Function.identity()));
        TaskPermissionRow deletedMembershipTask = permissionQueryMapper
                .selectTaskPermissionRows(12007L, List.of(62004L))
                .get(0);

        assertEquals(4, rows.size());

        TaskPermissionRow memberTask = rows.get(62002L);
        assertEquals(12001L, memberTask.getTaskCreatorUserId());
        assertEquals(12003L, memberTask.getAssigneeUserId());
        assertEquals(22001L, memberTask.getTeamId());
        assertEquals("ADMIN", memberTask.getActorTeamRole());

        TaskPermissionRow deletedTask = deletedMembershipTask;
        assertEquals(1, deletedTask.getTaskIsDelete());
        assertNotNull(deletedTask.getTaskDeletedAt());
        assertEquals(1, deletedTask.getActorMembershipIsDelete());
        assertNotNull(deletedTask.getActorMembershipDeletedAt());

        TaskPermissionRow deletedTeamTask = rows.get(62005L);
        assertEquals(1, deletedTeamTask.getTeamIsDelete());
        assertEquals(DELETED_TEAM_AT, deletedTeamTask.getTeamDeletedAt());
    }

    @Test
    void weeklyReviewQueryShouldReturnVisibilityAndTeamFactsWithoutPrivateFields() {
        Map<Long, WeeklyReviewPermissionRow> rows = permissionQueryMapper
                .selectWeeklyReviewPermissionRows(12003L, List.of(72001L, 72002L, 72004L))
                .stream()
                .collect(Collectors.toMap(WeeklyReviewPermissionRow::getReviewId, Function.identity()));
        WeeklyReviewPermissionRow exitedAuthorReview = permissionQueryMapper
                .selectWeeklyReviewPermissionRows(12007L, List.of(72003L))
                .get(0);

        assertEquals(3, rows.size());
        assertEquals("PRIVATE", rows.get(72001L).getVisibilityScope());
        assertNull(rows.get(72001L).getTeamId());

        WeeklyReviewPermissionRow activeTeamReview = rows.get(72002L);
        assertEquals(12002L, activeTeamReview.getAuthorUserId());
        assertEquals("TEAM", activeTeamReview.getVisibilityScope());
        assertEquals(22001L, activeTeamReview.getTeamId());
        assertEquals("MEMBER", activeTeamReview.getActorTeamRole());

        assertEquals(1, exitedAuthorReview.getActorMembershipIsDelete());
        assertNotNull(exitedAuthorReview.getActorMembershipDeletedAt());

        WeeklyReviewPermissionRow deletedTeamReview = rows.get(72004L);
        assertEquals(1, deletedTeamReview.getTeamIsDelete());
        assertEquals(DELETED_TEAM_AT, deletedTeamReview.getTeamDeletedAt());

        assertFalse(Arrays.stream(WeeklyReviewPermissionRow.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("reflection")
                        || field.getName().equals("nextPlan")
                        || field.getName().equals("sharedSummary")));
    }

    @Test
    void teamMemberQueryShouldKeepActorAndTargetFactsSeparate() {
        TeamMemberPermissionRow row = permissionQueryMapper
                .selectTeamMemberPermissionRow(12001L, 22001L, 12003L);

        assertNotNull(row);
        assertEquals(22001L, row.getTeamId());
        assertEquals(12001L, row.getTeamOwnerUserId());
        assertEquals(12001L, row.getActorUserId());
        assertEquals("OWNER", row.getActorTeamRole());
        assertEquals(12003L, row.getTargetUserId());
        assertEquals("MEMBER", row.getTargetTeamRole());

        TeamMemberPermissionRow deletedTarget = permissionQueryMapper
                .selectTeamMemberPermissionRow(12002L, 22001L, 12007L);
        assertEquals("ADMIN", deletedTarget.getActorTeamRole());
        assertEquals("MEMBER", deletedTarget.getTargetTeamRole());
        assertEquals(1, deletedTarget.getTargetMembershipIsDelete());
        assertNotNull(deletedTarget.getTargetMembershipDeletedAt());

        TeamMemberPermissionRow deletedTeam = permissionQueryMapper
                .selectTeamMemberPermissionRow(12001L, 22002L, 12002L);
        assertEquals(1, deletedTeam.getTeamIsDelete());
        assertEquals(DELETED_TEAM_AT, deletedTeam.getTeamDeletedAt());
    }

    @Test
    void collectionQueriesShouldHandleEmptyMissingAndDuplicateIds() {
        assertTrue(permissionQueryMapper.selectProjectPermissionRows(12001L, List.of()).isEmpty());
        assertTrue(permissionQueryMapper.selectTaskPermissionRows(12001L, null).isEmpty());
        assertTrue(permissionQueryMapper.selectWeeklyReviewPermissionRows(12001L, List.of(72999L)).isEmpty());

        List<ProjectPermissionRow> duplicateProjects = permissionQueryMapper
                .selectProjectPermissionRows(12001L, List.of(42001L, 42001L));
        assertEquals(1, duplicateProjects.size());
        assertEquals(42001L, duplicateProjects.get(0).getProjectId());
    }
}
