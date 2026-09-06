package com.spt.learningmanage.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.model.rag.RagCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextAssemblerTest {
    @Test
    void promptContainsEvidenceButSafeLogContainsHashesOnly() {
        RagContextAssembler assembler = new RagContextAssembler(new ObjectMapper());
        var context = assembler.assemble("为什么延期", List.of(new RagCandidate(
                "p1", "p1", "TASK:1:PRIVATE:2", KnowledgeSourceTypeEnum.TASK,
                1L, 0, "敏感标题", "敏感正文", "a".repeat(64), "b".repeat(64),
                0.9, 0.8, null)));

        assertTrue(context.userPrompt().contains("为什么延期"));
        assertTrue(context.userPrompt().contains("敏感正文"));
        assertFalse(context.safeLogSummary().contains("为什么延期"));
        assertFalse(context.safeLogSummary().contains("敏感正文"));
        assertFalse(context.safeLogSummary().contains("敏感标题"));
        assertTrue(context.safeLogSummary().contains("a".repeat(64)));
    }
}
