package com.spt.learningmanage.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.mapper.AiKnowledgeBackfillRunMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.knowledge.BackfillPageResult;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBackfillPageServiceTest {

    @Test
    void pageUsesKeysetResultsAndNormalOutboxPublisher() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        WeeklyReviewMapper reviewMapper = mock(WeeklyReviewMapper.class);
        AiKnowledgeBackfillRunMapper runMapper = mock(AiKnowledgeBackfillRunMapper.class);
        KnowledgeIndexEventPublisher publisher = mock(KnowledgeIndexEventPublisher.class);
        Task first = new Task();
        first.setId(101L);
        Task second = new Task();
        second.setId(102L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        when(runMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        AiKnowledgeBackfillRun run = run();

        BackfillPageResult result = new KnowledgeBackfillPageService(taskMapper, reviewMapper,
                runMapper, publisher, new KnowledgeIndexProperties()).enqueueTaskPage(run);

        assertTrue(result.done());
        verify(publisher).publish(KnowledgeSourceTypeEnum.TASK, 101L,
                KnowledgeEventTypeEnum.REBUILD, 7L);
        verify(publisher).publish(KnowledgeSourceTypeEnum.TASK, 102L,
                KnowledgeEventTypeEnum.REBUILD, 7L);
    }

    private AiKnowledgeBackfillRun run() {
        AiKnowledgeBackfillRun run = new AiKnowledgeBackfillRun();
        run.setId(7L);
        run.setBatchSize(100);
        run.setCursorTaskId(0L);
        run.setCursorReviewId(0L);
        run.setDiscoveredCount(0L);
        run.setEnqueuedCount(0L);
        run.setClaimToken("token");
        return run;
    }
}
