-- Stage 2 WP1 V3 preflight. Read-only, deterministic, single result set.
-- Run against the frozen V2 schema before applying V3.

WITH `v3_preflight_checks` AS (
    SELECT
        'V3-P-001' AS `check_id`,
        'required V2 AI tables present' AS `check_name`,
        ABS(4 - (
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                  'ai_call_log', 'ai_draft', 'ai_draft_confirm_log',
                  'ai_replan_operation'
              )
        )) AS `finding_count`,
        'BLOCKING_INTEGRITY_ERROR' AS `failure_class`,
        'ZERO_REQUIRED' AS `expectation`

    UNION ALL
    SELECT
        'V3-P-002',
        'V3 archive table absent',
        (
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_draft_confirm_log_archive'
        ),
        'BLOCKING_INTEGRITY_ERROR',
        'ZERO_REQUIRED'

    UNION ALL
    SELECT
        'V3-P-003',
        'V3 additive columns absent',
        (
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND (
                  (table_name = 'ai_call_log' AND column_name IN (
                      'requested_model', 'finish_reason', 'provider_request_id',
                      'prompt_tokens', 'completion_tokens', 'total_tokens',
                      'price_version', 'currency', 'estimated_cost', 'trace_id',
                      'failure_type', 'fallback_used', 'fallback_reason', 'degraded',
                      'request_sanitization_status', 'response_sanitization_status',
                      'error_sanitization_status', 'request_truncated',
                      'response_truncated', 'error_truncated', 'request_hash',
                      'response_hash', 'error_hash'
                  ))
                  OR (table_name = 'ai_draft' AND column_name IN ('schema_version', 'trace_id'))
                  OR (table_name = 'ai_draft_confirm_log' AND column_name = 'trace_id')
                  OR (table_name = 'ai_replan_operation' AND column_name = 'trace_id')
              )
        ),
        'BLOCKING_INTEGRITY_ERROR',
        'ZERO_REQUIRED'

    UNION ALL
    SELECT
        'V3-P-004',
        'legacy confirmation unique key has exact contract',
        CASE WHEN COALESCE((
            SELECT CONCAT(
                MIN(`non_unique`), ':',
                GROUP_CONCAT(`column_name` ORDER BY `seq_in_index` SEPARATOR ',')
            )
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_draft_confirm_log'
              AND index_name = 'uk_user_draft_op'
        ), '') = '0:user_id,draft_id,operation_id' THEN 0 ELSE 1 END,
        'BLOCKING_INTEGRITY_ERROR',
        'ZERO_REQUIRED'

    UNION ALL
    SELECT
        'V3-P-005',
        'V3 confirmation unique key absent',
        (
            SELECT COUNT(DISTINCT index_name)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_draft_confirm_log'
              AND index_name = 'uk_ai_confirm_user_draft'
        ),
        'BLOCKING_INTEGRITY_ERROR',
        'ZERO_REQUIRED'

    UNION ALL
    SELECT
        'V3-P-010',
        'equivalent confirmation duplicate groups',
        (
            SELECT COUNT(*)
            FROM (
                SELECT `user_id`, `draft_id`
                FROM `ai_draft_confirm_log`
                GROUP BY `user_id`, `draft_id`
                HAVING COUNT(*) > 1
                   AND COUNT(DISTINCT CAST(`scene` AS BINARY)) = 1
                   AND COUNT(DISTINCT COALESCE(CAST(`business_id` AS CHAR), '<NULL>')) = 1
            ) equivalent_duplicates
        ),
        'REPAIRABLE_EQUIVALENT_DUPLICATE',
        'INFORMATIONAL'

    UNION ALL
    SELECT
        'V3-P-011',
        'conflicting confirmation duplicate groups',
        (
            SELECT COUNT(*)
            FROM (
                SELECT `user_id`, `draft_id`
                FROM `ai_draft_confirm_log`
                GROUP BY `user_id`, `draft_id`
                HAVING COUNT(*) > 1
                   AND (
                       COUNT(DISTINCT CAST(`scene` AS BINARY)) > 1
                       OR COUNT(DISTINCT COALESCE(CAST(`business_id` AS CHAR), '<NULL>')) > 1
                   )
            ) conflicting_duplicates
        ),
        'BLOCKING_CONFLICT',
        'ZERO_REQUIRED'

    UNION ALL
    SELECT
        'V3-P-012',
        'confirmation log has draft',
        (
            SELECT COUNT(*)
            FROM `ai_draft_confirm_log` confirmation
            LEFT JOIN `ai_draft` draft ON draft.`draft_id` = confirmation.`draft_id`
            WHERE draft.`id` IS NULL
        ),
        'BLOCKING_INTEGRITY_ERROR',
        'ZERO_REQUIRED'

    UNION ALL
    SELECT
        'V3-P-013',
        'confirmation owner matches draft',
        (
            SELECT COUNT(*)
            FROM `ai_draft_confirm_log` confirmation
            JOIN `ai_draft` draft ON draft.`draft_id` = confirmation.`draft_id`
            WHERE confirmation.`user_id` <> draft.`user_id`
        ),
        'BLOCKING_INTEGRITY_ERROR',
        'ZERO_REQUIRED'

    UNION ALL
    SELECT
        'V3-P-014',
        'confirmation scene matches draft',
        (
            SELECT COUNT(*)
            FROM `ai_draft_confirm_log` confirmation
            JOIN `ai_draft` draft ON draft.`draft_id` = confirmation.`draft_id`
            WHERE BINARY confirmation.`scene` <> BINARY draft.`scene`
        ),
        'BLOCKING_INTEGRITY_ERROR',
        'ZERO_REQUIRED'

    UNION ALL
    SELECT
        'V3-P-015',
        'confirmation log belongs to confirmed draft',
        (
            SELECT COUNT(*)
            FROM `ai_draft_confirm_log` confirmation
            JOIN `ai_draft` draft ON draft.`draft_id` = confirmation.`draft_id`
            WHERE draft.`status` <> 1
        ),
        'BLOCKING_INTEGRITY_ERROR',
        'ZERO_REQUIRED'

    UNION ALL
    SELECT
        'V3-P-016',
        'confirmed draft has confirmation result',
        (
            SELECT COUNT(*)
            FROM `ai_draft` draft
            LEFT JOIN `ai_draft_confirm_log` confirmation
              ON confirmation.`user_id` = draft.`user_id`
             AND confirmation.`draft_id` = draft.`draft_id`
            WHERE draft.`status` = 1
              AND confirmation.`id` IS NULL
        ),
        'BLOCKING_INTEGRITY_ERROR',
        'ZERO_REQUIRED'

    UNION ALL
    SELECT
        'V3-P-017',
        'draft status is recognized',
        (
            SELECT COUNT(*)
            FROM `ai_draft`
            WHERE `status` NOT IN (0, 1, 2, 3)
        ),
        'BLOCKING_INTEGRITY_ERROR',
        'ZERO_REQUIRED'
)
SELECT
    `check_id`,
    `check_name`,
    `finding_count`,
    CASE
        WHEN `expectation` = 'INFORMATIONAL' AND `finding_count` > 0
            THEN `failure_class`
        WHEN `expectation` = 'INFORMATIONAL'
            THEN 'CLEAN'
        WHEN `finding_count` = 0
            THEN 'CLEAN'
        ELSE `failure_class`
    END AS `classification`,
    CASE
        WHEN `expectation` = 'INFORMATIONAL' THEN 'PASS'
        WHEN `finding_count` = 0 THEN 'PASS'
        ELSE 'FAIL'
    END AS `status`
FROM `v3_preflight_checks`
ORDER BY `check_id`;
