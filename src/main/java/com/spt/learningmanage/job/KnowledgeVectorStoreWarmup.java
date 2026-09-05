package com.spt.learningmanage.job;

import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.service.knowledge.KnowledgeVectorStoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeVectorStoreWarmup {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeVectorStoreWarmup.class);

    private final KnowledgeIndexProperties properties;
    private final KnowledgeVectorStoreManager manager;

    public KnowledgeVectorStoreWarmup(KnowledgeIndexProperties properties,
                                      KnowledgeVectorStoreManager manager) {
        this.properties = properties;
        this.manager = manager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        if (!properties.isWorkerEnabled()) {
            return;
        }
        try {
            manager.ensureReady();
        } catch (RuntimeException exception) {
            log.warn("knowledge vector store warmup failed; core application remains available: type={}",
                    exception.getClass().getSimpleName());
        }
    }
}
