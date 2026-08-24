# V2 数据字典与迁移合同

状态：`PROPOSED`；PR2 编写迁移 SQL 的冻结输入

目标迁移名：`V2__stage1_business_semantics_and_permissions.sql`

## 1. 范围

V2 只处理阶段 1 所需的系统角色、任务分配和周复盘语义。V2 不启用租户 RBAC、不新增向量索引表、不修改 AI 模型协议、不增加物理外键。

## 2. 结构变更清单

### `user`

| 列 | V1 | V2 | 说明 |
|---|---|---|---|
| `user_role` | `varchar(256)`，默认 `user` | 保持列名；默认 `USER` | 系统角色，只允许 `USER/SYSTEM_ADMIN` |

新增检查约束建议：`chk_user_system_role`。

### `task`

| 列 | 动作 | V2 定义 | 说明 |
|---|---|---|---|
| `user_id` | 保留 | `bigint NOT NULL` | 创建人；Java 映射名 `createdByUserId` |
| `assignee_id` | 重命名 | `assignee_user_id bigint NULL` | 当前受理人 |
| `assigned_by_user_id` | 新增 | `bigint NULL` | 最近实际分配人 |
| `assigned_at` | 新增 | `datetime NULL` | 最近实际分配时间 |

索引合同：

- 删除或重命名 `idx_task_assignee_id`；
- 增加 `idx_task_assignee_status(assignee_user_id,is_delete,status,due_date)`；
- 增加 `idx_task_project_assignee(project_id,assignee_user_id,is_delete)`。

### `task_assignment_log`

| 列 | 类型 | 可空 | 说明 |
|---|---|---:|---|
| `id` | bigint | 否 | 主键，应用生成 |
| `task_id` | bigint | 否 | 任务 ID |
| `from_assignee_user_id` | bigint | 是 | 原受理人 |
| `to_assignee_user_id` | bigint | 是 | 新受理人 |
| `assigned_by_user_id` | bigint | 否 | 操作人 |
| `action` | varchar(32) | 否 | 分配动作枚举 |
| `reason` | varchar(200) | 是 | 可选原因，不保存敏感正文 |
| `create_time` | datetime | 否 | 操作时间 |

允许动作：`INITIAL_ASSIGN`、`ASSIGN`、`REASSIGN`、`UNASSIGN`、`MEMBER_LEFT`、`MEMBER_REMOVED`。

索引：

- `idx_assignment_log_task_time(task_id,create_time)`；
- `idx_assignment_log_to_user_time(to_assignee_user_id,create_time)`；
- `idx_assignment_log_actor_time(assigned_by_user_id,create_time)`。

该表不使用逻辑删除，不提供业务更新/删除接口。

### `weekly_review`

| 列 | 动作 | V2 定义 | 说明 |
|---|---|---|---|
| `visibility_scope` | 新增 | `varchar(16) NOT NULL DEFAULT 'PRIVATE'` | `PRIVATE/TEAM` |
| `team_id` | 新增 | `bigint NULL` | TEAM 共享目标 |
| `focus_project_id` | 新增 | `bigint NULL` | 稳定重点项目关联 |
| `shared_summary` | 新增 | `text NULL` | 独立共享摘要 |
| `focus_project_name` | 保留 | 原定义 | 历史显示快照 |

索引和约束：

- `chk_weekly_review_visibility_scope`；
- `idx_weekly_review_team_scope_time(team_id,visibility_scope,year,week_no)`；
- `idx_weekly_review_focus_project(focus_project_id)`。

`TEAM → team_id 非空且 shared_summary 非空` 由应用强校验；若 PR2 能以兼容 MySQL 8.0.41 的检查约束表达，可额外增加，但不得用触发器。

### `weekly_review_task`

| 列 | 类型 | 可空 | 说明 |
|---|---|---:|---|
| `id` | bigint | 否 | 主键，应用生成 |
| `weekly_review_id` | bigint | 否 | 周复盘 ID |
| `task_id` | bigint | 否 | 任务 ID |
| `create_time` | datetime | 否 | 关联时间 |

约束和索引：

- `uk_weekly_review_task(weekly_review_id,task_id)`；
- `idx_weekly_review_task_task(task_id,weekly_review_id)`。

## 3. 迁移前检查

PR2 必须提供只读 preflight，至少验证：

1. `user.user_role` 只包含允许映射值；
2. `task.user_id` 都对应有效或可解释的用户记录；
3. 非空 `assignee_id` 都对应用户记录；
4. task、project、team 和 team_member 没有会使回填无法判定的冲突；
5. `weekly_review` 不存在违反当前用户/周唯一键的数据；
6. V1 结构、索引和 checksum 与已发布版本一致。

preflight 只报告，不修改数据；不允许在 V2 中用宽泛默认值隐藏未知脏数据。

## 4. 数据迁移规则

执行顺序：

```text
规范系统角色
→ 重命名 assignee_id
→ 增加任务分配列
→ 回填任务受理人/分配人/时间
→ 创建任务分配日志表
→ 为存量任务写 INITIAL_ASSIGN 日志
→ 增加周复盘列并默认 PRIVATE
→ 创建 weekly_review_task
→ 增加约束和索引
→ 执行 post-verify
```

回填合同：

```text
user.user_role: user→USER, admin→SYSTEM_ADMIN
task.assignee_user_id: COALESCE(V1 assignee_id, task.user_id)
task.assigned_by_user_id: task.user_id
task.assigned_at: task.create_time
weekly_review.visibility_scope: PRIVATE
weekly_review.team_id/focus_project_id/shared_summary: NULL
```

每个回填任务写且只写一条 `INITIAL_ASSIGN` 日志；若最终受理人为空则不写初始分配日志。

## 5. 迁移后验证

- Flyway history 版本总数为 2，V1/V2 均为 success；
- 未知系统角色数为 0；
- 非空受理人但缺少最近分配人/时间的任务数为 0；
- 非空受理人的存量任务与 `INITIAL_ASSIGN` 日志可对账；
- 存量周复盘非 PRIVATE 数为 0；
- 新表、列、索引和检查约束与本字典一致；
- V1 checksum 未改变。

## 6. 回滚与恢复

Flyway Community 不把破坏性逆向 DDL 作为阶段 1 的默认回滚方案。PR2 必须演练：

1. 迁移前逻辑备份可读；
2. 在隔离数据库执行 V1→V2；
3. 验证失败时停止应用发布；
4. 从备份恢复到新的隔离库并核对关键计数；
5. 若 V2 已在共享环境发布，优先使用前向修复迁移，不修改 V2。

本文件不授权连接或修改正式数据库。
