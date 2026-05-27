CREATE TABLE IF NOT EXISTS `ai_draft` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `draft_id` VARCHAR(64) NOT NULL COMMENT '草稿ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `scene` VARCHAR(32) NOT NULL COMMENT '场景',
  `payload_json` LONGTEXT NOT NULL COMMENT '草稿内容',
  `input_hash` VARCHAR(64) NULL COMMENT '输入摘要',
  `status` TINYINT NOT NULL COMMENT '状态: 0预览 1已确认 2已取消 3已过期',
  `expire_at` DATETIME NOT NULL COMMENT '过期时间',
  `confirmed_at` DATETIME NULL COMMENT '确认时间',
  `canceled_at` DATETIME NULL COMMENT '取消时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draft_id` (`draft_id`),
  KEY `idx_user_scene_status_expire` (`user_id`, `scene`, `status`, `expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI草稿表';
