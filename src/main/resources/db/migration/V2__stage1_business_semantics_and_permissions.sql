-- Flyway V2: Stage 1 business semantics and assignment foundations.
-- Input: the published V1 schema and a successful V2 preflight.
-- This migration does not enable tenant RBAC, create foreign keys, or modify
-- AI/vector/Redis structures.

-- ============================================================================
-- 1. Canonical system roles
-- ============================================================================

ALTER TABLE `user`
    ADD CONSTRAINT `chk_user_system_role_transition`
    CHECK (
        BINARY `user_role` IN ('user', 'admin', 'USER', 'SYSTEM_ADMIN')
    );

UPDATE `user`
SET `user_role` = CASE BINARY `user_role`
    WHEN BINARY 'user' THEN 'USER'
    WHEN BINARY 'admin' THEN 'SYSTEM_ADMIN'
    ELSE `user_role`
END
WHERE BINARY `user_role` IN ('user', 'admin');

ALTER TABLE `user`
    MODIFY COLUMN `user_role` varchar(256) NOT NULL DEFAULT 'USER'
        COMMENT '系统角色：USER/SYSTEM_ADMIN';

ALTER TABLE `user`
    DROP CHECK `chk_user_system_role_transition`,
    ADD CONSTRAINT `chk_user_system_role`
    CHECK (
        BINARY `user_role` IN ('USER', 'SYSTEM_ADMIN')
    );

-- ============================================================================
-- 2. Task creator/assignee semantics
-- ============================================================================

ALTER TABLE `task`
    CHANGE COLUMN `assignee_id` `assignee_user_id` bigint DEFAULT NULL
        COMMENT '任务当前受理人用户ID',
    ADD COLUMN `assigned_by_user_id` bigint DEFAULT NULL
        COMMENT '最近一次分配操作人用户ID'
        AFTER `assignee_user_id`,
    ADD COLUMN `assigned_at` datetime DEFAULT NULL
        COMMENT '最近一次分配时间'
        AFTER `assigned_by_user_id`,
    DROP INDEX `idx_task_assignee_id`,
    ADD KEY `idx_task_assignee_status`
        (`assignee_user_id`, `is_delete`, `status`, `due_date`),
    ADD KEY `idx_task_project_assignee`
        (`project_id`, `assignee_user_id`, `is_delete`);

UPDATE `task`
SET
    `assignee_user_id` = COALESCE(`assignee_user_id`, `user_id`),
    `assigned_by_user_id` = `user_id`,
    `assigned_at` = `create_time`;

-- ============================================================================
-- 3. Immutable assignment history
-- ============================================================================

CREATE TABLE `task_assignment_log` (
  `id` bigint NOT NULL COMMENT '主键；存量 INITIAL_ASSIGN 使用 task.id',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `from_assignee_user_id` bigint DEFAULT NULL COMMENT '原受理人用户ID',
  `to_assignee_user_id` bigint DEFAULT NULL COMMENT '新受理人用户ID',
  `assigned_by_user_id` bigint NOT NULL COMMENT '分配操作人用户ID',
  `action` varchar(32) NOT NULL COMMENT '分配动作',
  `reason` varchar(200) DEFAULT NULL COMMENT '可选原因，不保存敏感正文',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_assignment_log_task_time` (`task_id`, `create_time`),
  KEY `idx_assignment_log_to_user_time` (`to_assignee_user_id`, `create_time`),
  KEY `idx_assignment_log_actor_time` (`assigned_by_user_id`, `create_time`),
  CONSTRAINT `chk_task_assignment_action` CHECK (
      BINARY `action` IN (
          'INITIAL_ASSIGN', 'ASSIGN', 'REASSIGN', 'UNASSIGN',
          'MEMBER_LEFT', 'MEMBER_REMOVED'
      )
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='任务分配不可变历史';

INSERT INTO `task_assignment_log` (
    `id`,
    `task_id`,
    `from_assignee_user_id`,
    `to_assignee_user_id`,
    `assigned_by_user_id`,
    `action`,
    `reason`,
    `create_time`
)
SELECT
    `id`,
    `id`,
    NULL,
    `assignee_user_id`,
    `assigned_by_user_id`,
    'INITIAL_ASSIGN',
    NULL,
    `assigned_at`
FROM `task`
WHERE `assignee_user_id` IS NOT NULL;

-- ============================================================================
-- 4. Weekly-review visibility and stable associations
-- ============================================================================

ALTER TABLE `weekly_review`
    ADD COLUMN `visibility_scope` varchar(16) NOT NULL DEFAULT 'PRIVATE'
        COMMENT '可见范围：PRIVATE/TEAM',
    ADD COLUMN `team_id` bigint DEFAULT NULL
        COMMENT 'TEAM 复盘的唯一共享目标团队ID',
    ADD COLUMN `focus_project_id` bigint DEFAULT NULL
        COMMENT '稳定重点项目关联ID',
    ADD COLUMN `shared_summary` text NULL
        COMMENT '单独填写的团队共享摘要',
    ADD KEY `idx_weekly_review_team_scope_time`
        (`team_id`, `visibility_scope`, `year`, `week_no`),
    ADD KEY `idx_weekly_review_focus_project` (`focus_project_id`),
    ADD CONSTRAINT `chk_weekly_review_visibility_scope` CHECK (
        BINARY `visibility_scope` IN ('PRIVATE', 'TEAM')
    ),
    ADD CONSTRAINT `chk_weekly_review_visibility_target` CHECK (
        (
            BINARY `visibility_scope` = BINARY 'PRIVATE'
            AND `team_id` IS NULL
        )
        OR
        (
            BINARY `visibility_scope` = BINARY 'TEAM'
            AND `team_id` IS NOT NULL
            AND `shared_summary` IS NOT NULL
            AND CHAR_LENGTH(TRIM(`shared_summary`)) > 0
        )
    );

CREATE TABLE `weekly_review_task` (
  `id` bigint NOT NULL COMMENT '主键',
  `weekly_review_id` bigint NOT NULL COMMENT '周复盘ID',
  `task_id` bigint NOT NULL COMMENT '关联任务ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_weekly_review_task` (`weekly_review_id`, `task_id`),
  KEY `idx_weekly_review_task_task` (`task_id`, `weekly_review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='周复盘任务关联';
