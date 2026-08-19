package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV1MigrationStaticTest {

    private static final List<String> V1_TABLES = List.of(
            "user", "tenant", "role", "permission", "role_permission", "user_role",
            "team", "team_member", "project", "milestone", "task", "weekly_review",
            "prompt_template", "ai_call_log", "ai_draft", "ai_draft_confirm_log",
            "ai_replan_operation", "ai_replan_item", "task_status_idempotency",
            "task_title_rename_log"
    );

    @Test
    void v1ContainsOnlyTheFrozenTwentyTableStructure() throws IOException {
        String sql;
        try (InputStream inputStream = new ClassPathResource("db/migration/V1__baseline_schema.sql").getInputStream()) {
            sql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        Matcher matcher = Pattern.compile("CREATE TABLE `([^`]+)`").matcher(sql);
        List<String> actualTables = new ArrayList<>();
        while (matcher.find()) {
            actualTables.add(matcher.group(1));
        }

        assertEquals(V1_TABLES, actualTables);
        assertFalse(sql.matches("(?is).*\\bINSERT\\s+INTO\\b.*"));
        assertFalse(sql.matches("(?is).*\\bDROP\\s+TABLE\\b.*"));
        assertFalse(sql.matches("(?is).*\\bCREATE\\s+DATABASE\\b.*"));
        assertFalse(sql.matches("(?is).*\\bUSE\\s+`.*"));
        assertFalse(sql.matches("(?is).*\\bDEFINER\\s*=.*"));
        assertFalse(sql.matches("(?is).*CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS.*"));
        assertFalse(sql.matches("(?is).*\\bFOREIGN\\s+KEY\\b.*"));
        assertTrue(sql.contains("`assignee_id` bigint DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_task_assignee_id` (`assignee_id`)"));
        assertTrue(sql.contains("CONSTRAINT `chk_task_status_range` CHECK ((`status` in (0,1,2,3)))"));
    }
}
