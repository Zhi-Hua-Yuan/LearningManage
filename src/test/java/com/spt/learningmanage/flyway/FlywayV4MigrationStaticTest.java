package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV4MigrationStaticTest {

    private static final Path MIGRATION = FlywayTestSupport.projectRoot()
            .resolve("src/main/resources/db/migration/V4__stage4_knowledge_index_and_outbox.sql");

    @Test
    void guardRunsBeforePersistentDdl() throws IOException {
        String sql = read();
        assertBefore(sql, "CREATE TEMPORARY TABLE `_v4_knowledge_guard`",
                "CREATE TABLE `ai_knowledge_index_event`");
        assertBefore(sql, "INSERT INTO `_v4_knowledge_abort` (`id`)\nSELECT `id`",
                "CREATE TABLE `ai_knowledge_index_event`");
        for (String table : List.of(
                "ai_knowledge_index_event", "ai_knowledge_source_lock",
                "ai_knowledge_document", "ai_knowledge_backfill_run")) {
            assertTrue(sql.contains("'" + table + "'"));
            assertTrue(sql.contains("CREATE TABLE `" + table + "`"));
        }
    }

    @Test
    void outboxContainsLeaseFencingAndReadyIndexes() throws IOException {
        String sql = read();
        for (String column : List.of(
                "status", "attempt_count", "next_attempt_at", "claimed_by",
                "claim_token", "claimed_at", "lease_until", "failure_type",
                "last_error", "trace_id")) {
            assertTrue(sql.contains("`" + column + "`"), "missing outbox column " + column);
        }
        assertTrue(sql.contains("KEY `idx_kie_ready` (`status`, `next_attempt_at`, `id`)"));
        assertTrue(sql.contains("'PENDING', 'PROCESSING', 'RETRY_WAIT', 'SUCCESS', 'DEAD'"));
    }

    @Test
    void documentTableContainsNoBodyOrVectorColumns() throws IOException {
        String block = FlywayTestSupport.createTableBlocks(read()).get("ai_knowledge_document");
        assertTrue(block.contains("`content_hash` char(64) NOT NULL"));
        assertTrue(block.contains("`payload_hash` char(64) NOT NULL"));
        assertTrue(block.contains("UNIQUE KEY `uk_kd_document_key`"));
        assertFalse(block.matches("(?is).*`(content|body|vector|embedding)`\\s+(text|longtext|json|blob).*"));
    }

    @Test
    void v4DoesNotAlterPublishedBusinessTables() throws IOException {
        String sql = read();
        assertFalse(sql.matches("(?is).*ALTER\\s+TABLE\\s+`?(task|project|weekly_review|user)`?.*"));
        assertFalse(sql.matches("(?is).*(UPDATE|DELETE)\\s+`?(task|project|weekly_review|user)`?.*"));
    }

    private String read() throws IOException {
        return Files.readString(MIGRATION, StandardCharsets.UTF_8);
    }

    private void assertBefore(String text, String first, String second) {
        assertTrue(text.indexOf(first) >= 0, "missing migration fragment: " + first);
        assertTrue(text.indexOf(second) >= 0, "missing migration fragment: " + second);
        assertTrue(text.indexOf(first) < text.indexOf(second));
    }
}
