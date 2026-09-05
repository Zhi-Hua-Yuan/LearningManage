-- Flyway V4: Stage 4 transactional outbox and knowledge-index metadata.
--
-- MySQL remains the source of business truth. These tables contain queue,
-- lease, hash, and reconciliation metadata only; no source body or vector is
-- persisted here.

-- ============================================================================
-- 1. Abort before persistent DDL if any V4-owned table already exists.
-- ============================================================================

CREATE TEMPORARY TABLE `_v4_knowledge_guard` (
  `id` tinyint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

CREATE TEMPORARY TABLE `_v4_knowledge_abort` (
  `id` tinyint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

INSERT INTO `_v4_knowledge_abort` (`id`) VALUES (1);

INSERT INTO `_v4_knowledge_guard` (`id`)
SELECT 1
WHERE EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'ai_knowledge_index_event',
          'ai_knowledge_source_lock',
          'ai_knowledge_document',
          'ai_knowledge_backfill_run'
      )
);

-- Copying a blocker row into the occupied abort table intentionally fails.
INSERT INTO `_v4_knowledge_abort` (`id`)
SELECT `id`
FROM `_v4_knowledge_guard`;

DROP TEMPORARY TABLE `_v4_knowledge_guard`;
DROP TEMPORARY TABLE `_v4_knowledge_abort`;

-- ============================================================================
-- 2. Durable transaction outbox.
-- ============================================================================

CREATE TABLE `ai_knowledge_index_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Monotonic outbox/event ID',
  `source_type` varchar(32) NOT NULL COMMENT 'TASK/WEEKLY_REVIEW',
  `source_id` bigint NOT NULL COMMENT 'Business source ID',
  `event_type` varchar(32) NOT NULL COMMENT 'Reconciliation trigger reason',
  `status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'Queue state',
  `attempt_count` int NOT NULL DEFAULT 0 COMMENT 'Completed failed attempts',
  `next_attempt_at` datetime(3) DEFAULT NULL COMMENT 'Earliest retry time',
  `claimed_by` varchar(64) DEFAULT NULL COMMENT 'Worker instance ID',
  `claim_token` char(36) DEFAULT NULL COMMENT 'Fencing token for this claim',
  `claimed_at` datetime(3) DEFAULT NULL,
  `lease_until` datetime(3) DEFAULT NULL,
  `backfill_run_id` bigint DEFAULT NULL COMMENT 'Optional creating backfill run',
  `failure_type` varchar(32) DEFAULT NULL COMMENT 'Sanitized normalized failure type',
  `last_error` varchar(1000) DEFAULT NULL COMMENT 'Sanitized and truncated error',
  `trace_id` varchar(64) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_kie_ready` (`status`, `next_attempt_at`, `id`),
  KEY `idx_kie_lease` (`status`, `lease_until`),
  KEY `idx_kie_source` (`source_type`, `source_id`, `status`),
  KEY `idx_kie_backfill` (`backfill_run_id`, `status`),
  KEY `idx_kie_trace` (`trace_id`),
  CONSTRAINT `chk_kie_source_type` CHECK (
      BINARY `source_type` IN ('TASK', 'WEEKLY_REVIEW')
  ),
  CONSTRAINT `chk_kie_event_type` CHECK (
      BINARY `event_type` IN (
          'SOURCE_CHANGED', 'SOURCE_DELETED', 'ACCESS_CHANGED', 'REBUILD'
      )
  ),
  CONSTRAINT `chk_kie_status` CHECK (
      BINARY `status` IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'SUCCESS', 'DEAD')
  ),
  CONSTRAINT `chk_kie_attempt` CHECK (`attempt_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Transactional outbox for knowledge-source reconciliation';

-- ============================================================================
-- 3. Per-source distributed worker lease.
-- ============================================================================

