-- Stage 2 WP1 one-time historical backfill verification.
-- Run immediately after V2 -> V3, before V3-aware application writes begin.

WITH `v3_legacy_backfill_checks` AS (
    SELECT
        'V3-L-001' AS `check_id`,
        'legacy requested model copied from actual model' AS `check_name`,
        (
            SELECT COUNT(*)
            FROM `ai_call_log`
            WHERE `requested_model` IS NULL
               OR BINARY `requested_model` <> BINARY `model_name`
        ) AS `violation_count`

    UNION ALL
    SELECT
        'V3-L-002',
        'legacy usage and cost remain unknown',
        (
            SELECT COUNT(*)
            FROM `ai_call_log`
            WHERE `prompt_tokens` IS NOT NULL
               OR `completion_tokens` IS NOT NULL
               OR `total_tokens` IS NOT NULL
               OR `price_version` IS NOT NULL
               OR `currency` IS NOT NULL
               OR `estimated_cost` IS NOT NULL
        )

    UNION ALL
    SELECT
        'V3-L-003',
        'legacy AI bodies remain marked unknown',
        (
            SELECT COUNT(*)
            FROM `ai_call_log`
            WHERE BINARY `request_sanitization_status` <> BINARY 'LEGACY_UNKNOWN'
               OR BINARY `response_sanitization_status` <> BINARY 'LEGACY_UNKNOWN'
               OR BINARY `error_sanitization_status` <> BINARY 'LEGACY_UNKNOWN'
        )

    UNION ALL
    SELECT
        'V3-L-004',
        'legacy trace and hashes remain null',
        (
            SELECT COUNT(*)
            FROM `ai_call_log`
            WHERE `trace_id` IS NOT NULL
               OR `request_hash` IS NOT NULL
               OR `response_hash` IS NOT NULL
               OR `error_hash` IS NOT NULL
        )

    UNION ALL
    SELECT
        'V3-L-005',
        'legacy drafts use schema version one without synthetic trace',
        (
            SELECT COUNT(*)
            FROM `ai_draft`
            WHERE `schema_version` <> 1
               OR `trace_id` IS NOT NULL
        )
)
SELECT
    `check_id`,
    `check_name`,
    `violation_count`,
    CASE WHEN `violation_count` = 0 THEN 'PASS' ELSE 'FAIL' END AS `status`
FROM `v3_legacy_backfill_checks`
ORDER BY `check_id`;
