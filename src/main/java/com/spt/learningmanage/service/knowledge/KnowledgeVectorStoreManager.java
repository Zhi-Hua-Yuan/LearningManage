package com.spt.learningmanage.service.knowledge;

import com.spt.learningmanage.service.VectorStoreClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class KnowledgeVectorStoreManager {

    private final VectorStoreClient vectorStoreClient;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public KnowledgeVectorStoreManager(VectorStoreClient vectorStoreClient) {
        this.vectorStoreClient = vectorStoreClient;
    }

    public void ensureReady() {
        if (ready.get()) {
            return;
        }
        synchronized (this) {
            if (!ready.get()) {
                vectorStoreClient.ensureCollection();
                ready.set(true);
            }
        }
    }

    public void invalidate() {
        ready.set(false);
    }

    boolean isReady() {
        return ready.get();
    }
}
