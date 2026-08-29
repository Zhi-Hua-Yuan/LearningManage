-- WP5-D4: isolated MySQL concurrency fixture.
-- The script is intentionally scoped to the WP5-D4 ID range and project.
DELETE l
FROM task_assignment_log l
JOIN task t ON t.id = l.task_id
WHERE t.project_id = 47001;
DELETE i
FROM task_status_idempotency i
JOIN task t ON t.id = i.task_id
WHERE t.project_id = 47001;
DELETE FROM task WHERE project_id = 47001;
DELETE FROM project WHERE id = 47001;
DELETE FROM team_member WHERE team_id = 27001;
DELETE FROM team WHERE id = 27001;
DELETE FROM user WHERE id BETWEEN 17001 AND 17005;

INSERT INTO `user` (id, account, username, password, user_role,
                    create_time, update_time, is_delete)
VALUES
    (17001, 'wp5d4_owner', 'WP5D4 Owner', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (17002, 'wp5d4_admin', 'WP5D4 Admin', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (17003, 'wp5d4_member', 'WP5D4 Member', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (17004, 'wp5d4_target', 'WP5D4 Target', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (17005, 'wp5d4_observer', 'WP5D4 Observer', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0);

INSERT INTO team (id, name, description, owner_id, invite_code,
                  create_time, update_time, deleted_at, is_delete)
VALUES (27001, 'WP5D4 Team', 'task membership concurrency fixture', 17001,
        'wp5d4-team', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0);

INSERT INTO team_member (id, team_id, user_id, role,
                         create_time, update_time, deleted_at, is_delete)
VALUES
    (37001, 27001, 17001, 'OWNER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (37002, 27001, 17002, 'ADMIN', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (37003, 27001, 17003, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (37004, 27001, 17004, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (37005, 27001, 17005, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0);

INSERT INTO project (id, user_id, team_id, name, status, order_no, progress,
                     is_delete, create_time, update_time, deleted_at)
VALUES (47001, 17001, 27001, 'WP5D4 Project', 0, 0, 0.00, 0,
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL);

INSERT INTO task (id, project_id, milestone_id, user_id, title, description,
                  status, priority, due_date, completed_at, deleted_at,
                  delete_source, is_delete, create_time, update_time,
                  assignee_user_id, assigned_by_user_id, assigned_at)
VALUES
    (67001, 47001, NULL, 17001, 'WP5D4 assignment race', 'fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 17004, 17001,
        '2026-08-29 00:00:00'),
    (67002, 47001, NULL, 17001, 'WP5D4 reopen leave race', 'fixture',
        1, 0, NULL, '2026-08-29 02:00:00', NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 02:00:00', 17003, 17001,
        '2026-08-29 00:00:00'),
    (67003, 47001, NULL, 17001, 'WP5D4 reopen reassign race', 'fixture',
        1, 0, NULL, '2026-08-29 02:00:00', NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 02:00:00', 17003, 17001,
        '2026-08-29 00:00:00'),
    (67004, 47001, NULL, 17001, 'WP5D4 empty assignee race', 'fixture',
        1, 0, NULL, '2026-08-29 02:00:00', NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 02:00:00', NULL, 17001,
        NULL);

INSERT INTO task_assignment_log
    (id, task_id, from_assignee_user_id, to_assignee_user_id,
     assigned_by_user_id, action, reason, create_time)
VALUES
    (77001, 67001, NULL, 17004, 17001, 'INITIAL_ASSIGN', NULL,
        '2026-08-29 00:00:00'),
    (77002, 67002, NULL, 17003, 17001, 'INITIAL_ASSIGN', NULL,
        '2026-08-29 00:00:00'),
    (77003, 67003, NULL, 17003, 17001, 'INITIAL_ASSIGN', NULL,
        '2026-08-29 00:00:00');
