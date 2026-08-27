-- Negative V2 preflight fixture: V2-P-032 must fail.
-- The assignee user exists but has never joined team 2101.
-- Apply after v1_to_v2_seed.sql in an isolated V1 database.

INSERT INTO `user`
    (`id`, `account`, `username`, `password`, `user_role`, `create_time`, `update_time`, `is_delete`)
VALUES
    (1193, 'stage1_v2_external_user', 'Stage1 External User', 'not-a-real-password-hash', 'USER', '2026-08-06 08:30:00', '2026-08-06 08:30:00', 0);

INSERT INTO `task`
    (`id`, `project_id`, `milestone_id`, `user_id`, `title`, `description`, `status`, `priority`, `due_date`, `completed_at`, `deleted_at`, `delete_source`, `is_delete`, `create_time`, `update_time`, `assignee_id`)
VALUES
    (6193, 4102, 5102, 1101, 'Reject non-member assignee', 'The assignee user exists but is not a member of team 2101.', 0, 1, '2026-08-14', NULL, NULL, 0, 0, '2026-08-06 10:00:00', '2026-08-06 10:00:00', 1193);
