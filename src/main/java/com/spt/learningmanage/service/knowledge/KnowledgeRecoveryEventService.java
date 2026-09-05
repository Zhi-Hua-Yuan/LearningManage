package com.spt.learningmanage.service.knowledge;

import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeRecoveryEventService {

    private final KnowledgeIndexEventPublisher publisher;

    public KnowledgeRecoveryEventService(KnowledgeIndexEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void enqueue(KnowledgeSourceRef source) {
        publisher.publish(source.sourceType(), source.sourceId(), KnowledgeEventTypeEnum.SOURCE_CHANGED);
    }
}
