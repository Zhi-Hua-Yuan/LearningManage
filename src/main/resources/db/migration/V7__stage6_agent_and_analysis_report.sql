-- Flyway V7: controlled asynchronous Agent runs and confirmed analysis reports.
-- MySQL remains the source of truth. Agent execution metadata is append-oriented;
-- model/tool bodies are deliberately excluded from audit tables.

ALTER TABLE `project`
    ADD COLUMN `data_version` bigint NOT NULL DEFAULT 0
        COMMENT 'Monotonic version for Agent consistency checks' AFTER `deleted_at`,
    ADD KEY `idx_project_data_version` (`id`, `data_version`);

ALTER TABLE `team`
    ADD COLUMN `data_version` bigint NOT NULL DEFAULT 0
        COMMENT 'Monotonic version for team workload consistency checks' AFTER `deleted_at`,
    ADD KEY `idx_team_data_version` (`id`, `data_version`);

CREATE TABLE `ai_agent_run` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `run_id` varchar(64) NOT NULL,
  `client_request_id` varchar(64) NOT NULL,
  `scene` varchar(32) NOT NULL,
  `user_id` bigint NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `orchestration_mode` varchar(32) NOT NULL DEFAULT 'FIXED_WORKFLOW',
  `current_step` varchar(64) DEFAULT NULL,
  `tool_count` int NOT NULL DEFAULT 0,
  `attempt_count` int NOT NULL DEFAULT 0,
  `worker_id` varchar(96) DEFAULT NULL,
  `lease_until` datetime(3) DEFAULT NULL,
  `heartbeat_at` datetime(3) DEFAULT NULL,
  `cancel_requested_at` datetime(3) DEFAULT NULL,
  `execution_token` varchar(64) DEFAULT NULL,
  `start_data_version` bigint DEFAULT NULL,
  `end_data_version` bigint DEFAULT NULL,
  `draft_id` varchar(64) DEFAULT NULL,
  `ai_call_log_id` bigint DEFAULT NULL,
  `partial_reason` varchar(500) DEFAULT NULL,
  `failure_type` varchar(64) DEFAULT NULL,
  `error_summary` varchar(1000) DEFAULT NULL,
  `model` varchar(64) DEFAULT NULL,
  `prompt_code` varchar(64) DEFAULT NULL,
  `prompt_version` int DEFAULT NULL,
  `trace_id` varchar(64) NOT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_id` (`run_id`),
  UNIQUE KEY `uk_agent_user_scene_request` (`user_id`, `scene`, `client_request_id`),
  KEY `idx_agent_claim` (`status`, `lease_until`, `create_time`),
  KEY `idx_agent_user_status` (`user_id`, `status`, `create_time`),
  KEY `idx_agent_project_time` (`project_id`, `create_time`),
  KEY `idx_agent_team_time` (`team_id`, `create_time`),
  KEY `idx_agent_trace` (`trace_id`),
  CONSTRAINT `chk_agent_scene` CHECK (
      BINARY `scene` IN ('PROJECT_RISK', 'TEAM_WORKLOAD')
  ),
  CONSTRAINT `chk_agent_target` CHECK (
      (`scene` = 'PROJECT_RISK' AND `project_id` IS NOT NULL AND `team_id` IS NULL)
      OR (`scene` = 'TEAM_WORKLOAD' AND `team_id` IS NOT NULL AND `project_id` IS NULL)
  ),
  CONSTRAINT `chk_agent_status` CHECK (
      BINARY `status` IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL',
                          'FAILED', 'TIMED_OUT', 'CANCELED')
  ),
  CONSTRAINT `chk_agent_mode` CHECK (
      BINARY `orchestration_mode` IN ('TOOL_CALLING', 'FIXED_WORKFLOW')
  ),
  CONSTRAINT `chk_agent_counts` CHECK (
      `tool_count` BETWEEN 0 AND 4 AND `attempt_count` BETWEEN 0 AND 2
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Durable asynchronous Agent run queue and lifecycle';

CREATE TABLE `ai_agent_tool_log` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `run_id` varchar(64) NOT NULL,
  `attempt_no` int NOT NULL,
  `tool_sequence` int NOT NULL,
  `tool_call_id` varchar(128) DEFAULT NULL,
  `tool_name` varchar(64) NOT NULL,
  `status` varchar(24) NOT NULL,
  `argument_hash` char(64) NOT NULL,
  `argument_summary` varchar(2000) DEFAULT NULL,
  `result_hash` char(64) DEFAULT NULL,
  `result_summary` varchar(2000) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `observed_data_version` bigint DEFAULT NULL,
  `failure_type` varchar(64) DEFAULT NULL,
  `trace_id` varchar(64) NOT NULL,
  `started_at` datetime(3) NOT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_tool_sequence` (`run_id`, `attempt_no`, `tool_sequence`),
  KEY `idx_agent_tool_run` (`run_id`, `tool_sequence`),
  KEY `idx_agent_tool_name_time` (`tool_name`, `create_time`),
  KEY `idx_agent_tool_trace` (`trace_id`),
  CONSTRAINT `chk_agent_tool_status` CHECK (
      BINARY `status` IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED')
  ),
  CONSTRAINT `chk_agent_tool_sequence_range` CHECK (
      `attempt_no` BETWEEN 1 AND 2 AND `tool_sequence` BETWEEN 1 AND 4
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Metadata-only audit for registered Agent tool calls';

CREATE TABLE `ai_analysis_report` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `report_id` varchar(64) NOT NULL,
  `report_type` varchar(32) NOT NULL,
  `schema_version` int NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `creator_user_id` bigint NOT NULL,
  `source_run_id` varchar(64) NOT NULL,
  `source_data_version` bigint NOT NULL,
  `manager_summary` mediumtext DEFAULT NULL,
  `public_summary` mediumtext DEFAULT NULL,
  `member_metrics_json` longtext DEFAULT NULL,
  `recommendations_json` longtext NOT NULL,
  `content_hash` char(64) NOT NULL,
  `model` varchar(64) DEFAULT NULL,
  `prompt_code` varchar(64) DEFAULT NULL,
  `prompt_version` int DEFAULT NULL,
  `trace_id` varchar(64) NOT NULL,
  `generated_at` datetime(3) NOT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `is_delete` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_report_id` (`report_id`),
  UNIQUE KEY `uk_analysis_report_run_type` (`source_run_id`, `report_type`),
  KEY `idx_analysis_report_project` (`project_id`, `report_type`, `create_time`),
  KEY `idx_analysis_report_team` (`team_id`, `report_type`, `create_time`),
  KEY `idx_analysis_report_creator` (`creator_user_id`, `create_time`),
  KEY `idx_analysis_report_trace` (`trace_id`),
  CONSTRAINT `chk_analysis_report_type` CHECK (
      BINARY `report_type` IN ('PROJECT_RISK', 'TEAM_WORKLOAD')
  ),
  CONSTRAINT `chk_analysis_report_target` CHECK (
      (`report_type` = 'PROJECT_RISK' AND `project_id` IS NOT NULL AND `team_id` IS NULL)
      OR (`report_type` = 'TEAM_WORKLOAD' AND `team_id` IS NOT NULL AND `project_id` IS NULL)
  ),
  CONSTRAINT `chk_analysis_report_schema` CHECK (`schema_version` > 0),
  CONSTRAINT `chk_analysis_report_deleted` CHECK (`is_delete` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='User-confirmed Agent analysis report';

CREATE TABLE `ai_analysis_report_source` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `report_id` varchar(64) NOT NULL,
  `citation_id` varchar(16) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint NOT NULL,
  `document_key` varchar(255) NOT NULL,
  `chunk_index` int NOT NULL,
  `content_hash` char(64) NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `title_snapshot` varchar(255) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_source_citation` (`report_id`, `citation_id`),
  UNIQUE KEY `uk_analysis_source_chunk` (`report_id`, `document_key`, `chunk_index`),
  KEY `idx_analysis_source_business` (`source_type`, `source_id`),
  CONSTRAINT `chk_analysis_source_type` CHECK (
      BINARY `source_type` IN ('TASK', 'WEEKLY_REVIEW')
  ),
  CONSTRAINT `chk_analysis_source_chunk` CHECK (`chunk_index` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Citation metadata for confirmed Agent reports';

ALTER TABLE `ai_call_log`
    ADD COLUMN `agent_run_id` varchar(64) DEFAULT NULL
        COMMENT 'Related Agent run, null for non-Agent calls' AFTER `trace_id`,
    ADD COLUMN `agent_round_no` int DEFAULT NULL
        COMMENT 'One-based model round within an Agent run' AFTER `agent_run_id`,
    ADD KEY `idx_ai_call_log_agent_round` (`agent_run_id`, `agent_round_no`);
