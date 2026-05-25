CREATE TABLE IF NOT EXISTS `ai_draft_confirm_log` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `draft_id` VARCHAR(64) NOT NULL COMMENT '草稿ID',
  `operation_id` VARCHAR(64) NOT NULL COMMENT '幂等操作ID',
  `scene` VARCHAR(32) NOT NULL COMMENT '场景',
  `business_id` BIGINT NULL COMMENT '落库业务主键ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_draft_op` (`user_id`, `draft_id`, `operation_id`),
  KEY `idx_user_scene` (`user_id`, `scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI确认幂等日志表';
