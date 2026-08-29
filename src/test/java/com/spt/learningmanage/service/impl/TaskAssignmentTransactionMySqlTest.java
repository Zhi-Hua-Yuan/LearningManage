package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.vo.task.TaskAssignVO;
import com.spt.learningmanage.service.TaskAssignmentService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/db/stage1/task_assignment_d2e_transaction_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class TaskAssignmentTransactionMySqlTest {

    @Autowired
    private TaskAssignmentService taskAssignmentService;

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
    void logInsertFailureRollsBackRealTaskMutation() {
        Mockito.when(taskAssignmentLogMapper.insert(any(TaskAssignmentLog.class)))
                .thenThrow(new IllegalStateException("D2-E forced log failure"));

        assertThrows(IllegalStateException.class,
                () -> taskAssignmentService.assign(request(16003L, 16002L)));

        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT assignee_user_id, assigned_by_user_id, assigned_at "
                        + "FROM task WHERE id = 66001");
        assertEquals(16002L, ((Number) task.get("assignee_user_id")).longValue());
        assertEquals(16001L, ((Number) task.get("assigned_by_user_id")).longValue());
        assertTrue(task.get("assigned_at").toString().startsWith("2026-01-01"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log WHERE task_id = 66001", Integer.class));
    }

    @Test
    void noOpLeavesTaskSnapshotAndHistoryUnchanged() {
        Map<String, Object> before = jdbcTemplate.queryForMap(
                "SELECT assignee_user_id, assigned_by_user_id, assigned_at, update_time "
                        + "FROM task WHERE id = 66001");
        int beforeLogs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log WHERE task_id = 66001", Integer.class);

        TaskAssignVO result = taskAssignmentService.assign(request(16002L, 16002L));

        assertFalse(result.getChanged());
        Map<String, Object> after = jdbcTemplate.queryForMap(
                "SELECT assignee_user_id, assigned_by_user_id, assigned_at, update_time "
                        + "FROM task WHERE id = 66001");
        assertEquals(before, after);
        assertEquals(beforeLogs, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log WHERE task_id = 66001", Integer.class));
        Mockito.verifyNoInteractions(taskAssignmentLogMapper);
    }

    private TaskAssignRequest request(Long targetAssignee, Long expectedAssignee) {
        TaskAssignRequest request = new TaskAssignRequest();
        request.setTaskId(66001L);
        request.setAssigneeUserId(targetAssignee);
        request.setExpectedAssigneeUserId(expectedAssignee);
        request.setReason("D2-E transaction test");
        return request;
    }
}
