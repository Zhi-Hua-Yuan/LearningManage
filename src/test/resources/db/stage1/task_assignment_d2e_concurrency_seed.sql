-- D2-E: isolated committed fixture for the two-writer CAS test.
INSERT INTO `user` (
    `id`, `account`, `username`, `password`, `user_role`,
    `create_time`, `update_time`, `is_delete`
) VALUES
    (15001, 'd2e_concurrency_owner', 'D2E Concurrency Owner', 'not-a-real-password-hash', 'USER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 0),
    (15002, 'd2e_concurrency_bob', 'D2E Concurrency Bob', 'not-a-real-password-hash', 'USER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 0),
    (15003, 'd2e_concurrency_carol', 'D2E Concurrency Carol', 'not-a-real-password-hash', 'USER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 0);

INSERT INTO `team` (
    `id`, `name`, `description`, `owner_id`, `invite_code`,
    `create_time`, `update_time`, `deleted_at`, `is_delete`
) VALUES
    (25001, 'D2E Concurrency Team', 'D2-E concurrency fixture', 15001, 'd2e-concurrency-team',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0);

INSERT INTO `team_member` (
    `id`, `team_id`, `user_id`, `role`,
    `create_time`, `update_time`, `deleted_at`, `is_delete`
) VALUES
    (35001, 25001, 15001, 'OWNER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0),
    (35002, 25001, 15002, 'MEMBER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0),
    (35003, 25001, 15003, 'MEMBER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0);

INSERT INTO `project` (
    `id`, `user_id`, `team_id`, `name`, `status`, `order_no`, `progress`,
    `is_delete`, `create_time`, `update_time`, `deleted_at`
) VALUES
    (45001, 15001, 25001, 'D2E Concurrency Project', 0, 0, 0.00,
        0, '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL);

INSERT INTO `task` (
    `id`, `project_id`, `milestone_id`, `user_id`, `title`, `description`,
    `status`, `priority`, `due_date`, `completed_at`, `deleted_at`, `delete_source`,
    `is_delete`, `create_time`, `update_time`, `assignee_user_id`,
    `assigned_by_user_id`, `assigned_at`
) VALUES
    (65001, 45001, NULL, 15001, 'D2E Concurrent Task', 'concurrency fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 15001,
        15001, '2026-02-01 10:00:00');
