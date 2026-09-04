-- Stage 2 WP1 V3 reusable post-verify.
-- This file checks durable schema/data invariants and remains valid after later
-- work packages start writing V3 metadata. Historical backfill assertions live
-- in 03_verify_v3_legacy_backfill.sql.

WITH
`expected_v3_columns` AS (
    SELECT 'ai_call_log' AS `table_name`, 'requested_model' AS `column_name`, 'varchar(64)' AS `column_type`, 'YES' AS `is_nullable`, '<NULL>' AS `column_default`
    UNION ALL SELECT 'ai_call_log', 'finish_reason', 'varchar(32)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'provider_request_id', 'varchar(128)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'prompt_tokens', 'bigint', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'completion_tokens', 'bigint', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'total_tokens', 'bigint', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'price_version', 'varchar(64)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'currency', 'varchar(8)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'estimated_cost', 'decimal(20,8)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'trace_id', 'varchar(64)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'failure_type', 'varchar(32)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'fallback_used', 'tinyint', 'NO', '0'
    UNION ALL SELECT 'ai_call_log', 'fallback_reason', 'varchar(64)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'degraded', 'tinyint', 'NO', '0'
    UNION ALL SELECT 'ai_call_log', 'request_sanitization_status', 'varchar(24)', 'NO', 'LEGACY_UNKNOWN'
    UNION ALL SELECT 'ai_call_log', 'response_sanitization_status', 'varchar(24)', 'NO', 'LEGACY_UNKNOWN'
    UNION ALL SELECT 'ai_call_log', 'error_sanitization_status', 'varchar(24)', 'NO', 'LEGACY_UNKNOWN'
    UNION ALL SELECT 'ai_call_log', 'request_truncated', 'tinyint', 'NO', '0'
    UNION ALL SELECT 'ai_call_log', 'response_truncated', 'tinyint', 'NO', '0'
    UNION ALL SELECT 'ai_call_log', 'error_truncated', 'tinyint', 'NO', '0'
    UNION ALL SELECT 'ai_call_log', 'request_hash', 'char(64)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'response_hash', 'char(64)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_call_log', 'error_hash', 'char(64)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_draft', 'schema_version', 'int', 'NO', '1'
    UNION ALL SELECT 'ai_draft', 'trace_id', 'varchar(64)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_draft_confirm_log', 'trace_id', 'varchar(64)', 'YES', '<NULL>'
    UNION ALL SELECT 'ai_replan_operation', 'trace_id', 'varchar(64)', 'YES', '<NULL>'
),
`expected_v3_indexes` AS (
    SELECT 'ai_call_log' AS `table_name`, 'idx_ai_call_log_trace' AS `index_name`, 1 AS `non_unique`, 'trace_id' AS `column_list`
    UNION ALL SELECT 'ai_call_log', 'idx_ai_call_log_provider_request', 1, 'provider_request_id'
    UNION ALL SELECT 'ai_call_log', 'idx_ai_call_log_model_time', 1, 'model_name,create_time'
    UNION ALL SELECT 'ai_draft', 'idx_ai_draft_trace', 1, 'trace_id'
    UNION ALL SELECT 'ai_draft_confirm_log', 'uk_ai_confirm_user_draft', 0, 'user_id,draft_id'
    UNION ALL SELECT 'ai_replan_operation', 'idx_ai_replan_operation_trace', 1, 'trace_id'
),
`actual_v3_indexes` AS (
    SELECT
        `table_name`, `index_name`, MIN(`non_unique`) AS `non_unique`,
        GROUP_CONCAT(`column_name` ORDER BY `seq_in_index` SEPARATOR ',') AS `column_list`
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
    GROUP BY `table_name`, `index_name`
),
`v3_post_verify_checks` AS (
    SELECT
        'V3-V-001' AS `check_id`,
        'confirmation archive table present' AS `check_name`,
        ABS(1 - (
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_draft_confirm_log_archive'
        )) AS `violation_count`

    UNION ALL
    SELECT
        'V3-V-002',
        'all V3 column contracts match',
        (
            SELECT COUNT(*)
            FROM `expected_v3_columns` expected
            LEFT JOIN information_schema.columns actual
              ON actual.table_schema = DATABASE()
             AND actual.table_name = expected.table_name
             AND actual.column_name = expected.column_name
            WHERE actual.column_name IS NULL
               OR LOWER(actual.column_type) <> expected.column_type
               OR actual.is_nullable <> expected.is_nullable
               OR COALESCE(CAST(actual.column_default AS CHAR), '<NULL>') <> expected.column_default
        )

    UNION ALL
    SELECT
        'V3-V-003',
        'new confirmation unique key has exact contract',
        (
            SELECT COUNT(*)
            FROM `expected_v3_indexes` expected
            LEFT JOIN `actual_v3_indexes` actual
              ON actual.table_name = expected.table_name
             AND actual.index_name = expected.index_name
            WHERE expected.index_name = 'uk_ai_confirm_user_draft'
              AND (
                  actual.index_name IS NULL
                  OR actual.non_unique <> expected.non_unique
                  OR actual.column_list <> expected.column_list
              )
        )

    UNION ALL
    SELECT
        'V3-V-004',
        'legacy confirmation unique key absent',
        (
            SELECT COUNT(DISTINCT index_name)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_draft_confirm_log'
              AND index_name = 'uk_user_draft_op'
        )

    UNION ALL
    SELECT
        'V3-V-005',
        'all required V3 indexes have exact contracts',
        (
            SELECT COUNT(*)
            FROM `expected_v3_indexes` expected
            LEFT JOIN `actual_v3_indexes` actual
              ON actual.table_name = expected.table_name
             AND actual.index_name = expected.index_name
            WHERE actual.index_name IS NULL
               OR actual.non_unique <> expected.non_unique
               OR actual.column_list <> expected.column_list
        )

    UNION ALL
    SELECT
        'V3-V-006',
        'one live confirmation result per user draft',
        (
            SELECT COUNT(*)
            FROM (
                SELECT `user_id`, `draft_id`
                FROM `ai_draft_confirm_log`
                GROUP BY `user_id`, `draft_id`
                HAVING COUNT(*) > 1
            ) duplicate_confirmation
        )

    UNION ALL
    SELECT
        'V3-V-007',
        'V3 archive contains only equivalent duplicates',
        (
            SELECT COUNT(*)
            FROM `ai_draft_confirm_log_archive`
            WHERE BINARY `migration_version` = BINARY 'V3'
              AND BINARY `archive_reason` <> BINARY 'EQUIVALENT_DUPLICATE'
        )

    UNION ALL
    SELECT
        'V3-V-008',
        'archived confirmation is absent from live table',
        (
            SELECT COUNT(*)
            FROM `ai_draft_confirm_log_archive` archived
            JOIN `ai_draft_confirm_log` live ON live.`id` = archived.`source_log_id`
            WHERE BINARY archived.`migration_version` = BINARY 'V3'
        )

    UNION ALL
    SELECT
        'V3-V-009',
        'archive snapshot column contracts match',
        (
            SELECT
                ABS(11 - COUNT(*))
                + COALESCE(SUM(CASE
                    WHEN column_name IN (
                        'source_log_id', 'user_id', 'draft_id', 'operation_id', 'scene',
                        'create_time', 'archive_reason', 'archived_at', 'migration_version'
                    ) AND is_nullable <> 'NO' THEN 1
                    WHEN column_name IN ('business_id', 'trace_id') AND is_nullable <> 'YES' THEN 1
                    ELSE 0
                  END), 0)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_draft_confirm_log_archive'
        )

    UNION ALL
    SELECT
        'V3-V-010',
        'required V3 check constraints are enforced',
        ABS(4 - (
            SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE()
              AND table_name = 'ai_call_log'
              AND constraint_type = 'CHECK'
              AND enforced = 'YES'
              AND constraint_name IN (
                  'chk_ai_call_log_sanitization_status',
                  'chk_ai_call_log_boolean_flags',
                  'chk_ai_call_log_usage_nonnegative',
                  'chk_ai_call_log_cost_nonnegative'
              )
        ))

    UNION ALL
    SELECT
        'V3-V-011',
        'V3 metadata values remain inside their domains',
        (
            SELECT COUNT(*)
            FROM `ai_call_log`
            WHERE BINARY `request_sanitization_status` NOT IN ('LEGACY_UNKNOWN', 'CLEAN', 'REDACTED', 'BLOCKED')
               OR BINARY `response_sanitization_status` NOT IN ('LEGACY_UNKNOWN', 'CLEAN', 'REDACTED', 'BLOCKED')
               OR BINARY `error_sanitization_status` NOT IN ('LEGACY_UNKNOWN', 'CLEAN', 'REDACTED', 'BLOCKED')
               OR `fallback_used` NOT IN (0, 1)
               OR `degraded` NOT IN (0, 1)
               OR `request_truncated` NOT IN (0, 1)
               OR `response_truncated` NOT IN (0, 1)
               OR `error_truncated` NOT IN (0, 1)
               OR `prompt_tokens` < 0
               OR `completion_tokens` < 0
               OR `total_tokens` < 0
               OR `estimated_cost` < 0
        )

    UNION ALL
    SELECT
        'V3-V-012',
        'draft schema versions are positive',
        (
            SELECT COUNT(*)
            FROM `ai_draft`
            WHERE `schema_version` < 1
        )

    UNION ALL
    SELECT
        'V3-V-013',
        'confirmation integrity remains valid',
        (
            SELECT COUNT(*)
            FROM `ai_draft_confirm_log` confirmation
            LEFT JOIN `ai_draft` draft ON draft.`draft_id` = confirmation.`draft_id`
            WHERE draft.`id` IS NULL
               OR confirmation.`user_id` <> draft.`user_id`
               OR BINARY confirmation.`scene` <> BINARY draft.`scene`
               OR draft.`status` <> 1
        )

    UNION ALL
    SELECT
        'V3-V-014',
        'confirmed draft has live confirmation result',
        (
            SELECT COUNT(*)
            FROM `ai_draft` draft
            LEFT JOIN `ai_draft_confirm_log` confirmation
              ON confirmation.`user_id` = draft.`user_id`
             AND confirmation.`draft_id` = draft.`draft_id`
            WHERE draft.`status` = 1
              AND confirmation.`id` IS NULL
        )
)
SELECT
    `check_id`,
    `check_name`,
    `violation_count`,
    CASE
        WHEN `violation_count` = 0 THEN 'PASS'
        ELSE 'FAIL'
    END AS `status`
FROM `v3_post_verify_checks`
ORDER BY `check_id`;
