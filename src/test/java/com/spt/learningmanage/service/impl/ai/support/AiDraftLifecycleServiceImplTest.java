package com.spt.learningmanage.service.impl.ai.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.constant.AiDraftStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.AiDraftConfirmLogMapper;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.AiDraftConfirmLog;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiDraftLifecycleServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiDraft.class);
        TableInfoHelper.initTableInfo(assistant, AiDraftConfirmLog.class);
    }

    @AfterEach
    void clearUser() {
        UserHolder.remove();
    }

    @Test
    void createDraftPreservesPreviewContract() {
        AiDraftMapper draftMapper = mock(AiDraftMapper.class);
        when(draftMapper.insert(any(AiDraft.class))).thenReturn(1);
        AiDraftLifecycleServiceImpl service = service(draftMapper, mock(AiDraftConfirmLogMapper.class));

        AiDraft draft = service.createDraft(1L, "task-breakdown", "{}", "hash");

        assertNotNull(draft.getDraftId());
        assertEquals(AiDraftStatusEnum.PREVIEW.getValue(), draft.getStatus());
        assertEquals("task-breakdown", draft.getScene());
        assertTrue(draft.getExpireAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void cancelDraftIsIdempotentForCanceledDraft() {
        AiDraftMapper draftMapper = mock(AiDraftMapper.class);
        AiDraft draft = draft(AiDraftStatusEnum.CANCELED.getValue());
        when(draftMapper.selectOne(any())).thenReturn(draft);
        UserHolder.set(1L);

        assertTrue(service(draftMapper, mock(AiDraftConfirmLogMapper.class)).cancelDraft("draft", null));
        verify(draftMapper, never()).update(isNull(), any());
    }

    @Test
    void cancelDraftRejectsConfirmedDraft() {
        AiDraftMapper draftMapper = mock(AiDraftMapper.class);
        when(draftMapper.selectOne(any())).thenReturn(draft(AiDraftStatusEnum.CONFIRMED.getValue()));
        UserHolder.set(1L);

        assertThrows(BusinessException.class,
                () -> service(draftMapper, mock(AiDraftConfirmLogMapper.class)).cancelDraft("draft", null));
    }

    @Test
    void confirmResultKeepsIdempotentReplayFlag() {
        AiDraftLifecycleServiceImpl service = service(mock(AiDraftMapper.class), mock(AiDraftConfirmLogMapper.class));

        assertTrue(service.buildConfirmResult(true, 9L).getIdempotentReplay());
        assertEquals(9L, service.buildConfirmResult(false, 9L).getBusinessId());
        assertFalse(service.buildConfirmResult(false, 9L).getIdempotentReplay());
    }

    private AiDraftLifecycleServiceImpl service(AiDraftMapper draftMapper,
                                                AiDraftConfirmLogMapper confirmLogMapper) {
        return new AiDraftLifecycleServiceImpl(draftMapper, confirmLogMapper);
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
