package com.spt.learningmanage.job;

import com.spt.learningmanage.service.AiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiDraftExpireJob {

    @Resource
    private AiService aiService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void expirePreviewDrafts() {
        int updated = aiService.expirePreviewDrafts();
        if (updated > 0) {
            log.info("expired ai preview drafts: {}", updated);
        }
    }
}
