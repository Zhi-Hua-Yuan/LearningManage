-- Stage 0.2 read-only audit.
-- This file intentionally contains SELECT/SHOW statements only.
-- Run against a reviewed database; do not redirect raw output into Git.

SELECT 'DATABASE_PROFILE' AS section,
       DATABASE() AS database_name,
       VERSION() AS mysql_version,
       @@character_set_server AS server_charset,
       @@collation_server AS server_collation,
       @@time_zone AS server_timezone;

SELECT 'TABLE_PROFILE' AS section,
       table_name,
       table_rows,
       engine,
       table_collation,
       create_time,
       update_time
FROM information_schema.tables
WHERE table_schema = DATABASE()
ORDER BY table_name;

SELECT 'RBAC_COLUMNS' AS section,
       table_name,
       ordinal_position,
       column_name,
       column_type,
       is_nullable,
       column_default,
       extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('tenant', 'role', 'permission', 'role_permission', 'user_role')
ORDER BY table_name, ordinal_position;

SELECT 'RBAC_INDEXES' AS section,
       table_name,
       index_name,
       non_unique,
       seq_in_index,
       column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('tenant', 'role', 'permission', 'role_permission', 'user_role')
ORDER BY table_name, index_name, seq_in_index;

SELECT 'EXACT_COUNT' AS section, 'user' AS table_name, COUNT(*) AS row_count FROM user
UNION ALL SELECT 'EXACT_COUNT', 'tenant', COUNT(*) FROM tenant
UNION ALL SELECT 'EXACT_COUNT', 'role', COUNT(*) FROM role
UNION ALL SELECT 'EXACT_COUNT', 'permission', COUNT(*) FROM permission
UNION ALL SELECT 'EXACT_COUNT', 'role_permission', COUNT(*) FROM role_permission
UNION ALL SELECT 'EXACT_COUNT', 'user_role', COUNT(*) FROM user_role
UNION ALL SELECT 'EXACT_COUNT', 'team', COUNT(*) FROM team
UNION ALL SELECT 'EXACT_COUNT', 'team_member', COUNT(*) FROM team_member
UNION ALL SELECT 'EXACT_COUNT', 'project', COUNT(*) FROM project
UNION ALL SELECT 'EXACT_COUNT', 'milestone', COUNT(*) FROM milestone
UNION ALL SELECT 'EXACT_COUNT', 'task', COUNT(*) FROM task
UNION ALL SELECT 'EXACT_COUNT', 'weekly_review', COUNT(*) FROM weekly_review
UNION ALL SELECT 'EXACT_COUNT', 'ai_call_log', COUNT(*) FROM ai_call_log
UNION ALL SELECT 'EXACT_COUNT', 'ai_draft', COUNT(*) FROM ai_draft
UNION ALL SELECT 'EXACT_COUNT', 'ai_draft_confirm_log', COUNT(*) FROM ai_draft_confirm_log
UNION ALL SELECT 'EXACT_COUNT', 'ai_replan_operation', COUNT(*) FROM ai_replan_operation
UNION ALL SELECT 'EXACT_COUNT', 'ai_replan_item', COUNT(*) FROM ai_replan_item
UNION ALL SELECT 'EXACT_COUNT', 'prompt_template', COUNT(*) FROM prompt_template
UNION ALL SELECT 'EXACT_COUNT', 'task_status_idempotency', COUNT(*) FROM task_status_idempotency
UNION ALL SELECT 'EXACT_COUNT', 'task_title_rename_log', COUNT(*) FROM task_title_rename_log;

SELECT 'ROLE_VALUES' AS section, user_role AS value, COUNT(*) AS row_count
FROM user
GROUP BY user_role
ORDER BY user_role;

SELECT 'TEAM_ROLE_VALUES' AS section, role AS value, COUNT(*) AS row_count
FROM team_member
GROUP BY role
ORDER BY role;

SELECT 'INVALID_VALUES' AS section, 'user_role' AS rule_name, COUNT(*) AS row_count
FROM user
WHERE user_role IS NULL OR user_role NOT IN ('user', 'admin')
UNION ALL SELECT 'INVALID_VALUES', 'team_member.role', COUNT(*)
FROM team_member
WHERE role IS NULL OR role NOT IN ('OWNER', 'ADMIN', 'MEMBER')
UNION ALL SELECT 'INVALID_VALUES', 'task.status', COUNT(*)
FROM task
WHERE status IS NULL OR status NOT IN (0, 1, 2, 3)
UNION ALL SELECT 'INVALID_VALUES', 'task.progress_like_priority', COUNT(*)
FROM task
WHERE priority IS NULL OR priority < 0
UNION ALL SELECT 'INVALID_VALUES', 'project.progress', COUNT(*)
FROM project
WHERE progress IS NULL OR progress < 0 OR progress > 100
UNION ALL SELECT 'INVALID_VALUES', 'milestone.progress', COUNT(*)
FROM milestone
WHERE progress IS NULL OR progress < 0 OR progress > 100;

