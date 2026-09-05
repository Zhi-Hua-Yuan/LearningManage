package com.spt.learningmanage.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.constant.KnowledgeEventStatusEnum;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeEventQueueService {

    private final AiKnowledgeIndexEventMapper eventMapper;
    private final KnowledgeIndexProperties properties;

    public KnowledgeEventQueueService(AiKnowledgeIndexEventMapper eventMapper,
                                      KnowledgeIndexProperties properties) {
        this.eventMapper = eventMapper;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<AiKnowledgeIndexEvent> claimReady(String workerId, int requestedLimit) {
        LocalDateTime now = LocalDateTime.now();
        int limit = Math.max(1, Math.min(requestedLimit, properties.getClaimBatchSize()));
        List<AiKnowledgeIndexEvent> events = eventMapper.selectReadyForUpdate(now, limit);
        for (AiKnowledgeIndexEvent event : events) {
            String token = UUID.randomUUID().toString();
            event.setStatus(KnowledgeEventStatusEnum.PROCESSING.name());
            event.setClaimedBy(workerId);
            event.setClaimToken(token);
            event.setClaimedAt(now);
            event.setLeaseUntil(now.plusSeconds(properties.getLeaseSeconds()));
            event.setNextAttemptAt(null);
            if (eventMapper.updateById(event) != 1) {
                throw new IllegalStateException("Unable to claim knowledge event " + event.getId());
            }
        }
        return events;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markSuccess(Long eventId, String token) {
        return eventMapper.update(null, stateUpdate(eventId, token)
                .set(AiKnowledgeIndexEvent::getStatus, KnowledgeEventStatusEnum.SUCCESS.name())
                .set(AiKnowledgeIndexEvent::getLeaseUntil, null)
                .set(AiKnowledgeIndexEvent::getNextAttemptAt, null)
                .set(AiKnowledgeIndexEvent::getFailureType, null)
                .set(AiKnowledgeIndexEvent::getLastError, null)) == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markDeferred(Long eventId, String token) {
        return eventMapper.update(null, stateUpdate(eventId, token)
                .set(AiKnowledgeIndexEvent::getStatus, KnowledgeEventStatusEnum.RETRY_WAIT.name())
                .set(AiKnowledgeIndexEvent::getLeaseUntil, null)
                .set(AiKnowledgeIndexEvent::getNextAttemptAt, LocalDateTime.now().plusSeconds(2))) == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markFailure(AiKnowledgeIndexEvent event,
                               KnowledgeFailureTypeEnum failureType,
                               boolean retryable,
                               String safeError) {
        int attempts = Math.max(event.getAttemptCount() == null ? 0 : event.getAttemptCount(), 0) + 1;
        boolean dead = !retryable || attempts >= properties.getMaxAttempts();
        LocalDateTime next = dead ? null : LocalDateTime.now().plusSeconds(backoffSeconds(attempts));
        String error = sanitizeError(safeError);
        return eventMapper.update(null, stateUpdate(event.getId(), event.getClaimToken())
                .set(AiKnowledgeIndexEvent::getStatus,
                        dead ? KnowledgeEventStatusEnum.DEAD.name() : KnowledgeEventStatusEnum.RETRY_WAIT.name())
                .set(AiKnowledgeIndexEvent::getAttemptCount, attempts)
                .set(AiKnowledgeIndexEvent::getNextAttemptAt, next)
                .set(AiKnowledgeIndexEvent::getLeaseUntil, null)
                .set(AiKnowledgeIndexEvent::getFailureType, failureType.name())
                .set(AiKnowledgeIndexEvent::getLastError, error)) == 1;
    }

    private LambdaUpdateWrapper<AiKnowledgeIndexEvent> stateUpdate(Long eventId, String token) {
        return new LambdaUpdateWrapper<AiKnowledgeIndexEvent>()
                .eq(AiKnowledgeIndexEvent::getId, eventId)
                .eq(AiKnowledgeIndexEvent::getStatus, KnowledgeEventStatusEnum.PROCESSING.name())
                .eq(AiKnowledgeIndexEvent::getClaimToken, token);
    }

    private long backoffSeconds(int attempts) {
        return switch (attempts) {
            case 1 -> 60;
            case 2 -> 300;
            case 3 -> 900;
            case 4 -> 3600;
            default -> 21600;
        };
    }

    private String sanitizeError(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }
}
