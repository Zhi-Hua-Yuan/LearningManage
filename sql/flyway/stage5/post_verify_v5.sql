-- V5 post verification. Columns: check_id, description, violation_count, result.
SELECT
    'V5-V-001' AS check_id,
    'three V5 RAG tables exist' AS description,
    IF(COUNT(*) = 3, 0, 1) AS violation_count,
    IF(COUNT(*) = 3, 'PASS', 'FAIL') AS result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('ai_rag_query_log', 'ai_rag_result', 'ai_rag_result_source')
UNION ALL
SELECT
    'V5-V-002',
    'query request ID is unique',
    IF(COUNT(*) = 1, 0, 1),
    IF(COUNT(*) = 1, 'PASS', 'FAIL')
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'ai_rag_query_log'
  AND index_name = 'uk_rql_request'
  AND non_unique = 0
UNION ALL
SELECT
    'V5-V-003',
    'result request and query log are unique',
    IF(COUNT(DISTINCT index_name) = 2, 0, 1),
    IF(COUNT(DISTINCT index_name) = 2, 'PASS', 'FAIL')
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'ai_rag_result'
  AND index_name IN ('uk_rr_request', 'uk_rr_query_log')
  AND non_unique = 0
UNION ALL
SELECT
    'V5-V-004',
    'citation and source chunk identities are unique',
    IF(COUNT(DISTINCT index_name) = 2, 0, 1),
    IF(COUNT(DISTINCT index_name) = 2, 'PASS', 'FAIL')
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'ai_rag_result_source'
  AND index_name IN ('uk_rrs_citation', 'uk_rrs_chunk')
  AND non_unique = 0
UNION ALL
SELECT
    'V5-V-005',
    'query and citation tables contain no raw question or source body column',
    IF(COUNT(*) = 0, 0, COUNT(*)),
    IF(COUNT(*) = 0, 'PASS', 'FAIL')
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('ai_rag_query_log', 'ai_rag_result_source')
  AND column_name IN ('question', 'question_text', 'raw_question', 'content', 'body', 'source_text', 'chunk_text')
UNION ALL
SELECT
    'V5-V-006',
    'V5 lifecycle check constraints exist',
    IF(COUNT(*) >= 3, 0, 1),
    IF(COUNT(*) >= 3, 'PASS', 'FAIL')
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN ('ai_rag_query_log', 'ai_rag_result', 'ai_rag_result_source')
  AND constraint_type = 'CHECK';
