package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV2MigrationStaticTest {

    private static final Path MIGRATION_PATH = FlywayTestSupport.projectRoot()
            .resolve("src/main/resources/db/migration/V2__stage1_business_semantics_and_permissions.sql");

    @Test
    void migrationHasTheFrozenV2ObjectsAndOrder() throws IOException {
        String sql = readMigration();

        assertEquals(List.of("task_assignment_log", "weekly_review_task"),
                extractCreateTableNames(sql));
        assertBefore(sql, "CHANGE COLUMN `assignee_id` `assignee_user_id`", "CREATE TABLE `task_assignment_log`");
        assertBefore(sql, "CREATE TABLE `task_assignment_log`", "ALTER TABLE `weekly_review`");
        assertBefore(sql, "ALTER TABLE `weekly_review`", "CREATE TABLE `weekly_review_task`");

        assertTrue(sql.contains("ADD COLUMN `assigned_by_user_id` bigint DEFAULT NULL"));
        assertTrue(sql.contains("ADD COLUMN `assigned_at` datetime DEFAULT NULL"));
        assertTrue(sql.contains("ADD COLUMN `visibility_scope` varchar(16) NOT NULL DEFAULT 'PRIVATE'"));
        assertTrue(sql.contains("ADD COLUMN `team_id` bigint DEFAULT NULL"));
        assertTrue(sql.contains("ADD COLUMN `focus_project_id` bigint DEFAULT NULL"));
        assertTrue(sql.contains("ADD COLUMN `shared_summary` text NULL"));
    }

    @Test
    void migrationUsesCanonicalRolesAndStrictEnumChecks() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("BINARY `user_role` IN ('user', 'admin', 'USER', 'SYSTEM_ADMIN')"));
        assertTrue(sql.contains("WHEN BINARY 'user' THEN 'USER'"));
        assertTrue(sql.contains("WHEN BINARY 'admin' THEN 'SYSTEM_ADMIN'"));
        assertTrue(sql.contains("DEFAULT 'USER'"));
        assertTrue(sql.contains("BINARY `user_role` IN ('USER', 'SYSTEM_ADMIN')"));
        assertTrue(sql.contains("BINARY `action` IN ("));
        assertTrue(sql.contains("BINARY `visibility_scope` IN ('PRIVATE', 'TEAM')"));
        assertTrue(sql.contains("BINARY `visibility_scope` = BINARY 'PRIVATE'"));
        assertTrue(sql.contains("BINARY `visibility_scope` = BINARY 'TEAM'"));
    }

    @Test
    void migrationHasOneCurrentAssigneeAndTheRequiredIndexes() throws IOException {
        String sql = readMigration();

        assertEquals(1, countOccurrences(sql, "`assignee_id`"));
        assertEquals(1, countOccurrences(sql, "`assignee_user_id` bigint DEFAULT NULL"));
        assertFalse(sql.contains("ADD COLUMN `assignee_id`"));
        assertTrue(sql.contains("DROP INDEX `idx_task_assignee_id`"));
        assertTrue(sql.contains("ADD KEY `idx_task_assignee_status`"));
        assertTrue(sql.contains("(`assignee_user_id`, `is_delete`, `status`, `due_date`)"));
        assertTrue(sql.contains("ADD KEY `idx_task_project_assignee`"));
        assertTrue(sql.contains("(`project_id`, `assignee_user_id`, `is_delete`)"));
    }

    @Test
    void migrationBackfillsAllTaskLifecyclesAndUsesDeterministicInitialLogs() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("`assignee_user_id` = COALESCE(`assignee_user_id`, `user_id`)"));
        assertTrue(sql.contains("`assigned_by_user_id` = `user_id`"));
        assertTrue(sql.contains("`assigned_at` = `create_time`"));
        assertTrue(sql.contains("`id`,\n    `task_id`,"));
        assertTrue(sql.contains("SELECT\n    `id`,\n    `id`,"));
        assertTrue(sql.contains("'INITIAL_ASSIGN'"));
        assertTrue(sql.contains("`assigned_at`\nFROM `task`\nWHERE `assignee_user_id` IS NOT NULL"));
        assertFalse(sql.contains("AUTO_INCREMENT"));
        assertFalse(sql.matches("(?is).*INSERT\\s+INTO\\s+`task_assignment_log`.*(NOW\\(\\)|CURRENT_TIMESTAMP|ROW_NUMBER)"));
    }

    @Test
    void migrationCreatesImmutableHistoryAndReviewAssociationsWithoutForbiddenFeatures() throws IOException {
        String code = stripLineComments(readMigration());

        for (String forbidden : List.of(
                "CREATE\\s+DATABASE", "\\bUSE\\s+`", "\\bDEFINER\\s*=", "\\bGRANT\\b",
                "\\bFOREIGN\\s+KEY\\b", "\\bTRIGGER\\b", "\\bPROCEDURE\\b",
                "\\bTRUNCATE\\b", "\\bDROP\\s+TABLE", "IF\\s+EXISTS", "IF\\s+NOT\\s+EXISTS",
                "\\bDELETE\\s+FROM", "\\bINSERT\\s+INTO\\s+ai_") ) {
            assertFalse(code.matches("(?is).*" + forbidden + ".*"),
                    "migration contains forbidden construct: " + forbidden);
        }

        assertTrue(code.contains("CONSTRAINT `chk_task_assignment_action` CHECK"));
        assertTrue(code.contains("UNIQUE KEY `uk_weekly_review_task`"));
        assertTrue(code.contains("KEY `idx_weekly_review_task_task`"));
        assertFalse(code.contains("`reflection`"));
        assertFalse(code.contains("`next_plan`"));
        assertFalse(code.contains("`shared_summary` = `reflection`"));
    }

    private String readMigration() throws IOException {
        return Files.readString(MIGRATION_PATH, StandardCharsets.UTF_8);
    }

    private List<String> extractCreateTableNames(String sql) {
        Matcher matcher = Pattern.compile("(?im)^CREATE TABLE `([^`]+)`").matcher(sql);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int start = 0;
        while ((start = text.indexOf(token, start)) >= 0) {
            count++;
            start += token.length();
        }
        return count;
    }

    private void assertBefore(String text, String first, String second) {
        assertTrue(text.indexOf(first) >= 0, "missing migration fragment: " + first);
        assertTrue(text.indexOf(second) >= 0, "missing migration fragment: " + second);
        assertTrue(text.indexOf(first) < text.indexOf(second),
                first + " must appear before " + second);
    }

    private String stripLineComments(String sql) {
        return sql.replaceAll("(?m)^\\s*--[^\\r\\n]*(?:\\r?\\n|$)", "");
    }
}
