-- PR5-A main database preflight.
-- Read-only statements only. Run against learning_manage and keep raw output
-- outside Git. Stop if any gate_status is FAIL.

SET @expected_database = 'learning_manage';

SELECT 'DATABASE_NAME' AS check_name,
       @expected_database AS expected_value,
       DATABASE() AS actual_value,
       IF(DATABASE() = @expected_database, 'PASS', 'FAIL') AS gate_status;

SELECT 'MYSQL_VERSION' AS check_name,
       '8.0.x' AS expected_value,
       VERSION() AS actual_value,
       IF(VERSION() LIKE '8.0.%', 'PASS', 'FAIL') AS gate_status;

SELECT 'FLYWAY_HISTORY_ABSENT' AS check_name,
       0 AS expected_value,
       COUNT(*) AS actual_value,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') AS gate_status
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'flyway_schema_history';

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
       20 AS expected_value,
       COUNT(*) AS actual_value,
       IF(COUNT(*) = 20, 'PASS', 'FAIL') AS gate_status
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE';

SELECT 'ACTIVE_ORPHAN_COUNTS' AS check_name,
       CONCAT('project_user=', project_user,
              ',project_team=', project_team,
              ',milestone_project=', milestone_project,
              ',task_project=', task_project,
              ',task_milestone=', task_milestone,
              ',team_member_team=', team_member_team,
              ',team_member_user=', team_member_user,
              ',weekly_review_user=', weekly_review_user) AS actual_value,
       IF(project_user + project_team + milestone_project + task_project
          + task_milestone + team_member_team + team_member_user
          + weekly_review_user = 0, 'PASS', 'FAIL') AS gate_status
FROM (SELECT
        (SELECT COUNT(*) FROM project p LEFT JOIN user u ON u.id = p.user_id
         WHERE u.id IS NULL AND p.is_delete = 0) AS project_user,
        (SELECT COUNT(*) FROM project p LEFT JOIN team t ON t.id = p.team_id
         WHERE p.team_id IS NOT NULL AND t.id IS NULL AND p.is_delete = 0) AS project_team,
        (SELECT COUNT(*) FROM milestone m LEFT JOIN project p ON p.id = m.project_id
         WHERE p.id IS NULL AND m.is_delete = 0) AS milestone_project,
        (SELECT COUNT(*) FROM task t LEFT JOIN project p ON p.id = t.project_id
         WHERE p.id IS NULL AND t.is_delete = 0) AS task_project,
        (SELECT COUNT(*) FROM task t LEFT JOIN milestone m ON m.id = t.milestone_id
         WHERE t.milestone_id IS NOT NULL AND m.id IS NULL AND t.is_delete = 0) AS task_milestone,
        (SELECT COUNT(*) FROM team_member tm LEFT JOIN team t ON t.id = tm.team_id
         WHERE t.id IS NULL AND tm.is_delete = 0) AS team_member_team,
        (SELECT COUNT(*) FROM team_member tm LEFT JOIN user u ON u.id = tm.user_id
         WHERE u.id IS NULL AND tm.is_delete = 0) AS team_member_user,
        (SELECT COUNT(*) FROM weekly_review wr LEFT JOIN user u ON u.id = wr.user_id
         WHERE u.id IS NULL) AS weekly_review_user
     ) orphan_counts;

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

SELECT 'SCHEMA_COLUMNS' AS section, table_name, ordinal_position, column_name,
       column_type, is_nullable, column_default, extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
ORDER BY table_name, ordinal_position;

SELECT 'SCHEMA_INDEXES' AS section, table_name, index_name, non_unique,
       seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
ORDER BY table_name, index_name, seq_in_index;
