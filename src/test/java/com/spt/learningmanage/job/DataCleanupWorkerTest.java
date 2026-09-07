package com.spt.learningmanage.job;

import com.spt.learningmanage.config.DataCleanupProperties;
import com.spt.learningmanage.mapper.AiDataCleanupItemMapper;
import com.spt.learningmanage.mapper.AiDataCleanupRunMapper;
import com.spt.learningmanage.model.entity.AiDataCleanupItem;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.model.ops.CleanupBatchResult;
import com.spt.learningmanage.observability.AiMetricsRecorder;
import com.spt.learningmanage.service.CleanupRunQueueService;
import com.spt.learningmanage.service.DataCleanupService;
import com.spt.learningmanage.service.CleanupRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataCleanupWorkerTest {
    @Mock AiDataCleanupRunMapper runMapper;
    @Mock AiDataCleanupItemMapper itemMapper;
    @Mock DataCleanupService cleanupService;
    @Mock CleanupRunQueueService queueService;
    @Mock AiMetricsRecorder metrics;
    @Mock CleanupRunService runService;

    private DataCleanupWorker worker;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AiDataCleanupItem.class);
    }

    @BeforeEach
    void setUp() {
        worker = new DataCleanupWorker(runMapper, itemMapper, cleanupService, queueService,
                new DataCleanupProperties(), metrics, runService);
    }

    @Test
    void dryRunCountsWithoutMutatingProtectedData() {
        AiDataCleanupRun run = run(true);
        AiDataCleanupItem item = item();
        when(runMapper.selectById(1L)).thenReturn(run);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(cleanupService.estimate(any(), any())).thenReturn(7L);
        when(itemMapper.updateById(any(AiDataCleanupItem.class))).thenReturn(1);
        when(queueService.heartbeat(run)).thenReturn(true);
        doReturn(true).when(queueService).complete(any(AiDataCleanupRun.class), anyString(),
                anyLong(), anyLong(), anyLong(), anyLong(), nullable(String.class));

        worker.process(run);

        verify(cleanupService, never()).processBatch(any(), any(), anyLong(), anyInt());
        verify(queueService).complete(run, "SUCCEEDED", 7L, 7L, 0L, 0L, null);
        verify(metrics).recordCleanup(eq("SUCCEEDED"), anyLong(), eq(0L));
    }

    @Test
    void formalRunWithoutApprovedPreviewFailsBeforeMutation() {
        AiDataCleanupRun run = run(false);
        when(itemMapper.selectList(any())).thenReturn(List.of(item()));
        doReturn(true).when(queueService).complete(any(AiDataCleanupRun.class), anyString(),
                anyLong(), anyLong(), anyLong(), anyLong(), nullable(String.class));

        worker.process(run);

        verify(cleanupService, never()).processBatch(any(), any(), anyLong(), anyInt());
        verify(metrics).recordCleanup(eq("FAILED"), anyLong(), eq(0L));
    }

    @Test
    void resumedFormalRunKeepsPersistedCountsAndCursor() {
        AiDataCleanupRun run = run(false);
        AiDataCleanupItem item = item();
        item.setCursorId(10L);
        item.setScannedCount(2L);
        item.setEstimatedCount(5L);
        item.setRedactedCount(2L);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(runMapper.selectById(1L)).thenReturn(run);
        when(itemMapper.updateById(any(AiDataCleanupItem.class))).thenReturn(1);
        when(cleanupService.processBatch(any(), any(), eq(10L), anyInt()))
                .thenReturn(new CleanupBatchResult(1, 1, 1, 0, 11, true));
        when(queueService.heartbeat(run)).thenReturn(true);
        doReturn(true).when(queueService).complete(any(AiDataCleanupRun.class), anyString(),
                anyLong(), anyLong(), anyLong(), anyLong(), nullable(String.class));

        worker.process(run);

        verify(queueService).complete(run, "SUCCEEDED", 3L, 5L, 3L, 0L, null);
    }

    @Test
    void lostLeaseStopsBeforeTheNextDestructiveBatch() {
        AiDataCleanupRun run = run(false);
        AiDataCleanupItem item = item();
        item.setCursorId(10L);
        item.setScannedCount(1L);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(queueService.heartbeat(run)).thenReturn(false);

        worker.process(run);

        verify(cleanupService, never()).processBatch(any(), any(), anyLong(), anyInt());
        verify(queueService, never()).complete(any(), anyString(), anyLong(), anyLong(),
                anyLong(), anyLong(), nullable(String.class));
    }

    private AiDataCleanupRun run(boolean dryRun) {
        AiDataCleanupRun run = new AiDataCleanupRun();
        run.setId(1L);
        run.setRunId("cleanup_test");
        run.setDryRun(dryRun ? 1 : 0);
        run.setExecutionToken("token");
        run.setPolicyVersion("stage7-v1");
        run.setResourceHash("a".repeat(64));
        return run;
    }

    private AiDataCleanupItem item() {
        AiDataCleanupItem item = new AiDataCleanupItem();
        item.setId(2L);
        item.setRunId("cleanup_test");
        item.setResourceType("AI_CALL_BODY");
        item.setCutoffTime(LocalDateTime.now().minusDays(30));
        item.setStatus("PENDING");
        item.setCursorId(0L);
        item.setScannedCount(0L);
        item.setEstimatedCount(0L);
        item.setRedactedCount(0L);
        item.setDeletedCount(0L);
        return item;
    }
}