SELECT 'DUPLICATE_KEYS' AS section, 'user.account' AS rule_name, COUNT(*) AS duplicate_groups
FROM (SELECT account FROM user GROUP BY account HAVING COUNT(*) > 1) d
UNION ALL SELECT 'DUPLICATE_KEYS', 'team.invite_code', COUNT(*)
FROM (SELECT invite_code FROM team GROUP BY invite_code HAVING COUNT(*) > 1) d
UNION ALL SELECT 'DUPLICATE_KEYS', 'weekly_review.user_year_week', COUNT(*)
FROM (SELECT user_id, year, week_no FROM weekly_review GROUP BY user_id, year, week_no HAVING COUNT(*) > 1) d;

SELECT 'ORPHAN_COUNTS' AS section, 'project.user_id' AS rule_name, COUNT(*) AS row_count
FROM project p LEFT JOIN user u ON u.id = p.user_id
WHERE u.id IS NULL AND p.is_delete = 0
UNION ALL SELECT 'ORPHAN_COUNTS', 'project.team_id', COUNT(*)
FROM project p LEFT JOIN team t ON t.id = p.team_id
WHERE p.team_id IS NOT NULL AND t.id IS NULL AND p.is_delete = 0
UNION ALL SELECT 'ORPHAN_COUNTS', 'milestone.project_id', COUNT(*)
FROM milestone m LEFT JOIN project p ON p.id = m.project_id
WHERE p.id IS NULL AND m.is_delete = 0
UNION ALL SELECT 'ORPHAN_COUNTS', 'task.project_id', COUNT(*)
FROM task t LEFT JOIN project p ON p.id = t.project_id
WHERE p.id IS NULL AND t.is_delete = 0
UNION ALL SELECT 'ORPHAN_COUNTS', 'task.milestone_id', COUNT(*)
FROM task t LEFT JOIN milestone m ON m.id = t.milestone_id
WHERE t.milestone_id IS NOT NULL AND m.id IS NULL AND t.is_delete = 0
UNION ALL SELECT 'ORPHAN_COUNTS', 'team_member.team_id', COUNT(*)
FROM team_member tm LEFT JOIN team t ON t.id = tm.team_id
WHERE t.id IS NULL AND tm.is_delete = 0
UNION ALL SELECT 'ORPHAN_COUNTS', 'team_member.user_id', COUNT(*)
FROM team_member tm LEFT JOIN user u ON u.id = tm.user_id
WHERE u.id IS NULL AND tm.is_delete = 0
UNION ALL SELECT 'ORPHAN_COUNTS', 'weekly_review.user_id', COUNT(*)
FROM weekly_review wr LEFT JOIN user u ON u.id = wr.user_id
WHERE u.id IS NULL;

-- Restricted audit output: IDs and timestamps only, no titles, names, prompts or passwords.
SELECT 'ORPHAN_PROJECT_DETAIL' AS section,
       p.id,
       p.user_id,
       p.team_id,
       p.is_delete,
       p.create_time,
       p.update_time,
       p.deleted_at
FROM project p LEFT JOIN user u ON u.id = p.user_id
WHERE u.id IS NULL;

SELECT 'ORPHAN_TASK_DETAIL' AS section,
       t.id,
       t.project_id,
       t.milestone_id,
       t.user_id,
       t.status,
       t.is_delete,
       t.create_time,
       t.update_time,
       t.deleted_at
FROM task t LEFT JOIN project p ON p.id = t.project_id
WHERE p.id IS NULL;

SELECT 'TEMPORAL_RANGE' AS section, 'project' AS table_name,
       MIN(create_time) AS first_created, MAX(create_time) AS last_created,
       MIN(update_time) AS first_updated, MAX(update_time) AS last_updated
FROM project
UNION ALL SELECT 'TEMPORAL_RANGE', 'task', MIN(create_time), MAX(create_time), MIN(update_time), MAX(update_time) FROM task
UNION ALL SELECT 'TEMPORAL_RANGE', 'weekly_review', MIN(create_time), MAX(create_time), MIN(update_time), MAX(update_time) FROM weekly_review
UNION ALL SELECT 'TEMPORAL_RANGE', 'role', MIN(create_time), MAX(create_time), MIN(update_time), MAX(update_time) FROM role
UNION ALL SELECT 'TEMPORAL_RANGE', 'permission', MIN(create_time), MAX(create_time), MIN(update_time), MAX(update_time) FROM permission;
