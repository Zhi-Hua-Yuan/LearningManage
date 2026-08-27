package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TaskStatusIdempotencyMapper;
import com.spt.learningmanage.mapper.TaskTitleRenameLogMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
import com.spt.learningmanage.model.dto.task.TaskCreateRequest;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.vo.task.TaskAssignmentLogVO;
import com.spt.learningmanage.model.vo.task.TaskAssignmentResultVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAssignmentServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Task.class);
        TableInfoHelper.initTableInfo(assistant, TaskAssignmentLog.class);
    }

    @Mock private TaskMapper taskMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private MilestoneMapper milestoneMapper;
    @Mock private TaskTitleRenameLogMapper taskTitleRenameLogMapper;
    @Mock private TaskStatusIdempotencyMapper taskStatusIdempotencyMapper;
    @Mock private TaskAssignmentLogMapper taskAssignmentLogMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private UserMapper userMapper;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private TaskServiceImpl taskService;

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void assign_shouldWriteTaskAndAuditLogAtomically() {
        UserHolder.set(11L);
        Task task = task(101L, 21L, 11L);
        Project project = teamProject(21L, 31L);
        User target = user(12L);
        TeamMember membership = new TeamMember();
        membership.setTeamId(31L);
        membership.setUserId(12L);
        membership.setRole("MEMBER");
        membership.setIsDelete(0);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(projectMapper.selectById(21L)).thenReturn(project);
        when(userMapper.selectById(12L)).thenReturn(target);
        when(teamMemberMapper.selectOne(any())).thenReturn(membership);
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(taskAssignmentLogMapper.insert(any(TaskAssignmentLog.class))).thenAnswer(invocation -> {
            TaskAssignmentLog log = invocation.getArgument(0);
            log.setId(9001L);
            return 1;
        });

        TaskAssignRequest request = new TaskAssignRequest();
        request.setTaskId(101L);
        request.setAssigneeUserId(12L);
        request.setExpectedAssigneeUserId(11L);
        request.setReason("调整本周安排");

        TaskAssignmentResultVO result = taskService.assign(request);

        assertTrue(result.getChanged());
        assertEquals("REASSIGN", result.getAction());
        assertEquals(9001L, result.getLogId());
        verify(taskAssignmentLogMapper).insert(any(TaskAssignmentLog.class));
    }

    @Test
    void create_personalTask_shouldCreateInitialAssignmentLog() {
        UserHolder.set(11L);
        Project project = new Project();
        project.setId(21L);
        project.setUserId(11L);
        project.setIsDelete(0);
        when(projectMapper.selectOne(any())).thenReturn(project);
        when(projectMapper.selectById(21L)).thenReturn(project);
        when(taskMapper.insert(any(Task.class))).thenAnswer(invocation -> {
            Task created = invocation.getArgument(0);
            created.setId(101L);
            return 1;
        });
        when(taskMapper.selectCount(any())).thenReturn(1L, 0L);
        when(projectMapper.update(any(), any())).thenReturn(1);
        when(taskAssignmentLogMapper.insert(any(TaskAssignmentLog.class))).thenReturn(1);

        TaskCreateRequest request = new TaskCreateRequest();
        request.setProjectId(21L);
        request.setTitle("个人任务");

        assertEquals(101L, taskService.create(request));
        verify(taskAssignmentLogMapper).insert(any(TaskAssignmentLog.class));
    }

    @Test
    void assign_shouldRejectStaleExpectedAssigneeWithoutWriting() {
        UserHolder.set(11L);
        Task task = task(101L, 1001L, 21L);
        task.setAssigneeUserId(12L);
        when(taskMapper.selectOne(any())).thenReturn(task);

        TaskAssignRequest request = new TaskAssignRequest();
        request.setTaskId(101L);
        request.setAssigneeUserId(13L);
        request.setExpectedAssigneeUserId(11L);

        BusinessException exception = assertThrows(BusinessException.class, () -> taskService.assign(request));

        assertEquals(ErrorCode.OPERATION_ERROR, exception.getErrorCode());
        verify(taskMapper, never()).update(any(), any());
        verify(taskAssignmentLogMapper, never()).insert(any(TaskAssignmentLog.class));
    }

    @Test
    void assign_shouldRejectNonMemberTarget() {
        UserHolder.set(11L);
        when(taskMapper.selectOne(any())).thenReturn(task(101L, 21L, 11L));
        when(projectMapper.selectById(21L)).thenReturn(teamProject(21L, 31L));
        when(userMapper.selectById(12L)).thenReturn(user(12L));
        when(teamMemberMapper.selectOne(any())).thenReturn(null);

        TaskAssignRequest request = new TaskAssignRequest();
        request.setTaskId(101L);
        request.setAssigneeUserId(12L);

        BusinessException exception = assertThrows(BusinessException.class, () -> taskService.assign(request));

        assertEquals(ErrorCode.PARAMS_ERROR, exception.getErrorCode());
        verify(taskMapper, never()).update(any(), any());
    }

    @Test
    void assign_shouldRejectCompareAndSetConflictWithoutAuditLog() {
        UserHolder.set(11L);
        when(taskMapper.selectOne(any())).thenReturn(task(101L, 21L, 11L));
        when(projectMapper.selectById(21L)).thenReturn(teamProject(21L, 31L));
        when(userMapper.selectById(12L)).thenReturn(user(12L));
        TeamMember membership = new TeamMember();
        membership.setTeamId(31L);
        membership.setUserId(12L);
        membership.setRole("MEMBER");
        membership.setIsDelete(0);
        when(teamMemberMapper.selectOne(any())).thenReturn(membership);
        when(taskMapper.update(any(), any())).thenReturn(0);

        TaskAssignRequest request = new TaskAssignRequest();
        request.setTaskId(101L);
        request.setAssigneeUserId(12L);

        BusinessException exception = assertThrows(BusinessException.class, () -> taskService.assign(request));

        assertEquals(ErrorCode.OPERATION_ERROR, exception.getErrorCode());
        verify(taskAssignmentLogMapper, never()).insert(any(TaskAssignmentLog.class));
    }

    @Test
    void assign_nullTarget_shouldWriteUnassignLog() {
        UserHolder.set(11L);
        Task task = task(101L, 21L, 11L);
        task.setAssigneeUserId(12L);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(taskAssignmentLogMapper.insert(any(TaskAssignmentLog.class))).thenReturn(1);

        TaskAssignRequest request = new TaskAssignRequest();
        request.setTaskId(101L);
        request.setExpectedAssigneeUserId(12L);
        request.setExpectedAssigneeProvided(true);

        TaskAssignmentResultVO result = taskService.assign(request);

        assertEquals("UNASSIGN", result.getAction());
        assertEquals(null, result.getToAssigneeUserId());
        verify(taskAssignmentLogMapper).insert(any(TaskAssignmentLog.class));
    }

    @Test
    void assignmentHistory_shouldRequireViewAndReturnOrderedRecords() {
        UserHolder.set(11L);
        TaskAssignmentLog log = new TaskAssignmentLog();
        log.setId(9001L);
        log.setTaskId(101L);
        log.setAction("REASSIGN");
        log.setCreateTime(LocalDateTime.now());
        when(taskAssignmentLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(log));

        List<TaskAssignmentLogVO> result = taskService.assignmentHistory(101L);

        assertEquals(1, result.size());
        assertEquals("REASSIGN", result.get(0).getAction());
        verify(permissionService).requireTaskView(11L, 101L);
    }

    private Task task(Long id, Long projectId, Long creatorId) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId(projectId);
        task.setUserId(creatorId);
        task.setAssigneeUserId(creatorId);
        task.setIsDelete(0);
        return task;
    }

    private Project teamProject(Long id, Long teamId) {
        Project project = new Project();
        project.setId(id);
        project.setUserId(11L);
        project.setTeamId(teamId);
        project.setIsDelete(0);
        return project;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUserRole("USER");
        user.setIsDelete(0);
        return user;
    }
}
