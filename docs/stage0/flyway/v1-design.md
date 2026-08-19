# Flyway V1 设计方案

状态：PR 1 设计冻结（文档变更）  
适用环境：开发、测试、生产的 `learning_manage` 数据库  
冻结时间：2026-08-19 Asia/Shanghai

## 1. 目标与非目标

### 目标

1. 将阶段 0 审计后的 20 张表定义为 V1 的唯一结构基线。
2. 让空库可以从 V1 建立与主库一致的结构，让已有库可以在受控窗口内被 Flyway 接管。
3. 把 RBAC 遗留结构、`task.assignee_id` 和任务状态检查约束纳入可追溯的结构清单。
4. 为 PR 2 的 `V1__baseline_schema.sql` 和 PR 3 的接管/回滚演练提供可验证输入。

### 非目标

- PR 1 不添加 Maven 依赖、Spring 配置或迁移 SQL。
- PR 1 不连接、修改、清理或重建任何数据库。
- 不把业务数据、用户密码、AI Prompt 正文或运行时密钥写入仓库。
- 不在 V1 引入新的业务外键、`system_role`、`assignee_user_id` 等规划字段。

## 2. 权威来源层级

当来源冲突时按以下顺序处理：

1. 主库修复后的结构快照（2026-08-18，SHA-256 见 [README](README.md)）。
2. 同期 `information_schema` 结构和索引审计结果。
3. 阶段 0 数据修复、安全和 RBAC 决策记录。
4. 当前 Java Entity/Mapper/Service 的实际使用方式。
5. 仓库 `sql/` 下的历史初始化脚本。

历史 SQL 只用于发现遗漏和差异，不能覆盖已审计的实际结构。

## 3. V1 范围

V1 创建以下 20 张表，顺序按逻辑依赖组织，但不新增物理外键：

1. `user`
2. `tenant`
3. `role`
4. `permission`
5. `role_permission`
6. `user_role`
7. `team`
8. `team_member`
9. `project`
10. `milestone`
11. `task`
12. `weekly_review`
13. `prompt_template`
14. `ai_call_log`
15. `ai_draft`
16. `ai_draft_confirm_log`
17. `ai_replan_operation`
18. `ai_replan_item`
19. `task_status_idempotency`
20. `task_title_rename_log`

完整列、索引和检查约束见 [v1-schema-manifest.md](v1-schema-manifest.md)。

### 关键冻结决定

- `task.assignee_id` 是当前实际列，V1 必须保留，同时保留 `idx_task_assignee_id`。
- `task.status` 保留 `chk_task_status_range`（允许值 0、1、2、3）。
- `tenant`、`role`、`permission`、`role_permission`、`user_role` 是 V1 正式结构；当前不把 `user.user_role` 自动映射到 RBAC `user_role`。
- `team_member.role` 继续表示团队内部角色，不与系统 RBAC `role` 合并。
- V1 不新增 `system_role`，也不把规划中的 `assignee_user_id`、`assigned_by`、`assigned_at` 提前加入。负责人模型采用后续 V2 的扩展/收缩迁移，暂时兼容 `assignee_id`。
- V1 不新增外键。现有应用通过代码和审计约束维护关系，外键引入需另行评估历史脏数据、删除语义和回滚成本。
- 所有表显式使用 `ENGINE=InnoDB`、`DEFAULT CHARSET=utf8mb4`、`COLLATE=utf8mb4_0900_ai_ci`。

## 4. 迁移文件和执行策略（PR 2/3）

### 空库路径

PR 2 生成严格 DDL 的 `V1__baseline_schema.sql`。空库执行 `flyway migrate` 后应存在 20 张业务/RBAC/AI 表和 1 张 `flyway_schema_history`，不插入业务数据。

### 已有库接管路径

PR 3 执行前置备份、结构和数据守卫，再使用明确版本号执行 `flyway baseline -baselineVersion=1`，随后 `validate`、`info`，最后才允许应用启动。接管过程不执行 `V1__baseline_schema.sql` 的建表语句，避免对已有表重复建表。

### 迁移账户

应用账号保持 DDL-free。后续应单独创建最小权限的 `learning_manage_migrator`，只授予 Flyway 所需 DDL 和 `flyway_schema_history` 权限；账号创建和授权不属于 PR 1。

## 5. DDL 编写规则

- 使用严格 `CREATE TABLE`，不使用 `CREATE TABLE IF NOT EXISTS`。
- 不包含 `CREATE DATABASE`、`USE`、`DROP TABLE`、`LOCK TABLES`、`DEFINER` 或当前自增值。
- 明确列顺序、类型、是否可空、默认值、注释、主键、唯一键、普通索引和检查约束。
- 不复制任何生产/开发业务行；不写入密码、Token、API Key、Prompt 正文。
- 保持当前逻辑删除字段和时间字段语义；任何字段语义变更必须升为 V2 迁移。

## 6. 参考数据策略

V1 默认不包含业务数据。RBAC 结构中的已审计固定参考数据和 Prompt 模板候选数据单独受控，详见 [v1-reference-data-manifest.md](v1-reference-data-manifest.md)。在 Prompt 六行完成分类和审批前，PR 2 不得把其正文写入迁移文件。

## 7. PR 2/3 验收门槛

### PR 2（迁移文件）

- `mvn test` 和 Flyway 校验通过。
- 空 MySQL 8.0.41 库执行迁移成功，表数为 20，关键索引/检查约束存在。
- 第二次执行幂等（无新迁移、无校验漂移）。
- 迁移 SQL 静态扫描不含密钥、业务数据和禁用语句。

### PR 3（已有库接管）

- 备份可读且可恢复演练完成。
- 接管前后 20 张表的列、索引、引擎、字符集/排序规则一致。
- 结构和业务行数守卫通过；阶段 0 已确认的孤儿记录修复结果保持不变。
- `flyway_schema_history` 记录 V1，`validate` 和应用核心测试通过。
- 明确失败回滚点；任何失败先停止应用发布，不直接删除历史表或回滚生产数据。

## 8. PR 1 交付边界

本 PR 仅提交四份设计/清单文档。工作区不应出现 `src/main/resources/db/migration/`、Flyway 依赖、应用配置或数据库变更；这些内容留给 PR 2/3。
