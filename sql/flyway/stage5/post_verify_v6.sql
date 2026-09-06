-- V6 post verification. Columns: check_id, description, violation_count, result.
SELECT
    'V6-V-001' AS check_id,
    'required Qdrant numeric-payload rebuild is scheduled exactly once' AS description,
    IF(COUNT(*) = 1, 0, 1) AS violation_count,
    IF(COUNT(*) = 1, 'PASS', 'FAIL') AS result
FROM ai_knowledge_backfill_run
WHERE run_key = 'stage5-qdrant-numeric-payload-v1'
  AND BINARY run_type = 'REBUILD'
  AND BINARY source_scope = 'ALL';
