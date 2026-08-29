-- D2-E: isolated fixture for transaction rollback and no-op tests.
INSERT INTO `user` (
    `id`, `account`, `username`, `password`, `user_role`,
    `create_time`, `update_time`, `is_delete`
) VALUES
    (16001, 'd2e_transaction_owner', 'D2E Transaction Owner', 'not-a-real-password-hash', 'USER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 0),
    (16002, 'd2e_transaction_bob', 'D2E Transaction Bob', 'not-a-real-password-hash', 'USER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 0),
    (16003, 'd2e_transaction_carol', 'D2E Transaction Carol', 'not-a-real-password-hash', 'USER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 0);

INSERT INTO `team` (
    `id`, `name`, `description`, `owner_id`, `invite_code`,
    `create_time`, `update_time`, `deleted_at`, `is_delete`
) VALUES
    (26001, 'D2E Transaction Team', 'D2-E transaction fixture', 16001, 'd2e-transaction-team',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0);

INSERT INTO `team_member` (
    `id`, `team_id`, `user_id`, `role`,
    `create_time`, `update_time`, `deleted_at`, `is_delete`
) VALUES
    (36001, 26001, 16001, 'OWNER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0),
    (36002, 26001, 16002, 'MEMBER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0),
    (36003, 26001, 16003, 'MEMBER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0);

INSERT INTO `project` (
    `id`, `user_id`, `team_id`, `name`, `status`, `order_no`, `progress`,
    `is_delete`, `create_time`, `update_time`, `deleted_at`
) VALUES
    (46001, 16001, 26001, 'D2E Transaction Project', 0, 0, 0.00,
        0, '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL);

INSERT INTO `task` (
    `id`, `project_id`, `milestone_id`, `user_id`, `title`, `description`,
    `status`, `priority`, `due_date`, `completed_at`, `deleted_at`, `delete_source`,
    `is_delete`, `create_time`, `update_time`, `assignee_user_id`,
    `assigned_by_user_id`, `assigned_at`
) VALUES
    (66001, 46001, NULL, 16001, 'D2E Transaction Task', 'transaction fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 16002,
        16001, '2026-01-01 00:00:00');
