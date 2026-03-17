-- 创建项目表
CREATE TABLE `project` (
                           `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                           `name` VARCHAR(100) NOT NULL COMMENT '项目名称',
                           `goal` VARCHAR(500) DEFAULT NULL COMMENT '项目目标',
                           `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0进行中, 1已归档',
                           `start_date` DATE DEFAULT NULL COMMENT '开始日期',
                           `end_date` DATE DEFAULT NULL COMMENT '结束日期',
                           `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除, 1已删除',
                           `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `deleted_at` DATETIME DEFAULT NULL COMMENT '软删除时间',
                           PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目表';

-- Indexes
CREATE INDEX `idx_project_status` ON `project` (`status`);
CREATE INDEX `idx_project_create_time` ON `project` (`create_time`);