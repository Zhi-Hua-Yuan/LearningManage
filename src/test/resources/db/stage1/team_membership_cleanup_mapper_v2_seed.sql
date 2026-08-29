-- WP5-B: deterministic V2 DML fixture for membership cleanup Mapper tests.

INSERT INTO `user` (
    id, account, username, password, user_role,
    create_time, update_time, is_delete
) VALUES
    (16001, 'wp5b_owner', 'WP5B Owner', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (16002, 'wp5b_admin', 'WP5B Admin', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (16003, 'wp5b_target', 'WP5B Target', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (16004, 'wp5b_other', 'WP5B Other', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0),
    (16005, 'wp5b_exited', 'WP5B Exited', 'not-a-real-password-hash', 'USER',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', 0);

INSERT INTO team (
    id, name, description, owner_id, invite_code,
    create_time, update_time, deleted_at, is_delete
) VALUES
    (26001, 'WP5B Team A', 'mapper fixture', 16001, 'wp5b-team-a',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (26002, 'WP5B Team B', 'cross-team fixture', 16005, 'wp5b-team-b',
        '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0);

INSERT INTO team_member (
    id, team_id, user_id, role,
    create_time, update_time, deleted_at, is_delete
) VALUES
    (36001, 26001, 16001, 'OWNER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (36002, 26001, 16002, 'ADMIN', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (36003, 26001, 16003, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (36004, 26001, 16004, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (36005, 26001, 16005, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', '2026-08-29 01:00:00', 1),
    (36006, 26002, 16003, 'MEMBER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0),
    (36007, 26002, 16005, 'OWNER', '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL, 0);

INSERT INTO project (
    id, user_id, team_id, name, status, order_no, progress,
    is_delete, create_time, update_time, deleted_at
) VALUES
    (46001, 16001, 26001, 'WP5B Active Project', 0, 0, 0.00,
        0, '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL),
    (46002, 16001, 26001, 'WP5B Archived Project', 1, 1, 0.00,
        0, '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL),
    (46003, 16001, 26001, 'WP5B Deleted Project', 0, 2, 0.00,
        1, '2026-08-29 00:00:00', '2026-08-29 01:00:00', '2026-08-29 01:00:00'),
    (46004, 16005, 26002, 'WP5B Other Team Project', 0, 0, 0.00,
        0, '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL),
    (46005, 16003, NULL, 'WP5B Personal Project', 0, 0, 0.00,
        0, '2026-08-29 00:00:00', '2026-08-29 00:00:00', NULL);

INSERT INTO task (
    id, project_id, milestone_id, user_id, title, description,
    status, priority, due_date, completed_at, deleted_at, delete_source,
    is_delete, create_time, update_time, assignee_user_id,
    assigned_by_user_id, assigned_at
) VALUES
    (66001, 46001, NULL, 16001, 'WP5B active incomplete', 'fixture',
        0, 0, NULL, NULL, NULL, 0, 0, '2026-08-29 00:00:00', '2026-08-29 00:00:00', 16003, 16001, '2026-08-29 00:00:00'),
    (66002, 46001, NULL, 16001, 'WP5B deleted incomplete', 'fixture',
        0, 0, NULL, NULL, '2026-08-29 01:00:00', 1, 1, '2026-08-29 00:00:00', '2026-08-29 01:00:00', 16003, 16001, '2026-08-29 00:00:00'),
    (66003, 46002, NULL, 16001, 'WP5B archived incomplete', 'fixture',
        0, 0, NULL, NULL, NULL, 0, 0, '2026-08-29 00:00:00', '2026-08-29 00:00:00', 16003, 16001, '2026-08-29 00:00:00'),
    (66004, 46003, NULL, 16001, 'WP5B deleted project incomplete', 'fixture',
        0, 0, NULL, NULL, '2026-08-29 01:00:00', 1, 0, '2026-08-29 00:00:00', '2026-08-29 01:00:00', 16003, 16001, '2026-08-29 00:00:00'),
    (66005, 46001, NULL, 16001, 'WP5B completed basic', 'fixture',
        1, 0, NULL, '2026-08-29 02:00:00', NULL, 0, 0, '2026-08-29 00:00:00', '2026-08-29 02:00:00', 16003, 16001, '2026-08-29 00:00:00'),
    (66006, 46001, NULL, 16001, 'WP5B completed standard', 'fixture',
        2, 0, NULL, '2026-08-29 02:00:00', NULL, 0, 0, '2026-08-29 00:00:00', '2026-08-29 02:00:00', 16003, 16001, '2026-08-29 00:00:00'),
    (66007, 46001, NULL, 16001, 'WP5B completed excellent', 'fixture',
        3, 0, NULL, '2026-08-29 02:00:00', NULL, 0, 0, '2026-08-29 00:00:00', '2026-08-29 02:00:00', 16003, 16001, '2026-08-29 00:00:00'),
    (66008, 46004, NULL, 16005, 'WP5B other team task', 'fixture',
        0, 0, NULL, NULL, NULL, 0, 0, '2026-08-29 00:00:00', '2026-08-29 00:00:00', 16003, 16005, '2026-08-29 00:00:00'),
    (66009, 46005, NULL, 16003, 'WP5B personal task', 'fixture',
        0, 0, NULL, NULL, NULL, 0, 0, '2026-08-29 00:00:00', '2026-08-29 00:00:00', 16003, 16003, '2026-08-29 00:00:00'),
    (66010, 46001, NULL, 16001, 'WP5B other assignee task', 'fixture',
        0, 0, NULL, NULL, NULL, 0, 0, '2026-08-29 00:00:00', '2026-08-29 00:00:00', 16004, 16001, '2026-08-29 00:00:00');
