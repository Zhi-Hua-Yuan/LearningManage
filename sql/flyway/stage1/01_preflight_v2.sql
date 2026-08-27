-- Stage 1 V2 preflight. Read-only, deterministic, single result set.
-- Run against a database containing the frozen V1 schema before V2 migration.

WITH `v2_preflight_checks` AS (
    SELECT
        'V2-P-001' AS `check_id`,
        'required V1 business tables' AS `check_name`,
        ABS(20 - (
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                  'user', 'tenant', 'role', 'permission', 'role_permission', 'user_role',
                  'team', 'team_member', 'project', 'milestone', 'task', 'weekly_review',
                  'prompt_template', 'ai_call_log', 'ai_draft', 'ai_draft_confirm_log',
                  'ai_replan_operation', 'ai_replan_item', 'task_status_idempotency',
                  'task_title_rename_log'
              )
        )) AS `violation_count`
    UNION ALL
    SELECT
        'V2-P-002',
        'V1 task assignee column',
        ABS(1 - (
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'task'
              AND column_name = 'assignee_id'
        ))
    UNION ALL
    SELECT
        'V2-P-003',
        'V1 task assignee index',
        ABS(1 - (
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'task'
              AND index_name = 'idx_task_assignee_id'
        ))
    UNION ALL
    SELECT
        'V2-P-004',
        'V2 task assignee column absent',
        (
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'task'
              AND column_name = 'assignee_user_id'
        )
    UNION ALL
    SELECT
        'V2-P-005',
        'V2 relation tables absent',
        (
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN ('task_assignment_log', 'weekly_review_task')
        )
    UNION ALL
    SELECT
        'V2-P-006',
        'V2 weekly review columns absent',
        (
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'weekly_review'
              AND column_name IN ('visibility_scope', 'team_id', 'focus_project_id', 'shared_summary')
        )
    UNION ALL
    SELECT
        'V2-P-010',
        'allowed system role values',
        (
            SELECT COUNT(*)
            FROM `user`
            WHERE user_role IS NOT NULL
              AND BINARY TRIM(user_role) NOT IN ('user', 'admin', 'USER', 'SYSTEM_ADMIN')
        )
    UNION ALL
    SELECT
        'V2-P-011',
        'nonblank system role',
        (
            SELECT COUNT(*)
            FROM `user`
            WHERE user_role IS NULL
               OR BINARY TRIM(user_role) = ''
        )
    UNION ALL
    SELECT
        'V2-P-012',
        'system role has no surrounding whitespace',
        (
            SELECT COUNT(*)
            FROM `user`
            WHERE user_role IS NOT NULL
              AND BINARY user_role <> BINARY TRIM(user_role)
        )
    UNION ALL
    SELECT
        'V2-P-020',
        'task creator record exists',
        (
            SELECT COUNT(*)
            FROM task t
            LEFT JOIN `user` u ON u.id = t.user_id
            WHERE u.id IS NULL
        )
    UNION ALL
    SELECT
        'V2-P-021',
        'derived task assignee record exists',
        (
            SELECT COUNT(*)
            FROM task t
            LEFT JOIN `user` u ON u.id = COALESCE(t.assignee_id, t.user_id)
            WHERE u.id IS NULL
        )
    UNION ALL
    SELECT
        'V2-P-022',
        'task project record exists',
        (
            SELECT COUNT(*)
            FROM task t
            LEFT JOIN project p ON p.id = t.project_id
            WHERE p.id IS NULL
        )
    UNION ALL
    SELECT
        'V2-P-023',
        'task milestone record exists',
        (
            SELECT COUNT(*)
            FROM task t
            LEFT JOIN milestone m ON m.id = t.milestone_id
            WHERE t.milestone_id IS NOT NULL
              AND m.id IS NULL
        )
    UNION ALL
    SELECT
        'V2-P-024',
        'task milestone belongs to project',
        (
            SELECT COUNT(*)
            FROM task t
            JOIN milestone m ON m.id = t.milestone_id
            WHERE t.milestone_id IS NOT NULL
              AND m.project_id <> t.project_id
        )
    UNION ALL
    SELECT
        'V2-P-025',
        'active incomplete task has live project',
        (
            SELECT COUNT(*)
            FROM task t
            JOIN project p ON p.id = t.project_id
            WHERE t.is_delete = 0
              AND t.status = 0
              AND p.is_delete <> 0
        )
    UNION ALL
    SELECT
        'V2-P-026',
        'active team task has live team',
        (
            SELECT COUNT(*)
            FROM task t
            JOIN project p ON p.id = t.project_id
            LEFT JOIN team tm ON tm.id = p.team_id
            WHERE t.is_delete = 0
              AND t.status = 0
              AND p.team_id IS NOT NULL
              AND (tm.id IS NULL OR tm.is_delete <> 0)
        )
    UNION ALL
    SELECT
        'V2-P-030',
        'active incomplete assignee is live user',
        (
            SELECT COUNT(*)
            FROM task t
            JOIN `user` u ON u.id = COALESCE(t.assignee_id, t.user_id)
            WHERE t.is_delete = 0
              AND t.status = 0
              AND u.is_delete <> 0
        )
    UNION ALL
    SELECT
        'V2-P-031',
        'personal task assignee is project owner',
        (
            SELECT COUNT(*)
            FROM task t
            JOIN project p ON p.id = t.project_id
            WHERE p.team_id IS NULL
              AND COALESCE(t.assignee_id, t.user_id) <> p.user_id
        )
    UNION ALL
    SELECT
        'V2-P-032',
        'active team task assignee is live member',
        (
            SELECT COUNT(*)
            FROM task t
            JOIN project p ON p.id = t.project_id
            JOIN `user` u ON u.id = COALESCE(t.assignee_id, t.user_id)
            WHERE t.is_delete = 0
              AND t.status = 0
              AND p.team_id IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM team_member tm
                  WHERE tm.team_id = p.team_id
                    AND tm.user_id = u.id
                    AND tm.is_delete = 0
              )
        )
    UNION ALL
    SELECT
        'V2-P-033',
        'historical team task assignee has team history',
        (
            SELECT COUNT(*)
            FROM task t
            JOIN project p ON p.id = t.project_id
            JOIN `user` u ON u.id = COALESCE(t.assignee_id, t.user_id)
            WHERE (t.is_delete <> 0 OR t.status IN (1, 2, 3))
              AND p.team_id IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM team_member tm
                  WHERE tm.team_id = p.team_id
                    AND tm.user_id = u.id
              )
        )
    UNION ALL
    SELECT
        'V2-P-040',
        'weekly review author record exists',
        (
            SELECT COUNT(*)
            FROM weekly_review review
            LEFT JOIN `user` u ON u.id = review.user_id
            WHERE u.id IS NULL
        )
    UNION ALL
    SELECT
        'V2-P-041',
        'weekly review year and week are valid',
        (
            SELECT COUNT(*)
            FROM weekly_review
            WHERE `year` <= 0
               OR `week_no` NOT BETWEEN 1 AND 53
        )
    UNION ALL
    SELECT
        'V2-P-042',
        'weekly review date range is valid',
        (
            SELECT COUNT(*)
            FROM weekly_review
            WHERE end_date < start_date
        )
    UNION ALL
    SELECT
        'V2-P-043',
        'weekly review user week is unique',
        (
            SELECT COUNT(*)
            FROM (
                SELECT user_id, `year`, `week_no`
                FROM weekly_review
                GROUP BY user_id, `year`, `week_no`
                HAVING COUNT(*) > 1
            ) duplicate_reviews
        )
    UNION ALL
    SELECT
        'V2-P-044',
        'weekly review unique index exists',
        ABS(1 - (
            SELECT COUNT(DISTINCT index_name)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'weekly_review'
              AND index_name = 'uk_weekly_review_user_year_week'
        ))
)
SELECT
    `check_id`,
    `check_name`,
    `violation_count`,
    CASE
        WHEN `violation_count` = 0 THEN 'PASS'
        ELSE 'FAIL'
    END AS `status`
FROM `v2_preflight_checks`
ORDER BY `check_id`;
