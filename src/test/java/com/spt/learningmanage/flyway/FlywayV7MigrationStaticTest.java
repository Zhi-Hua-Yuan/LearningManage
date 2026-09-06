package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV7MigrationStaticTest {
    private static final Path MIGRATION = FlywayTestSupport.projectRoot()
            .resolve("src/main/resources/db/migration/V7__stage6_agent_and_analysis_report.sql");

    @Test
    void migrationCreatesDurableAgentAndReportContracts() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertTrue(sql.contains("CREATE TABLE `ai_agent_run`"));
        assertTrue(sql.contains("CREATE TABLE `ai_agent_tool_log`"));
        assertTrue(sql.contains("CREATE TABLE `ai_analysis_report`"));
        assertTrue(sql.contains("CREATE TABLE `ai_analysis_report_source`"));
        assertTrue(sql.contains("`execution_token`"));
        assertTrue(sql.contains("`data_version`"));
        assertTrue(sql.contains("uk_agent_user_scene_request"));
        assertTrue(sql.contains("uk_analysis_report_run_type"));
    }
}
