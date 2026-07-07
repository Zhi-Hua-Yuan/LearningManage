CREATE TABLE IF NOT EXISTS `ai_call_log` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `scene` VARCHAR(64) NOT NULL COMMENT '调用场景，如 task-breakdown、weekly-polish',
  `model_name` VARCHAR(64) NOT NULL COMMENT '模型名称',
  `prompt_type` VARCHAR(64) NULL COMMENT 'Prompt 类型',
  `request_text` LONGTEXT NULL COMMENT '请求内容',
  `response_text` LONGTEXT NULL COMMENT '响应内容',
  `status` TINYINT NOT NULL COMMENT '状态: 0调用中 1成功 2调用失败 3解析失败 4超时',
  `error_message` TEXT NULL COMMENT '错误信息',
  `cost_time_ms` BIGINT NULL COMMENT '耗时，单位毫秒',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_scene_time` (`user_id`, `scene`, `create_time`),
  KEY `idx_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用记录表';