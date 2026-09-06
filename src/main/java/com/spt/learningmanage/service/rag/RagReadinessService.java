package com.spt.learningmanage.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.constant.KnowledgeBackfillStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiKnowledgeBackfillRunMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import com.spt.learningmanage.service.knowledge.KnowledgeVectorStoreManager;
import org.springframework.stereotype.Service;

@Service
public class RagReadinessService {
    private final RagProperties properties;
    private final AiKnowledgeBackfillRunMapper backfillRunMapper;
    private final KnowledgeVectorStoreManager vectorStoreManager;

    public RagReadinessService(RagProperties properties,
                               AiKnowledgeBackfillRunMapper backfillRunMapper,
                               KnowledgeVectorStoreManager vectorStoreManager) {
        this.properties = properties;
        this.backfillRunMapper = backfillRunMapper;
        this.vectorStoreManager = vectorStoreManager;
    }

    public void requireReady() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.RAG_DISABLED);
        }
        if (properties.isRequireCompletedBackfill()) {
            Long count = backfillRunMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeBackfillRun>()
                    .eq(AiKnowledgeBackfillRun::getSourceScope, "ALL")
                    .eq(AiKnowledgeBackfillRun::getStatus, KnowledgeBackfillStatusEnum.SUCCEEDED.name()));
            if (count == null || count == 0) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_INDEX_NOT_READY);
            }
        }
        try {
            vectorStoreManager.ensureReady();
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.RAG_DEPENDENCY_UNAVAILABLE,
                    "向量检索服务尚未就绪");
        }
    }
}
