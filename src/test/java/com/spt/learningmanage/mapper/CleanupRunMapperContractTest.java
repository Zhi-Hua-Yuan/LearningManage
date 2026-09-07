package com.spt.learningmanage.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanupRunMapperContractTest {
    @Test
    void submissionClaimHeartbeatAndTerminalWritesAreFenced() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/spt/learningmanage/mapper/AiDataCleanupRunMapper.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("ai_data_cleanup_lock WHERE id=1 FOR UPDATE"));
        assertTrue(source.contains("selectActiveForUpdate"));
        assertTrue(source.contains("selectByRequestForUpdate"));
        assertTrue(source.contains("selectLatestDryRunForUpdate"));
        assertTrue(source.contains("FOR UPDATE SKIP LOCKED"));
        assertTrue(source.contains("status='RUNNING' AND execution_token=#{token}"));
        assertTrue(source.contains("status='PENDING', worker_id=NULL, execution_token=NULL"));
        assertTrue(source.contains("status='CANCELED'"));
    }
}
