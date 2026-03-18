-- 创建任务表
CREATE TABLE `task`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`   BIGINT       NOT NULL COMMENT '项目ID',
    `user_id`      BIGINT       NOT NULL COMMENT '用户ID',
    `title`        VARCHAR(100) NOT NULL COMMENT '任务标题',
    `description`  VARCHAR(500)          DEFAULT NULL COMMENT '任务描述',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0-未完成, 1-已完成',
    `priority`     TINYINT      NOT NULL DEFAULT 0 COMMENT '优先级',
    `due_date`     DATE                  DEFAULT NULL COMMENT '截止时间',
    `completed_at` DATETIME              DEFAULT NULL COMMENT '完成时间',
    `is_delete`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除, 1已删除',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='任务表';

-- Indexes
CREATE INDEX `idx_task_project_id` ON `task` (`project_id`);
CREATE INDEX `idx_task_user_id` ON `task` (`user_id`);
