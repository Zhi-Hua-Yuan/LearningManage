-- Flyway V3: Stage 2 AI invocation governance database foundation.
--
-- This migration intentionally changes no public API or runtime AI behavior.
-- It adds forward-compatible metadata, archives only equivalent confirmation
-- duplicates, and makes (user_id, draft_id) the database idempotency boundary.

-- ============================================================================
-- 1. Abort before persistent DDL when confirmation history is ambiguous.
-- ============================================================================

CREATE TEMPORARY TABLE `_v3_ai_confirmation_guard` (
  `id` tinyint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

CREATE TEMPORARY TABLE `_v3_ai_confirmation_abort` (
  `id` tinyint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

INSERT INTO `_v3_ai_confirmation_abort` (`id`) VALUES (1);

INSERT INTO `_v3_ai_confirmation_guard` (`id`)
SELECT 1
FROM (
    SELECT 1 AS `blocking_violation`
    WHERE (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'ai_call_log', 'ai_draft', 'ai_draft_confirm_log',
              'ai_replan_operation'
          )
    ) <> 4

    UNION ALL

    SELECT 1
    WHERE EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'ai_draft_confirm_log_archive'
    )

    UNION ALL

    SELECT 1
    WHERE EXISTS (
        SELECT 1
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
    )

    UNION ALL

    SELECT 1
    WHERE COALESCE((
        SELECT CONCAT(
            MIN(`non_unique`), ':',
            GROUP_CONCAT(`column_name` ORDER BY `seq_in_index` SEPARATOR ',')
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'ai_draft_confirm_log'
          AND index_name = 'uk_user_draft_op'
    ), '') <> '0:user_id,draft_id,operation_id'

    UNION ALL

    SELECT 1
    WHERE EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'ai_draft_confirm_log'
          AND index_name = 'uk_ai_confirm_user_draft'
    )

    UNION ALL

    SELECT 1
    FROM `ai_draft_confirm_log`
    GROUP BY `user_id`, `draft_id`
    HAVING COUNT(*) > 1
       AND (
           COUNT(DISTINCT CAST(`scene` AS BINARY)) > 1
           OR COUNT(DISTINCT COALESCE(CAST(`business_id` AS CHAR), '<NULL>')) > 1
       )

    UNION ALL

    SELECT 1
    FROM `ai_draft_confirm_log` confirmation
    LEFT JOIN `ai_draft` draft
      ON draft.`draft_id` = confirmation.`draft_id`
    WHERE draft.`id` IS NULL

    UNION ALL

    SELECT 1
    FROM `ai_draft_confirm_log` confirmation
    JOIN `ai_draft` draft
      ON draft.`draft_id` = confirmation.`draft_id`
    WHERE confirmation.`user_id` <> draft.`user_id`
       OR BINARY confirmation.`scene` <> BINARY draft.`scene`
       OR draft.`status` <> 1

    UNION ALL

    SELECT 1
    FROM `ai_draft` draft
    LEFT JOIN `ai_draft_confirm_log` confirmation
      ON confirmation.`user_id` = draft.`user_id`
     AND confirmation.`draft_id` = draft.`draft_id`
    WHERE draft.`status` = 1
      AND confirmation.`id` IS NULL

    UNION ALL

    SELECT 1
    FROM `ai_draft`
    WHERE `status` NOT IN (0, 1, 2, 3)
) blocking_findings
LIMIT 1;

-- Copying a guard row into an already occupied abort table deliberately fails
-- only when a blocker was found. Separate temporary tables avoid MySQL 1137.
INSERT INTO `_v3_ai_confirmation_abort` (`id`)
SELECT `id`
FROM `_v3_ai_confirmation_guard`;

DROP TEMPORARY TABLE `_v3_ai_confirmation_guard`;
DROP TEMPORARY TABLE `_v3_ai_confirmation_abort`;

