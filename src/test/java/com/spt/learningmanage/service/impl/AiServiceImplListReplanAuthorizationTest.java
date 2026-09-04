package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiReplanItemMapper;
import com.spt.learningmanage.mapper.AiReplanOperationMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.AiReplanOperation;
import com.spt.learningmanage.model.entity.AiReplanItem;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.ai.AiListReplanPreviewVO;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import com.spt.learningmanage.service.impl.ai.scene.ListReplanAiServiceImpl;
import com.spt.learningmanage.service.impl.ai.support.AiJsonResponseSanitizerImpl;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceImplListReplanAuthorizationTest {

    private static final Long USER_ID = 1L;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Project.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiReplanOperation.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiReplanItem.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Task.class);
    }

    @Mock private TaskMapper taskMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private AiReplanOperationMapper aiReplanOperationMapper;
    @Mock private AiReplanItemMapper aiReplanItemMapper;
    @Mock private AiCallLogService aiCallLogService;
    @Mock private AiModelClient aiModelClient;
    @Mock private PromptTemplateResolver promptTemplateResolver;
    @Mock private PermissionService permissionService;
    @Mock private AiModelSelector modelSelector;

    private ListReplanAiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        AiInvocationPipeline pipeline = new AiInvocationPipeline(
                promptTemplateResolver, aiModelClient, aiCallLogService);
        aiService = new ListReplanAiServiceImpl(
                taskMapper, projectMapper, aiReplanOperationMapper, aiReplanItemMapper,
                pipeline, permissionService, modelSelector, new AiJsonResponseSanitizerImpl());
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void preview_shouldRejectUnauthorizedProjectBeforeModelInvocation() {
        UserHolder.set(USER_ID);
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权管理项目"))
                .when(permissionService).requireProjectManage(USER_ID, 9001L);

        assertThrows(BusinessException.class, () -> aiService.previewListReplan(9001L));

        verify(aiModelClient, never()).chat(any());
        verify(aiCallLogService, never()).createRunningLog(any());
    }

    @Test
    void preview_shouldRejectMissingProjectBeforeModelInvocation() {
        UserHolder.set(USER_ID);
        when(projectMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> aiService.previewListReplan(9002L));

        verify(aiModelClient, never()).chat(any());
        verify(aiCallLogService, never()).createRunningLog(any());
    }

    @Test
    void confirm_shouldRejectUnauthorizedProjectBeforeReadingOperation() {
        UserHolder.set(USER_ID);
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权管理项目"))
                .when(permissionService).requireProjectManage(USER_ID, 9003L);

        assertThrows(BusinessException.class,
                () -> aiService.confirmListReplan(9003L, "operation-1"));

        verify(aiReplanOperationMapper, never()).selectOne(any());
        verify(aiModelClient, never()).chat(any());
    }

    @Test
    void execute_shouldRejectUnauthorizedProjectBeforeModelInvocation() {
        UserHolder.set(USER_ID);
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权管理项目"))
                .when(permissionService).requireProjectManage(USER_ID, 9004L);

        assertThrows(BusinessException.class, () -> aiService.replanListTasks(9004L));

        verify(aiModelClient, never()).chat(any());
        verify(taskMapper, never()).selectList(any());
    }

    @Test
    void execute_shouldRejectMissingProjectBeforeModelInvocation() {
        UserHolder.set(USER_ID);
        when(projectMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> aiService.replanListTasks(9005L));

        verify(aiModelClient, never()).chat(any());
        verify(taskMapper, never()).selectList(any());
    }

    @Test
    void previewWithNoTasksPersistsEmptyOperationWithoutCallingModel() {
        UserHolder.set(USER_ID);
        Project project = new Project();
        project.setId(9010L);
        when(projectMapper.selectOne(any())).thenReturn(project);
        when(taskMapper.selectList(any())).thenReturn(java.util.List.of());
        when(aiReplanOperationMapper.insert(any(AiReplanOperation.class))).thenReturn(1);

        AiListReplanPreviewVO result = aiService.previewListReplan(9010L);

        assertTrue(result.getOperationId() != null && !result.getOperationId().isBlank());
        assertEquals(0, result.getChangedCount());
        assertTrue(result.getPreviewTasks().isEmpty());
        verify(aiReplanOperationMapper).insert(any(AiReplanOperation.class));
        verify(aiModelClient, never()).chat(any());
    }

    @Test
    void cancelPreviewOperationUpdatesStateAndIsNotModelBacked() {
        UserHolder.set(USER_ID);
        AiReplanOperation operation = new AiReplanOperation();
        operation.setId(11L);
        operation.setOperationId("operation-1");
        operation.setUserId(USER_ID);
        operation.setStatus(0);
        when(aiReplanOperationMapper.selectOne(any())).thenReturn(operation);
        when(aiReplanOperationMapper.update(any(), any())).thenReturn(1);

        assertTrue(aiService.cancelListReplan(" operation-1 "));
        verify(aiReplanOperationMapper).update(any(), any());
        verify(aiModelClient, never()).chat(any());
    }

    @Test
    void confirmAppliesChangedItemsAndMarksOperationConfirmed() {
        UserHolder.set(USER_ID);
        Project project = new Project();
        project.setId(9011L);
        project.setEndDate(java.time.LocalDate.of(2026, 9, 20));
        AiReplanOperation operation = new AiReplanOperation();
        operation.setId(12L);
        operation.setOperationId("operation-2");
        operation.setUserId(USER_ID);
        operation.setProjectId(9011L);
        operation.setStatus(0);
        AiReplanItem item = new AiReplanItem();
        item.setTaskId(301L);
        item.setOldTitle("旧任务");
        item.setNewTitle("新任务");
        item.setOldPriority(1);
        item.setNewPriority(2);
        item.setOldDueDate(java.time.LocalDate.of(2026, 9, 10));
        item.setNewDueDate(java.time.LocalDate.of(2026, 9, 15));
        when(aiReplanOperationMapper.selectOne(any())).thenReturn(operation);
        when(aiReplanItemMapper.selectList(any())).thenReturn(java.util.List.of(item));
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(taskMapper.selectList(any())).thenReturn(java.util.List.of(task(301L, 9011L,
                java.time.LocalDate.of(2026, 9, 20))));
        when(projectMapper.selectOne(any())).thenReturn(project);
        when(aiReplanOperationMapper.update(any(), any())).thenReturn(1);

        assertTrue(aiService.confirmListReplan(9011L, " operation-2 "));

        verify(taskMapper).update(any(), any());
        verify(aiReplanOperationMapper).update(any(), any());
    }

    @Test
    void confirmDoesNotMarkOperationWhenTaskUpdateFails() {
        UserHolder.set(USER_ID);
        AiReplanOperation operation = new AiReplanOperation();
        operation.setId(13L);
        operation.setOperationId("operation-3");
        operation.setUserId(USER_ID);
        operation.setProjectId(9012L);
        operation.setStatus(0);
        AiReplanItem item = new AiReplanItem();
        item.setTaskId(302L);
        item.setOldTitle("旧任务");
        item.setNewTitle("新任务");
        item.setOldPriority(1);
        item.setNewPriority(2);
        when(aiReplanOperationMapper.selectOne(any())).thenReturn(operation);
        when(aiReplanItemMapper.selectList(any())).thenReturn(java.util.List.of(item));
        when(taskMapper.update(any(), any())).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class,
                () -> aiService.confirmListReplan(9012L, "operation-3"));
        verify(aiReplanOperationMapper, never()).update(any(), any());
    }

    private Task task(Long id, Long projectId, java.time.LocalDate dueDate) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId(projectId);
        task.setStatus(0);
        task.setIsDelete(0);
        task.setTitle("任务");
        task.setPriority(2);
        task.setDueDate(dueDate);
        return task;
    }
}
