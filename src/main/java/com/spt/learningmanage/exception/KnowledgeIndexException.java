package com.spt.learningmanage.exception;

import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import lombok.Getter;

@Getter
public class KnowledgeIndexException extends RuntimeException {

    private final KnowledgeFailureTypeEnum failureType;
    private final boolean retryable;
    private final String safeMessage;

    public KnowledgeIndexException(KnowledgeFailureTypeEnum failureType,
                                   boolean retryable,
                                   String safeMessage,
                                   String internalMessage,
                                   Throwable cause) {
        super(internalMessage, cause);
        this.failureType = failureType;
        this.retryable = retryable;
        this.safeMessage = safeMessage;
    }
}
