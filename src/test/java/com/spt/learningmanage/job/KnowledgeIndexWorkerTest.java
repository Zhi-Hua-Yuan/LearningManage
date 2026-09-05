package com.spt.learningmanage.job;

import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.model.knowledge.IndexExecutionContext;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.service.KnowledgeIndexService;
import com.spt.learningmanage.service.knowledge.KnowledgeEventQueueService;
import com.spt.learningmanage.service.knowledge.KnowledgeSourceLeaseService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIndexWorkerTest {

    @Test
    void successfulRunOwnsSourceAndCompletesEvent() {
        KnowledgeIndexService indexService = mock(KnowledgeIndexService.class);
        KnowledgeEventQueueService queue = mock(KnowledgeEventQueueService.class);
        KnowledgeSourceLeaseService lease = mock(KnowledgeSourceLeaseService.class);
        when(lease.acquire(any(), any())).thenReturn(true);
        when(queue.markSuccess(1L, "token")).thenReturn(true);
        AiKnowledgeIndexEvent event = event();

        new KnowledgeIndexWorker(indexService, queue, lease).process(event);

        verify(indexService).reconcileSource(any(KnowledgeSourceRef.class), any(IndexExecutionContext.class));
        verify(queue).markSuccess(1L, "token");
        verify(lease).release(any(), any());
    }

    @Test
    void busySourceDefersWithoutRunningIndexer() {
        KnowledgeIndexService indexService = mock(KnowledgeIndexService.class);
        KnowledgeEventQueueService queue = mock(KnowledgeEventQueueService.class);
        KnowledgeSourceLeaseService lease = mock(KnowledgeSourceLeaseService.class);
        when(lease.acquire(any(), any())).thenReturn(false);

        new KnowledgeIndexWorker(indexService, queue, lease).process(event());

        verify(queue).markDeferred(1L, "token");
        verify(indexService, never()).reconcileSource(any(), any());
    }

    @Test
    void typedFailurePreservesRetryabilityAndAlwaysReleasesLease() {
        KnowledgeIndexService indexService = mock(KnowledgeIndexService.class);
        KnowledgeEventQueueService queue = mock(KnowledgeEventQueueService.class);
        KnowledgeSourceLeaseService lease = mock(KnowledgeSourceLeaseService.class);
        when(lease.acquire(any(), any())).thenReturn(true);
        doThrow(new KnowledgeIndexException(KnowledgeFailureTypeEnum.RATE_LIMIT, true,
                "请求过多", "rate limited", null)).when(indexService).reconcileSource(any(), any());
        AiKnowledgeIndexEvent event = event();

        new KnowledgeIndexWorker(indexService, queue, lease).process(event);

        verify(queue).markFailure(event, KnowledgeFailureTypeEnum.RATE_LIMIT, true, "请求过多");
        verify(lease).release(any(), any());
    }

    private AiKnowledgeIndexEvent event() {
        AiKnowledgeIndexEvent event = new AiKnowledgeIndexEvent();
        event.setId(1L);
        event.setSourceType("TASK");
        event.setSourceId(2L);
        event.setClaimToken("token");
        event.setTraceId("trace-id");
        return event;
    }
}
