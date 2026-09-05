package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.KnowledgeEventStatusEnum;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.trace.TraceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;

@Service
public class KnowledgeIndexEventPublisherImpl implements KnowledgeIndexEventPublisher {

    private final AiKnowledgeIndexEventMapper eventMapper;

    public KnowledgeIndexEventPublisherImpl(AiKnowledgeIndexEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(KnowledgeSourceTypeEnum sourceType,
                        Long sourceId,
                        KnowledgeEventTypeEnum eventType) {
        publish(sourceType, sourceId, eventType, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(KnowledgeSourceTypeEnum sourceType,
                        Long sourceId,
                        KnowledgeEventTypeEnum eventType,
                        Long backfillRunId) {
        if (sourceType == null || eventType == null || sourceId == null || sourceId <= 0) {
            throw new IllegalArgumentException("Knowledge index event source is invalid");
        }
        AiKnowledgeIndexEvent event = new AiKnowledgeIndexEvent();
        event.setSourceType(sourceType.name());
        event.setSourceId(sourceId);
        event.setEventType(eventType.name());
        event.setStatus(KnowledgeEventStatusEnum.PENDING.name());
        event.setAttemptCount(0);
        event.setBackfillRunId(backfillRunId);
        event.setTraceId(TraceContext.currentOrCreate());
        if (eventMapper.insert(event) != 1) {
            throw new IllegalStateException("Unable to persist knowledge index event");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAll(KnowledgeSourceTypeEnum sourceType,
                           Collection<Long> sourceIds,
                           KnowledgeEventTypeEnum eventType) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return;
        }
        for (Long sourceId : new LinkedHashSet<>(sourceIds)) {
            publish(sourceType, sourceId, eventType, null);
        }
    }
}
