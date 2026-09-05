package com.spt.learningmanage.job;

import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.model.knowledge.IndexExecutionContext;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.service.KnowledgeIndexService;
import com.spt.learningmanage.service.knowledge.KnowledgeEventQueueService;
import com.spt.learningmanage.service.knowledge.KnowledgeSourceLeaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIndexWorker {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexWorker.class);

    private final KnowledgeIndexService indexService;
    private final KnowledgeEventQueueService queueService;
    private final KnowledgeSourceLeaseService leaseService;

    public KnowledgeIndexWorker(KnowledgeIndexService indexService,
                                KnowledgeEventQueueService queueService,
                                KnowledgeSourceLeaseService leaseService) {
        this.indexService = indexService;
        this.queueService = queueService;
        this.leaseService = leaseService;
    }

    public void process(AiKnowledgeIndexEvent event) {
        KnowledgeSourceRef source;
        KnowledgeEventTypeEnum eventType;
        try {
            source = new KnowledgeSourceRef(
                    KnowledgeSourceTypeEnum.valueOf(event.getSourceType()), event.getSourceId());
            eventType = KnowledgeEventTypeEnum.valueOf(event.getEventType());
        } catch (RuntimeException exception) {
            queueService.markFailure(event, KnowledgeFailureTypeEnum.CONFIG, false,
                    "索引事件来源类型不合法");
            return;
        }
        String token = event.getClaimToken();
        if (!leaseService.acquire(source, token)) {
            queueService.markDeferred(event.getId(), token);
            return;
        }
        try {
            indexService.reconcileSource(source,
                    new IndexExecutionContext(event.getId(), token, event.getTraceId(), eventType));
            if (!queueService.markSuccess(event.getId(), token)) {
                log.warn("knowledge event success lost fencing race: eventId={}", event.getId());
            }
        } catch (KnowledgeIndexException exception) {
            markDocumentFailureSafely(source, event, exception.getFailureType(), exception.getSafeMessage());
            queueService.markFailure(event, exception.getFailureType(), exception.isRetryable(),
                    exception.getSafeMessage());
        } catch (RuntimeException exception) {
            log.warn("knowledge event failed: eventId={}, type={}",
                    event.getId(), exception.getClass().getSimpleName());
            markDocumentFailureSafely(source, event, KnowledgeFailureTypeEnum.INTERNAL,
                    "知识索引内部处理失败");
            queueService.markFailure(event, KnowledgeFailureTypeEnum.INTERNAL, true,
                    "知识索引内部处理失败");
        } finally {
            leaseService.release(source, token);
        }
    }

    private void markDocumentFailureSafely(KnowledgeSourceRef source,
                                           AiKnowledgeIndexEvent event,
                                           KnowledgeFailureTypeEnum failureType,
                                           String safeError) {
        try {
            indexService.markFailure(source,
                    new IndexExecutionContext(event.getId(), event.getClaimToken(), event.getTraceId(),
                            KnowledgeEventTypeEnum.valueOf(event.getEventType())),
                    failureType, safeError);
        } catch (RuntimeException exception) {
            log.warn("knowledge document failure state update failed: eventId={}", event.getId());
        }
    }
}
