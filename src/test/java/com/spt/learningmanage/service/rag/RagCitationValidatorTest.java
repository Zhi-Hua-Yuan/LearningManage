package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.model.rag.RagAnswerContent;
import com.spt.learningmanage.model.rag.RagCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagCitationValidatorTest {
    private final RagCitationValidator validator = new RagCitationValidator();

    @Test
    void acceptsOnlyMarkersDeclaredByTheModelAndKnownToTheServer() {
        RagAnswerContent result = validator.validate(
                new RagAnswerContent("结论一 [S1]，结论二 [S2]。", false, List.of("S1", "S2")),
                Map.of("S1", candidate(1L), "S2", candidate(2L)));
        assertEquals(List.of("S1", "S2"), result.citations());
    }

    @Test
    void rejectsForgedMissingAndUndeclaredCitations() {
        Map<String, RagCandidate> evidence = Map.of("S1", candidate(1L));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new RagAnswerContent("伪造 [S9]", false, List.of("S9")), evidence));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new RagAnswerContent("没有标记", false, List.of("S1")), evidence));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new RagAnswerContent("包含 [S1]", false, List.of()), evidence));
    }

    @Test
    void insufficientAnswerMayHaveNoCitation() {
        RagAnswerContent result = validator.validate(
                new RagAnswerContent("依据不足。", true, List.of()), Map.of());
        assertEquals(List.of(), result.citations());
    }

    private RagCandidate candidate(Long id) {
        return new RagCandidate("p" + id, "p" + id, "TASK:" + id,
                KnowledgeSourceTypeEnum.TASK, id, 0, "title", "body",
                "a".repeat(64), "b".repeat(64), 0.8, null, null);
    }
}
