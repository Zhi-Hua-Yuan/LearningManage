-- Stage 1 V2 post-verify. Read-only, deterministic, single result set.
-- Run immediately after V2 against the same database that passed preflight.
-- The checks reconcile the migration result without returning business正文.

WITH `v2_post_verify_checks` AS (
    SELECT
        'V2-V-001' AS `check_id`,
        'V2 relation tables present' AS `check_name`,
        ABS(2 - (
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN ('task_assignment_log', 'weekly_review_task')
        )) AS `violation_count`
    UNION ALL
    SELECT
        'V2-V-002',
        'task assignee rename complete',
        (
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'task'
              AND column_name = 'assignee_id'
        )
        + ABS(1 - (
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'task'
              AND column_name = 'assignee_user_id'
        ))
    UNION ALL
    SELECT
        'V2-V-003',
        'canonical system roles',
        (
            SELECT COUNT(*)
            FROM `user`
            WHERE user_role IS NULL
               OR BINARY user_role NOT IN ('USER', 'SYSTEM_ADMIN')
        )
    UNION ALL
    SELECT
        'V2-V-004',
        'task assignment metadata complete',
        (
            SELECT COUNT(*)
            FROM task
            WHERE assignee_user_id IS NULL
               OR assigned_by_user_id IS NULL
               OR assigned_at IS NULL
        )
    UNION ALL
    SELECT
        'V2-V-005',
        'initial log count matches assigned tasks',
        ABS(
            (
                SELECT COUNT(*)
                FROM task
                WHERE assignee_user_id IS NOT NULL
            )
            -
            (
                SELECT COUNT(*)
                FROM task_assignment_log
                WHERE BINARY action = BINARY 'INITIAL_ASSIGN'
            )
        )
    UNION ALL
    SELECT
        'V2-V-006',
        'initial log payload is deterministic',
        (
            SELECT COUNT(*)
            FROM task_assignment_log log_entry
            LEFT JOIN task t ON t.id = log_entry.task_id
            WHERE BINARY log_entry.action = BINARY 'INITIAL_ASSIGN'
              AND (
                  t.id IS NULL
                  OR log_entry.id <> t.id
                  OR log_entry.from_assignee_user_id IS NOT NULL
                  OR log_entry.to_assignee_user_id IS NULL
                  OR log_entry.to_assignee_user_id <> t.assignee_user_id
                  OR log_entry.assigned_by_user_id <> t.user_id
                  OR log_entry.reason IS NOT NULL
                  OR log_entry.create_time <> t.create_time
              )
        )
    UNION ALL
    SELECT
        'V2-V-007',
        'one initial log per task',
        (
            SELECT COUNT(*)
            FROM (
                SELECT task_id
                FROM task_assignment_log
                WHERE BINARY action = BINARY 'INITIAL_ASSIGN'
                GROUP BY task_id
                HAVING COUNT(*) <> 1
            ) duplicate_initial_logs
        )
    UNION ALL
    SELECT
        'V2-V-008',
        'legacy weekly reviews default private',
        (
            SELECT COUNT(*)
            FROM weekly_review
            WHERE BINARY visibility_scope <> BINARY 'PRIVATE'
               OR team_id IS NOT NULL
               OR focus_project_id IS NOT NULL
               OR shared_summary IS NOT NULL
        )
    UNION ALL
    SELECT
        'V2-V-009',
        'weekly review visibility invariant',
        (
            SELECT COUNT(*)
            FROM weekly_review
            WHERE BINARY visibility_scope NOT IN ('PRIVATE', 'TEAM')
               OR (
                   BINARY visibility_scope = BINARY 'PRIVATE'
                   AND team_id IS NOT NULL
               )
               OR (
                   BINARY visibility_scope = BINARY 'TEAM'
                   AND (
                       team_id IS NULL
                       OR shared_summary IS NULL
                       OR CHAR_LENGTH(TRIM(shared_summary)) = 0
                   )
               )
        )
    UNION ALL
    SELECT
        'V2-V-010',
        'weekly review task associations start empty',
        (
            SELECT COUNT(*)
            FROM weekly_review_task
        )
    UNION ALL
    SELECT
        'V2-V-011',
        'required V2 indexes present and V1 assignee index absent',
        (
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'task'
              AND index_name = 'idx_task_assignee_id'
        )
        + ABS(6 - (
            SELECT COUNT(DISTINCT index_name)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND (
                  (table_name = 'task' AND index_name IN (
                      'idx_task_assignee_status', 'idx_task_project_assignee'
                  ))
                  OR (table_name = 'weekly_review' AND index_name IN (
                      'idx_weekly_review_team_scope_time', 'idx_weekly_review_focus_project'
                  ))
                  OR (table_name = 'weekly_review_task' AND index_name IN (
                      'uk_weekly_review_task', 'idx_weekly_review_task_task'
                  ))
              )
        ))
    UNION ALL
    SELECT
        'V2-V-012',
        'initial logs have existing tasks',
        (
            SELECT COUNT(*)
            FROM task_assignment_log log_entry
            LEFT JOIN task t ON t.id = log_entry.task_id
            WHERE BINARY log_entry.action = BINARY 'INITIAL_ASSIGN'
              AND t.id IS NULL
        )
)
SELECT
    `check_id`,
    `check_name`,
    `violation_count`,
    CASE
        WHEN `violation_count` = 0 THEN 'PASS'
        ELSE 'FAIL'
    END AS `status`
FROM `v2_post_verify_checks`
ORDER BY `check_id`;
