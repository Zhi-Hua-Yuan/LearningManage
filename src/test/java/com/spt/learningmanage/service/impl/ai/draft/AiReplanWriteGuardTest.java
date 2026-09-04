package com.spt.learningmanage.service.impl.ai.draft;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.AiReplanOperationMapper;
import com.spt.learningmanage.model.entity.AiReplanOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReplanWriteGuardTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiReplanOperation.class);
    }

    private final AiReplanOperationMapper mapper = mock(AiReplanOperationMapper.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-04T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private AiReplanWriteGuard guard;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        guard = new AiReplanWriteGuard(mapper, transactionManager, clock);
    }

    @Test
    void confirmLocksRunsWorkAndCasTransitionsToConfirmed() {
        AiReplanOperation operation = operation(AiReplanWriteGuard.PREVIEW,
                LocalDateTime.of(2026, 9, 4, 12, 20));
        when(mapper.selectForUpdate(1L, 10L, "op-1")).thenReturn(operation);
        when(mapper.update(isNull(), any())).thenReturn(1);

        assertTrue(guard.confirm(1L, 10L, "op-1", ignored -> true));

        verify(mapper).selectForUpdate(1L, 10L, "op-1");
        verify(mapper).update(isNull(), any());
        verify(transactionManager).commit(any(TransactionStatus.class));
    }

    @Test
    void expiredOperationCommitsExpiredStateBeforeReturningError() {
        AiReplanOperation operation = operation(AiReplanWriteGuard.PREVIEW,
                LocalDateTime.of(2026, 9, 4, 12, 0));
        when(mapper.selectForUpdate(1L, 10L, "op-1")).thenReturn(operation);
        when(mapper.update(isNull(), any())).thenReturn(1);

        assertThrows(BusinessException.class,
                () -> guard.confirm(1L, 10L, "op-1", ignored -> true));

        verify(transactionManager).commit(any(TransactionStatus.class));
        verify(transactionManager, never()).rollback(any(TransactionStatus.class));
    }

    @Test
    void workFailureRollsBackAndDoesNotAdvanceOperationState() {
        AiReplanOperation operation = operation(AiReplanWriteGuard.PREVIEW,
                LocalDateTime.of(2026, 9, 4, 12, 20));
        when(mapper.selectForUpdate(1L, 10L, "op-1")).thenReturn(operation);

        assertThrows(IllegalStateException.class,
                () -> guard.confirm(1L, 10L, "op-1", ignored -> {
                    throw new IllegalStateException("task update failed");
                }));

        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void cancellationPreservesLegacyFalseForTerminalOperation() {
        when(mapper.selectForUpdateByUser(1L, "op-1"))
                .thenReturn(operation(AiReplanWriteGuard.CONFIRMED, null));

        assertFalse(guard.cancel(1L, "op-1"));

        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void scheduledExpiryOnlyTargetsExpiredPreviewOperations() {
        when(mapper.update(isNull(), any())).thenReturn(3);

        assertEquals(3, guard.expirePreviewOperations());

        verify(mapper).update(isNull(), any());
    }

    private AiReplanOperation operation(int status, LocalDateTime expiresAt) {
        AiReplanOperation operation = new AiReplanOperation();
        operation.setId(20L);
        operation.setOperationId("op-1");
        operation.setUserId(1L);
        operation.setProjectId(10L);
        operation.setStatus(status);
        operation.setExpiresAt(expiresAt);
        return operation;
    }
}
