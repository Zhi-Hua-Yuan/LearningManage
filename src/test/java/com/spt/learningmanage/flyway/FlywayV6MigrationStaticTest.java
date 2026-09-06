package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV6MigrationStaticTest {
    private static final Path MIGRATION = FlywayTestSupport.projectRoot()
            .resolve("src/main/resources/db/migration/V6__stage5_qdrant_numeric_permission_payload_rebuild.sql");

    @Test
    void migrationOnlySchedulesTheRequiredIdempotentRebuild() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertTrue(sql.contains("'stage5-qdrant-numeric-payload-v1', 'REBUILD', 'ALL'"));
        assertTrue(sql.contains("INSERT INTO `ai_knowledge_backfill_run`"));
        assertFalse(sql.matches("(?is).*ALTER\\s+TABLE.*"));
        assertFalse(sql.matches("(?is).*CREATE\\s+TABLE.*"));
    }
}
