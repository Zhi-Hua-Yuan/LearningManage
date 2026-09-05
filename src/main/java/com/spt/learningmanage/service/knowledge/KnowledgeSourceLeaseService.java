package com.spt.learningmanage.service.knowledge;

import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.mapper.AiKnowledgeSourceLockMapper;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class KnowledgeSourceLeaseService {

    private final AiKnowledgeSourceLockMapper lockMapper;
    private final KnowledgeIndexProperties properties;

    public KnowledgeSourceLeaseService(AiKnowledgeSourceLockMapper lockMapper,
                                       KnowledgeIndexProperties properties) {
        this.lockMapper = lockMapper;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean acquire(KnowledgeSourceRef source, String token) {
        lockMapper.ensureRow(source.sourceType().name(), source.sourceId());
        LocalDateTime now = LocalDateTime.now();
        return lockMapper.acquire(source.sourceType().name(), source.sourceId(), token,
                now, now.plusSeconds(properties.getLeaseSeconds())) == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public void renew(KnowledgeSourceRef source, String token) {
        LocalDateTime now = LocalDateTime.now();
        if (lockMapper.renew(source.sourceType().name(), source.sourceId(), token,
                now, now.plusSeconds(properties.getLeaseSeconds())) != 1) {
            throw lostLease();
        }
    }

    @Transactional(readOnly = true)
    public void requireOwned(KnowledgeSourceRef source, String token) {
        if (lockMapper.isOwned(source.sourceType().name(), source.sourceId(), token,
                LocalDateTime.now()) != 1) {
            throw lostLease();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void release(KnowledgeSourceRef source, String token) {
        lockMapper.release(source.sourceType().name(), source.sourceId(), token);
    }

    private KnowledgeIndexException lostLease() {
        return new KnowledgeIndexException(KnowledgeFailureTypeEnum.STALE_SOURCE, true,
                "索引来源租约已失效", "Knowledge source lease was lost", null);
    }
}
