package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV5MigrationStaticTest {
    private static final Path MIGRATION = FlywayTestSupport.projectRoot()
            .resolve("src/main/resources/db/migration/V5__stage5_permission_aware_rag.sql");

    @Test
    void guardPrecedesAllPersistentRagTables() throws Exception {
        String sql = read();
        int guard = sql.indexOf("INSERT INTO `_v5_rag_abort` (`id`)\nSELECT `id`");
        assertTrue(guard >= 0);
        for (String table : List.of("ai_rag_query_log", "ai_rag_result", "ai_rag_result_source")) {
            assertTrue(sql.contains("'" + table + "'"));
            assertTrue(sql.indexOf("CREATE TABLE `" + table + "`") > guard);
        }
    }

    @Test
    void migrationStoresOnlyQuestionHmacAndCitationMetadata() throws Exception {
        String sql = read();
        String queryBlock = FlywayTestSupport.createTableBlocks(sql).get("ai_rag_query_log");
        String sourceBlock = FlywayTestSupport.createTableBlocks(sql).get("ai_rag_result_source");
        assertTrue(queryBlock.contains("`question_hmac` char(64) NOT NULL"));
        assertFalse(queryBlock.matches("(?is).*`(question|question_text|raw_question)`\\s+.*"));
        assertFalse(sourceBlock.matches("(?is).*`(content|body|source_text|chunk_text)`\\s+.*"));
        assertTrue(sourceBlock.contains("UNIQUE KEY `uk_rrs_citation`"));
        assertTrue(sourceBlock.contains("KEY `idx_rrs_source`"));
    }

    @Test
    void migrationDoesNotAlterPublishedTables() throws Exception {
        assertFalse(read().matches("(?is).*ALTER\\s+TABLE.*"));
    }

    private String read() throws Exception {
        return Files.readString(MIGRATION, StandardCharsets.UTF_8);
    }
}
