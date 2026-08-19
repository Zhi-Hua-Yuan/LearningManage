# V1 与旧初始化 SQL 差异

比较基准：主库审计快照（20 张表）对比仓库 `sql/` 下的历史初始化脚本。旧脚本用于开发环境初始化，不能直接作为 Flyway V1。

## 1. 覆盖关系

| 旧脚本 | 覆盖表 | 结论 |
|---|---|---|
| `init_user.sql` | `user` | 覆盖；以快照列定义、索引、排序规则为准 |
| `init_team.sql` | `team` | 覆盖；以快照为准 |
| `init_team_member.sql` | `team_member` | 覆盖；以快照为准 |
| `init_project.sql` | `project` | 覆盖；以快照为准 |
| `init_milestone.sql` | `milestone` | 覆盖；以快照为准 |
| `init_task.sql` | `task` | 覆盖但存在关键遗漏，见下节 |
| `init_weekly_review.sql` | `weekly_review` | 覆盖；以快照为准 |
| `init_prompt_template.sql` | `prompt_template` | 覆盖；数据正文暂不进入 V1 |
| `init_ai_call_log.sql` | `ai_call_log` | 覆盖；以快照为准 |
| `init_ai_draft.sql` | `ai_draft` | 覆盖；以快照为准 |
| `init_ai_draft_confirm_log.sql` | `ai_draft_confirm_log` | 覆盖；以快照为准 |
| `init_ai_replan_operation.sql` | `ai_replan_operation` | 覆盖；以快照为准 |
| `init_ai_replan_item.sql` | `ai_replan_item` | 覆盖；以快照为准 |
| `init_task_status_idempotency.sql` | `task_status_idempotency` | 覆盖；以快照为准 |
| `init_task_title_rename_log.sql` | `task_title_rename_log` | 覆盖；以快照为准 |

旧脚本未覆盖以下五张已存在的正式 V1 表：`tenant`、`role`、`permission`、`role_permission`、`user_role`。V1 必须补齐其 DDL，不能删除或忽略。

## 2. 已确认的结构差异

### `task`

- 主库存在 `assignee_id bigint NULL`；旧 `init_task.sql` 未包含。
- 主库存在 `idx_task_assignee_id(assignee_id)`；旧脚本未包含。
- 主库存在 `chk_task_status_range`，限制 `status IN (0,1,2,3)`；旧脚本未包含。
- V1 按主库保留三项；后续 V2 才讨论 `assignee_user_id` 等负责人模型扩展。

### 全局结构差异

- 主库统一为 MySQL 8.0.41、InnoDB、`utf8mb4_0900_ai_ci`；旧脚本中的排序规则和默认值不能未经比较直接复制。
- 主库索引名称和组合已被阶段 0 审计确认；V1 必须按 [v1-schema-manifest.md](v1-schema-manifest.md) 重建，不能只依赖 ORM 自动建索引。
- 主库不以旧脚本中的表顺序作为权威；V1 采用逻辑依赖顺序，且不新增物理外键。

## 3. 数据与脚本差异

- 旧 `init_prompt_template.sql` 可能包含模板数据；V1 不直接复制其正文，先按参考数据清单完成六行分类。
- 旧脚本不应被用于生产接管；它们没有 Flyway 版本记录、校验和接管守卫。
- 旧脚本中的初始化业务数据不属于 V1 结构基线，生产接管时不得重复执行。

## 4. 处理结论

1. PR 1 只记录差异，不修改旧 `sql/` 文件。
2. PR 2 根据快照生成严格的 `V1__baseline_schema.sql`，补齐 RBAC、`task` 遗漏和全部实际索引/检查约束。
3. PR 2 将结构迁移与参考数据播种分离；Prompt 和 RBAC 固定数据没有审批就不播种。
4. PR 3 先对已有库做备份与结构守卫，再执行 Flyway baseline 接管，不重复执行旧初始化脚本。
