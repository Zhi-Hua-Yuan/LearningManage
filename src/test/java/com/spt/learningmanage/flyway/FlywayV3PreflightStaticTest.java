package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayV3PreflightStaticTest {

    private static final Path PREFLIGHT_PATH = FlywayTestSupport.projectRoot()
            .resolve("sql/flyway/stage2/01_preflight_v3.sql");
    private static final Path POST_VERIFY_PATH = FlywayTestSupport.projectRoot()
            .resolve("sql/flyway/stage2/02_post_verify_v3.sql");
    private static final Path LEGACY_BACKFILL_VERIFY_PATH = FlywayTestSupport.projectRoot()
            .resolve("sql/flyway/stage2/03_verify_v3_legacy_backfill.sql");

    @Test
    void preflightIsReadOnlyAndReportsOneDeterministicResultSet() throws IOException {
        String sql = stripComments(Files.readString(PREFLIGHT_PATH, StandardCharsets.UTF_8));

        for (String forbidden : List.of(
                "INSERT\\s+INTO", "UPDATE\\s+", "DELETE\\s+FROM", "ALTER\\s+TABLE",
                "CREATE\\s+TABLE", "DROP\\s+TABLE", "TRUNCATE\\s+TABLE")) {
            assertFalse(sql.matches("(?is).*" + forbidden + ".*"),
                    "preflight contains write statement: " + forbidden);
        }
        assertTrue(sql.trim().startsWith("WITH `v3_preflight_checks` AS"));
        assertTrue(sql.trim().endsWith("ORDER BY `check_id`;"));
    }

    @Test
    void preflightClassifiesRepairableAndBlockingFindings() throws IOException {
        String sql = Files.readString(PREFLIGHT_PATH, StandardCharsets.UTF_8);

        assertTrue(sql.contains("REPAIRABLE_EQUIVALENT_DUPLICATE"));
        assertTrue(sql.contains("BLOCKING_CONFLICT"));
        assertTrue(sql.contains("BLOCKING_INTEGRITY_ERROR"));
        assertTrue(sql.contains("COUNT(DISTINCT COALESCE(CAST(`business_id` AS CHAR), '<NULL>'))"));
        assertTrue(sql.contains("'V3-P-010'"));
        assertTrue(sql.contains("'V3-P-017'"));
    }

    @Test
    void preflightCoversOwnershipSceneStatusAndMissingResults() throws IOException {
        String sql = Files.readString(PREFLIGHT_PATH, StandardCharsets.UTF_8);

        assertTrue(sql.contains("confirmation.`user_id` <> draft.`user_id`"));
        assertTrue(sql.contains("BINARY confirmation.`scene` <> BINARY draft.`scene`"));
        assertTrue(sql.contains("draft.`status` <> 1"));
        assertTrue(sql.contains("draft.`status` = 1"));
        assertTrue(sql.contains("confirmation.`id` IS NULL"));
    }

    @Test
    void postVerifyCoversSchemaBackfillUniquenessAndIntegrity() throws IOException {
        String sql = stripComments(Files.readString(POST_VERIFY_PATH, StandardCharsets.UTF_8));

        assertTrue(sql.contains("expected_v3_columns"));
        assertTrue(sql.contains("actual.column_type"));
        assertTrue(sql.contains("actual.column_default"));
        assertTrue(sql.contains("actual_v3_indexes"));
        assertTrue(sql.contains("actual.column_list"));
        assertTrue(sql.contains("uk_ai_confirm_user_draft"));
        assertTrue(sql.contains("uk_user_draft_op"));
        assertTrue(sql.contains("required V3 check constraints are enforced"));
        assertTrue(sql.contains("'V3-V-014'"));
        for (String forbidden : List.of(
                "INSERT\\s+INTO", "UPDATE\\s+", "DELETE\\s+FROM", "ALTER\\s+TABLE",
                "CREATE\\s+TABLE", "DROP\\s+TABLE", "TRUNCATE\\s+TABLE")) {
            assertFalse(sql.matches("(?is).*" + forbidden + ".*"),
                    "post-verify contains write statement: " + forbidden);
        }
    }

    @Test
    void historicalBackfillChecksAreSeparatedFromReusablePostVerify() throws IOException {
        String reusable = Files.readString(POST_VERIFY_PATH, StandardCharsets.UTF_8);
        String legacy = Files.readString(LEGACY_BACKFILL_VERIFY_PATH, StandardCharsets.UTF_8);

        assertFalse(reusable.contains("legacy usage and cost remain unknown"));
        assertFalse(reusable.contains("legacy drafts use schema version one without synthetic trace"));
        assertTrue(legacy.contains("requested_model` <> BINARY `model_name"));
        assertTrue(legacy.contains("`schema_version` <> 1"));
        assertTrue(legacy.contains("`trace_id` IS NOT NULL"));
        assertTrue(legacy.contains("'V3-L-005'"));
    }

    private String stripComments(String sql) {
        return sql.replaceAll("(?m)^\\s*--[^\\r\\n]*(?:\\r?\\n|$)", "");
    }
}
