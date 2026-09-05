package com.spt.learningmanage.model.knowledge;

import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;

public record IndexExecutionContext(Long eventId,
                                    String claimToken,
                                    String traceId,
                                    KnowledgeEventTypeEnum eventType) {
}
