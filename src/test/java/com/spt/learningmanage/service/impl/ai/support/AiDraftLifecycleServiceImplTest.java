package com.spt.learningmanage.service.impl.ai.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.constant.AiDraftStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftCreateCommand;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.service.ai.draft.AiDraftHandlerRegistry;
import com.spt.learningmanage.service.impl.ai.draft.AiDraftStateMachine;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiDraftLifecycleServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiDraft.class);
    }

    @AfterEach
    void clearUser() {
        UserHolder.remove();
    }

    @Test
    void createDraftPersistsVersionTraceAndPreviewContract() {
        AiDraftMapper draftMapper = mock(AiDraftMapper.class);
        AiDraftHandlerRegistry registry = mock(AiDraftHandlerRegistry.class);
        AiDraftStateMachine stateMachine = mock(AiDraftStateMachine.class);
        when(registry.currentSchemaVersion("task-breakdown")).thenReturn(1);
        when(stateMachine.now()).thenReturn(LocalDateTime.of(2026, 9, 4, 12, 0));
        when(draftMapper.insert(any(AiDraft.class))).thenReturn(1);
        AiDraftLifecycleServiceImpl service = new AiDraftLifecycleServiceImpl(draftMapper, registry, stateMachine);

        AiDraft draft = service.createDraft(new AiDraftCreateCommand(
                1L, "task-breakdown", "{}", "hash", 1, "a".repeat(32)));

        assertNotNull(draft.getDraftId());
        assertEquals(AiDraftStatusEnum.PREVIEW.getValue(), draft.getStatus());
        assertEquals(1, draft.getSchemaVersion());
        assertEquals("a".repeat(32), draft.getTraceId());
        assertEquals(LocalDateTime.of(2026, 9, 4, 12, 20), draft.getExpireAt());
    }

    @Test
    void cancelDraftIsIdempotentForCanceledDraft() {
        AiDraftMapper draftMapper = mock(AiDraftMapper.class);
        AiDraftStateMachine stateMachine = mock(AiDraftStateMachine.class);
        when(draftMapper.selectOne(any())).thenReturn(draft(AiDraftStatusEnum.CANCELED.getValue()));
        UserHolder.set(1L);

        assertTrue(service(draftMapper, stateMachine).cancelDraft("draft", null));
        verify(stateMachine, never()).markCanceled(any());
    }

    @Test
    void cancelDraftRejectsConfirmedDraft() {
        AiDraftMapper draftMapper = mock(AiDraftMapper.class);
        AiDraftStateMachine stateMachine = mock(AiDraftStateMachine.class);
        when(draftMapper.selectOne(any())).thenReturn(draft(AiDraftStatusEnum.CONFIRMED.getValue()));
        UserHolder.set(1L);

        assertThrows(BusinessException.class,
                () -> service(draftMapper, stateMachine).cancelDraft("draft", null));
    }

    @Test
    void cancelDraftRereadsWinnerAfterCasLoss() {
        AiDraftMapper draftMapper = mock(AiDraftMapper.class);
        AiDraftStateMachine stateMachine = mock(AiDraftStateMachine.class);
        when(draftMapper.selectOne(any()))
                .thenReturn(draft(AiDraftStatusEnum.PREVIEW.getValue()))
                .thenReturn(draft(AiDraftStatusEnum.CANCELED.getValue()));
        when(stateMachine.isExpired(any())).thenReturn(false);
        when(stateMachine.markCanceled(10L)).thenReturn(false);
        UserHolder.set(1L);

        assertTrue(service(draftMapper, stateMachine).cancelDraft("draft", null));
        verify(draftMapper, org.mockito.Mockito.times(2)).selectOne(any());
    }

    private AiDraftLifecycleServiceImpl service(AiDraftMapper draftMapper,
                                                AiDraftStateMachine stateMachine) {
        return new AiDraftLifecycleServiceImpl(
                draftMapper, mock(AiDraftHandlerRegistry.class), stateMachine);
    }

    private AiDraft draft(Integer status) {
        AiDraft draft = new AiDraft();
        draft.setId(10L);
        draft.setDraftId("draft");
        draft.setUserId(1L);
        draft.setScene("task-breakdown");
        draft.setStatus(status);
        draft.setExpireAt(LocalDateTime.now().plusMinutes(5));
        return draft;
    }
}
