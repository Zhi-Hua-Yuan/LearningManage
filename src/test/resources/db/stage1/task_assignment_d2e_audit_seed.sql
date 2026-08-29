-- D2-E: coherent assignment history used by reconciliation tests.
INSERT INTO `user` (
    `id`, `account`, `username`, `password`, `user_role`,
    `create_time`, `update_time`, `is_delete`
) VALUES
    (14001, 'd2e_owner', 'D2E Owner', 'not-a-real-password-hash', 'USER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 0),
    (14002, 'd2e_member', 'D2E Member', 'not-a-real-password-hash', 'USER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 0);

INSERT INTO `team` (
    `id`, `name`, `description`, `owner_id`, `invite_code`,
    `create_time`, `update_time`, `deleted_at`, `is_delete`
) VALUES
    (24001, 'D2E Team', 'D2-E audit fixture', 14001, 'd2e-team',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0);

INSERT INTO `team_member` (
    `id`, `team_id`, `user_id`, `role`,
    `create_time`, `update_time`, `deleted_at`, `is_delete`
) VALUES
    (34001, 24001, 14001, 'OWNER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0),
    (34002, 24001, 14002, 'MEMBER',
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL, 0);

INSERT INTO `project` (
    `id`, `user_id`, `team_id`, `name`, `status`, `order_no`, `progress`,
    `is_delete`, `create_time`, `update_time`, `deleted_at`
) VALUES
    (44001, 14001, 24001, 'D2E Team Project', 0, 0, 0.00,
        0, '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL);

INSERT INTO `task` (
    `id`, `project_id`, `milestone_id`, `user_id`, `title`, `description`,
    `status`, `priority`, `due_date`, `completed_at`, `deleted_at`, `delete_source`,
    `is_delete`, `create_time`, `update_time`, `assignee_user_id`,
    `assigned_by_user_id`, `assigned_at`
) VALUES
    (64001, 44001, NULL, 14001, 'D2E Reconciled Task', 'audit fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', 14002,
        14002, '2026-02-01 10:03:00'),
    (64002, 44001, NULL, 14001, 'D2E Unassigned Task', 'audit fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-02-01 00:00:00', '2026-02-01 00:00:00', NULL,
        NULL, NULL);

INSERT INTO `task_assignment_log` (
    `id`, `task_id`, `from_assignee_user_id`, `to_assignee_user_id`,
    `assigned_by_user_id`, `action`, `reason`, `create_time`
) VALUES
    (940001, 64001, NULL, 14001, 14001, 'INITIAL_ASSIGN', 'initial',
        '2026-02-01 10:00:00'),
    (940002, 64001, 14001, 14002, 14001, 'REASSIGN', 'handoff',
        '2026-02-01 10:01:00'),
    (940003, 64001, 14002, NULL, 14002, 'UNASSIGN', 'pause',
        '2026-02-01 10:02:00'),
    (940004, 64001, NULL, 14002, 14002, 'ASSIGN', 'resume',
        '2026-02-01 10:03:00');
