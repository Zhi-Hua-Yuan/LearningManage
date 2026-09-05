package com.spt.learningmanage.service.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeTextPipelineTest {

    @Test
    void normalizerMakesLineEndingsWhitespaceAndUnicodeStable() {
        KnowledgeTextNormalizer normalizer = new KnowledgeTextNormalizer();
        String composed = "Cafe\u0301  \r\nnext\t value";
        assertEquals("Café\nnext value", normalizer.normalize(composed));
    }

    @Test
    void chunkerRepeatsPrefixAndKeepsBounds() {
        KnowledgeChunker chunker = new KnowledgeChunker();
        String body = "内容。".repeat(700);
        var chunks = chunker.chunk("任务标题: 固定标题", body);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.text().startsWith("任务标题: 固定标题\n")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.text().length() <= KnowledgeChunker.MAX_CHARS));
        for (int index = 0; index < chunks.size(); index++) {
            assertEquals(index, chunks.get(index).index());
        }
    }

    @Test
    void payloadHashIgnoresMapInsertionOrderAndPointIdIsStable() {
        KnowledgeHashing hashing = new KnowledgeHashing(new ObjectMapper());
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);

        assertEquals(hashing.payloadHash(first), hashing.payloadHash(second));
        assertNotEquals(hashing.contentHash("one"), hashing.contentHash("two"));

        DeterministicPointIdFactory ids = new DeterministicPointIdFactory();
        assertEquals(ids.pointId("TASK:1:PRIVATE:2", 0), ids.pointId("TASK:1:PRIVATE:2", 0));
        assertNotEquals(ids.pointId("TASK:1:PRIVATE:2", 0), ids.pointId("TASK:1:PRIVATE:2", 1));
    }
}
