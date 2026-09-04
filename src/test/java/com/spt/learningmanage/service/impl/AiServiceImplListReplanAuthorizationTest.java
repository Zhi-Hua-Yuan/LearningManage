package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiDraftConfirmLogMapper;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.mapper.AiReplanItemMapper;
import com.spt.learningmanage.mapper.AiReplanOperationMapper;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TaskTitleRenameLogMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
    }

    @Mock private AiProperties aiProperties;
    @Mock private TaskMapper taskMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private TaskTitleRenameLogMapper taskTitleRenameLogMapper;
    @Mock private AiDraftMapper aiDraftMapper;
    @Mock private AiDraftConfirmLogMapper aiDraftConfirmLogMapper;
    @Mock private MilestoneMapper milestoneMapper;
    @Mock private WeeklyReviewMapper weeklyReviewMapper;
    @Mock private AiReplanOperationMapper aiReplanOperationMapper;
    @Mock private AiReplanItemMapper aiReplanItemMapper;
    @Mock private AiCallLogService aiCallLogService;
    @Mock private AiModelClient aiModelClient;
    @Mock private PromptTemplateResolver promptTemplateResolver;
    @Mock private PermissionService permissionService;
    @Mock private TaskCreationService taskCreationService;

    @InjectMocks
    private AiServiceImpl aiService;

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
}
