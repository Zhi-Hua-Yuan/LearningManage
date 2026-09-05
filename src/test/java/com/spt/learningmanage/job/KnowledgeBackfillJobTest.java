package com.spt.learningmanage.job;

import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import com.spt.learningmanage.model.knowledge.BackfillPageResult;
import com.spt.learningmanage.service.knowledge.KnowledgeBackfillPageService;
import com.spt.learningmanage.service.knowledge.KnowledgeBackfillQueueService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBackfillJobTest {

    @Test
    void disabledWorkerNeverTouchesBackfillTable() {
        KnowledgeBackfillQueueService queue = mock(KnowledgeBackfillQueueService.class);
        KnowledgeBackfillPageService pages = mock(KnowledgeBackfillPageService.class);
        new KnowledgeBackfillJob(new KnowledgeIndexProperties(), queue, pages).run();
        verify(queue, never()).claim(any(), anyInt());
    }

    @Test
    void allScopeEnqueuesBothSourcesThenMarksEnqueued() {
        KnowledgeIndexProperties properties = new KnowledgeIndexProperties();
        properties.setWorkerEnabled(true);
        KnowledgeBackfillQueueService queue = mock(KnowledgeBackfillQueueService.class);
        KnowledgeBackfillPageService pages = mock(KnowledgeBackfillPageService.class);
        AiKnowledgeBackfillRun run = new AiKnowledgeBackfillRun();
        run.setId(1L);
        run.setSourceScope("ALL");
        run.setCursorTaskId(0L);
        run.setCursorReviewId(0L);
        when(queue.claim(any(), anyInt())).thenReturn(run);
        when(pages.enqueueTaskPage(run)).thenReturn(new BackfillPageResult(0, Long.MAX_VALUE, true));
        when(pages.enqueueReviewPage(run)).thenReturn(new BackfillPageResult(0, Long.MAX_VALUE, true));

        new KnowledgeBackfillJob(properties, queue, pages).run();

        verify(pages).enqueueTaskPage(run);
        verify(pages).enqueueReviewPage(run);
        verify(queue).markEnqueued(run);
    }
}
