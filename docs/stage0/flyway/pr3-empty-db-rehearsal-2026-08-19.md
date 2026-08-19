# PR3 空库 Flyway 迁移演练记录

执行日期：2026-08-19（Asia/Shanghai）  
状态：通过

## 1. 隔离边界

- Docker Desktop Linux 守护进程不可用，因此未使用 Docker/Testcontainers。
- 在项目 `.codex-tmp/pr3-mysql-20260819d/` 初始化独立 MySQL 8.0.41 实例。
- 实例仅监听本机 3307；3306 上的现有实例未连接、未写入。
- 目标库：`learning_manage_flyway_empty_20260819`。
- 迁移文件：`src/main/resources/db/migration/V1__baseline_schema.sql`。
- V1 SQL SHA-256：`E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9`。

## 2. 执行结果

通过 Flyway 10.10.0 Java API，以 `baselineOnMigrate=false`、`validateOnMigrate=true`、`cleanDisabled=true`、`outOfOrder=false` 执行 `migrate`：

| 检查项 | 结果 |
|---|---|
| `migrate.success` | `true` |
| `migrationsExecuted` | `1` |
| 目标版本 | `1` |
| 业务表数量 | `20` |
| 总表数量（含历史表） | `21` |
| `flyway_schema_history` | 已创建 |
| 历史记录 | V1 `success=1` |
| 业务表数据 | 全部为 0 行（结构演练，无业务数据） |

历史记录确认如下：

```text
version=1
description=baseline schema
script=V1__baseline_schema.sql
success=1
```

## 3. 结论

- 空库可以按 V1 正常建立结构，Flyway 历史记录与 V1 脚本一致。
- 未执行任何业务数据、RBAC 数据或 Prompt 正文播种。
- 未执行 `clean`、乱序迁移或自动 baseline。
- 该演练库仅用于复核，未替代生产/开发主库迁移。

