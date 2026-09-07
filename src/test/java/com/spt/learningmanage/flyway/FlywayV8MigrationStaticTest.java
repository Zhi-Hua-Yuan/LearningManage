package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV8MigrationStaticTest {
    private static final Path MIGRATION = FlywayTestSupport.projectRoot()
            .resolve("src/main/resources/db/migration/V8__stage7_observability_and_data_lifecycle.sql");

    @Test
    void migrationCreatesResumableLifecycleContracts() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertTrue(sql.contains("CREATE TEMPORARY TABLE `_v8_stage7_guard`"));
        assertTrue(sql.contains("information_schema.columns"));
        assertTrue(sql.contains("CREATE TABLE `ai_data_cleanup_run`"));
        assertTrue(sql.contains("CREATE TABLE `ai_data_cleanup_lock`"));
        assertTrue(sql.contains("CREATE TABLE `ai_data_cleanup_item`"));
        assertTrue(sql.contains("CREATE TABLE `ai_admin_operation_log`"));
        assertTrue(sql.contains("`execution_token`"));
        assertTrue(sql.contains("`resource_hash`"));
        assertTrue(sql.contains("`approved_dry_run_id`"));
        assertTrue(sql.contains("`cancel_requested_at`"));
        assertTrue(sql.contains("`body_purged_at`"));
        assertTrue(sql.contains("`payload_purged_at`"));
        assertTrue(sql.contains("`content_purged_at`"));
        assertTrue(sql.contains("uk_cleanup_user_request"));
    }
}
