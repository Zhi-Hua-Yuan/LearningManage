-- Flyway V6: repair the provider representation of Qdrant permission fields.
--
-- Stage 4 used the browser-facing ObjectMapper for Qdrant requests, which
-- rendered Java Long identifiers as JSON strings. The Stage 5 provider adapter
-- writes these identifiers as JSON numbers. Schedule one idempotent rebuild so
-- existing points become compatible with Qdrant integer payload indexes.

INSERT INTO `ai_knowledge_backfill_run` (
  `run_key`, `run_type`, `source_scope`, `batch_size`, `status`,
  `cursor_task_id`, `cursor_review_id`, `discovered_count`, `enqueued_count`,
  `success_count`, `failed_count`, `dead_count`, `trace_id`
) VALUES (
  'stage5-qdrant-numeric-payload-v1', 'REBUILD', 'ALL', 500, 'PENDING',
  0, 0, 0, 0, 0, 0, 0, 'flyway-v6-qdrant-payload-rebuild'
);
