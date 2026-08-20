package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV1MigrationStaticTest {

    @Test
    void v1ContainsOnlyTheFrozenTwentyTableStructure() throws IOException {
        String sql = FlywayTestSupport.readResourceText(FlywayTestSupport.V1_RESOURCE);

        assertEquals(FlywayTestSupport.V1_TABLES, FlywayTestSupport.createTableNames(sql));
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
