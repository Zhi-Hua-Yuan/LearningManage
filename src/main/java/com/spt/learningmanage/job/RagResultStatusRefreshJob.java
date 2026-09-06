package com.spt.learningmanage.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.constant.RagResultStatusEnum;
import com.spt.learningmanage.mapper.AiRagResultMapper;
import com.spt.learningmanage.model.entity.AiRagResult;
import com.spt.learningmanage.service.rag.RagResultViewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class RagResultStatusRefreshJob {
    private static final Logger log = LoggerFactory.getLogger(RagResultStatusRefreshJob.class);
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final RagProperties properties;
    private final AiRagResultMapper resultMapper;
    private final RagResultViewService viewService;

    public RagResultStatusRefreshJob(RagProperties properties,
                                     AiRagResultMapper resultMapper,
                                     RagResultViewService viewService) {
        this.properties = properties;
        this.resultMapper = resultMapper;
        this.viewService = viewService;
    }

    @Scheduled(fixedDelayString = "${ai.rag.status-refresh-ms:600000}")
    public void refresh() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        resultMapper.update(null, new UpdateWrapper<AiRagResult>()
                .in("status",
                        RagResultStatusEnum.ACTIVE.name(), RagResultStatusEnum.STALE.name())
                .le("expires_at", now)
                .set("status", RagResultStatusEnum.EXPIRED.name())
                .set("answer_text", "")
                .set("answer_hash", EMPTY_SHA256));

        List<AiRagResult> candidates = resultMapper.selectList(new QueryWrapper<AiRagResult>()
                .in("status",
                        RagResultStatusEnum.ACTIVE.name(), RagResultStatusEnum.STALE.name())
                .gt("expires_at", now)
                .orderByAsc("update_time")
                .last("limit " + properties.getStatusRefreshBatchSize()));
        for (AiRagResult candidate : candidates) {
            try {
                viewService.get(candidate.getUserId(), candidate.getRequestId());
            } catch (RuntimeException expectedStatusChange) {
                log.debug("RAG result status refreshed: requestId={}, reason={}",
                        candidate.getRequestId(), expectedStatusChange.getClass().getSimpleName());
            }
        }
    }
}
