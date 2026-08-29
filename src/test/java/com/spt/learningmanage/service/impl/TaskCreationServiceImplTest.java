package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.TaskAssignmentActionEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.TaskAssigneePolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskCreationServiceImplTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskAssignmentLogMapper logMapper;
    @Mock
    private TaskAssigneePolicy policy;

    @InjectMocks
    private TaskCreationServiceImpl service;

    @Test
    void createTask_teamAssignee_resolvesBeforeTaskInsertAndInitialLog() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L,
                com.spt.learningmanage.constant.TeamRoleEnum.OWNER);
        Task task = new Task();
        when(policy.resolveInitialAssignee(scope, 2L)).thenReturn(2L);
        when(taskMapper.insert(task)).thenAnswer(invocation -> {
            task.setId(100L);
            return 1;
        });
        when(logMapper.insert(any(TaskAssignmentLog.class))).thenReturn(1);

        Assertions.assertEquals(100L, service.createTask(task, scope, 2L));
        Assertions.assertEquals(1L, task.getCreatedByUserId());
        Assertions.assertEquals(2L, task.getAssigneeUserId());
        Assertions.assertEquals(1L, task.getAssignedByUserId());
        Assertions.assertNotNull(task.getAssignedAt());
        ArgumentCaptor<TaskAssignmentLog> captor = ArgumentCaptor.forClass(TaskAssignmentLog.class);
        InOrder inOrder = inOrder(policy, taskMapper, logMapper);
        inOrder.verify(policy).resolveInitialAssignee(scope, 2L);
        inOrder.verify(taskMapper).insert(task);
        inOrder.verify(logMapper).insert(captor.capture());
        TaskAssignmentLog log = captor.getValue();
        Assertions.assertEquals(100L, log.getTaskId());
        Assertions.assertEquals(TaskAssignmentActionEnum.INITIAL_ASSIGN.getValue(), log.getAction());
        Assertions.assertEquals(2L, log.getToAssigneeUserId());
        Assertions.assertNull(log.getFromAssigneeUserId());
    }

    @Test
    void createTask_teamUnassigned_doesNotWriteInitialLog() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L,
                com.spt.learningmanage.constant.TeamRoleEnum.MEMBER);
        Task task = new Task();
        when(policy.resolveInitialAssignee(scope, null)).thenReturn(null);
        when(taskMapper.insert(task)).thenAnswer(invocation -> {
            task.setId(101L);
            return 1;
        });

        service.createTask(task, scope, null);
        Assertions.assertNull(task.getAssigneeUserId());
        Assertions.assertNull(task.getAssignedAt());
        InOrder inOrder = inOrder(policy, taskMapper);
        inOrder.verify(policy).resolveInitialAssignee(scope, null);
        inOrder.verify(taskMapper).insert(task);
        verify(logMapper, never()).insert(any(TaskAssignmentLog.class));
    }

    @Test
    void createTask_invalidTeamAssignee_rejectsBeforeTaskInsert() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L,
                com.spt.learningmanage.constant.TeamRoleEnum.ADMIN);
        Task task = new Task();
        doThrow(new BusinessException(ErrorCode.PARAMS_ERROR, "负责人不是有效团队成员"))
                .when(policy).resolveInitialAssignee(scope, 2L);

        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> service.createTask(task, scope, 2L));

        Assertions.assertEquals(ErrorCode.PARAMS_ERROR, ex.getErrorCode());
        verify(policy).resolveInitialAssignee(scope, 2L);
        verifyNoInteractions(taskMapper, logMapper);
    }

    @Test
    void createTask_rejectsTaskInsertFailure() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, null, null);
        Task task = new Task();
        when(policy.resolveInitialAssignee(scope, null)).thenReturn(1L);
        when(taskMapper.insert(task)).thenReturn(0);
        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> service.createTask(task, scope, null));
        Assertions.assertEquals(ErrorCode.SYSTEM_ERROR, ex.getErrorCode());
        InOrder inOrder = inOrder(policy, taskMapper);
        inOrder.verify(policy).resolveInitialAssignee(scope, null);
        inOrder.verify(taskMapper).insert(task);
        verify(logMapper, never()).insert(any(TaskAssignmentLog.class));
    }

    @Test
    void createTask_initialLogFailure_propagatesAfterTaskInsert() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L,
                com.spt.learningmanage.constant.TeamRoleEnum.OWNER);
        Task task = new Task();
        when(policy.resolveInitialAssignee(scope, 2L)).thenReturn(2L);
        when(taskMapper.insert(task)).thenAnswer(invocation -> {
            task.setId(102L);
            return 1;
        });
        when(logMapper.insert(any(TaskAssignmentLog.class))).thenReturn(0);

        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> service.createTask(task, scope, 2L));

        Assertions.assertEquals(ErrorCode.SYSTEM_ERROR, ex.getErrorCode());
        InOrder inOrder = inOrder(policy, taskMapper, logMapper);
        inOrder.verify(policy).resolveInitialAssignee(scope, 2L);
        inOrder.verify(taskMapper).insert(task);
        inOrder.verify(logMapper).insert(argThat((TaskAssignmentLog log) ->
                TaskAssignmentActionEnum.INITIAL_ASSIGN.getValue().equals(log.getAction())));
    }
}
