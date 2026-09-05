-- V4 preflight. A zero-row result means the database is ready for V4.
SELECT 'V4_TARGET_TABLE_ALREADY_EXISTS' AS violation, table_name AS detail
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'ai_knowledge_index_event',
      'ai_knowledge_source_lock',
      'ai_knowledge_document',
      'ai_knowledge_backfill_run'
  )
UNION ALL
SELECT 'REQUIRED_V3_TABLE_MISSING', required.table_name
FROM (
    SELECT 'task' AS table_name
    UNION ALL SELECT 'project'
    UNION ALL SELECT 'team_member'
    UNION ALL SELECT 'weekly_review'
    UNION ALL SELECT 'weekly_review_task'
    UNION ALL SELECT 'ai_call_log'
) required
LEFT JOIN information_schema.tables actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = required.table_name
WHERE actual.table_name IS NULL;
