package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIndexEventPublisherImplTest {

    @Test
    void buildsPendingSanitizedMetadataAndDeduplicatesBatchIds() {
        AiKnowledgeIndexEventMapper mapper = mock(AiKnowledgeIndexEventMapper.class);
        when(mapper.insert(any(AiKnowledgeIndexEvent.class))).thenAnswer(invocation -> {
            AiKnowledgeIndexEvent event = invocation.getArgument(0);
            assertEquals("TASK", event.getSourceType());
            assertEquals("SOURCE_CHANGED", event.getEventType());
            assertEquals("PENDING", event.getStatus());
            assertEquals(0, event.getAttemptCount());
            return 1;
        });
        KnowledgeIndexEventPublisherImpl publisher = new KnowledgeIndexEventPublisherImpl(mapper);

        publisher.publishAll(KnowledgeSourceTypeEnum.TASK, List.of(1L, 1L, 2L),
                KnowledgeEventTypeEnum.SOURCE_CHANGED);

        verify(mapper, times(2)).insert(any(AiKnowledgeIndexEvent.class));
    }

    @Test
    void rejectsInvalidSource() {
        KnowledgeIndexEventPublisherImpl publisher = new KnowledgeIndexEventPublisherImpl(
                mock(AiKnowledgeIndexEventMapper.class));
        assertThrows(IllegalArgumentException.class, () -> publisher.publish(
                KnowledgeSourceTypeEnum.TASK, 0L, KnowledgeEventTypeEnum.SOURCE_CHANGED));
    }
}
