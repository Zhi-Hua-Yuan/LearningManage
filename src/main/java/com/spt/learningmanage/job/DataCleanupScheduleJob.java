package com.spt.learningmanage.job;

import com.spt.learningmanage.service.CleanupRunService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataCleanupScheduleJob {
    private final CleanupRunService service;

    public DataCleanupScheduleJob(CleanupRunService service) {
        this.service = service;
    }

    @Scheduled(cron = "${ai.cleanup.cron:0 30 2 * * *}", zone = "Asia/Shanghai")
    public void schedule() {
        service.submitScheduled();
    }
}
