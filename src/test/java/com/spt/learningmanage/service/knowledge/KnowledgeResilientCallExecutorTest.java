package com.spt.learningmanage.service.knowledge;

import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeResilientCallExecutorTest {

    @Test
    void dependenciesUseIndependentBulkheadsAndCircuits() {
        KnowledgeResilientCallExecutor executor = new KnowledgeResilientCallExecutor(
                new KnowledgeIndexProperties());
        assertNotSame(executor.bulkhead(KnowledgeDependencyType.EMBEDDING),
                executor.bulkhead(KnowledgeDependencyType.VECTOR_STORE));
        assertNotSame(executor.circuit(KnowledgeDependencyType.EMBEDDING),
                executor.circuit(KnowledgeDependencyType.VECTOR_STORE));
    }

    @Test
    void retryableFailuresAreRecordedByOnlyTheSelectedCircuit() {
        KnowledgeIndexProperties properties = new KnowledgeIndexProperties();
        properties.setCircuitMinimumCalls(2);
        properties.setCircuitSlidingWindowSize(2);
        KnowledgeResilientCallExecutor executor = new KnowledgeResilientCallExecutor(properties);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThrows(KnowledgeIndexException.class, () -> executor.execute(
                    KnowledgeDependencyType.EMBEDDING,
                    () -> {
                        throw new KnowledgeIndexException(KnowledgeFailureTypeEnum.NETWORK,
                                true, "network", "network", null);
                    }));
        }
        assertEquals("OPEN", executor.circuit(KnowledgeDependencyType.EMBEDDING).getState().name());
        assertEquals("CLOSED", executor.circuit(KnowledgeDependencyType.VECTOR_STORE).getState().name());
    }
}
