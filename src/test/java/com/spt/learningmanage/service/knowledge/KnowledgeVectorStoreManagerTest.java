package com.spt.learningmanage.service.knowledge;

import com.spt.learningmanage.service.VectorStoreClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class KnowledgeVectorStoreManagerTest {

    @Test
    void cachesSuccessfulInitializationAndCanBeInvalidated() {
        VectorStoreClient client = mock(VectorStoreClient.class);
        KnowledgeVectorStoreManager manager = new KnowledgeVectorStoreManager(client);

        manager.ensureReady();
        manager.ensureReady();
        assertTrue(manager.isReady());
        verify(client, times(1)).ensureCollection();

        manager.invalidate();
        manager.ensureReady();
        verify(client, times(2)).ensureCollection();
    }
}
