package com.spt.learningmanage.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiOpsQuerySqlContractTest {
    @Test
    void summariesAndFailuresStayDatabaseBounded() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/spt/learningmanage/service/impl/AiOpsQueryServiceImpl.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("GROUP BY "));
        assertTrue(source.contains("LIMIT 1 OFFSET ?"));
        assertTrue(source.contains("UNION ALL"));
        assertTrue(source.contains("LIMIT ? OFFSET ?"));
        assertTrue(source.contains("status IN (2, 3, 4)"));
        assertFalse(source.contains("limit 500"));
        assertFalse(source.contains("selectList("));
    }
}
