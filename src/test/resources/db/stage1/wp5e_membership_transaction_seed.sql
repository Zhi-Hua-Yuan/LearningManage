-- WP5-E: isolated transaction and reconciliation fixture.
DELETE l
FROM task_assignment_log l
JOIN task t ON t.id = l.task_id
WHERE t.project_id = 48001;
DELETE i
FROM task_status_idempotency i
JOIN task t ON t.id = i.task_id
WHERE t.project_id = 48001;
DELETE FROM task WHERE project_id = 48001;
DELETE FROM project WHERE id = 48001;
DELETE FROM team_member WHERE team_id = 28001;
DELETE FROM team WHERE id = 28001;
DELETE FROM user WHERE id BETWEEN 18001 AND 18004;

INSERT INTO `user` (id, account, username, password, user_role,
                    create_time, update_time, is_delete)
VALUES
    (18001, 'wp5e_owner', 'WP5E Owner', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (18002, 'wp5e_admin', 'WP5E Admin', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (18003, 'wp5e_target', 'WP5E Target', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (18004, 'wp5e_completed_only', 'WP5E Completed Only', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0);

INSERT INTO team (id, name, description, owner_id, invite_code,
                  create_time, update_time, deleted_at, is_delete)
VALUES (28001, 'WP5E Team', 'transaction reconciliation fixture', 18001,
        'wp5e-team', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0);

INSERT INTO team_member (id, team_id, user_id, role,
                         create_time, update_time, deleted_at, is_delete)
VALUES
    (38001, 28001, 18001, 'OWNER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (38002, 28001, 18002, 'ADMIN', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (38003, 28001, 18003, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (38004, 28001, 18004, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0);

INSERT INTO project (id, user_id, team_id, name, status, order_no, progress,
                     is_delete, create_time, update_time, deleted_at)
VALUES (48001, 18001, 28001, 'WP5E Project', 0, 0, 0.00, 0,
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL);

INSERT INTO task (id, project_id, milestone_id, user_id, title, description,
                  status, priority, due_date, completed_at, deleted_at,
                  delete_source, is_delete, create_time, update_time,
                  assignee_user_id, assigned_by_user_id, assigned_at)
VALUES
    (68001, 48001, NULL, 18001, 'WP5E incomplete target one', 'fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 18003, 18001,
        '2026-08-29 00:00:00'),
    (68002, 48001, NULL, 18001, 'WP5E incomplete target two', 'fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 18003, 18001,
        '2026-08-29 00:00:00'),
    (68003, 48001, NULL, 18001, 'WP5E completed target', 'fixture',
        1, 0, NULL, '2026-08-29 02:00:00', NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 02:00:00', 18003, 18001,
        '2026-08-29 00:00:00'),
    (68004, 48001, NULL, 18001, 'WP5E completed other member', 'fixture',
        1, 0, NULL, '2026-08-29 02:00:00', NULL, 0, 0,
        '2026-08-29 00:00:00', '2026-08-29 02:00:00', 18004, 18001,
        '2026-08-29 00:00:00');

INSERT INTO task_assignment_log
    (id, task_id, from_assignee_user_id, to_assignee_user_id,
     assigned_by_user_id, action, reason, create_time)
VALUES
    (78001, 68001, NULL, 18003, 18001, 'INITIAL_ASSIGN', NULL,
        '2026-08-29 00:00:00'),
    (78002, 68002, NULL, 18003, 18001, 'INITIAL_ASSIGN', NULL,
        '2026-08-29 00:00:00'),
    (78003, 68003, NULL, 18003, 18001, 'INITIAL_ASSIGN', NULL,
        '2026-08-29 00:00:00'),
    (78004, 68004, NULL, 18004, 18001, 'INITIAL_ASSIGN', NULL,
        '2026-08-29 00:00:00');
