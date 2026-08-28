-- WP4-B: deterministic V2 fixture for PermissionQueryMapper integration tests.
-- This file contains DML only. The schema must already be at Flyway version 2.

INSERT INTO `user` (
    `id`, `account`, `username`, `password`, `user_role`,
    `create_time`, `update_time`, `is_delete`
) VALUES
    (12001, 'wp4b_alice', 'WP4B Alice', 'not-a-real-password-hash', 'USER',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0),
    (12002, 'wp4b_bob', 'WP4B Bob', 'not-a-real-password-hash', 'USER',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0),
    (12003, 'wp4b_carol', 'WP4B Carol', 'not-a-real-password-hash', 'USER',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0),
    (12004, 'wp4b_admin', 'WP4B System Admin', 'not-a-real-password-hash', 'SYSTEM_ADMIN',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0),
    (12005, 'wp4b_deleted', 'WP4B Deleted User', 'not-a-real-password-hash', 'USER',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 1),
    (12006, 'wp4b_external', 'WP4B External User', 'not-a-real-password-hash', 'USER',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0),
    (12007, 'wp4b_exited', 'WP4B Exited Member', 'not-a-real-password-hash', 'USER',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0);

INSERT INTO `team` (
    `id`, `name`, `description`, `owner_id`, `invite_code`,
    `create_time`, `update_time`, `deleted_at`, `is_delete`
) VALUES
    (22001, 'WP4B Active Team', 'mapper integration fixture', 12001, 'wp4b-active-team',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL, 0),
    (22002, 'WP4B Deleted Team', 'mapper integration fixture', 12001, 'wp4b-deleted-team',
        '2026-01-01 00:00:00', '2026-01-02 00:00:00', '2026-01-02 00:00:00', 1),
    (22003, 'WP4B Inconsistent Owner Team', 'raw fact fixture', 12001, 'wp4b-inconsistent-team',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL, 0);

INSERT INTO `team_member` (
    `id`, `team_id`, `user_id`, `role`,
    `create_time`, `update_time`, `deleted_at`, `is_delete`
) VALUES
    (32001, 22001, 12001, 'OWNER',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL, 0),
    (32002, 22001, 12002, 'ADMIN',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL, 0),
    (32003, 22001, 12003, 'MEMBER',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL, 0),
    (32004, 22001, 12007, 'MEMBER',
        '2026-01-01 00:00:00', '2026-01-03 00:00:00', '2026-01-03 00:00:00', 1),
    (32005, 22002, 12001, 'OWNER',
        '2026-01-01 00:00:00', '2026-01-02 00:00:00', '2026-01-02 00:00:00', 1),
    (32006, 22002, 12002, 'MEMBER',
        '2026-01-01 00:00:00', '2026-01-02 00:00:00', '2026-01-02 00:00:00', 1),
    (32007, 22003, 12002, 'OWNER',
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL, 0);

INSERT INTO `project` (
    `id`, `user_id`, `team_id`, `name`, `status`, `order_no`, `progress`,
    `is_delete`, `create_time`, `update_time`, `deleted_at`
) VALUES
    (42001, 12001, NULL, 'WP4B Personal Project', 0, 0, 0.00,
        0, '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL),
    (42002, 12001, 22001, 'WP4B Active Team Project', 0, 0, 0.00,
        0, '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL),
    (42003, 12001, 22001, 'WP4B Deleted Project', 0, 0, 0.00,
        1, '2026-01-01 00:00:00', '2026-01-04 00:00:00', '2026-01-04 00:00:00'),
    (42004, 12001, 22002, 'WP4B Deleted Team Project', 0, 0, 0.00,
        0, '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL);

INSERT INTO `task` (
    `id`, `project_id`, `milestone_id`, `user_id`, `title`, `description`,
    `status`, `priority`, `due_date`, `completed_at`, `deleted_at`, `delete_source`,
    `is_delete`, `create_time`, `update_time`, `assignee_user_id`,
    `assigned_by_user_id`, `assigned_at`
) VALUES
    (62001, 42001, NULL, 12001, 'WP4B Personal Task', 'mapper fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 12001, 12001, '2026-01-01 00:00:00'),
    (62002, 42002, NULL, 12001, 'WP4B Member Task', 'mapper fixture',
        0, 1, NULL, NULL, NULL, 0, 0,
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 12003, 12001, '2026-01-01 00:00:00'),
    (62003, 42002, NULL, 12001, 'WP4B Admin Task', 'mapper fixture',
        0, 2, NULL, NULL, NULL, 0, 0,
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 12002, 12001, '2026-01-01 00:00:00'),
    (62004, 42002, NULL, 12001, 'WP4B Deleted Task', 'mapper fixture',
        2, 0, NULL, '2026-01-02 00:00:00', '2026-01-02 00:00:00', 1, 1,
        '2026-01-01 00:00:00', '2026-01-02 00:00:00', 12007, 12001, '2026-01-01 00:00:00'),
    (62005, 42004, NULL, 12001, 'WP4B Deleted Team Task', 'mapper fixture',
        0, 0, NULL, NULL, NULL, 0, 0,
        '2026-01-01 00:00:00', '2026-01-01 00:00:00', 12002, 12001, '2026-01-01 00:00:00');

INSERT INTO `weekly_review` (
    `id`, `user_id`, `year`, `week_no`, `start_date`, `end_date`,
    `completed_task_count`, `focus_project_name`, `reflection`, `next_plan`,
    `create_time`, `update_time`, `visibility_scope`, `team_id`,
    `focus_project_id`, `shared_summary`
) VALUES
    (72001, 12001, 2026, 1, '2025-12-29', '2026-01-04',
        1, 'WP4B Personal Project', 'PRIVATE_REFLECTION_72001', 'PRIVATE_PLAN_72001',
        '2026-01-05 00:00:00', '2026-01-05 00:00:00', 'PRIVATE', NULL, NULL, NULL),
    (72002, 12002, 2026, 1, '2025-12-29', '2026-01-04',
        2, 'WP4B Active Team Project', 'PRIVATE_REFLECTION_72002', 'PRIVATE_PLAN_72002',
        '2026-01-05 00:00:00', '2026-01-05 00:00:00', 'TEAM', 22001, 42002, 'TEAM_SUMMARY_72002'),
    (72003, 12007, 2026, 2, '2026-01-05', '2026-01-11',
        0, NULL, 'PRIVATE_REFLECTION_72003', 'PRIVATE_PLAN_72003',
        '2026-01-12 00:00:00', '2026-01-12 00:00:00', 'TEAM', 22001, NULL, 'TEAM_SUMMARY_72003'),
    (72004, 12001, 2026, 2, '2026-01-05', '2026-01-11',
        0, NULL, 'PRIVATE_REFLECTION_72004', 'PRIVATE_PLAN_72004',
        '2026-01-12 00:00:00', '2026-01-12 00:00:00', 'TEAM', 22002, NULL, 'TEAM_SUMMARY_72004');
