-- 周总结表
CREATE TABLE IF NOT EXISTS `weekly_review`
(
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`              BIGINT       NOT NULL COMMENT '所属用户ID',
    `year`                 INT          NOT NULL COMMENT '年份',
    `week_no`              INT          NOT NULL COMMENT '本年第几周',
    `start_date`           DATE         NOT NULL COMMENT '周一日期',
    `end_date`             DATE         NOT NULL COMMENT '周日日期',
    `completed_task_count` INT          NOT NULL DEFAULT 0 COMMENT '本周完成任务数快照',
    `focus_project_name`   VARCHAR(100)          DEFAULT NULL COMMENT '本周重点项目名称快照',
    `reflection`           TEXT                  DEFAULT NULL COMMENT '本周反思',
    `next_plan`            TEXT                  DEFAULT NULL COMMENT '下周计划',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_weekly_review_user_year_week` (`user_id`, `year`, `week_no`),
    KEY `idx_weekly_review_user_id` (`user_id`),
    KEY `idx_weekly_review_start_date` (`start_date`),
    KEY `idx_weekly_review_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='周总结表';

