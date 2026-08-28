package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV2PostVerifyStaticTest {

    private static final Path POST_VERIFY_PATH = FlywayTestSupport.projectRoot()
            .resolve("sql/flyway/stage1/02_post_verify_v2.sql");

    private static final List<String> EXPECTED_CHECK_IDS = List.of(
            "V2-V-001", "V2-V-002", "V2-V-003", "V2-V-004",
            "V2-V-005", "V2-V-006", "V2-V-007", "V2-V-008",
            "V2-V-009", "V2-V-010", "V2-V-011", "V2-V-012"
    );

    @Test
    void postVerifyHasExactlyTheFrozenCheckCatalog() throws IOException {
        String sql = readPostVerify();
        List<String> actualIds = extractCheckIds(sql);

        assertEquals(EXPECTED_CHECK_IDS, actualIds);
        assertEquals(actualIds.size(), new HashSet<>(actualIds).size());
        assertTrue(sql.contains("WITH `v2_post_verify_checks` AS ("));
        assertTrue(sql.contains("FROM `v2_post_verify_checks`"));
        assertTrue(sql.contains("ORDER BY `check_id`"));
        assertTrue(sql.contains("`check_name`"));
        assertTrue(sql.contains("`violation_count`"));
        assertTrue(sql.contains("AS `status`"));
    }

    @Test
    void postVerifyIsReadOnlyAndDoesNotExposeSensitiveFields() throws IOException {
        String code = stripLineComments(readPostVerify());

        for (String forbidden : List.of(
                "INSERT\\s+INTO", "UPDATE\\s+", "DELETE\\s+FROM", "ALTER\\s+TABLE",
                "CREATE\\s+", "DROP\\s+", "TRUNCATE\\s+", "GRANT\\s+", "CALL\\s+",
                "SIGNAL\\s+", "\\bpassword\\b", "\\breflection\\b", "\\bnext_plan\\b",
                "\\brequest_text\\b", "\\bresponse_text\\b", "\\bpayload_json\\b")) {
            assertFalse(code.matches("(?is).*" + forbidden + ".*"),
                    "post-verify contains forbidden content: " + forbidden);
        }
    }

    @Test
    void postVerifyReconcilesFrozenAssignmentAndReviewRules() throws IOException {
        String sql = readPostVerify();

        assertTrue(sql.contains("assignee_user_id IS NULL"));
        assertTrue(sql.contains("assigned_by_user_id IS NULL"));
        assertTrue(sql.contains("assigned_at IS NULL"));
        assertTrue(sql.contains("BINARY action = BINARY 'INITIAL_ASSIGN'"));
        assertTrue(sql.contains("log_entry.id <> t.id"));
        assertTrue(sql.contains("log_entry.create_time <> t.create_time"));
        assertTrue(sql.contains("BINARY visibility_scope <> BINARY 'PRIVATE'"));
        assertTrue(sql.contains("shared_summary IS NOT NULL"));
        assertTrue(sql.contains("UNIQUE KEY") || sql.contains("uk_weekly_review_task"));
        assertTrue(sql.contains("idx_task_assignee_status"));
        assertTrue(sql.contains("idx_task_project_assignee"));
    }

    private String readPostVerify() throws IOException {
        return Files.readString(POST_VERIFY_PATH, StandardCharsets.UTF_8);
    }

    private List<String> extractCheckIds(String sql) {
        Matcher matcher = Pattern.compile("'(?<id>V2-V-[0-9]{3})'").matcher(sql);
        List<String> ids = new ArrayList<>();
        while (matcher.find()) {
            ids.add(matcher.group("id"));
        }
        return ids;
    }

    private String stripLineComments(String sql) {
        return sql.replaceAll("(?m)^\\s*--[^\\r\\n]*(?:\\r?\\n|$)", "");
    }
}
