CREATE TABLE IF NOT EXISTS `ai_replan_item` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `operation_id` VARCHAR(64) NOT NULL COMMENT '重排操作ID',
  `task_id` BIGINT NOT NULL COMMENT '任务ID',
  `old_title` VARCHAR(60) NOT NULL COMMENT '原标题',
  `new_title` VARCHAR(60) NOT NULL COMMENT '新标题',
  `old_priority` TINYINT NOT NULL COMMENT '原优先级',
  `new_priority` TINYINT NOT NULL COMMENT '新优先级',
  `old_due_date` DATE NULL COMMENT '原截止日期',
  `new_due_date` DATE NULL COMMENT '新截止日期',
  `confidence` TINYINT NOT NULL DEFAULT 0 COMMENT '置信度',
  `reason` VARCHAR(200) NULL COMMENT '调整原因',
  `task_snapshot_update_time` DATETIME NULL COMMENT '任务快照更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_task` (`operation_id`, `task_id`),
  KEY `idx_operation_id` (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清单任务重排明细表';
