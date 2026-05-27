CREATE TABLE `task_status_idempotency`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`           BIGINT       NOT NULL COMMENT '用户ID',
    `task_id`           BIGINT       NOT NULL COMMENT '任务ID',
    `client_request_id` VARCHAR(64)  NOT NULL COMMENT '客户端幂等键',
    `target_status`     TINYINT      NOT NULL COMMENT '目标状态',
    `changed`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否发生状态变化',
    `final_status`      TINYINT      NOT NULL COMMENT '最终状态',
    `completed_at`      DATETIME              DEFAULT NULL COMMENT '最终完成时间',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_task_request` (`user_id`, `task_id`, `client_request_id`),
    KEY `idx_user_task` (`user_id`, `task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='任务状态幂等表';
