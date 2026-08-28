package com.spt.learningmanage.flyway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV2SeedFixtureStaticTest {

    private static final String SEED_RESOURCE = "db/stage1/v1_to_v2_seed.sql";
    private static final String EXPECTED_RESOURCE = "db/stage1/v1_to_v2_expected.json";
    private static final List<String> EXPECTED_INSERT_TABLES = List.of(
            "user", "team", "team_member", "project", "milestone", "task", "weekly_review"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void seedUsesOnlyTheAllowedV1DataTables() throws IOException {
        String sql = FlywayTestSupport.readResourceText(SEED_RESOURCE);
        String code = stripLineComments(sql);
        List<String> actualTables = extractInsertTables(code);

        assertEquals(EXPECTED_INSERT_TABLES, actualTables);
        assertFalse(code.matches("(?is).*\\bCREATE\\s+DATABASE\\b.*"));
        assertFalse(code.matches("(?is).*\\bUSE\\s+`.*"));
        assertFalse(code.matches("(?is).*\\bDROP\\s+TABLE\\b.*"));
        assertFalse(code.matches("(?is).*\\bALTER\\s+TABLE\\b.*"));
        assertFalse(code.matches("(?is).*\\bTRUNCATE\\s+TABLE\\b.*"));
        assertFalse(code.matches("(?is).*flyway_schema_history.*"));
        assertFalse(code.matches("(?is).*\\bINSERT\\s+INTO\\s+ai_.*"));
    }

    @Test
    void seedRowCountsMatchTheExpectedFixtureContract() throws IOException {
        String code = stripLineComments(FlywayTestSupport.readResourceText(SEED_RESOURCE));
        JsonNode expected = readExpected();
        JsonNode counts = expected.at("/expectedCounts/beforeMigration");

        assertEquals(counts.path("user").asInt(), countInsertRows(code, "user"));
        assertEquals(counts.path("team").asInt(), countInsertRows(code, "team"));
        assertEquals(counts.path("teamMember").asInt(), countInsertRows(code, "team_member"));
        assertEquals(counts.path("project").asInt(), countInsertRows(code, "project"));
        assertEquals(counts.path("milestone").asInt(), countInsertRows(code, "milestone"));
        assertEquals(counts.path("task").asInt(), countInsertRows(code, "task"));
        assertEquals(counts.path("weeklyReview").asInt(), countInsertRows(code, "weekly_review"));
    }

    @Test
    void expectedRolesAndTaskLifecyclesAreCovered() throws IOException {
        JsonNode expected = readExpected();
        Set<String> roleInputs = new HashSet<>();
        for (JsonNode role : expected.path("systemRoles")) {
            roleInputs.add(role.path("v1Role").asText());
        }
        assertEquals(Set.of("user", "admin", "USER", "SYSTEM_ADMIN"), roleInputs);

        Set<String> lifecycles = new HashSet<>();
        for (JsonNode task : expected.path("taskAssignments")) {
            lifecycles.add(task.path("lifecycle").asText());
        }
        assertEquals(Set.of("ACTIVE_INCOMPLETE", "COMPLETED", "LOGICALLY_DELETED"), lifecycles);
    }

    @Test
    void expectedInitialAssignmentsAreOneToOneAndUseTaskIds() throws IOException {
        JsonNode expected = readExpected();
        JsonNode tasks = expected.path("taskAssignments");
        Set<Long> taskIds = new HashSet<>();
        Set<Long> logIds = new HashSet<>();

        for (JsonNode task : tasks) {
            long taskId = task.path("taskId").asLong();
            JsonNode log = task.path("expectedInitialLog");
            taskIds.add(taskId);
            logIds.add(log.path("id").asLong());

            assertEquals(taskId, log.path("id").asLong());
            assertTrue(log.path("fromAssigneeUserId").isNull());
            assertEquals(task.path("expectedAssigneeUserId").asLong(),
                    log.path("toAssigneeUserId").asLong());
            assertEquals(task.path("expectedAssignedByUserId").asLong(),
                    log.path("assignedByUserId").asLong());
            assertEquals(task.path("expectedAssignedAt").asText(), log.path("createTime").asText());
            assertEquals("INITIAL_ASSIGN", log.path("action").asText());
            assertTrue(log.path("reason").isNull());
        }

        assertEquals(tasks.size(), taskIds.size());
        assertEquals(tasks.size(), logIds.size());
        assertEquals(expected.at("/expectedCounts/afterMigration/taskAssignmentLog").asInt(), tasks.size());
    }

    @Test
    void expectedWeeklyReviewsRemainPrivateAndUnlinked() throws IOException {
        JsonNode expected = readExpected();
        assertEquals(2, expected.path("weeklyReviews").size());
        assertEquals(0, expected.at("/expectedCounts/afterMigration/weeklyReviewTask").asInt());

        for (JsonNode review : expected.path("weeklyReviews")) {
            assertEquals("PRIVATE", review.path("expectedVisibilityScope").asText());
            assertTrue(review.path("expectedTeamId").isNull());
            assertTrue(review.path("expectedFocusProjectId").isNull());
            assertTrue(review.path("expectedSharedSummary").isNull());
        }
    }

    @Test
    void negativeFixturesPointToDistinctFrozenChecks() throws IOException {
        assertNegativeFixture("db/stage1/negative/unknown_system_role.sql", "V2-P-010");
        assertNegativeFixture("db/stage1/negative/orphan_assignee.sql", "V2-P-021");
        assertNegativeFixture("db/stage1/negative/team_assignee_not_member.sql", "V2-P-032");
    }

    private JsonNode readExpected() throws IOException {
        return OBJECT_MAPPER.readTree(FlywayTestSupport.readResourceText(EXPECTED_RESOURCE));
    }

    private List<String> extractInsertTables(String sql) {
        Matcher matcher = Pattern.compile("(?im)^\\s*INSERT\\s+INTO\\s+`([^`]+)`").matcher(sql);
        List<String> tables = new ArrayList<>();
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    private int countInsertRows(String sql, String table) {
        String expression = "(?is)INSERT\\s+INTO\\s+`" + Pattern.quote(table)
                + "`\\s*\\(.*?\\)\\s*VALUES\\s*(?<rows>.*?);";
        Matcher statement = Pattern.compile(expression).matcher(sql);
        assertTrue(statement.find(), "missing insert statement for " + table);
        Matcher row = Pattern.compile("\\([^()]*\\)").matcher(statement.group("rows"));
        int count = 0;
        while (row.find()) {
            count++;
        }
        return count;
    }

    private void assertNegativeFixture(String resource, String expectedCheckId) throws IOException {
        String sql = FlywayTestSupport.readResourceText(resource);
        String code = stripLineComments(sql);
        assertTrue(sql.contains(expectedCheckId), resource + " must identify " + expectedCheckId);
        assertFalse(code.matches("(?is).*\\bCREATE\\s+DATABASE\\b.*"));
        assertFalse(code.matches("(?is).*\\bUSE\\s+`.*"));
        assertFalse(code.matches("(?is).*\\bDROP\\s+TABLE\\b.*"));
        assertFalse(code.matches("(?is).*\\bALTER\\s+TABLE\\b.*"));
        assertFalse(code.matches("(?is).*flyway_schema_history.*"));
    }

    private String stripLineComments(String sql) {
        return sql.replaceAll("(?m)^\\s*--[^\\r\\n]*(?:\\r?\\n|$)", "");
    }
}
