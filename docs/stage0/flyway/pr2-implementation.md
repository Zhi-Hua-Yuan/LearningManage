# Flyway V1 PR 2 实施记录

状态：已实施，等待评审与提交  
实施日期：2026-08-19 Asia/Shanghai

## 1. 实施范围

- 在 `pom.xml` 增加 `flyway-core` 与 `flyway-mysql`，版本由 Spring Boot 3.3.6 BOM 管理。
- 在 `application.yml` 增加 Flyway 配置：默认关闭、禁止自动基线、启用校验、禁止 clean、禁止乱序迁移。
- 新增 `src/main/resources/db/migration/V1__baseline_schema.sql`，按 2026-08-18 审计快照创建冻结的 20 张表。
- 新增 `FlywayV1MigrationStaticTest`，校验表清单、关键任务结构和危险/数据写入语句禁用规则。

## 2. 安全与边界

- `FLYWAY_ENABLED` 默认值为 `false`；PR 2 不会在应用启动时连接数据库或执行迁移。
- `baseline-on-migrate=false`，已有数据库接管留给 PR 3 的显式 `flyway baseline -baselineVersion=1` 流程。
- V1 只包含 DDL，不包含 `INSERT`、业务数据、RBAC 参考数据、Prompt 正文、密钥或 Token。
- 未修改旧 `sql/` 初始化脚本、Docker 挂载、数据库账号权限和现有数据库。
- 未引入外键、`system_role`、V2 负责人字段；保留 `task.assignee_id`、`idx_task_assignee_id` 和 `chk_task_status_range`。

## 3. 验证结果

| 检查项 | 结果 |
|---|---|
| `git diff --check` | 通过 |
| V1 表数量与顺序 | 20 张，和结构清单一致 |
| 静态禁用规则 | 未发现 INSERT、DROP、CREATE DATABASE、USE、DEFINER、IF NOT EXISTS、FOREIGN KEY |
| Maven 测试 | 62 项通过，0 失败，0 错误 |
| Maven 打包 | 通过；产物包含 V1 SQL、`flyway-core` 10.10.0 和 `flyway-mysql` 10.10.0 |
| 现有数据库 | 未连接、未修改；未创建 `flyway_schema_history` |

打包产物 SHA-256：`968605C4E8F13FF1AB6DA20D54A215E86DC9E94D0A723D9D55C4355C98538059`。

## 4. 后续边界

PR 3 再执行空库迁移验证、已有库备份与 Flyway baseline 接管、结构/数据守卫和回滚演练。PR 2 不应在生产或当前开发主库开启 `FLYWAY_ENABLED`。
