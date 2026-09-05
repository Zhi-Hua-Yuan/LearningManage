package com.spt.learningmanage.service.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeIndexScaleContractTest {

    @Test
    void oneHundredThousandSourcesProduceUniqueStablePointIdsAndHashes() {
        DeterministicPointIdFactory pointIds = new DeterministicPointIdFactory();
        KnowledgeHashing hashing = new KnowledgeHashing(new ObjectMapper());
        Set<String> uniquePointIds = new HashSet<>(140_000);
        Set<String> uniqueHashes = new HashSet<>(140_000);

        for (int index = 1; index <= 100_000; index++) {
            String documentKey = "TASK:" + index + ":PRIVATE:1";
            uniquePointIds.add(pointIds.pointId(documentKey, 0));
            uniqueHashes.add(hashing.contentHash("任务标题: scale-" + index));
        }

        assertEquals(100_000, uniquePointIds.size());
        assertEquals(100_000, uniqueHashes.size());
    }
}
