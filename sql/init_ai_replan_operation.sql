CREATE TABLE IF NOT EXISTS `ai_replan_operation` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `operation_id` VARCHAR(64) NOT NULL COMMENT '重排操作ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `project_id` BIGINT NOT NULL COMMENT '清单ID',
  `status` TINYINT NOT NULL COMMENT '状态: 0-预览 1-已确认 2-已取消 3-已过期',
  `expires_at` DATETIME NOT NULL COMMENT '预览过期时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `confirmed_at` DATETIME NULL COMMENT '确认时间',
  `canceled_at` DATETIME NULL COMMENT '取消时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_id` (`operation_id`),
  KEY `idx_user_project_status` (`user_id`, `project_id`, `status`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清单任务重排操作表';
