package com.spt.learningmanage.service;

public interface AdminOperationAuditService {
    void success(Long actorUserId, String operation, String targetType,
                 String targetId, String argumentSummary, String resultSummary);

    void failure(Long actorUserId, String operation, String targetType,
                 String targetId, String argumentSummary, String resultSummary);
}
