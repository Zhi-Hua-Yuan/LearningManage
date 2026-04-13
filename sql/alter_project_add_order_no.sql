ALTER TABLE `project`
    ADD COLUMN `order_no` INT NOT NULL DEFAULT 0 COMMENT '排序号，从0开始，越小越靠前' AFTER `status`;

CREATE INDEX `idx_project_user_order_no` ON `project` (`user_id`, `order_no`);

