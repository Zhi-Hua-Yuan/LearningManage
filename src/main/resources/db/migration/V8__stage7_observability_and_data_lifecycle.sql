-- Flyway V8: Stage 7 production observability and resumable data lifecycle.
-- V1-V7 are immutable. Cleanup metadata never stores AI or business bodies.

-- Abort before persistent DDL when the V7 prerequisites are missing or any V8
-- artifact already exists. This keeps a manually modified/partially applied
-- database out of Flyway's normal forward path.
CREATE TEMPORARY TABLE `_v8_stage7_guard` (`id` tinyint NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB;
CREATE TEMPORARY TABLE `_v8_stage7_abort` (`id` tinyint NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB;
INSERT INTO `_v8_stage7_abort` (`id`) VALUES (1);

INSERT INTO `_v8_stage7_guard` (`id`)
SELECT 1
WHERE (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('ai_call_log', 'ai_draft', 'ai_rag_result',
                         'ai_analysis_report', 'ai_agent_run', 'ai_knowledge_index_event')
) <> 6
OR EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('ai_data_cleanup_lock', 'ai_data_cleanup_run',
                         'ai_data_cleanup_item', 'ai_admin_operation_log')
)
OR EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'ai_call_log' AND column_name = 'body_purged_at')
        OR (table_name = 'ai_draft' AND column_name = 'payload_purged_at')
        OR (table_name = 'ai_rag_result' AND column_name = 'body_purged_at')
        OR (table_name = 'ai_analysis_report' AND column_name = 'content_purged_at'))
);

INSERT INTO `_v8_stage7_abort` (`id`) SELECT `id` FROM `_v8_stage7_guard`;
DROP TEMPORARY TABLE `_v8_stage7_guard`;
DROP TEMPORARY TABLE `_v8_stage7_abort`;

