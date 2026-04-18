-- 任务改名日志表（用于日报回顾改名建议的应用与回滚）
CREATE TABLE `task_title_rename_log`
(
    `id`           BIGINT       NOT NULL COMMENT '主键ID',
    `operation_id` VARCHAR(64)  NOT NULL COMMENT '改名批次ID',
    `user_id`      BIGINT       NOT NULL COMMENT '用户ID',
    `task_id`      BIGINT       NOT NULL COMMENT '任务ID',
    `review_date`  DATE         NOT NULL COMMENT '回顾日期',
    `old_title`    VARCHAR(60)  NOT NULL COMMENT '改名前任务标题',
    `new_title`    VARCHAR(60)  NOT NULL COMMENT '改名后任务标题',
    `reason`       VARCHAR(120)          DEFAULT NULL COMMENT '改名原因',
    `confidence`   TINYINT      NOT NULL DEFAULT 0 COMMENT '置信度（0-100）',
    `is_applied`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已应用改名',
    `applied_at`   DATETIME              DEFAULT NULL COMMENT '应用时间',
    `is_rollback`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已回滚',
    `rollback_at`  DATETIME              DEFAULT NULL COMMENT '回滚时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='任务改名日志表';

CREATE INDEX `idx_rename_log_user_operation` ON `task_title_rename_log` (`user_id`, `operation_id`);
CREATE INDEX `idx_rename_log_user_task` ON `task_title_rename_log` (`user_id`, `task_id`);
