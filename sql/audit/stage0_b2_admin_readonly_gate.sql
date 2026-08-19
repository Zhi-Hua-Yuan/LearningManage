-- Stage 0.3-B2-0 MySQL administrator read-only gate.
-- This file contains SELECT/SHOW statements only. It must be run with a
-- protected local administrator session; never put the password in this file.

SELECT USER() AS connected_user,
       CURRENT_USER() AS authenticated_account,
       VERSION() AS mysql_version;

SELECT User,
       Host,
       plugin,
       account_locked,
       password_expired
FROM mysql.user
WHERE User IN ('root', 'learning_manage_app', 'learning_manage_test_app');

SELECT SCHEMA_NAME
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME IN ('learning_manage', 'learning_manage_stage0b2_test_20260818');

SELECT 'learning_manage_app' AS account_name,
       COUNT(*) AS account_rows
FROM mysql.user
WHERE User = 'learning_manage_app'
  AND Host = 'localhost'
UNION ALL
SELECT 'learning_manage_test_app',
       COUNT(*)
FROM mysql.user
WHERE User = 'learning_manage_test_app'
  AND Host = 'localhost';

SELECT 'main' AS db_name,
       (SELECT COUNT(*)
        FROM learning_manage.project p
        LEFT JOIN learning_manage.user u
          ON u.id = p.user_id AND u.is_delete = 0
        WHERE p.is_delete = 0 AND u.id IS NULL) AS active_orphan_projects,
       (SELECT COUNT(*)
        FROM learning_manage.task t
        LEFT JOIN learning_manage.project p
          ON p.id = t.project_id AND p.is_delete = 0
        WHERE t.is_delete = 0 AND p.id IS NULL) AS active_orphan_tasks,
       (SELECT COUNT(*)
        FROM learning_manage.milestone m
        LEFT JOIN learning_manage.project p
          ON p.id = m.project_id AND p.is_delete = 0
        WHERE m.is_delete = 0 AND p.id IS NULL) AS active_orphan_milestones,
       (SELECT COUNT(*)
        FROM learning_manage.team_member tm
        LEFT JOIN learning_manage.team te
          ON te.id = tm.team_id AND te.is_delete = 0
        LEFT JOIN learning_manage.user u
          ON u.id = tm.user_id AND u.is_delete = 0
        WHERE tm.is_delete = 0 AND (te.id IS NULL OR u.id IS NULL)) AS active_orphan_team_members
UNION ALL
SELECT 'isolated_test',
       (SELECT COUNT(*)
        FROM learning_manage_stage0b2_test_20260818.project p
        LEFT JOIN learning_manage_stage0b2_test_20260818.user u
          ON u.id = p.user_id AND u.is_delete = 0
        WHERE p.is_delete = 0 AND u.id IS NULL),
       (SELECT COUNT(*)
        FROM learning_manage_stage0b2_test_20260818.task t
        LEFT JOIN learning_manage_stage0b2_test_20260818.project p
          ON p.id = t.project_id AND p.is_delete = 0
        WHERE t.is_delete = 0 AND p.id IS NULL),
       (SELECT COUNT(*)
        FROM learning_manage_stage0b2_test_20260818.milestone m
        LEFT JOIN learning_manage_stage0b2_test_20260818.project p
          ON p.id = m.project_id AND p.is_delete = 0
        WHERE m.is_delete = 0 AND p.id IS NULL),
       (SELECT COUNT(*)
        FROM learning_manage_stage0b2_test_20260818.team_member tm
        LEFT JOIN learning_manage_stage0b2_test_20260818.team te
          ON te.id = tm.team_id AND te.is_delete = 0
        LEFT JOIN learning_manage_stage0b2_test_20260818.user u
          ON u.id = tm.user_id AND u.is_delete = 0
        WHERE tm.is_delete = 0 AND (te.id IS NULL OR u.id IS NULL));

SELECT 'main_counts' AS section,
       (SELECT COUNT(*) FROM learning_manage.user) AS user_rows,
       (SELECT COUNT(*) FROM learning_manage.project) AS project_rows,
       (SELECT COUNT(*) FROM learning_manage.milestone) AS milestone_rows,
       (SELECT COUNT(*) FROM learning_manage.task) AS task_rows,
       (SELECT COUNT(*) FROM learning_manage.weekly_review) AS weekly_review_rows
UNION ALL
SELECT 'isolated_test_counts',
       (SELECT COUNT(*) FROM learning_manage_stage0b2_test_20260818.user),
       (SELECT COUNT(*) FROM learning_manage_stage0b2_test_20260818.project),
       (SELECT COUNT(*) FROM learning_manage_stage0b2_test_20260818.milestone),
       (SELECT COUNT(*) FROM learning_manage_stage0b2_test_20260818.task),
       (SELECT COUNT(*) FROM learning_manage_stage0b2_test_20260818.weekly_review);

SELECT @@default_authentication_plugin AS default_authentication_plugin;
SHOW VARIABLES LIKE 'require_secure_transport';
SHOW VARIABLES LIKE 'have_ssl';
SHOW GRANTS FOR 'root'@'localhost';
