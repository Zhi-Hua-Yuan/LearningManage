-- V4 post verification. Every row must report PASS.
SELECT
    'V4_TABLE_COUNT' AS check_name,
    IF(COUNT(*) = 4, 'PASS', 'FAIL') AS result,
    COUNT(*) AS actual
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
    'V4_EVENT_INDEX_COUNT',
    IF(COUNT(DISTINCT index_name) >= 6, 'PASS', 'FAIL'),
    COUNT(DISTINCT index_name)
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'ai_knowledge_index_event'
UNION ALL
SELECT
    'V4_DOCUMENT_UNIQUE_KEY',
    IF(COUNT(*) = 1, 'PASS', 'FAIL'),
    COUNT(*)
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'ai_knowledge_document'
  AND index_name = 'uk_kd_document_key'
  AND non_unique = 0;
