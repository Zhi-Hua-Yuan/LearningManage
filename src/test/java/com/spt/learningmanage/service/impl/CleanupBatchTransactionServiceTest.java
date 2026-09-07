package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.CleanupResourceTypeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.AiDataCleanupItemMapper;
import com.spt.learningmanage.model.entity.AiDataCleanupItem;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.model.ops.CleanupBatchResult;
import com.spt.learningmanage.service.DataCleanupService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CleanupBatchTransactionServiceTest {
    @Test
    void persistsFencedProgressInTheSameServiceTransaction() {
        DataCleanupService cleanup = mock(DataCleanupService.class);
        AiDataCleanupItemMapper items = mock(AiDataCleanupItemMapper.class);
        CleanupBatchTransactionService service = new CleanupBatchTransactionService(cleanup, items);
        AiDataCleanupRun run = run();
        AiDataCleanupItem item = item();
        when(cleanup.processBatch(any(), any(), eq(4L), eq(500)))
                .thenReturn(new CleanupBatchResult(3, 2, 2, 0, 7, true));
        when(items.updateProgressFenced(eq(2L), eq(1L), eq("token"), eq(7L),
                eq(8L), eq(9L), eq(3L), eq(0L), eq("SUCCEEDED"), any())).thenReturn(1);

        service.process(run, item, CleanupResourceTypeEnum.AI_CALL_BODY, 500);

        assertEquals(7L, item.getCursorId());
        assertEquals(8L, item.getScannedCount());
        assertEquals(9L, item.getEstimatedCount());
        assertEquals(3L, item.getRedactedCount());
        assertEquals("SUCCEEDED", item.getStatus());
    }

    @Test
    void rejectsAndRollsBackWhenTheExecutionTokenLostItsLease() {
        DataCleanupService cleanup = mock(DataCleanupService.class);
        AiDataCleanupItemMapper items = mock(AiDataCleanupItemMapper.class);
        CleanupBatchTransactionService service = new CleanupBatchTransactionService(cleanup, items);
        when(cleanup.processBatch(any(), any(), anyLong(), anyInt()))
                .thenReturn(new CleanupBatchResult(1, 1, 1, 0, 5, false));
        when(items.updateProgressFenced(anyLong(), anyLong(), anyString(), anyLong(),
                anyLong(), anyLong(), anyLong(), anyLong(), anyString(), isNull())).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.process(run(), item(), CleanupResourceTypeEnum.AI_CALL_BODY, 500));
    }

    private AiDataCleanupRun run() {
        AiDataCleanupRun run = new AiDataCleanupRun();
        run.setId(1L);
        run.setExecutionToken("token");
        return run;
    }

    private AiDataCleanupItem item() {
        AiDataCleanupItem item = new AiDataCleanupItem();
        item.setId(2L);
        item.setCutoffTime(LocalDateTime.now().minusDays(30));
        item.setCursorId(4L);
        item.setScannedCount(5L);
        item.setEstimatedCount(9L);
        item.setRedactedCount(1L);
        item.setDeletedCount(0L);
        item.setStatus("RUNNING");
        return item;
    }
}
