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
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(scripts = "/db/stage1/permission_mapper_v2_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TaskAssignmentTransactionMySqlTest {

    @Autowired
    private TaskAssignmentService taskAssignmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private TaskAssignmentLogMapper taskAssignmentLogMapper;

    @BeforeEach
    void setActor() {
        UserHolder.set(12001L);
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
                () -> taskAssignmentService.assign(request(12003L, 12002L)));

        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT assignee_user_id, assigned_by_user_id, assigned_at "
                        + "FROM task WHERE id = 62003");
        assertEquals(12002L, ((Number) task.get("assignee_user_id")).longValue());
        assertEquals(12001L, ((Number) task.get("assigned_by_user_id")).longValue());
        assertTrue(task.get("assigned_at").toString().startsWith("2026-01-01 00:00"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log WHERE task_id = 62003", Integer.class));
    }

    @Test
    void noOpLeavesTaskSnapshotAndHistoryUnchanged() {
        Map<String, Object> before = jdbcTemplate.queryForMap(
                "SELECT assignee_user_id, assigned_by_user_id, assigned_at, update_time "
                        + "FROM task WHERE id = 62003");
        int beforeLogs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log WHERE task_id = 62003", Integer.class);

        TaskAssignVO result = taskAssignmentService.assign(request(12002L, 12002L));

        assertFalse(result.getChanged());
        Map<String, Object> after = jdbcTemplate.queryForMap(
                "SELECT assignee_user_id, assigned_by_user_id, assigned_at, update_time "
                        + "FROM task WHERE id = 62003");
        assertEquals(before, after);
        assertEquals(beforeLogs, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_assignment_log WHERE task_id = 62003", Integer.class));
        Mockito.verifyNoInteractions(taskAssignmentLogMapper);
    }

    private TaskAssignRequest request(Long targetAssignee, Long expectedAssignee) {
        TaskAssignRequest request = new TaskAssignRequest();
        request.setTaskId(62003L);
        request.setAssigneeUserId(targetAssignee);
        request.setExpectedAssigneeUserId(expectedAssignee);
        request.setReason("D2-E transaction test");
        return request;
    }
}
