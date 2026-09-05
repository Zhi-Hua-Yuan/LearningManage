package com.spt.learningmanage.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeSourceLockMapperContractTest {

    @Test
    void lockSqlUsesLeaseAndOwnerTokenFencing() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/spt/learningmanage/mapper/AiKnowledgeSourceLockMapper.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("owner_token = #{token}"));
        assertTrue(source.contains("lease_until < #{now}"));
        assertTrue(source.contains("AND owner_token = #{token}"));
        assertTrue(source.contains("INSERT IGNORE"));
    }
}
