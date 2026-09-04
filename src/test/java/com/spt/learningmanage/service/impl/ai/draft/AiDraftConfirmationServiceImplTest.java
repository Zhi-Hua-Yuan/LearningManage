package com.spt.learningmanage.service.impl.ai.draft;

import com.spt.learningmanage.constant.AiDraftStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiDraftConfirmLogMapper;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftConfirmationCommand;
import com.spt.learningmanage.model.dto.ai.draft.TaskBreakdownConfirmationContext;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.AiDraftConfirmLog;
import com.spt.learningmanage.service.ai.draft.AiDraftHandler;
import com.spt.learningmanage.service.ai.draft.AiDraftHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiDraftConfirmationServiceImplTest {

    private final AiDraftMapper draftMapper = mock(AiDraftMapper.class);
    private final AiDraftConfirmLogMapper logMapper = mock(AiDraftConfirmLogMapper.class);
    private final AiDraftHandlerRegistry registry = mock(AiDraftHandlerRegistry.class);
    private final AiDraftStateMachine stateMachine = mock(AiDraftStateMachine.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    @SuppressWarnings("rawtypes")
    private final AiDraftHandler handler = mock(AiDraftHandler.class);

    private AiDraftConfirmationServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(registry.require(any(), any())).thenReturn(handler);
        when(handler.contextType()).thenReturn(TaskBreakdownConfirmationContext.class);
        when(handler.supportedSchemaVersions()).thenReturn(Set.of(1));
        service = new AiDraftConfirmationServiceImpl(
                draftMapper, logMapper, registry, stateMachine, transactionManager);
    }

    @Test
    void firstConfirmationWritesBusinessLogAndTerminalStateAtomically() {
        AiDraft draft = draft(AiDraftStatusEnum.PREVIEW.getValue());
        when(draftMapper.selectForUpdate(1L, "draft-1", "task-breakdown")).thenReturn(draft);
        when(logMapper.selectByUserAndDraft(1L, "draft-1")).thenReturn(null);
        when(stateMachine.isExpired(draft)).thenReturn(false);
        when(handler.applyValidated(draft, command().context())).thenReturn(101L);
        when(logMapper.insert(any(AiDraftConfirmLog.class))).thenReturn(1);
        when(stateMachine.markConfirmed(11L)).thenReturn(true);

        var result = service.confirm(command());

        assertTrue(result.getSuccess());
        assertFalse(result.getIdempotentReplay());
        assertEquals(101L, result.getBusinessId());
        verify(logMapper).insert(org.mockito.ArgumentMatchers.<AiDraftConfirmLog>argThat(log ->
                "op-1".equals(log.getOperationId())
                        && "f".repeat(32).equals(log.getTraceId())
                        && Long.valueOf(101L).equals(log.getBusinessId())));
        verify(transactionManager).commit(any(TransactionStatus.class));
    }

    @Test
    void differentOperationIdReplaysFirstBusinessResult() {
        AiDraft draft = draft(AiDraftStatusEnum.CONFIRMED.getValue());
        AiDraftConfirmLog existing = new AiDraftConfirmLog();
        existing.setUserId(1L);
        existing.setDraftId("draft-1");
        existing.setOperationId("first-operation");
        existing.setScene("task-breakdown");
        existing.setBusinessId(101L);
        when(draftMapper.selectForUpdate(1L, "draft-1", "task-breakdown")).thenReturn(draft);
        when(logMapper.selectByUserAndDraft(1L, "draft-1")).thenReturn(existing);

        AiDraftConfirmationCommand retry = new AiDraftConfirmationCommand(
                1L, "draft-1", "new-operation", "task-breakdown",
                new TaskBreakdownConfirmationContext(null, null));
        var result = service.confirm(retry);

        assertTrue(result.getIdempotentReplay());
        assertEquals(101L, result.getBusinessId());
        verify(handler, never()).applyValidated(any(), any());
        verify(logMapper, never()).insert(any(AiDraftConfirmLog.class));
        verify(stateMachine, never()).markConfirmed(any());
    }

    @Test
    void expiryTransitionCommitsBeforeExpiredErrorIsReturned() {
        AiDraft draft = draft(AiDraftStatusEnum.PREVIEW.getValue());
        when(draftMapper.selectForUpdate(1L, "draft-1", "task-breakdown")).thenReturn(draft);
        when(stateMachine.isExpired(draft)).thenReturn(true);
        when(stateMachine.markExpired(11L)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirm(command()));

        assertEquals(ErrorCode.AI_DRAFT_EXPIRED, exception.getErrorCode());
        verify(transactionManager).commit(any(TransactionStatus.class));
        verify(transactionManager, never()).rollback(any(TransactionStatus.class));
        verify(handler, never()).applyValidated(any(), any());
    }

    @Test
    void handlerFailureRollsBackWithoutWritingConfirmationState() {
        AiDraft draft = draft(AiDraftStatusEnum.PREVIEW.getValue());
        when(draftMapper.selectForUpdate(1L, "draft-1", "task-breakdown")).thenReturn(draft);
        when(stateMachine.isExpired(draft)).thenReturn(false);
        when(handler.applyValidated(draft, command().context()))
                .thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "业务写入失败"));

        assertThrows(BusinessException.class, () -> service.confirm(command()));

        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(logMapper, never()).insert(any(AiDraftConfirmLog.class));
        verify(stateMachine, never()).markConfirmed(any());
    }

    @Test
    void unsupportedSchemaFailsClosedWithoutExecutingHandler() {
        AiDraft draft = draft(AiDraftStatusEnum.PREVIEW.getValue());
        draft.setSchemaVersion(2);
        when(draftMapper.selectForUpdate(1L, "draft-1", "task-breakdown")).thenReturn(draft);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirm(command()));

        assertEquals(ErrorCode.AI_DRAFT_SCHEMA_UNSUPPORTED, exception.getErrorCode());
        verify(handler, never()).applyValidated(any(), any());
    }

    private AiDraftConfirmationCommand command() {
        return new AiDraftConfirmationCommand(
                1L, "draft-1", "op-1", "task-breakdown",
                new TaskBreakdownConfirmationContext(null, null));
    }

    private AiDraft draft(Integer status) {
        AiDraft draft = new AiDraft();
        draft.setId(11L);
        draft.setDraftId("draft-1");
        draft.setUserId(1L);
        draft.setScene("task-breakdown");
        draft.setSchemaVersion(1);
        draft.setStatus(status);
        draft.setTraceId("f".repeat(32));
        draft.setExpireAt(LocalDateTime.now().plusMinutes(20));
        return draft;
    }
}
