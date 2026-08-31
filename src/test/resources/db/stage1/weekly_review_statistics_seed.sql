INSERT INTO `user` (
    `id`, `account`, `username`, `password`, `user_role`,
    `create_time`, `update_time`, `is_delete`
) VALUES
    (13001, 'c4_stats_owner', 'C4 Stats Owner', 'test-only', 'USER',
        '2026-08-01 00:00:00', '2026-08-01 00:00:00', 0),
    (13002, 'c4_stats_assignee', 'C4 Stats Assignee', 'test-only', 'USER',
        '2026-08-01 00:00:00', '2026-08-01 00:00:00', 0);

INSERT INTO `project` (
    `id`, `user_id`, `team_id`, `name`, `status`, `order_no`, `progress`,
    `is_delete`, `create_time`, `update_time`, `deleted_at`
) VALUES
    (43001, 13001, NULL, 'C4 Stats Project A', 0, 0, 0.00,
        0, '2026-08-01 00:00:00', '2026-08-01 00:00:00', NULL),
    (43002, 13001, NULL, 'C4 Stats Project B', 0, 1, 0.00,
        0, '2026-08-01 00:00:00', '2026-08-01 00:00:00', NULL);

INSERT INTO `task` (
    `id`, `project_id`, `milestone_id`, `user_id`, `title`, `description`,
    `status`, `priority`, `due_date`, `completed_at`, `deleted_at`, `delete_source`,
    `is_delete`, `create_time`, `update_time`, `assignee_user_id`,
    `assigned_by_user_id`, `assigned_at`
) VALUES
    (63001, 43001, NULL, 13001, 'created by owner assigned to member', 'c4',
        2, 0, NULL, '2026-08-10 10:00:00', NULL, 0, 0,
        '2026-08-01 00:00:00', '2026-08-10 10:00:00', 13002, 13001, '2026-08-01 00:00:00'),
    (63002, 43002, NULL, 13001, 'created by owner assigned to member two', 'c4',
        1, 0, NULL, '2026-08-10 11:00:00', NULL, 0, 0,
        '2026-08-01 00:00:00', '2026-08-10 11:00:00', 13002, 13001, '2026-08-01 00:00:00'),
    (63003, 43001, NULL, 13002, 'created by member assigned to owner', 'c4',
        3, 0, NULL, '2026-08-10 12:00:00', NULL, 0, 0,
        '2026-08-01 00:00:00', '2026-08-10 12:00:00', 13001, 13002, '2026-08-01 00:00:00'),
    (63004, 43001, NULL, 13002, 'todo is not completed', 'c4',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-08-01 00:00:00', '2026-08-01 00:00:00', 13002, 13002, '2026-08-01 00:00:00'),
    (63005, 43001, NULL, 13002, 'outside week', 'c4',
        2, 0, NULL, '2026-08-17 10:00:00', NULL, 0, 0,
        '2026-08-01 00:00:00', '2026-08-17 10:00:00', 13002, 13002, '2026-08-01 00:00:00'),
    (63006, 43001, NULL, 13002, 'deleted completed task', 'c4',
        2, 0, NULL, '2026-08-10 13:00:00', '2026-08-10 13:00:00', 1, 1,
        '2026-08-01 00:00:00', '2026-08-10 13:00:00', 13002, 13002, '2026-08-01 00:00:00');
