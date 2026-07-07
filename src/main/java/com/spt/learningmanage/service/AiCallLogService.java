package com.spt.learningmanage.service;

public interface AiCallLogService {

    Long createRunningLog(Long userId,
                          String scene,
                          String modelName,
                          String promptType,
                          String requestText,
                          Integer retryCount);

    void markSuccess(Long logId, String responseText, Long costTimeMs);

    void markFailed(Long logId, String errorMessage, Long costTimeMs);

    void markParseFailed(Long logId, String responseText, String errorMessage, Long costTimeMs);

    void markTimeout(Long logId, String errorMessage, Long costTimeMs);
}
