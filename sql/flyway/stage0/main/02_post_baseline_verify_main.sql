-- PR5-A/PR5-B post-baseline verification.
-- Read-only statements only. Compare row counts and schema output with the
-- preflight evidence; no manual history-table edits are permitted.

SELECT 'FLYWAY_HISTORY_PROFILE' AS section,
       COUNT(*) AS history_rows,
       SUM(type = 'BASELINE') AS baseline_rows,
       MIN(version) AS first_version,
       MAX(version) AS last_version
FROM flyway_schema_history;

SELECT installed_rank, version, description, type, script, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT 'FLYWAY_BASELINE_RECORD' AS check_name,
       1 AS expected_value,
       COUNT(*) AS actual_value,
       IF(COUNT(*) = 1
          AND SUM(type = 'BASELINE') = 1
          AND SUM(version = '1') = 1
          AND SUM(success = 1) = 1,
          'PASS', 'FAIL') AS gate_status
FROM flyway_schema_history;

SELECT 'BUSINESS_TABLE_COUNT' AS check_name,
       20 AS expected_value,
       COUNT(*) AS actual_value,
       IF(COUNT(*) = 20, 'PASS', 'FAIL') AS gate_status
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE'
  AND table_name IN (
      'user', 'tenant', 'role', 'permission', 'role_permission', 'user_role',
      'team', 'team_member', 'project', 'milestone', 'task', 'weekly_review',
      'prompt_template', 'ai_call_log', 'ai_draft', 'ai_draft_confirm_log',
      'ai_replan_operation', 'ai_replan_item', 'task_status_idempotency',
      'task_title_rename_log'
  );

SELECT 'TOTAL_BASE_TABLE_COUNT' AS check_name,
       21 AS expected_value,
       COUNT(*) AS actual_value,
       IF(COUNT(*) = 21, 'PASS', 'FAIL') AS gate_status
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE';

SELECT 'ACTIVE_ORPHAN_COUNTS' AS check_name,
       COUNT(*) AS active_orphan_rows,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') AS gate_status
FROM (
    SELECT p.id FROM project p LEFT JOIN user u ON u.id = p.user_id
    WHERE u.id IS NULL AND p.is_delete = 0
    UNION ALL
    SELECT p.id FROM project p LEFT JOIN team t ON t.id = p.team_id
    WHERE p.team_id IS NOT NULL AND t.id IS NULL AND p.is_delete = 0
    UNION ALL
    SELECT m.id FROM milestone m LEFT JOIN project p ON p.id = m.project_id
    WHERE p.id IS NULL AND m.is_delete = 0
    UNION ALL
    SELECT t.id FROM task t LEFT JOIN project p ON p.id = t.project_id
    WHERE p.id IS NULL AND t.is_delete = 0
    UNION ALL
    SELECT t.id FROM task t LEFT JOIN milestone m ON m.id = t.milestone_id
    WHERE t.milestone_id IS NOT NULL AND m.id IS NULL AND t.is_delete = 0
    UNION ALL
    SELECT tm.id FROM team_member tm LEFT JOIN team t ON t.id = tm.team_id
    WHERE t.id IS NULL AND tm.is_delete = 0
    UNION ALL
    SELECT tm.id FROM team_member tm LEFT JOIN user u ON u.id = tm.user_id
    WHERE u.id IS NULL AND tm.is_delete = 0
    UNION ALL
    SELECT wr.id FROM weekly_review wr LEFT JOIN user u ON u.id = wr.user_id
    WHERE u.id IS NULL
) active_orphans;

SELECT 'TABLE_ROW_COUNTS' AS section, 'user' AS table_name, COUNT(*) AS row_count FROM user
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'tenant', COUNT(*) FROM tenant
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'role', COUNT(*) FROM role
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'permission', COUNT(*) FROM permission
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'role_permission', COUNT(*) FROM role_permission
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'user_role', COUNT(*) FROM user_role
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'team', COUNT(*) FROM team
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'team_member', COUNT(*) FROM team_member
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'project', COUNT(*) FROM project
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'milestone', COUNT(*) FROM milestone
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'task', COUNT(*) FROM task
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'weekly_review', COUNT(*) FROM weekly_review
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'prompt_template', COUNT(*) FROM prompt_template
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'ai_call_log', COUNT(*) FROM ai_call_log
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'ai_draft', COUNT(*) FROM ai_draft
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'ai_draft_confirm_log', COUNT(*) FROM ai_draft_confirm_log
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'ai_replan_operation', COUNT(*) FROM ai_replan_operation
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'ai_replan_item', COUNT(*) FROM ai_replan_item
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'task_status_idempotency', COUNT(*) FROM task_status_idempotency
UNION ALL SELECT 'TABLE_ROW_COUNTS', 'task_title_rename_log', COUNT(*) FROM task_title_rename_log
ORDER BY table_name;
