package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV2PreflightStaticTest {

    private static final Path PREFLIGHT_PATH = FlywayTestSupport.projectRoot()
            .resolve("sql/flyway/stage1/01_preflight_v2.sql");

    private static final List<String> EXPECTED_CHECK_IDS = List.of(
            "V2-P-001", "V2-P-002", "V2-P-003", "V2-P-004", "V2-P-005", "V2-P-006",
            "V2-P-010", "V2-P-011", "V2-P-012",
            "V2-P-020", "V2-P-021", "V2-P-022", "V2-P-023", "V2-P-024", "V2-P-025", "V2-P-026",
            "V2-P-030", "V2-P-031", "V2-P-032", "V2-P-033",
            "V2-P-040", "V2-P-041", "V2-P-042", "V2-P-043", "V2-P-044"
    );

    @Test
    void preflightHasExactlyTheFrozenCheckCatalog() throws IOException {
        String sql = readPreflight();
        List<String> actualIds = extractCheckIds(sql);

        assertEquals(EXPECTED_CHECK_IDS, actualIds);
        assertEquals(actualIds.size(), new HashSet<>(actualIds).size());
        assertTrue(sql.contains("WITH `v2_preflight_checks` AS ("));
        assertTrue(sql.contains("FROM `v2_preflight_checks`"));
        assertTrue(sql.contains("ORDER BY `check_id`"));
        assertTrue(sql.contains("`check_name`"));
        assertTrue(sql.contains("`violation_count`"));
        assertTrue(sql.contains("AS `status`"));
    }

    @Test
    void preflightIsReadOnlyAndDoesNotExposeSensitiveFields() throws IOException {
        String code = stripLineComments(readPreflight());

        for (String forbidden : List.of(
                "INSERT\\s+INTO", "UPDATE\\s+", "DELETE\\s+FROM", "ALTER\\s+TABLE",
                "CREATE\\s+", "DROP\\s+", "TRUNCATE\\s+", "GRANT\\s+", "CALL\\s+", "SIGNAL\\s+",
                "\\bpassword\\b", "\\breflection\\b", "\\bnext_plan\\b",
                "\\brequest_text\\b", "\\bresponse_text\\b", "\\bpayload_json\\b")) {
            assertFalse(code.matches("(?is).*" + forbidden + ".*"),
                    "preflight contains forbidden content: " + forbidden);
        }
    }

    @Test
    void preflightUsesTheFrozenAssigneeAndRoleRules() throws IOException {
        String sql = readPreflight();

        assertTrue(sql.contains("COALESCE(t.assignee_id, t.user_id)"));
        assertTrue(sql.contains("BINARY TRIM(user_role)"));
        assertTrue(sql.contains("BINARY user_role <> BINARY TRIM(user_role)"));
        assertTrue(sql.contains("team_member tm"));
        assertTrue(sql.contains("tm.is_delete = 0"));
        assertTrue(sql.contains("tm.team_id = p.team_id"));
        assertTrue(sql.contains("`year`"));
        assertTrue(sql.contains("`week_no`"));
        assertTrue(sql.contains("COUNT(DISTINCT index_name)"));
    }

    private String readPreflight() throws IOException {
        return Files.readString(PREFLIGHT_PATH, StandardCharsets.UTF_8);
    }

    private List<String> extractCheckIds(String sql) {
        Matcher matcher = Pattern.compile("'(?<id>V2-P-[0-9]{3})'").matcher(sql);
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