CREATE TABLE `ai_knowledge_source_lock` (
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint NOT NULL,
  `owner_token` char(36) DEFAULT NULL COMMENT 'Current fencing token',
  `lease_until` datetime(3) DEFAULT NULL,
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`source_type`, `source_id`),
  KEY `idx_ksl_lease` (`lease_until`),
  CONSTRAINT `chk_ksl_source_type` CHECK (
      BINARY `source_type` IN ('TASK', 'WEEKLY_REVIEW')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Renewable lease serializing workers for one knowledge source';

-- ============================================================================
-- 4. Logical knowledge-document reconciliation state.
-- ============================================================================

CREATE TABLE `ai_knowledge_document` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `document_key` varchar(255) NOT NULL COMMENT 'Stable logical document key',
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `team_id` bigint DEFAULT NULL,
  `owner_user_id` bigint NOT NULL,
  `visibility_type` varchar(16) NOT NULL COMMENT 'PRIVATE/TEAM',
  `content_hash` char(64) NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `indexed_content_hash` char(64) DEFAULT NULL,
  `indexed_payload_hash` char(64) DEFAULT NULL,
  `normalizer_version` varchar(32) NOT NULL,
  `chunking_version` varchar(32) NOT NULL,
  `embedding_model` varchar(64) NOT NULL,
  `embedding_dimension` int NOT NULL,
  `chunk_count` int NOT NULL DEFAULT 0,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `skip_reason` varchar(64) DEFAULT NULL,
  `worker_token` char(36) DEFAULT NULL COMMENT 'Latest writer fencing token',
  `last_event_id` bigint DEFAULT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `indexed_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kd_document_key` (`document_key`),
  KEY `idx_kd_source` (`source_type`, `source_id`),
  KEY `idx_kd_project_visibility` (`project_id`, `visibility_type`),
  KEY `idx_kd_team_visibility` (`team_id`, `visibility_type`),
  KEY `idx_kd_owner_visibility` (`owner_user_id`, `visibility_type`),
  KEY `idx_kd_status_time` (`status`, `update_time`),
  CONSTRAINT `chk_kd_source_type` CHECK (
      BINARY `source_type` IN ('TASK', 'WEEKLY_REVIEW')
  ),
  CONSTRAINT `chk_kd_visibility` CHECK (
      BINARY `visibility_type` IN ('PRIVATE', 'TEAM')
  ),
  CONSTRAINT `chk_kd_team_scope` CHECK (
      (BINARY `visibility_type` = 'PRIVATE')
      OR (BINARY `visibility_type` = 'TEAM' AND `team_id` IS NOT NULL)
  ),
  CONSTRAINT `chk_kd_status` CHECK (
      BINARY `status` IN (
          'PENDING', 'INDEXING', 'INDEXED', 'FAILED', 'SKIPPED', 'DELETED'
      )
  ),
  CONSTRAINT `chk_kd_hashes` CHECK (
      CHAR_LENGTH(`content_hash`) = 64 AND CHAR_LENGTH(`payload_hash`) = 64
  ),
  CONSTRAINT `chk_kd_dimension` CHECK (`embedding_dimension` > 0),
  CONSTRAINT `chk_kd_chunk_count` CHECK (`chunk_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Knowledge-document desired/indexed metadata; contains no body/vector';

-- ============================================================================
-- 5. Resumable keyset backfill state.
-- ============================================================================

CREATE TABLE `ai_knowledge_backfill_run` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_key` varchar(64) NOT NULL,
  `run_type` varchar(16) NOT NULL,
  `source_scope` varchar(32) NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `cursor_task_id` bigint NOT NULL DEFAULT 0,
  `cursor_review_id` bigint NOT NULL DEFAULT 0,
  `discovered_count` bigint NOT NULL DEFAULT 0,
  `enqueued_count` bigint NOT NULL DEFAULT 0,
  `success_count` bigint NOT NULL DEFAULT 0,
  `failed_count` bigint NOT NULL DEFAULT 0,
  `dead_count` bigint NOT NULL DEFAULT 0,
  `worker_id` varchar(64) DEFAULT NULL,
  `lease_until` datetime(3) DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kbr_run_key` (`run_key`),
  KEY `idx_kbr_status_lease` (`status`, `lease_until`),
  KEY `idx_kbr_trace` (`trace_id`),
  CONSTRAINT `chk_kbr_run_type` CHECK (
      BINARY `run_type` IN ('INITIAL', 'RECONCILE', 'REBUILD')
  ),
  CONSTRAINT `chk_kbr_source_scope` CHECK (
      BINARY `source_scope` IN ('TASK', 'WEEKLY_REVIEW', 'ALL')
  ),
  CONSTRAINT `chk_kbr_status` CHECK (
      BINARY `status` IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')
  ),
  CONSTRAINT `chk_kbr_counts` CHECK (
      `cursor_task_id` >= 0
      AND `cursor_review_id` >= 0
      AND `discovered_count` >= 0
      AND `enqueued_count` >= 0
      AND `success_count` >= 0
      AND `failed_count` >= 0
      AND `dead_count` >= 0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Resumable idempotent knowledge-index backfill run';
