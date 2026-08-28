package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.model.query.task.TaskAssignmentHistoryRow;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(
        scripts = {
                "/db/stage1/permission_mapper_v2_seed.sql",
                "/db/stage1/task_assignment_history_mapper_v2_seed.sql"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class TaskAssignmentLogMapperMySqlTest {

    @Autowired
    private TaskAssignmentLogMapper taskAssignmentLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void databaseMustBeAnIsolatedV2Database() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"),
                "assignment history Mapper tests must use an isolated test database");

        Integer currentVersion = jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) "
                        + "FROM flyway_schema_history WHERE success = 1",
                Integer.class
        );
        assertEquals(2, currentVersion,
                "assignment history Mapper tests require the V2 schema");
    }

    @Test
    void shouldPageOneTaskWithFrozenStableOrdering() {
        IPage<TaskAssignmentHistoryRow> firstPage =
                taskAssignmentLogMapper.selectAssignmentHistoryPage(
                        new Page<>(1, 2), 62001L);

        assertEquals(5, firstPage.getTotal());
        assertEquals(1, firstPage.getCurrent());
        assertEquals(2, firstPage.getSize());
        assertEquals(3, firstPage.getPages());
        assertEquals(2, firstPage.getRecords().size());
        assertEquals(862005L, firstPage.getRecords().get(0).getId());
        assertEquals(862004L, firstPage.getRecords().get(1).getId());

        IPage<TaskAssignmentHistoryRow> secondPage =
                taskAssignmentLogMapper.selectAssignmentHistoryPage(
                        new Page<>(2, 2), 62001L);
        assertEquals(862003L, secondPage.getRecords().get(0).getId(),
                "same timestamp must be ordered by id DESC");
        assertEquals(862002L, secondPage.getRecords().get(1).getId());

        IPage<TaskAssignmentHistoryRow> thirdPage =
                taskAssignmentLogMapper.selectAssignmentHistoryPage(
                        new Page<>(3, 2), 62001L);
        assertEquals(1, thirdPage.getRecords().size());
        assertEquals(862001L, thirdPage.getRecords().get(0).getId());
    }

    @Test
    void shouldKeepHistoryRowsWhenUsersAreDeletedOrMissing() {
        IPage<TaskAssignmentHistoryRow> page =
                taskAssignmentLogMapper.selectAssignmentHistoryPage(
                        new Page<>(1, 10), 62001L);

        TaskAssignmentHistoryRow deletedTarget = page.getRecords().stream()
                .filter(row -> row.getId().equals(862004L))
                .findFirst()
                .orElseThrow();
        assertNull(deletedTarget.getFromAssigneeUserId());
        assertNull(deletedTarget.getFromAssigneeUsername());
        assertEquals(12005L, deletedTarget.getToAssigneeUserId());
        assertNull(deletedTarget.getToAssigneeUsername());
        assertEquals("WP4B Bob", deletedTarget.getAssignedByUsername());

        TaskAssignmentHistoryRow missingUsers = page.getRecords().stream()
                .filter(row -> row.getId().equals(862005L))
                .findFirst()
                .orElseThrow();
        assertEquals(12005L, missingUsers.getFromAssigneeUserId());
        assertNull(missingUsers.getFromAssigneeUsername());
        assertEquals(12999L, missingUsers.getToAssigneeUserId());
        assertNull(missingUsers.getToAssigneeUsername());
        assertEquals(12998L, missingUsers.getAssignedByUserId());
        assertNull(missingUsers.getAssignedByUsername());
    }

    @Test
    void shouldPreserveNullAssignmentAndReasonSemantics() {
        IPage<TaskAssignmentHistoryRow> page =
                taskAssignmentLogMapper.selectAssignmentHistoryPage(
                        new Page<>(1, 10), 62001L);

        TaskAssignmentHistoryRow initial = page.getRecords().stream()
                .filter(row -> row.getId().equals(862001L))
                .findFirst()
                .orElseThrow();
        assertNull(initial.getFromAssigneeUserId());
        assertNull(initial.getFromAssigneeUsername());
        assertEquals(12001L, initial.getToAssigneeUserId());
        assertEquals("WP4B Alice", initial.getToAssigneeUsername());
        assertEquals("initial", initial.getReason());
        assertEquals(LocalDateTime.of(2026, 1, 1, 9, 0), initial.getCreateTime());

        TaskAssignmentHistoryRow unassigned = page.getRecords().stream()
                .filter(row -> row.getId().equals(862003L))
                .findFirst()
                .orElseThrow();
        assertEquals(12002L, unassigned.getFromAssigneeUserId());
        assertEquals("WP4B Bob", unassigned.getFromAssigneeUsername());
        assertNull(unassigned.getToAssigneeUserId());
        assertNull(unassigned.getToAssigneeUsername());
        assertNull(unassigned.getReason());
    }

    @Test
    void shouldIsolateTaskRowsAndReturnEmptyPageForUnknownTask() {
        IPage<TaskAssignmentHistoryRow> otherTask =
                taskAssignmentLogMapper.selectAssignmentHistoryPage(
                        new Page<>(1, 10), 62002L);
        assertEquals(1, otherTask.getTotal());
        assertEquals(862006L, otherTask.getRecords().get(0).getId());
        assertEquals(62002L, otherTask.getRecords().get(0).getTaskId());

        IPage<TaskAssignmentHistoryRow> empty =
                taskAssignmentLogMapper.selectAssignmentHistoryPage(
                        new Page<>(1, 10), 62999L);
        assertEquals(0, empty.getTotal());
        assertTrue(empty.getRecords().isEmpty());
    }

    @Test
    void shouldReadCurrentUsernameFromUserTable() {
        jdbcTemplate.update(
                "UPDATE `user` SET username = ? WHERE id = ?",
                "WP4B Bob Renamed", 12002L);

        IPage<TaskAssignmentHistoryRow> page =
                taskAssignmentLogMapper.selectAssignmentHistoryPage(
                        new Page<>(1, 10), 62001L);
        TaskAssignmentHistoryRow row = page.getRecords().stream()
                .filter(item -> item.getId().equals(862003L))
                .findFirst()
                .orElseThrow();
        assertEquals("WP4B Bob Renamed", row.getFromAssigneeUsername());
    }
}
