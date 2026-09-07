package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.mapper.AiAdminOperationLogMapper;
import com.spt.learningmanage.model.entity.AiAdminOperationLog;
import com.spt.learningmanage.service.AdminOperationAuditService;
import com.spt.learningmanage.trace.TraceContext;
import org.springframework.stereotype.Service;

@Service
public class AdminOperationAuditServiceImpl implements AdminOperationAuditService {
    private final AiAdminOperationLogMapper mapper;

    public AdminOperationAuditServiceImpl(AiAdminOperationLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void success(Long actorUserId, String operation, String targetType,
                        String targetId, String argumentSummary, String resultSummary) {
        record(actorUserId, operation, targetType, targetId, argumentSummary, resultSummary, "SUCCEEDED");
    }

    @Override
    public void failure(Long actorUserId, String operation, String targetType,
                        String targetId, String argumentSummary, String resultSummary) {
        record(actorUserId, operation, targetType, targetId, argumentSummary, resultSummary, "FAILED");
    }

    private void record(Long actorUserId, String operation, String targetType,
                        String targetId, String argumentSummary, String resultSummary, String status) {
        if (actorUserId == null || actorUserId <= 0) {
            return;
        }
        AiAdminOperationLog log = new AiAdminOperationLog();
        log.setOperatorUserId(actorUserId);
        log.setOperationType(safe(operation, 64));
        log.setTargetType(safe(targetType, 32));
        log.setTargetId(safe(targetId, 64));
        log.setArgumentSummary(safe(argumentSummary, 1000));
        log.setResultSummary(safe(resultSummary, 1000));
        log.setStatus(status);
        log.setTraceId(TraceContext.currentOrCreate());
        mapper.insert(log);
    }

    private String safe(String value, int limit) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.substring(0, Math.min(normalized.length(), limit));
    }
}
