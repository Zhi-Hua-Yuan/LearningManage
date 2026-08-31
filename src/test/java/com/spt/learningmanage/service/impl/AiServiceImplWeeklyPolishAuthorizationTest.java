package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.constant.AiDraftStatusEnum;
import com.spt.learningmanage.constant.AiSceneEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiDraftConfirmLogMapper;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.AiDraftConfirmLog;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class AiServiceImplWeeklyPolishAuthorizationTest {

    private static final Long USER_ID = 1L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiDraft.class);
        TableInfoHelper.initTableInfo(assistant, AiDraftConfirmLog.class);
        TableInfoHelper.initTableInfo(assistant, WeeklyReview.class);
    }

    @Mock
    private AiDraftMapper aiDraftMapper;
    @Mock
    private AiDraftConfirmLogMapper aiDraftConfirmLogMapper;
    @Mock
    private WeeklyReviewMapper weeklyReviewMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AiModelClient aiModelClient;

    @InjectMocks
    private AiServiceImpl aiService;

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void confirm_shouldRejectWhenPersistedTaskLosesReadPermission() {
        UserHolder.set(USER_ID);
        when(aiDraftMapper.selectOne(any())).thenReturn(draft("{\"taskIds\":[101],\"polished\":\"{\\\"review\\\":\\\"ok\\\"}\"}"));
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问任务"))
                .when(permissionService).requireAllTasksReadable(USER_ID, List.of(101L));

        assertThrows(BusinessException.class,
                () -> aiService.confirmWeeklyPolish("draft-1", "op-1", 7001L));

        verify(permissionService, never()).requireWeeklyReviewUpdate(any(), any());
        verify(weeklyReviewMapper, never()).updateById(any(WeeklyReview.class));
        verify(aiDraftMapper, never()).update(any(), any());
    }

    @Test
    void confirm_shouldRecheckReviewPermissionBeforeWriting() {
        UserHolder.set(USER_ID);
        when(aiDraftMapper.selectOne(any())).thenReturn(draft("{\"taskIds\":[],\"polished\":\"{\\\"review\\\":\\\"ok\\\"}\"}"));
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权更新周复盘"))
                .when(permissionService).requireWeeklyReviewUpdate(USER_ID, 7001L);

        assertThrows(BusinessException.class,
                () -> aiService.confirmWeeklyPolish("draft-1", "op-1", 7001L));

        verify(permissionService).requireWeeklyReviewUpdate(eq(USER_ID), eq(7001L));
        verify(weeklyReviewMapper, never()).selectByIdForUpdate(any());
        verify(weeklyReviewMapper, never()).updateById(any(WeeklyReview.class));
    }

    @Test
    void polish_shouldRejectUnauthorizedExplicitTaskBeforeModelInvocation() {
        UserHolder.set(USER_ID);
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问任务"))
                .when(permissionService).requireAllTasksReadable(USER_ID, List.of(101L, 202L));

        assertThrows(BusinessException.class,
                () -> aiService.polishWeeklyReview(List.of(101L, 202L), "本周反思"));

        verify(aiModelClient, never()).invoke(anyString(), anyString(), anyString());
        verifyNoWrites();
    }

    @Test
    void polish_shouldRejectMissingExplicitTaskBeforeModelInvocation() {
        UserHolder.set(USER_ID);
        when(permissionService.requireAllTasksReadable(USER_ID, List.of(999L)))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(999L)));
        when(taskMapper.selectList(any())).thenReturn(List.of());

        assertThrows(BusinessException.class,
                () -> aiService.polishWeeklyReview(List.of(999L), "本周反思"));

        verify(aiModelClient, never()).invoke(anyString(), anyString(), anyString());
        verifyNoWrites();
    }

    private void verifyNoWrites() {
        verify(aiDraftMapper, never()).insert(any(AiDraft.class));
        verify(weeklyReviewMapper, never()).updateById(any(WeeklyReview.class));
    }

    private AiDraft draft(String payload) {
        AiDraft draft = new AiDraft();
        draft.setId(9001L);
        draft.setDraftId("draft-1");
        draft.setUserId(USER_ID);
        draft.setScene(AiSceneEnum.WEEKLY_POLISH.getCode());
        draft.setPayloadJson(payload);
        draft.setStatus(AiDraftStatusEnum.PREVIEW.getValue());
        return draft;
    }
}
