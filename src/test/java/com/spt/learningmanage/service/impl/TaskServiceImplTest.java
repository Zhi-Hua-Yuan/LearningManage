package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TaskStatusIdempotencyMapper;
import com.spt.learningmanage.mapper.TaskTitleRenameLogMapper;
import com.spt.learningmanage.model.dto.task.TaskStatusChangeRequest;
import com.spt.learningmanage.model.dto.task.TaskUpdateRequest;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TaskStatusIdempotency;
import com.spt.learningmanage.model.vo.task.TaskStatusChangeVO;
import com.spt.learningmanage.utils.UserHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Task.class
        );
    }

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private MilestoneMapper milestoneMapper;
    @Mock
    private TaskTitleRenameLogMapper taskTitleRenameLogMapper;
    @Mock
    private TaskStatusIdempotencyMapper taskStatusIdempotencyMapper;

    @InjectMocks
    private TaskServiceImpl taskService;

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void update_shouldRejectStatusChange() {
        UserHolder.set(1L);
        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setId(100L);
        request.setStatus(1);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> taskService.update(request));
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR, ex.getErrorCode());
    }

    @Test
    void changeStatus_shouldReplayWhenIdempotencyRecordExists() {
        UserHolder.set(1L);
        TaskStatusChangeRequest request = new TaskStatusChangeRequest();
        request.setTaskId(100L);
        request.setTargetStatus(1);
        request.setClientRequestId("req-1");

        TaskStatusIdempotency record = new TaskStatusIdempotency();
        record.setChanged(1);
        record.setFinalStatus(1);
        record.setCompletedAt(LocalDateTime.now());
        when(taskStatusIdempotencyMapper.selectOne(any())).thenReturn(record);

        TaskStatusChangeVO result = taskService.changeStatus(request);
        Assertions.assertTrue(result.getIdempotentReplay());
        Assertions.assertTrue(result.getChanged());
        Assertions.assertEquals(1, result.getFinalStatus());
        verify(taskMapper, never()).update(any(), any());
    }

    @Test
    void changeStatus_shouldThrowWhenConcurrentConflict() {
        UserHolder.set(1L);
        TaskStatusChangeRequest request = new TaskStatusChangeRequest();
        request.setTaskId(100L);
        request.setTargetStatus(1);
        request.setClientRequestId("req-2");

        Task task = new Task();
        task.setId(100L);
        task.setUserId(1L);
        task.setStatus(0);
        when(taskStatusIdempotencyMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectOne(any())).thenReturn(task, taskWithStatus(0));
        when(taskMapper.update(any(), any())).thenReturn(0);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> taskService.changeStatus(request));
        Assertions.assertEquals(ErrorCode.OPERATION_ERROR, ex.getErrorCode());
    }

    @Test
    void changeStatus_shouldSetCompletedAtWhenTodoToDone() {
        UserHolder.set(1L);
        TaskStatusChangeRequest request = new TaskStatusChangeRequest();
        request.setTaskId(100L);
        request.setTargetStatus(1);
        request.setClientRequestId("req-3");

        Task task = new Task();
        task.setId(100L);
        task.setUserId(1L);
        task.setStatus(0);
        task.setCompletedAt(null);
        when(taskStatusIdempotencyMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(taskStatusIdempotencyMapper.insert(any(TaskStatusIdempotency.class))).thenReturn(1);

        TaskStatusChangeVO result = taskService.changeStatus(request);
        Assertions.assertFalse(result.getIdempotentReplay());
        Assertions.assertTrue(result.getChanged());
        Assertions.assertEquals(1, result.getFinalStatus());
        Assertions.assertNotNull(result.getCompletedAt());
    }

    private Task taskWithStatus(int status) {
        Task task = new Task();
        task.setId(100L);
        task.setUserId(1L);
        task.setStatus(status);
        return task;
    }
}
