-- Stage 1 V1 fixture for deterministic V1 -> V2 migration verification.
-- This file contains test data only. It must run after the frozen V1 schema exists.
-- It intentionally does not create schema objects, Flyway history, credentials, or AI data.

INSERT INTO `user`
    (`id`, `account`, `username`, `password`, `user_role`, `create_time`, `update_time`, `is_delete`)
VALUES
    (1101, 'stage1_v2_owner', 'Stage1 Owner', 'not-a-real-password-hash', 'user', '2026-08-01 08:00:00', '2026-08-01 08:00:00', 0),
    (1102, 'stage1_v2_legacy_admin', 'Stage1 Legacy Admin', 'not-a-real-password-hash', 'admin', '2026-08-01 08:01:00', '2026-08-01 08:01:00', 0),
    (1103, 'stage1_v2_member', 'Stage1 Member', 'not-a-real-password-hash', 'USER', '2026-08-01 08:02:00', '2026-08-01 08:02:00', 0),
    (1104, 'stage1_v2_system_admin', 'Stage1 System Admin', 'not-a-real-password-hash', 'SYSTEM_ADMIN', '2026-08-01 08:03:00', '2026-08-01 08:03:00', 0),
    (1105, 'stage1_v2_departed_member', 'Stage1 Departed Member', 'not-a-real-password-hash', 'USER', '2026-08-01 08:04:00', '2026-08-01 08:04:00', 0);

INSERT INTO `team`
    (`id`, `name`, `description`, `owner_id`, `invite_code`, `create_time`, `update_time`, `deleted_at`, `is_delete`)
VALUES
    (2101, 'Stage1 Team', 'Deterministic V1 to V2 migration fixture', 1101, 'S1V2TEAM', '2026-08-01 08:10:00', '2026-08-01 08:10:00', NULL, 0);

INSERT INTO `team_member`
    (`id`, `team_id`, `user_id`, `role`, `create_time`, `update_time`, `deleted_at`, `is_delete`)
VALUES
    (3101, 2101, 1101, 'OWNER', '2026-08-01 08:11:00', '2026-08-01 08:11:00', NULL, 0),
    (3102, 2101, 1103, 'MEMBER', '2026-08-01 08:12:00', '2026-08-01 08:12:00', NULL, 0),
    (3103, 2101, 1105, 'MEMBER', '2026-08-01 08:13:00', '2026-08-05 09:00:00', '2026-08-05 09:00:00', 1);

INSERT INTO `project`
    (`id`, `user_id`, `team_id`, `name`, `icon`, `color`, `goal`, `status`, `order_no`, `progress`, `start_date`, `end_date`, `is_delete`, `create_time`, `update_time`, `deleted_at`)
VALUES
    (4101, 1101, NULL, 'Stage1 Personal Project', 'P', '#2563EB', 'Verify personal task fallback assignment', 0, 0, 20.00, '2026-08-01', '2026-08-31', 0, '2026-08-01 08:20:00', '2026-08-01 08:20:00', NULL),
    (4102, 1101, 2101, 'Stage1 Team Project', 'T', '#16A34A', 'Verify team task assignment history', 0, 0, 40.00, '2026-08-01', '2026-09-15', 0, '2026-08-01 08:21:00', '2026-08-01 08:21:00', NULL);

INSERT INTO `milestone`
    (`id`, `project_id`, `user_id`, `name`, `order_no`, `progress`, `deleted_at`, `delete_source`, `is_delete`, `create_time`, `update_time`)
VALUES
    (5101, 4101, 1101, 'Personal migration checks', 0, 20.00, NULL, 0, 0, '2026-08-01 08:30:00', '2026-08-01 08:30:00'),
    (5102, 4102, 1101, 'Team migration checks', 0, 40.00, NULL, 0, 0, '2026-08-01 08:31:00', '2026-08-01 08:31:00');

INSERT INTO `task`
    (`id`, `project_id`, `milestone_id`, `user_id`, `title`, `description`, `status`, `priority`, `due_date`, `completed_at`, `deleted_at`, `delete_source`, `is_delete`, `create_time`, `update_time`, `assignee_id`)
VALUES
    (6101, 4101, 5101, 1101, 'Verify personal fallback', 'V1 assignee is null and must fall back to the creator.', 0, 2, '2026-08-10', NULL, NULL, 0, 0, '2026-08-01 09:00:00', '2026-08-01 09:00:00', NULL),
    (6102, 4102, 5102, 1101, 'Verify team owner fallback', 'V1 assignee is null on a team task.', 0, 2, '2026-08-11', NULL, NULL, 0, 0, '2026-08-01 10:00:00', '2026-08-01 10:00:00', NULL),
    (6103, 4102, 5102, 1101, 'Verify explicit member assignment', 'V1 explicit assignee must be preserved.', 0, 1, '2026-08-12', NULL, NULL, 0, 0, '2026-08-01 11:00:00', '2026-08-01 11:00:00', 1103),
    (6104, 4102, 5102, 1101, 'Verify completed historical assignment', 'Completed work keeps the departed member history.', 2, 1, '2026-08-05', '2026-08-04 16:00:00', NULL, 0, 0, '2026-08-02 09:00:00', '2026-08-04 16:00:00', 1105),
    (6105, 4102, 5102, 1101, 'Verify deleted historical assignment', 'Deleted work remains auditable after migration.', 0, 0, '2026-08-06', NULL, '2026-08-05 09:00:00', 1, 1, '2026-08-03 09:00:00', '2026-08-05 09:00:00', 1105);

INSERT INTO `weekly_review`
    (`id`, `user_id`, `year`, `week_no`, `start_date`, `end_date`, `completed_task_count`, `focus_project_name`, `reflection`, `next_plan`, `create_time`, `update_time`)
VALUES
    (7101, 1101, 2026, 31, '2026-07-27', '2026-08-02', 2, 'Stage1 Personal Project', 'Fixture private reflection for owner.', 'Fixture private plan for owner.', '2026-08-02 18:00:00', '2026-08-02 18:00:00'),
    (7102, 1103, 2026, 31, '2026-07-27', '2026-08-02', 1, 'Stage1 Team Project', 'Fixture private reflection for member.', 'Fixture private plan for member.', '2026-08-02 18:01:00', '2026-08-02 18:01:00');
