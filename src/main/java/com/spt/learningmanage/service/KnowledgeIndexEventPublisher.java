package com.spt.learningmanage.service;

import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;

import java.util.Collection;

public interface KnowledgeIndexEventPublisher {

    void publish(KnowledgeSourceTypeEnum sourceType,
                 Long sourceId,
                 KnowledgeEventTypeEnum eventType);

    void publish(KnowledgeSourceTypeEnum sourceType,
                 Long sourceId,
                 KnowledgeEventTypeEnum eventType,
                 Long backfillRunId);

    void publishAll(KnowledgeSourceTypeEnum sourceType,
                    Collection<Long> sourceIds,
                    KnowledgeEventTypeEnum eventType);
}
