-- V7 post verification. Columns: check_id, description, violation_count, result.
SELECT
    'V7-V-001' AS check_id,
    'four Stage 6 Agent/report tables exist' AS description,
    IF(COUNT(*) = 4, 0, 1) AS violation_count,
    IF(COUNT(*) = 4, 'PASS', 'FAIL') AS result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('ai_agent_run', 'ai_agent_tool_log', 'ai_analysis_report', 'ai_analysis_report_source')
UNION ALL
SELECT
    'V7-V-002',
    'project and team data versions exist and are non-null',
    IF(COUNT(*) = 2, 0, 1),
    IF(COUNT(*) = 2, 'PASS', 'FAIL')
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'project' AND column_name = 'data_version' AND is_nullable = 'NO')
    OR (table_name = 'team' AND column_name = 'data_version' AND is_nullable = 'NO'))
UNION ALL
SELECT
    'V7-V-003',
    'AI call log has Agent round correlation',
    IF(COUNT(*) = 2, 0, 1),
    IF(COUNT(*) = 2, 'PASS', 'FAIL')
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'ai_call_log'
  AND column_name IN ('agent_run_id', 'agent_round_no')
UNION ALL
SELECT
    'V7-V-004',
    'Agent idempotency and opaque run identifiers are unique',
    IF(COUNT(DISTINCT index_name) = 2, 0, 1),
    IF(COUNT(DISTINCT index_name) = 2, 'PASS', 'FAIL')
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'ai_agent_run'
  AND index_name IN ('uk_agent_run_id', 'uk_agent_user_scene_request')
  AND non_unique = 0
UNION ALL
SELECT
    'V7-V-005',
    'one report per source run and report type is enforced',
    IF(COUNT(DISTINCT index_name) = 1, 0, 1),
    IF(COUNT(DISTINCT index_name) = 1, 'PASS', 'FAIL')
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'ai_analysis_report'
  AND index_name = 'uk_analysis_report_run_type'
  AND non_unique = 0
UNION ALL
SELECT
    'V7-V-006',
    'Agent lifecycle check constraints exist',
    IF(COUNT(*) >= 8, 0, 1),
    IF(COUNT(*) >= 8, 'PASS', 'FAIL')
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN ('ai_agent_run', 'ai_agent_tool_log', 'ai_analysis_report', 'ai_analysis_report_source')
  AND constraint_type = 'CHECK'
UNION ALL
SELECT
    'V7-V-007',
    'Tool audit contains no raw prompt or source body column',
    IF(COUNT(*) = 0, 0, COUNT(*)),
    IF(COUNT(*) = 0, 'PASS', 'FAIL')
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'ai_agent_tool_log'
  AND column_name IN ('prompt_text', 'request_text', 'response_text', 'source_text', 'result_text', 'raw_arguments')
UNION ALL
SELECT
    'V7-V-008',
    'Agent claim and tool sequence indexes exist',
    IF(COUNT(DISTINCT CONCAT(table_name, ':', index_name)) = 2, 0, 1),
    IF(COUNT(DISTINCT CONCAT(table_name, ':', index_name)) = 2, 'PASS', 'FAIL')
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND ((table_name = 'ai_agent_run' AND index_name = 'idx_agent_claim')
    OR (table_name = 'ai_agent_tool_log' AND index_name = 'uk_agent_tool_sequence'));
