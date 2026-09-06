package com.spt.learningmanage.exception;

import lombok.Getter;

@Getter
public class RagDependencyException extends RuntimeException {
    private final ErrorCode errorCode;
    private final boolean retryable;
    private final String safeMessage;

    public RagDependencyException(ErrorCode errorCode,
                                  boolean retryable,
                                  String safeMessage,
                                  String internalMessage,
                                  Throwable cause) {
        super(internalMessage, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.safeMessage = safeMessage;
    }
}
