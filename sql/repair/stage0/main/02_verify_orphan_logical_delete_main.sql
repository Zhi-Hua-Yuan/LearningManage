-- Read-only verification for the Stage B main-database repair.
-- This file may be executed after an approved main-database change.

USE `learning_manage`;

SELECT
    (SELECT COUNT(*)
     FROM `project` p
     LEFT JOIN `user` u ON u.id = p.user_id AND u.is_delete = 0
     WHERE p.is_delete = 0 AND u.id IS NULL) AS active_orphan_projects,
    (SELECT COUNT(*)
     FROM `task` t
     LEFT JOIN `project` p ON p.id = t.project_id AND p.is_delete = 0
     WHERE t.is_delete = 0 AND p.id IS NULL) AS active_orphan_tasks,
    (SELECT COUNT(*)
     FROM `milestone` m
     LEFT JOIN `project` p ON p.id = m.project_id AND p.is_delete = 0
     WHERE m.is_delete = 0 AND p.id IS NULL) AS active_orphan_milestones,
    (SELECT COUNT(*)
     FROM `team_member` tm
     LEFT JOIN `team` te ON te.id = tm.team_id AND te.is_delete = 0
     LEFT JOIN `user` u ON u.id = tm.user_id AND u.is_delete = 0
     WHERE tm.is_delete = 0 AND (te.id IS NULL OR u.id IS NULL)) AS active_orphan_team_members;

SELECT
    (SELECT COUNT(*)
     FROM `project`
     WHERE LEFT(SHA2(CAST(id AS CHAR), 256), 12) = '10da6d2c6938'
       AND is_delete = 1
       AND deleted_at IS NOT NULL) AS repaired_project_count,
    (SELECT COUNT(*)
     FROM `task`
     WHERE LEFT(SHA2(CAST(id AS CHAR), 256), 12) IN (
               '80d94ad62151',
               '4693a21c3076'
           )
       AND is_delete = 1
       AND delete_source = 1) AS repaired_task_count,
    (SELECT COUNT(*)
     FROM `task`
     WHERE LEFT(SHA2(CAST(id AS CHAR), 256), 12) = '110aa8b40378'
       AND is_delete = 1
       AND delete_source = 1
       AND update_time = '2026-04-15 15:36:21') AS preexisting_deleted_task_unchanged;

SELECT 'user' AS table_name, COUNT(*) AS row_count FROM `user`
UNION ALL SELECT 'project', COUNT(*) FROM `project`
UNION ALL SELECT 'milestone', COUNT(*) FROM `milestone`
UNION ALL SELECT 'task', COUNT(*) FROM `task`
UNION ALL SELECT 'weekly_review', COUNT(*) FROM `weekly_review`;

SELECT
    (SELECT COUNT(*) FROM `user` WHERE is_delete NOT IN (0, 1)) AS invalid_user_delete_flag,
    (SELECT COUNT(*) FROM `project` WHERE is_delete NOT IN (0, 1)) AS invalid_project_delete_flag,
    (SELECT COUNT(*) FROM `task` WHERE is_delete NOT IN (0, 1)) AS invalid_task_delete_flag,
    (SELECT COUNT(*) FROM `task` WHERE status NOT IN (0, 1, 2, 3)) AS invalid_task_status,
    (SELECT COUNT(*) FROM `task` WHERE priority < 0) AS invalid_task_priority;