-- ============================================================================
-- 2. Preserve equivalent duplicate confirmation records before deduplication.
-- ============================================================================

CREATE TABLE `ai_draft_confirm_log_archive` (
  `source_log_id` bigint NOT NULL COMMENT '原确认日志主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `draft_id` varchar(64) NOT NULL COMMENT '草稿ID',
  `operation_id` varchar(64) NOT NULL COMMENT '原幂等操作ID',
  `scene` varchar(32) NOT NULL COMMENT '场景',
  `business_id` bigint DEFAULT NULL COMMENT '原落库业务主键ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '原确认链路Trace；V3前记录为空',
  `create_time` datetime NOT NULL COMMENT '原记录创建时间',
  `archive_reason` varchar(64) NOT NULL COMMENT '归档原因',
  `archived_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  `migration_version` varchar(16) NOT NULL COMMENT '执行归档的迁移版本',
  PRIMARY KEY (`source_log_id`),
  KEY `idx_ai_confirm_archive_user_draft` (`user_id`, `draft_id`),
  KEY `idx_ai_confirm_archive_time` (`archived_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='AI确认等价重复日志归档表';

INSERT INTO `ai_draft_confirm_log_archive` (
    `source_log_id`, `user_id`, `draft_id`, `operation_id`, `scene`,
    `business_id`, `trace_id`, `create_time`, `archive_reason`,
    `archived_at`, `migration_version`
)
SELECT
    candidate.`id`, candidate.`user_id`, candidate.`draft_id`,
    candidate.`operation_id`, candidate.`scene`, candidate.`business_id`,
    NULL, candidate.`create_time`, 'EQUIVALENT_DUPLICATE',
    CURRENT_TIMESTAMP, 'V3'
FROM `ai_draft_confirm_log` candidate
JOIN (
    SELECT `user_id`, `draft_id`
    FROM `ai_draft_confirm_log`
    GROUP BY `user_id`, `draft_id`
    HAVING COUNT(*) > 1
       AND COUNT(DISTINCT CAST(`scene` AS BINARY)) = 1
       AND COUNT(DISTINCT COALESCE(CAST(`business_id` AS CHAR), '<NULL>')) = 1
) equivalent_group
  ON equivalent_group.`user_id` = candidate.`user_id`
 AND equivalent_group.`draft_id` = candidate.`draft_id`
WHERE EXISTS (
    SELECT 1
    FROM `ai_draft_confirm_log` earlier
    WHERE earlier.`user_id` = candidate.`user_id`
      AND earlier.`draft_id` = candidate.`draft_id`
      AND (
          earlier.`create_time` < candidate.`create_time`
          OR (
              earlier.`create_time` = candidate.`create_time`
              AND earlier.`id` < candidate.`id`
          )
      )
);

-- Verify that every record selected for deletion has first been archived.
CREATE TEMPORARY TABLE `_v3_ai_archive_count_guard` (
  `id` tinyint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

CREATE TEMPORARY TABLE `_v3_ai_archive_count_abort` (
  `id` tinyint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

INSERT INTO `_v3_ai_archive_count_abort` (`id`) VALUES (1);

INSERT INTO `_v3_ai_archive_count_guard` (`id`)
SELECT 1
WHERE (
    SELECT COUNT(*)
    FROM `ai_draft_confirm_log_archive`
    WHERE BINARY `migration_version` = BINARY 'V3'
) <> (
    SELECT COALESCE(SUM(equivalent_group.`record_count` - 1), 0)
    FROM (
        SELECT COUNT(*) AS `record_count`
        FROM `ai_draft_confirm_log`
        GROUP BY `user_id`, `draft_id`
        HAVING COUNT(*) > 1
           AND COUNT(DISTINCT CAST(`scene` AS BINARY)) = 1
           AND COUNT(DISTINCT COALESCE(CAST(`business_id` AS CHAR), '<NULL>')) = 1
    ) equivalent_group
);

INSERT INTO `_v3_ai_archive_count_abort` (`id`)
SELECT `id`
FROM `_v3_ai_archive_count_guard`;

DROP TEMPORARY TABLE `_v3_ai_archive_count_guard`;
DROP TEMPORARY TABLE `_v3_ai_archive_count_abort`;

DELETE confirmation
FROM `ai_draft_confirm_log` confirmation
JOIN `ai_draft_confirm_log_archive` archived
  ON archived.`source_log_id` = confirmation.`id`
 AND BINARY archived.`migration_version` = BINARY 'V3';

-- Create the stronger constraint before removing the legacy one. If unexpected
-- duplicates remain, migration stops while the old unique key still exists.
CREATE UNIQUE INDEX `uk_ai_confirm_user_draft`
    ON `ai_draft_confirm_log` (`user_id`, `draft_id`);

ALTER TABLE `ai_draft_confirm_log`
    DROP INDEX `uk_user_draft_op`;

-- ============================================================================
-- 3. Add AI call observability, sanitization, usage and cost metadata.
-- ============================================================================

ALTER TABLE `ai_call_log`
    MODIFY COLUMN `model_name` varchar(64) NOT NULL
        COMMENT '实际执行的模型名称',
    ADD COLUMN `requested_model` varchar(64) DEFAULT NULL
        COMMENT '调用方请求的模型名称' AFTER `model_name`,
    ADD COLUMN `finish_reason` varchar(32) DEFAULT NULL
        COMMENT '供应商结束原因' AFTER `requested_model`,
    ADD COLUMN `provider_request_id` varchar(128) DEFAULT NULL
        COMMENT '供应商请求ID' AFTER `finish_reason`,
    ADD COLUMN `prompt_tokens` bigint DEFAULT NULL
        COMMENT '输入Token；未知时为空' AFTER `provider_request_id`,
    ADD COLUMN `completion_tokens` bigint DEFAULT NULL
        COMMENT '输出Token；未知时为空' AFTER `prompt_tokens`,
    ADD COLUMN `total_tokens` bigint DEFAULT NULL
        COMMENT '总Token；未知时为空' AFTER `completion_tokens`,
    ADD COLUMN `price_version` varchar(64) DEFAULT NULL
        COMMENT '价格配置版本' AFTER `total_tokens`,
    ADD COLUMN `currency` varchar(8) DEFAULT NULL
        COMMENT '估算成本币种' AFTER `price_version`,
    ADD COLUMN `estimated_cost` decimal(20,8) DEFAULT NULL
        COMMENT '估算成本；未知时为空' AFTER `currency`,
    ADD COLUMN `trace_id` varchar(64) DEFAULT NULL
        COMMENT '调用链Trace ID' AFTER `estimated_cost`,
    ADD COLUMN `failure_type` varchar(32) DEFAULT NULL
        COMMENT '规范化失败类型' AFTER `trace_id`,
    ADD COLUMN `fallback_used` tinyint NOT NULL DEFAULT 0
        COMMENT '是否使用兜底模型' AFTER `failure_type`,
    ADD COLUMN `fallback_reason` varchar(64) DEFAULT NULL
        COMMENT '模型回退原因' AFTER `fallback_used`,
    ADD COLUMN `degraded` tinyint NOT NULL DEFAULT 0
        COMMENT '是否进入规则降级' AFTER `fallback_reason`,
    ADD COLUMN `request_sanitization_status` varchar(24) NOT NULL DEFAULT 'LEGACY_UNKNOWN'
        COMMENT '请求正文脱敏状态' AFTER `degraded`,
    ADD COLUMN `response_sanitization_status` varchar(24) NOT NULL DEFAULT 'LEGACY_UNKNOWN'
        COMMENT '响应正文脱敏状态' AFTER `request_sanitization_status`,
    ADD COLUMN `error_sanitization_status` varchar(24) NOT NULL DEFAULT 'LEGACY_UNKNOWN'
        COMMENT '错误正文脱敏状态' AFTER `response_sanitization_status`,
    ADD COLUMN `request_truncated` tinyint NOT NULL DEFAULT 0
        COMMENT '请求正文是否截断' AFTER `error_sanitization_status`,
    ADD COLUMN `response_truncated` tinyint NOT NULL DEFAULT 0
        COMMENT '响应正文是否截断' AFTER `request_truncated`,
    ADD COLUMN `error_truncated` tinyint NOT NULL DEFAULT 0
        COMMENT '错误正文是否截断' AFTER `response_truncated`,
    ADD COLUMN `request_hash` char(64) DEFAULT NULL
        COMMENT '脱敏后请求正文SHA-256' AFTER `error_truncated`,
    ADD COLUMN `response_hash` char(64) DEFAULT NULL
        COMMENT '脱敏后响应正文SHA-256' AFTER `request_hash`,
    ADD COLUMN `error_hash` char(64) DEFAULT NULL
        COMMENT '脱敏后错误正文SHA-256' AFTER `response_hash`,
    ADD KEY `idx_ai_call_log_trace` (`trace_id`),
    ADD KEY `idx_ai_call_log_provider_request` (`provider_request_id`),
    ADD KEY `idx_ai_call_log_model_time` (`model_name`, `create_time`),
    ADD CONSTRAINT `chk_ai_call_log_sanitization_status` CHECK (
        BINARY `request_sanitization_status` IN ('LEGACY_UNKNOWN', 'CLEAN', 'REDACTED', 'BLOCKED')
        AND BINARY `response_sanitization_status` IN ('LEGACY_UNKNOWN', 'CLEAN', 'REDACTED', 'BLOCKED')
        AND BINARY `error_sanitization_status` IN ('LEGACY_UNKNOWN', 'CLEAN', 'REDACTED', 'BLOCKED')
    ),
    ADD CONSTRAINT `chk_ai_call_log_boolean_flags` CHECK (
        `fallback_used` IN (0, 1)
        AND `degraded` IN (0, 1)
        AND `request_truncated` IN (0, 1)
        AND `response_truncated` IN (0, 1)
        AND `error_truncated` IN (0, 1)
    ),
    ADD CONSTRAINT `chk_ai_call_log_usage_nonnegative` CHECK (
        (`prompt_tokens` IS NULL OR `prompt_tokens` >= 0)
        AND (`completion_tokens` IS NULL OR `completion_tokens` >= 0)
        AND (`total_tokens` IS NULL OR `total_tokens` >= 0)
    ),
    ADD CONSTRAINT `chk_ai_call_log_cost_nonnegative` CHECK (
        `estimated_cost` IS NULL OR `estimated_cost` >= 0
    );

UPDATE `ai_call_log`
SET `requested_model` = `model_name`
WHERE `requested_model` IS NULL;

-- ============================================================================
-- 4. Add draft and operation schema/trace metadata for later work packages.
-- ============================================================================

ALTER TABLE `ai_draft`
    ADD COLUMN `schema_version` int NOT NULL DEFAULT 1
        COMMENT '草稿Payload Schema版本' AFTER `scene`,
    ADD COLUMN `trace_id` varchar(64) DEFAULT NULL
        COMMENT '草稿生成链路Trace ID' AFTER `input_hash`,
    ADD KEY `idx_ai_draft_trace` (`trace_id`);

ALTER TABLE `ai_draft_confirm_log`
    ADD COLUMN `trace_id` varchar(64) DEFAULT NULL
        COMMENT '确认请求Trace ID' AFTER `business_id`;

ALTER TABLE `ai_replan_operation`
    ADD COLUMN `trace_id` varchar(64) DEFAULT NULL
        COMMENT '重排生成链路Trace ID' AFTER `project_id`,
    ADD KEY `idx_ai_replan_operation_trace` (`trace_id`);
