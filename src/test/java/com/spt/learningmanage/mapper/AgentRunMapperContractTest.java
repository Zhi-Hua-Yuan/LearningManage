package com.spt.learningmanage.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunMapperContractTest {
    @Test
    void lifecycleWritesAreFencedAndClaimsSkipLockedRows() throws Exception {
        Path path = Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/spt/learningmanage/mapper/AiAgentRunMapper.java");
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(source.contains("FOR UPDATE SKIP LOCKED"));
        assertTrue(source.contains("status = 'RUNNING' AND execution_token = #{executionToken}"));
        assertTrue(source.contains("attempt_count < #{maxAttempts}"));
        assertTrue(source.contains("cancel_requested_at"));
    }

    @Test
    void toolFallbackAdvancesPersistedSequenceAndReportListExcludesInactiveTargets() throws Exception {
        String toolLogMapper = Files.readString(Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/spt/learningmanage/mapper/AiAgentToolLogMapper.java"),
                StandardCharsets.UTF_8);
        assertTrue(toolLogMapper.contains("MAX(tool_sequence)"));

        String reportMapper = Files.readString(Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/spt/learningmanage/mapper/AiAnalysisReportMapper.java"),
                StandardCharsets.UTF_8);
        assertTrue(reportMapper.contains("p.deleted_at IS NULL"));
        assertTrue(reportMapper.contains("rt.deleted_at IS NULL"));
        assertTrue(reportMapper.contains("pm.deleted_at IS NULL"));
        assertTrue(reportMapper.contains("tm.deleted_at IS NULL"));
    }
}
