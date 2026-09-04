package com.spt.learningmanage.job;

import com.spt.learningmanage.service.AiService;
import com.spt.learningmanage.service.impl.ai.draft.AiReplanWriteGuard;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiDraftExpireJob {

    @Resource
    private AiService aiService;

    @Resource
    private AiReplanWriteGuard replanWriteGuard;

    @Scheduled(cron = "0 */5 * * * ?")
    public void expirePreviewDrafts() {
        int updated = aiService.expirePreviewDrafts();
        if (updated > 0) {
            log.info("expired ai preview drafts: {}", updated);
        }
        int expiredReplans = replanWriteGuard.expirePreviewOperations();
        if (expiredReplans > 0) {
            log.info("expired ai replan previews: {}", expiredReplans);
        }
    }
}
