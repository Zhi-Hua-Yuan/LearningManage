package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV3MigrationStaticTest {

    private static final Path MIGRATION_PATH = FlywayTestSupport.projectRoot()
            .resolve("src/main/resources/db/migration/V3__stage2_ai_invocation_governance.sql");

    @Test
    void blockerGuardRunsBeforeEveryPersistentSchemaChange() throws IOException {
        String sql = readMigration();

        assertBefore(sql, "CREATE TEMPORARY TABLE `_v3_ai_confirmation_guard`",
                "CREATE TABLE `ai_draft_confirm_log_archive`");
        assertBefore(sql, "INSERT INTO `_v3_ai_confirmation_guard` (`id`)",
                "CREATE TABLE `ai_draft_confirm_log_archive`");
        assertBefore(sql, "DROP TEMPORARY TABLE `_v3_ai_confirmation_guard`",
                "CREATE TABLE `ai_draft_confirm_log_archive`");
        assertTrue(sql.contains("table_name = 'ai_draft_confirm_log_archive'"));
        assertTrue(sql.contains("column_name IN ("));
        assertTrue(sql.contains("'0:user_id,draft_id,operation_id'"));
        assertTrue(sql.contains("index_name = 'uk_ai_confirm_user_draft'"));
        assertTrue(sql.contains("COUNT(DISTINCT CAST(`scene` AS BINARY)) > 1"));
        assertTrue(sql.contains("draft.`status` <> 1"));
        assertTrue(sql.contains("WHERE `status` NOT IN (0, 1, 2, 3)"));
    }

    @Test
    void migrationArchivesOnlyEquivalentDuplicatesAndKeepsEarliest() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("CREATE TABLE `ai_draft_confirm_log_archive`"));
        assertTrue(sql.contains("'EQUIVALENT_DUPLICATE'"));
        assertTrue(sql.contains("COUNT(DISTINCT CAST(`scene` AS BINARY)) = 1"));
        assertTrue(sql.contains("earlier.`create_time` < candidate.`create_time`"));
        assertTrue(sql.contains("earlier.`id` < candidate.`id`"));
        assertBefore(sql, "INSERT INTO `ai_draft_confirm_log_archive`",
                "DELETE confirmation");
        assertBefore(sql, "CREATE TEMPORARY TABLE `_v3_ai_archive_count_guard`",
                "DELETE confirmation");
        assertTrue(sql.contains("SUM(equivalent_group.`record_count` - 1)"));
    }

    @Test
    void strongerUniqueKeyIsCreatedBeforeLegacyKeyIsRemoved() throws IOException {
        String sql = readMigration();

        assertBefore(sql, "CREATE UNIQUE INDEX `uk_ai_confirm_user_draft`",
                "DROP INDEX `uk_user_draft_op`");
        assertTrue(sql.contains("ON `ai_draft_confirm_log` (`user_id`, `draft_id`)"));
        assertFalse(sql.contains("DROP COLUMN `operation_id`"));
    }

    @Test
    void callLogContainsTheFrozenGovernanceColumns() throws IOException {
        String sql = readMigration();

        for (String column : List.of(
                "requested_model", "finish_reason", "provider_request_id",
                "prompt_tokens", "completion_tokens", "total_tokens",
                "price_version", "currency", "estimated_cost", "trace_id",
                "failure_type", "fallback_used", "fallback_reason", "degraded",
                "request_sanitization_status", "response_sanitization_status",
                "error_sanitization_status", "request_truncated", "response_truncated",
                "error_truncated", "request_hash", "response_hash", "error_hash")) {
            assertTrue(sql.contains("`" + column + "`"), "missing V3 column: " + column);
        }
        assertTrue(sql.contains("decimal(20,8) DEFAULT NULL"));
        assertTrue(sql.contains("DEFAULT 'LEGACY_UNKNOWN'"));
    }

    @Test
    void migrationPreservesUnknownHistoricalMetadata() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("SET `requested_model` = `model_name`"));
        assertFalse(sql.matches("(?is).*UPDATE\s+`ai_call_log`.*`prompt_tokens`\s*=\s*0.*"));
        assertFalse(sql.matches("(?is).*UPDATE\s+`ai_call_log`.*`estimated_cost`\s*=\s*0.*"));
        assertFalse(sql.contains("DROP COLUMN `model_name`"));
    }

    @Test
    void draftConfirmationAndReplanTraceColumnsAreForwardCompatible() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("ALTER TABLE `ai_draft`"));
        assertTrue(sql.contains("`schema_version` int NOT NULL DEFAULT 1"));
        assertTrue(sql.contains("ADD KEY `idx_ai_draft_trace`"));
        assertTrue(sql.contains("ALTER TABLE `ai_draft_confirm_log`"));
        assertTrue(sql.contains("COMMENT '确认请求Trace ID'"));
        assertTrue(sql.contains("ALTER TABLE `ai_replan_operation`"));
        assertTrue(sql.contains("ADD KEY `idx_ai_replan_operation_trace`"));
    }

    private String readMigration() throws IOException {
        return Files.readString(MIGRATION_PATH, StandardCharsets.UTF_8);
    }

    private void assertBefore(String text, String first, String second) {
        assertTrue(text.indexOf(first) >= 0, "missing migration fragment: " + first);
        assertTrue(text.indexOf(second) >= 0, "missing migration fragment: " + second);
        assertTrue(text.indexOf(first) < text.indexOf(second), first + " must appear before " + second);
    }
}
