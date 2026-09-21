package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV9MigrationStaticTest {
    private static final Path MIGRATION = FlywayTestSupport.projectRoot()
            .resolve("src/main/resources/db/migration/V9__stage3_rag_canceled_query_status.sql");

    @Test
    void migrationExtendsQueryAuditStatusWithoutChangingPublishedMigrations() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertTrue(sql.contains("DROP CHECK `chk_rql_status`"));
        assertTrue(sql.contains("ADD CONSTRAINT `chk_rql_status`"));
        assertTrue(sql.contains("'CANCELED'"));
    }
}
