-- Sanitized pre-Flyway V1 schema fixture.
-- Source snapshot: PR5-A, MySQL 8.0.41, 2026-08-20.
-- Schema only: no business data, credentials, users, grants, or Flyway history.
-- CI use only: import into an empty learning_manage_ci_* database.

CREATE TABLE `user` (
  `id` bigint NOT NULL COMMENT '主键',
  `account` varchar(256) NOT NULL COMMENT '账号',
  `username` varchar(256) NOT NULL COMMENT '用户名',
  `password` varchar(512) NOT NULL COMMENT '密码（加密存储）',
  `user_role` varchar(256) NOT NULL DEFAULT 'user' COMMENT '用户角色：user/admin',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account` (`account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE `tenant` (
  `id` bigint NOT NULL COMMENT '租户主键ID',
  `code` varchar(64) NOT NULL COMMENT '租户编码（系统级稳定标识）',
  `name` varchar(128) NOT NULL COMMENT '租户名称',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 1启用, 0停用',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认租户: 1是, 0否',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除, 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`code`),
  KEY `idx_tenant_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户主档案表';

CREATE TABLE `role` (
  `id` bigint NOT NULL COMMENT '角色主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户ID',
  `code` varchar(64) NOT NULL COMMENT '角色编码（租户内稳定标识）',
  `name` varchar(128) NOT NULL COMMENT '角色名称',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 1启用, 0停用',
  `is_system` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统内置角色: 1是, 0否',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除, 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_tenant_code` (`tenant_id`,`code`),
  KEY `idx_role_tenant_id` (`tenant_id`),
  KEY `idx_role_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户角色表';

CREATE TABLE `permission` (
  `id` bigint NOT NULL COMMENT '权限主键',
  `code` varchar(64) NOT NULL COMMENT '权限编码（资源:动作）',
  `name` varchar(128) NOT NULL COMMENT '权限名称',
  `resource` varchar(64) NOT NULL COMMENT '资源标识',
  `action` varchar(64) NOT NULL COMMENT '动作标识',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 1启用, 0停用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除, 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`code`),
  KEY `idx_permission_resource_action` (`resource`,`action`),
  KEY `idx_permission_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限字典表';

CREATE TABLE `role_permission` (
  `id` bigint NOT NULL COMMENT '关联主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除, 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission_tenant_role_perm` (`tenant_id`,`role_id`,`permission_id`),
  KEY `idx_role_permission_tenant_role` (`tenant_id`,`role_id`),
  KEY `idx_role_permission_tenant_permission` (`tenant_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

CREATE TABLE `user_role` (
  `id` bigint NOT NULL COMMENT '关联主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '关联状态: 1有效, 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除, 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role_tenant_user_role` (`tenant_id`,`user_id`,`role_id`),
  KEY `idx_user_role_tenant_user` (`tenant_id`,`user_id`),
  KEY `idx_user_role_tenant_role` (`tenant_id`,`role_id`),
  KEY `idx_user_role_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

CREATE TABLE `team` (
  `id` bigint NOT NULL COMMENT '团队ID',
  `name` varchar(60) NOT NULL COMMENT '团队名称',
  `description` varchar(200) DEFAULT NULL COMMENT '团队描述',
  `owner_id` bigint NOT NULL COMMENT '团队创建者用户ID',
  `invite_code` varchar(60) NOT NULL COMMENT '团队邀请码',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_is_delete` (`is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队表';

CREATE TABLE `team_member` (
  `id` bigint NOT NULL COMMENT '团队成员关系ID',
  `team_id` bigint NOT NULL COMMENT '团队ID',
  `user_id` bigint NOT NULL COMMENT '成员用户ID',
  `role` varchar(20) NOT NULL DEFAULT 'MEMBER' COMMENT '成员角色：OWNER/ADMIN/MEMBER',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_user` (`team_id`,`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_team_role` (`team_id`,`role`),
  KEY `idx_is_delete` (`is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队成员关系表';

CREATE TABLE `project` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `team_id` bigint DEFAULT NULL COMMENT '团队ID，NULL表示个人项目',
  `name` varchar(100) NOT NULL COMMENT '项目名称',
  `icon` varchar(50) DEFAULT NULL COMMENT '清单图标（emoji或图标标识）',
  `color` varchar(7) DEFAULT NULL COMMENT '清单颜色，格式 #RRGGBB',
  `goal` varchar(500) DEFAULT NULL COMMENT '项目目标',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0进行中, 1已归档',
  `order_no` int NOT NULL DEFAULT '0' COMMENT '排序号，从0开始，越小越靠前',
  `progress` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '项目进度百分比(0-100)',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除, 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_status` (`status`),
  KEY `idx_project_create_time` (`create_time`),
  KEY `idx_project_user_id` (`user_id`),
  KEY `idx_project_user_order_no` (`user_id`,`order_no`),
  KEY `idx_project_team_id` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目表';

CREATE TABLE `milestone` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `name` varchar(100) NOT NULL COMMENT '里程碑名称',
  `order_no` int NOT NULL COMMENT '排序号(项目内唯一, 从小到大)',
  `progress` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '进度百分比(0-100)',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  `delete_source` tinyint NOT NULL DEFAULT '0' COMMENT '删除来源: 0未删除, 1手动删除, 2项目级联删除',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除, 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_milestone_project_order_no` (`project_id`,`order_no`),
  KEY `idx_milestone_project_id` (`project_id`),
  KEY `idx_milestone_user_id` (`user_id`),
  KEY `idx_milestone_create_time` (`create_time`),
  KEY `idx_milestone_project_delete_source` (`user_id`,`project_id`,`is_delete`,`delete_source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='里程碑表';

CREATE TABLE `task` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `milestone_id` bigint DEFAULT NULL COMMENT '所属里程碑ID，可为空',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(60) NOT NULL COMMENT '任务标题',
  `description` varchar(550) DEFAULT NULL COMMENT '任务描述',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0-未完成, 1-一般完成, 2-正常完成, 3-超额完成',
  `priority` tinyint NOT NULL DEFAULT '0' COMMENT '优先级',
  `due_date` date DEFAULT NULL COMMENT '截止时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  `delete_source` tinyint NOT NULL DEFAULT '0' COMMENT '删除来源: 0未删除, 1手动删除, 2项目级联删除',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除, 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `assignee_id` bigint DEFAULT NULL COMMENT '任务负责人用户ID',
  PRIMARY KEY (`id`),
  KEY `idx_task_project_id` (`project_id`),
  KEY `idx_task_user_id` (`user_id`),
  KEY `idx_task_milestone_id` (`milestone_id`),
  KEY `idx_task_stats` (`project_id`,`status`,`due_date`,`completed_at`),
  KEY `idx_task_project_delete_source` (`user_id`,`project_id`,`is_delete`,`delete_source`),
  KEY `idx_task_assignee_id` (`assignee_id`),
  CONSTRAINT `chk_task_status_range` CHECK ((`status` in (0,1,2,3)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务表';

CREATE TABLE `weekly_review` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `year` int NOT NULL COMMENT '年份',
  `week_no` int NOT NULL COMMENT '本年第几周',
  `start_date` date NOT NULL COMMENT '周一日期',
  `end_date` date NOT NULL COMMENT '周日日期',
  `completed_task_count` int NOT NULL DEFAULT '0' COMMENT '本周完成任务数快照',
  `focus_project_name` varchar(100) DEFAULT NULL COMMENT '本周重点项目名称快照',
  `reflection` text COMMENT '本周反思',
  `next_plan` text COMMENT '下周计划',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_weekly_review_user_year_week` (`user_id`,`year`,`week_no`),
  KEY `idx_weekly_review_user_id` (`user_id`),
  KEY `idx_weekly_review_start_date` (`start_date`),
  KEY `idx_weekly_review_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='周总结表';

CREATE TABLE `prompt_template` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码，如 task-breakdown.default',
  `scene` varchar(64) NOT NULL COMMENT 'AI业务场景',
  `template_name` varchar(100) NOT NULL COMMENT '模板名称',
  `template_content` longtext NOT NULL COMMENT '系统Prompt正文',
  `version` int NOT NULL COMMENT '版本号，从1递增',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用：0否，1是',
  `remark` varchar(255) DEFAULT NULL COMMENT '版本说明',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_code_version` (`template_code`,`version`),
  KEY `idx_template_code_enabled` (`template_code`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Prompt模板表';

CREATE TABLE `ai_call_log` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `scene` varchar(64) NOT NULL COMMENT '调用场景，如 task-breakdown、weekly-polish',
  `model_name` varchar(64) NOT NULL COMMENT '模型名称',
  `prompt_type` varchar(64) DEFAULT NULL COMMENT 'Prompt 类型',
  `prompt_template_id` bigint DEFAULT NULL COMMENT '实际使用的数据库模板ID，内置模板为空',
  `prompt_version` int DEFAULT NULL COMMENT '实际使用的Prompt版本',
  `prompt_source` varchar(16) DEFAULT NULL COMMENT '模板来源：database/builtin',
  `request_text` longtext COMMENT '请求内容',
  `response_text` longtext COMMENT '响应内容',
  `status` tinyint NOT NULL COMMENT '状态: 0调用中 1成功 2调用失败 3解析失败 4超时',
  `error_message` text COMMENT '错误信息',
  `cost_time_ms` bigint DEFAULT NULL COMMENT '耗时，单位毫秒',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_scene_time` (`user_id`,`scene`,`create_time`),
  KEY `idx_status_time` (`status`,`create_time`),
  KEY `idx_prompt_template_version` (`prompt_template_id`,`prompt_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI调用记录表';

CREATE TABLE `ai_draft` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `draft_id` varchar(64) NOT NULL COMMENT '草稿ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `scene` varchar(32) NOT NULL COMMENT '场景',
  `payload_json` longtext NOT NULL COMMENT '草稿内容',
  `input_hash` varchar(64) DEFAULT NULL COMMENT '输入摘要',
  `status` tinyint NOT NULL COMMENT '状态: 0预览 1已确认 2已取消 3已过期',
  `expire_at` datetime NOT NULL COMMENT '过期时间',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
  `canceled_at` datetime DEFAULT NULL COMMENT '取消时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draft_id` (`draft_id`),
  KEY `idx_user_scene_status_expire` (`user_id`,`scene`,`status`,`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI草稿表';

CREATE TABLE `ai_draft_confirm_log` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `draft_id` varchar(64) NOT NULL COMMENT '草稿ID',
  `operation_id` varchar(64) NOT NULL COMMENT '幂等操作ID',
  `scene` varchar(32) NOT NULL COMMENT '场景',
  `business_id` bigint DEFAULT NULL COMMENT '落库业务主键ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_draft_op` (`user_id`,`draft_id`,`operation_id`),
  KEY `idx_user_scene` (`user_id`,`scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI确认幂等日志表';

CREATE TABLE `ai_replan_operation` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `operation_id` varchar(64) NOT NULL COMMENT '重排操作ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `project_id` bigint NOT NULL COMMENT '清单ID',
  `status` tinyint NOT NULL COMMENT '状态: 0-预览 1-已确认 2-已取消 3-已过期',
  `expires_at` datetime NOT NULL COMMENT '预览过期时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
  `canceled_at` datetime DEFAULT NULL COMMENT '取消时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_id` (`operation_id`),
  KEY `idx_user_project_status` (`user_id`,`project_id`,`status`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='清单任务重排操作表';

CREATE TABLE `ai_replan_item` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `operation_id` varchar(64) NOT NULL COMMENT '重排操作ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `old_title` varchar(60) NOT NULL COMMENT '原标题',
  `new_title` varchar(60) NOT NULL COMMENT '新标题',
  `old_priority` tinyint NOT NULL COMMENT '原优先级',
  `new_priority` tinyint NOT NULL COMMENT '新优先级',
  `old_due_date` date DEFAULT NULL COMMENT '原截止日期',
  `new_due_date` date DEFAULT NULL COMMENT '新截止日期',
  `confidence` tinyint NOT NULL DEFAULT '0' COMMENT '置信度',
  `reason` varchar(200) DEFAULT NULL COMMENT '调整原因',
  `task_snapshot_update_time` datetime DEFAULT NULL COMMENT '任务快照更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_task` (`operation_id`,`task_id`),
  KEY `idx_operation_id` (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='清单任务重排明细表';

CREATE TABLE `task_status_idempotency` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `client_request_id` varchar(64) NOT NULL COMMENT '客户端幂等键',
  `target_status` tinyint NOT NULL COMMENT '目标状态',
  `changed` tinyint NOT NULL DEFAULT '0' COMMENT '是否发生状态变化',
  `final_status` tinyint NOT NULL COMMENT '最终状态',
  `completed_at` datetime DEFAULT NULL COMMENT '最终完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_task_request` (`user_id`,`task_id`,`client_request_id`),
  KEY `idx_user_task` (`user_id`,`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务状态幂等表';

CREATE TABLE `task_title_rename_log` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `operation_id` varchar(64) NOT NULL COMMENT '改名批次ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `review_date` date NOT NULL COMMENT '回顾日期',
  `old_title` varchar(60) NOT NULL COMMENT '改名前任务标题',
  `new_title` varchar(60) NOT NULL COMMENT '改名后任务标题',
  `reason` varchar(120) DEFAULT NULL COMMENT '改名原因',
  `confidence` tinyint NOT NULL DEFAULT '0' COMMENT '置信度（0-100）',
  `is_applied` tinyint NOT NULL DEFAULT '0' COMMENT '是否已应用改名',
  `applied_at` datetime DEFAULT NULL COMMENT '应用时间',
  `is_rollback` tinyint NOT NULL DEFAULT '0' COMMENT '是否已回滚',
  `rollback_at` datetime DEFAULT NULL COMMENT '回滚时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_rename_log_user_operation` (`user_id`,`operation_id`),
  KEY `idx_rename_log_user_task` (`user_id`,`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务改名日志表';
