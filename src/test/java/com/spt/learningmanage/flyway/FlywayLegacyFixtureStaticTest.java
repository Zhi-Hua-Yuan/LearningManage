package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayLegacyFixtureStaticTest {

    @Test
    void fixtureMatchesTheFrozenStructureAndHash() throws IOException {
        byte[] fixtureBytes = FlywayTestSupport.readResourceBytes(FlywayTestSupport.LEGACY_FIXTURE_RESOURCE);
        String fixture = new String(fixtureBytes, java.nio.charset.StandardCharsets.UTF_8);
        String v1 = FlywayTestSupport.readResourceText(FlywayTestSupport.V1_RESOURCE);

        assertEquals(FlywayTestSupport.EXPECTED_LEGACY_FIXTURE_SHA256,
                FlywayTestSupport.sha256(fixtureBytes));
        assertEquals(FlywayTestSupport.V1_TABLES, FlywayTestSupport.createTableNames(fixture));
        assertEquals(FlywayTestSupport.createTableBlocks(v1), FlywayTestSupport.createTableBlocks(fixture));
    }

    @Test
    void fixtureContainsNoDataCredentialsOrDatabaseControlStatements() throws IOException {
        String fixture = FlywayTestSupport.readResourceText(FlywayTestSupport.LEGACY_FIXTURE_RESOURCE);

        for (String forbidden : new String[]{
                "INSERT\\s+INTO", "DROP\\s+TABLE", "CREATE\\s+DATABASE", "\\bUSE\\s+`",
                "\\bDEFINER\\s*=", "\\bGRANT\\b", "CREATE\\s+USER", "LOCK\\s+TABLES",
                "UNLOCK\\s+TABLES", "flyway_schema_history", "CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS",
                "AUTO_INCREMENT\\s*=\\s*\\d+"
        }) {
            assertFalse(fixture.matches("(?is).*" + forbidden + ".*"),
                    "fixture contains forbidden content: " + forbidden);
        }

        assertTrue(fixture.contains("`assignee_id` bigint DEFAULT NULL"));
        assertTrue(fixture.contains("KEY `idx_task_assignee_id` (`assignee_id`)"));
        assertTrue(fixture.contains("CONSTRAINT `chk_task_status_range` CHECK ((`status` in (0,1,2,3)))"));
    }
}