CREATE TABLE `ai_data_cleanup_lock` (
  `id` tinyint NOT NULL,
  `owner_run_id` varchar(64) DEFAULT NULL,
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_cleanup_singleton_lock` CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Singleton transaction lock serializing cleanup submission';

INSERT INTO `ai_data_cleanup_lock` (`id`) VALUES (1);

CREATE TABLE `ai_data_cleanup_run` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `run_id` varchar(64) NOT NULL,
  `client_request_id` varchar(64) NOT NULL,
  `initiator_user_id` bigint DEFAULT NULL COMMENT 'Null only for scheduled runs',
  `trigger_type` varchar(16) NOT NULL,
  `policy_version` varchar(32) NOT NULL,
  `resource_hash` char(64) NOT NULL,
  `approved_dry_run_id` bigint DEFAULT NULL,
  `dry_run` tinyint NOT NULL DEFAULT 1,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `worker_id` varchar(96) DEFAULT NULL,
  `execution_token` varchar(64) DEFAULT NULL,
  `lease_until` datetime(3) DEFAULT NULL,
  `heartbeat_at` datetime(3) DEFAULT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `canceled_at` datetime(3) DEFAULT NULL,
  `cancel_requested_at` datetime(3) DEFAULT NULL,
  `scanned_count` bigint NOT NULL DEFAULT 0,
  `estimated_count` bigint NOT NULL DEFAULT 0,
  `affected_count` bigint NOT NULL DEFAULT 0,
  `failure_count` bigint NOT NULL DEFAULT 0,
  `error_summary` varchar(1000) DEFAULT NULL,
  `trace_id` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cleanup_run_id` (`run_id`),
  UNIQUE KEY `uk_cleanup_user_request` (`initiator_user_id`, `client_request_id`),
  KEY `idx_cleanup_claim` (`status`, `lease_until`, `create_time`),
  KEY `idx_cleanup_time` (`create_time`, `status`),
  KEY `idx_cleanup_dry_run` (`policy_version`, `resource_hash`, `dry_run`, `status`, `finished_at`),
  KEY `idx_cleanup_trace` (`trace_id`),
  CONSTRAINT `chk_cleanup_trigger` CHECK (BINARY `trigger_type` IN ('MANUAL', 'SCHEDULED')),
  CONSTRAINT `chk_cleanup_status` CHECK (
      BINARY `status` IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELED')
  ),
  CONSTRAINT `chk_cleanup_boolean` CHECK (`dry_run` IN (0, 1)),
  CONSTRAINT `chk_cleanup_resource_hash` CHECK (CHAR_LENGTH(`resource_hash`) = 64),
  CONSTRAINT `chk_cleanup_counts` CHECK (
      `scanned_count` >= 0 AND `estimated_count` >= 0
      AND `affected_count` >= 0 AND `failure_count` >= 0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Durable, leased and resumable data-cleanup run';

CREATE TABLE `ai_data_cleanup_item` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `run_id` varchar(64) NOT NULL,
  `resource_type` varchar(32) NOT NULL,
  `cutoff_time` datetime(3) NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `cursor_id` bigint NOT NULL DEFAULT 0,
  `scanned_count` bigint NOT NULL DEFAULT 0,
  `estimated_count` bigint NOT NULL DEFAULT 0,
  `redacted_count` bigint NOT NULL DEFAULT 0,
  `deleted_count` bigint NOT NULL DEFAULT 0,
  `error_summary` varchar(1000) DEFAULT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cleanup_item_resource` (`run_id`, `resource_type`),
  KEY `idx_cleanup_item_run_status` (`run_id`, `status`, `id`),
  CONSTRAINT `chk_cleanup_item_resource` CHECK (BINARY `resource_type` IN (
      'AI_CALL_BODY', 'AI_CALL_METADATA', 'RAG_RESULT_BODY', 'RAG_HISTORY',
      'AGENT_HISTORY', 'KNOWLEDGE_EVENT', 'DRAFT_PAYLOAD', 'DELETED_REPORT',
      'ADMIN_AUDIT'
  )),
  CONSTRAINT `chk_cleanup_item_status` CHECK (
      BINARY `status` IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')
  ),
  CONSTRAINT `chk_cleanup_item_counts` CHECK (
      `cursor_id` >= 0 AND `scanned_count` >= 0 AND `estimated_count` >= 0
      AND `redacted_count` >= 0 AND `deleted_count` >= 0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Per-resource progress for a cleanup run';

CREATE TABLE `ai_admin_operation_log` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `operator_user_id` bigint NOT NULL,
  `operation_type` varchar(64) NOT NULL,
  `target_type` varchar(32) NOT NULL,
  `target_id` varchar(64) DEFAULT NULL,
  `argument_summary` varchar(1000) DEFAULT NULL,
  `result_summary` varchar(1000) DEFAULT NULL,
  `status` varchar(24) NOT NULL,
  `trace_id` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_admin_operation_actor_time` (`operator_user_id`, `create_time`),
  KEY `idx_admin_operation_type_time` (`operation_type`, `create_time`),
  KEY `idx_admin_operation_trace` (`trace_id`),
  CONSTRAINT `chk_admin_operation_status` CHECK (BINARY `status` IN ('SUCCEEDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Sanitized audit of privileged AI operations';

ALTER TABLE `ai_call_log`
    ADD COLUMN `body_purged_at` datetime(3) DEFAULT NULL AFTER `error_hash`,
    ADD KEY `idx_ai_call_body_cleanup` (`body_purged_at`, `create_time`, `id`);

ALTER TABLE `ai_draft`
    MODIFY COLUMN `payload_json` longtext NULL COMMENT 'Draft payload; null after retention purge',
    ADD COLUMN `payload_purged_at` datetime(3) DEFAULT NULL AFTER `payload_json`,
    ADD KEY `idx_ai_draft_payload_cleanup` (`status`, `payload_purged_at`, `update_time`, `id`);

ALTER TABLE `ai_rag_result`
    MODIFY COLUMN `answer_text` mediumtext NULL,
    ADD COLUMN `body_purged_at` datetime(3) DEFAULT NULL AFTER `answer_text`,
    ADD KEY `idx_rag_result_body_cleanup` (`body_purged_at`, `expires_at`, `id`);

ALTER TABLE `ai_analysis_report`
    MODIFY COLUMN `manager_summary` mediumtext NULL,
    MODIFY COLUMN `public_summary` mediumtext NULL,
    MODIFY COLUMN `member_metrics_json` longtext NULL,
    MODIFY COLUMN `recommendations_json` longtext NULL,
    ADD COLUMN `content_purged_at` datetime(3) DEFAULT NULL AFTER `recommendations_json`,
    ADD KEY `idx_report_content_cleanup` (`is_delete`, `content_purged_at`, `deleted_at`, `id`);
