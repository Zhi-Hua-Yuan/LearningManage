package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
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
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(scripts = "/db/stage1/permission_mapper_v2_seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TaskAssignmentServiceMySqlTest {

    @Autowired
    private TaskAssignmentService taskAssignmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setActor() {
        UserHolder.set(12001L);
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertTrue(database != null && database.matches("(?i).*(?:_test|_ci_).*"));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1", Integer.class));
    }

    @AfterEach
    void clearActor() {
        UserHolder.remove();
    }

    @Test
    void reassignUpdatesTaskAndWritesImmutableHistory() {
        TaskAssignRequest request = new TaskAssignRequest();
        request.setTaskId(62003L);
        request.setAssigneeUserId(12003L);
        request.setExpectedAssigneeUserId(12002L);
        request.setReason("handoff");

        assertNotNull(taskAssignmentService.assign(request));
        assertEquals(12003L, jdbcTemplate.queryForObject(
                "SELECT assignee_user_id FROM task WHERE id = 62003", Long.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log "
                        + "WHERE task_id = 62003 AND action = 'REASSIGN' "
                        + "AND from_assignee_user_id = 12002 AND to_assignee_user_id = 12003", Integer.class));
    }
}
