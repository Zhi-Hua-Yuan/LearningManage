package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.vo.task.TaskAssignVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TaskAssigneePolicy;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskAssignmentServiceImplTest {
    @Mock private TaskMapper taskMapper;
    @Mock private TaskAssignmentLogMapper taskAssignmentLogMapper;
    @Mock private PermissionService permissionService;
    @Mock private TaskAssigneePolicy taskAssigneePolicy;
    @InjectMocks private TaskAssignmentServiceImpl service;

    @BeforeEach
    void setUp() { UserHolder.set(9L); }

    @AfterEach
    void tearDown() { UserHolder.remove(); }

    @Test
    void reassignWritesCasAndAuditLog() {
        Task task = task(10L, 1L, 2L);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(permissionService.requireProjectManage(9L, 1L))
                .thenReturn(new ProjectAccessScope(9L, 1L, 9L, 20L, TeamRoleEnum.ADMIN));
        when(taskMapper.compareAndSetAssignee(eq(10L), eq(2L), eq(3L), eq(9L), any())).thenReturn(1);
        when(taskAssignmentLogMapper.insert(any(com.spt.learningmanage.model.entity.TaskAssignmentLog.class))).thenReturn(1);

        TaskAssignRequest request = request(10L, 3L, 2L);
        request.setReason("  handoff  ");
        TaskAssignVO result = service.assign(request);

        assertTrue(result.getChanged());
        assertEquals(2L, result.getPreviousAssigneeUserId());
        assertEquals(3L, result.getAssigneeUserId());
        verify(taskAssignmentLogMapper).insert(argThat((com.spt.learningmanage.model.entity.TaskAssignmentLog log) ->
                "REASSIGN".equals(log.getAction()) && "handoff".equals(log.getReason())));
    }

    @Test
    void noOpDoesNotWriteTaskOrHistory() {
        Task task = task(10L, 1L, 2L);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(permissionService.requireProjectManage(9L, 1L))
                .thenReturn(new ProjectAccessScope(9L, 1L, 9L, 20L, TeamRoleEnum.ADMIN));

        TaskAssignVO result = service.assign(request(10L, 2L, 2L));

        assertFalse(result.getChanged());
        verify(taskMapper, never()).compareAndSetAssignee(any(), any(), any(), any(), any());
        verify(taskAssignmentLogMapper, never()).insert(any(com.spt.learningmanage.model.entity.TaskAssignmentLog.class));
    }

    @Test
    void staleExpectedAssigneeReturnsConflict() {
        Task task = task(10L, 1L, 2L);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(permissionService.requireProjectManage(9L, 1L))
                .thenReturn(new ProjectAccessScope(9L, 1L, 9L, 20L, TeamRoleEnum.ADMIN));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assign(request(10L, 3L, 1L)));
        assertEquals(50001, ex.getErrorCode().getCode());
        verify(taskMapper, never()).compareAndSetAssignee(any(), any(), any(), any(), any());
    }

    @Test
    void expectedAssigneeMustBePresent() {
        TaskAssignRequest request = new TaskAssignRequest();
        request.setTaskId(10L);
        request.setAssigneeUserId(2L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.assign(request));
        assertEquals(40000, ex.getErrorCode().getCode());
        verifyNoInteractions(permissionService, taskMapper, taskAssignmentLogMapper);
    }

    @Test
    void logFailureFailsOperationAfterCas() {
        Task task = task(10L, 1L, null);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(permissionService.requireProjectManage(9L, 1L))
                .thenReturn(new ProjectAccessScope(9L, 1L, 9L, 20L, TeamRoleEnum.ADMIN));
        when(taskMapper.compareAndSetAssignee(eq(10L), isNull(), eq(3L), eq(9L), any())).thenReturn(1);
        when(taskAssignmentLogMapper.insert(any(com.spt.learningmanage.model.entity.TaskAssignmentLog.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assign(request(10L, 3L, null)));
        assertEquals(50000, ex.getErrorCode().getCode());
    }

    @Test
    void reasonRejectsControlCharacters() {
        TaskAssignRequest request = request(10L, 2L, null);
        request.setReason("bad\nreason");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.assign(request));
        assertEquals(40000, ex.getErrorCode().getCode());
        verifyNoInteractions(permissionService, taskMapper, taskAssignmentLogMapper);
    }

    private TaskAssignRequest request(Long taskId, Long assignee, Long expected) {
        TaskAssignRequest request = new TaskAssignRequest();
        request.setTaskId(taskId);
        request.setAssigneeUserId(assignee);
        request.setExpectedAssigneeUserId(expected);
        return request;
    }

    private Task task(Long id, Long projectId, Long assignee) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId(projectId);
        task.setAssigneeUserId(assignee);
        task.setIsDelete(0);
        return task;
    }
}
