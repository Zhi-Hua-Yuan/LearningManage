package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.TaskAssignmentActionEnum;
import com.spt.learningmanage.exception.BusinessException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void createTask_setsAssignmentAndWritesInitialLog() {
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
        verify(logMapper).insert(captor.capture());
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
        verify(logMapper, never()).insert(any(TaskAssignmentLog.class));
    }

    @Test
    void createTask_rejectsTaskInsertFailure() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, null, null);
        Task task = new Task();
        when(policy.resolveInitialAssignee(scope, null)).thenReturn(1L);
        when(taskMapper.insert(task)).thenReturn(0);
        Assertions.assertThrows(BusinessException.class, () -> service.createTask(task, scope, null));
        verify(logMapper, never()).insert(any(TaskAssignmentLog.class));
    }
}
