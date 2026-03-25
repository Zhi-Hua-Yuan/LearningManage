-- 里程碑表
CREATE TABLE IF NOT EXISTS `milestone`
(
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`  BIGINT        NOT NULL COMMENT '项目ID',
    `user_id`     BIGINT        NOT NULL COMMENT '所属用户ID',
    `name`        VARCHAR(100)  NOT NULL COMMENT '里程碑名称',
    `order_no`    INT           NOT NULL COMMENT '排序号(项目内唯一, 从小到大)',
    `progress`    DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '进度百分比(0-100)',
    `is_delete`   TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除, 1已删除',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_milestone_project_order_no` (`project_id`, `order_no`),
    KEY `idx_milestone_project_id` (`project_id`),
    KEY `idx_milestone_user_id` (`user_id`),
    KEY `idx_milestone_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='里程碑表';
