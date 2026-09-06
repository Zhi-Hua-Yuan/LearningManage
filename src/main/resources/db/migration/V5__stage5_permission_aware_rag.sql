-- Flyway V5: permission-aware RAG query audit, persisted answers, and citations.
--
-- Raw questions and source bodies are deliberately excluded. MySQL business
-- rows remain the source of truth and every stored citation is revalidated on
-- read.

CREATE TEMPORARY TABLE `_v5_rag_guard` (
  `id` tinyint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

CREATE TEMPORARY TABLE `_v5_rag_abort` (
  `id` tinyint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

INSERT INTO `_v5_rag_abort` (`id`) VALUES (1);

INSERT INTO `_v5_rag_guard` (`id`)
SELECT 1
WHERE EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('ai_rag_query_log', 'ai_rag_result', 'ai_rag_result_source')
);

INSERT INTO `_v5_rag_abort` (`id`)
SELECT `id` FROM `_v5_rag_guard`;

DROP TEMPORARY TABLE `_v5_rag_guard`;
DROP TEMPORARY TABLE `_v5_rag_abort`;

CREATE TABLE `ai_rag_query_log` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `request_id` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `question_hmac` char(64) NOT NULL COMMENT 'Keyed HMAC; raw question is never persisted',
  `status` varchar(24) NOT NULL DEFAULT 'RUNNING',
  `retrieval_config_version` varchar(32) NOT NULL,
  `embedding_model` varchar(64) NOT NULL,
  `embedding_dimension` int NOT NULL,
  `rerank_model` varchar(64) DEFAULT NULL,
  `initial_top_k` int NOT NULL,
  `final_top_k` int NOT NULL,
  `vector_threshold` decimal(8,6) NOT NULL,
  `rerank_threshold` decimal(8,6) DEFAULT NULL,
  `candidate_count` int NOT NULL DEFAULT 0,
  `authorized_count` int NOT NULL DEFAULT 0,
  `final_count` int NOT NULL DEFAULT 0,
  `degraded` tinyint NOT NULL DEFAULT 0,
  `failure_type` varchar(64) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `trace_id` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rql_request` (`request_id`),
  KEY `idx_rql_user_time` (`user_id`, `create_time`),
  KEY `idx_rql_project_status_time` (`project_id`, `status`, `create_time`),
  KEY `idx_rql_trace` (`trace_id`),
  CONSTRAINT `chk_rql_status` CHECK (
      BINARY `status` IN ('RUNNING', 'SUCCEEDED', 'INSUFFICIENT', 'FAILED')
  ),
  CONSTRAINT `chk_rql_dimension` CHECK (`embedding_dimension` > 0),
  CONSTRAINT `chk_rql_counts` CHECK (
      `initial_top_k` BETWEEN 1 AND 100
      AND `final_top_k` BETWEEN 1 AND `initial_top_k`
      AND `candidate_count` >= 0
      AND `authorized_count` >= 0
      AND `final_count` >= 0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Metadata-only audit for one permission-aware RAG query';

CREATE TABLE `ai_rag_result` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `request_id` varchar(64) NOT NULL,
  `query_log_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `answer_text` mediumtext NOT NULL,
  `answer_hash` char(64) NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'ACTIVE',
  `insufficient_evidence` tinyint NOT NULL DEFAULT 0,
  `degraded` tinyint NOT NULL DEFAULT 0,
  `degradation_reason` varchar(500) DEFAULT NULL,
  `ai_call_log_id` bigint DEFAULT NULL,
  `model` varchar(64) DEFAULT NULL,
  `prompt_code` varchar(64) DEFAULT NULL,
  `prompt_version` int DEFAULT NULL,
  `retrieval_config_version` varchar(32) NOT NULL,
  `knowledge_as_of` datetime(3) DEFAULT NULL,
  `trace_id` varchar(64) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rr_request` (`request_id`),
  UNIQUE KEY `uk_rr_query_log` (`query_log_id`),
  KEY `idx_rr_user_status_expiry` (`user_id`, `status`, `expires_at`),
  KEY `idx_rr_project_time` (`project_id`, `create_time`),
  KEY `idx_rr_trace` (`trace_id`),
  CONSTRAINT `chk_rr_status` CHECK (
      BINARY `status` IN ('ACTIVE', 'STALE', 'INVALIDATED', 'EXPIRED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Persisted RAG answer owned by the requesting user';

CREATE TABLE `ai_rag_result_source` (
  `id` bigint NOT NULL COMMENT 'Application-generated ID',
  `result_id` bigint NOT NULL,
  `citation_id` varchar(16) NOT NULL COMMENT 'Stable answer-local ID such as S1',
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint NOT NULL,
  `document_key` varchar(255) NOT NULL,
  `chunk_index` int NOT NULL,
  `content_hash` char(64) NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `vector_score` double NOT NULL,
  `rerank_score` double DEFAULT NULL,
  `title_snapshot` varchar(255) NOT NULL,
  `source_updated_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rrs_citation` (`result_id`, `citation_id`),
  UNIQUE KEY `uk_rrs_chunk` (`result_id`, `document_key`, `chunk_index`),
  KEY `idx_rrs_source` (`source_type`, `source_id`),
  CONSTRAINT `chk_rrs_source_type` CHECK (
      BINARY `source_type` IN ('TASK', 'WEEKLY_REVIEW')
  ),
  CONSTRAINT `chk_rrs_chunk_index` CHECK (`chunk_index` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Citation metadata without copied source body';
