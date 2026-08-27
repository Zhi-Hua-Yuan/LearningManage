-- Negative V2 preflight fixture: V2-P-021 must fail.
-- The project, creator and milestone are valid; only assignee_id is orphaned.
-- Apply after v1_to_v2_seed.sql in an isolated V1 database.

INSERT INTO `task`
    (`id`, `project_id`, `milestone_id`, `user_id`, `title`, `description`, `status`, `priority`, `due_date`, `completed_at`, `deleted_at`, `delete_source`, `is_delete`, `create_time`, `update_time`, `assignee_id`)
VALUES
    (6192, 4102, 5102, 1101, 'Reject orphan assignee', 'The assignee user record does not exist.', 0, 1, '2026-08-13', NULL, NULL, 0, 0, '2026-08-06 09:00:00', '2026-08-06 09:00:00', 999999);
