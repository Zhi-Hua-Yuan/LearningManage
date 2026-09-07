package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.config.DataCleanupProperties;
import com.spt.learningmanage.constant.CleanupResourceTypeEnum;
import com.spt.learningmanage.service.DataRetentionPolicy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DefaultDataRetentionPolicy implements DataRetentionPolicy {
    private final DataCleanupProperties properties;

    public DefaultDataRetentionPolicy(DataCleanupProperties properties) {
        this.properties = properties;
    }

    @Override
    public String version() {
        return properties.getPolicyVersion();
    }

    @Override
    public LocalDateTime cutoff(CleanupResourceTypeEnum type, LocalDateTime now) {
        int days = switch (type) {
            case AI_CALL_BODY, RAG_RESULT_BODY, DRAFT_PAYLOAD, DELETED_REPORT ->
                    properties.getBodyRetentionDays();
            case KNOWLEDGE_EVENT -> properties.getSuccessfulEventRetentionDays();
            case ADMIN_AUDIT -> properties.getAdminAuditRetentionDays();
            case AI_CALL_METADATA, RAG_HISTORY, AGENT_HISTORY -> properties.getMetadataRetentionDays();
        };
        return now.minusDays(days);
    }
}
