package com.spt.learningmanage.job;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.mapper.AiKnowledgeBackfillRunMapper;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import com.spt.learningmanage.service.knowledge.KnowledgeBackfillQueueService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBackfillCompletionJobTest {

    @Test
    void marksRunTerminalOnlyWhenAllChildEventsAreTerminal() {
        KnowledgeIndexProperties properties = new KnowledgeIndexProperties();
        properties.setWorkerEnabled(true);
        AiKnowledgeBackfillRunMapper runs = mock(AiKnowledgeBackfillRunMapper.class);
        AiKnowledgeIndexEventMapper events = mock(AiKnowledgeIndexEventMapper.class);
        KnowledgeBackfillQueueService queue = mock(KnowledgeBackfillQueueService.class);
        AiKnowledgeBackfillRun run = new AiKnowledgeBackfillRun();
        run.setId(7L);
        run.setEnqueuedCount(2L);
        when(runs.selectList(any(Wrapper.class))).thenReturn(List.of(run));
        when(events.selectCount(any(Wrapper.class))).thenReturn(2L, 0L, 0L);

        new KnowledgeBackfillCompletionJob(properties, runs, events, queue).monitor();

        verify(queue).updateCompletion(run, 2L, 0L, 0L, true);
    }
}
