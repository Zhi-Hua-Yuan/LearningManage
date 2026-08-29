-- WP5-F: isolated MySQL termination-concurrency fixture.
DELETE l
FROM task_assignment_log l
JOIN task t ON t.id = l.task_id
WHERE t.project_id = 49001;
DELETE i
FROM task_status_idempotency i
JOIN task t ON t.id = i.task_id
WHERE t.project_id = 49001;
DELETE FROM task WHERE project_id = 49001;
DELETE FROM project WHERE id = 49001;
DELETE FROM team_member WHERE team_id = 29001;
DELETE FROM team WHERE id = 29001;
DELETE FROM user WHERE id BETWEEN 19001 AND 19004;

INSERT INTO `user` (id, account, username, password, user_role,
                    create_time, update_time, is_delete)
VALUES
    (19001, 'wp5f_owner', 'WP5F Owner', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (19002, 'wp5f_admin', 'WP5F Admin', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (19003, 'wp5f_target', 'WP5F Target', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (19004, 'wp5f_other', 'WP5F Other', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0);

INSERT INTO team (id, name, description, owner_id, invite_code,
                  create_time, update_time, deleted_at, is_delete)
VALUES (29001, 'WP5F Team', 'final termination concurrency fixture', 19001,
        'wp5f-team', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0);

INSERT INTO team_member (id, team_id, user_id, role,
                         create_time, update_time, deleted_at, is_delete)
VALUES
    (39001, 29001, 19001, 'OWNER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (39002, 29001, 19002, 'ADMIN', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (39003, 29001, 19003, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (39004, 29001, 19004, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0);

INSERT INTO project (id, user_id, team_id, name, status, order_no, progress,
                     is_delete, create_time, update_time, deleted_at)
VALUES (49001, 19001, 29001, 'WP5F Project', 0, 0, 0.00, 0,
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL);

INSERT INTO task (id, project_id, milestone_id, user_id, title, description,
                  status, priority, due_date, completed_at, deleted_at,
                  delete_source, is_delete, create_time, update_time,
                  assignee_user_id, assigned_by_user_id, assigned_at)
VALUES
    (69001, 49001, NULL, 19001, 'WP5F incomplete target', 'fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 19003, 19001,
        '2026-08-29 00:00:00'),
    (69002, 49001, NULL, 19001, 'WP5F completed target', 'fixture',
        1, 0, NULL, '2026-08-29 02:00:00', NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 02:00:00', 19003, 19001,
        '2026-08-29 00:00:00'),
    (69003, 49001, NULL, 19001, 'WP5F incomplete other', 'fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 19004, 19001,
        '2026-08-29 00:00:00');

INSERT INTO task_assignment_log
    (id, task_id, from_assignee_user_id, to_assignee_user_id,
     assigned_by_user_id, action, reason, create_time)
VALUES
    (79001, 69001, NULL, 19003, 19001, 'INITIAL_ASSIGN', NULL,
        '2026-08-29 00:00:00'),
    (79002, 69002, NULL, 19003, 19001, 'INITIAL_ASSIGN', NULL,
        '2026-08-29 00:00:00'),
    (79003, 69003, NULL, 19004, 19001, 'INITIAL_ASSIGN', NULL,
        '2026-08-29 00:00:00');
