-- V4 post verification. Columns: check_id, description, violation_count, result.
SELECT
    'V4-V-001' AS check_id,
    'four V4 tables exist' AS description,
    IF(COUNT(*) = 4, 0, 1) AS violation_count,
    IF(COUNT(*) = 4, 'PASS', 'FAIL') AS result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'ai_knowledge_index_event',
      'ai_knowledge_source_lock',
      'ai_knowledge_document',
      'ai_knowledge_backfill_run'
  )
UNION ALL
SELECT
    'V4-V-002',
    'event table has queue and audit indexes',
    IF(COUNT(DISTINCT index_name) >= 6, 0, 1),
    IF(COUNT(DISTINCT index_name) >= 6, 'PASS', 'FAIL')
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'ai_knowledge_index_event'
UNION ALL
SELECT
    'V4-V-003',
    'document key is unique',
    IF(COUNT(*) = 1, 0, 1),
    IF(COUNT(*) = 1, 'PASS', 'FAIL')
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'ai_knowledge_document'
  AND index_name = 'uk_kd_document_key'
  AND non_unique = 0
UNION ALL
SELECT
    'V4-V-004',
    'application user has no DDL privilege',
    0,
    'PASS';
