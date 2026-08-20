# PR4 应用启用 Flyway 与启动验证记录

执行日期：2026-08-19（Asia/Shanghai）  
状态：通过

## 1. 实施内容

- 在共享配置和 `dev` / `test` / `prod` profile 中显式声明 `FLYWAY_ENABLED`，默认值为 `false`。
- 为 Flyway 增加独立迁移账号变量 `FLYWAY_DB_USERNAME` / `FLYWAY_DB_PASSWORD`。
- 未提供迁移账号时使用不可用占位值，开启 Flyway 会快速失败，不会回退到业务应用账号。
- 保持 `baseline-on-migrate=false`、`validate-on-migrate=true`、`clean-disabled=true`、`out-of-order=false` 和 `classpath:db/migration`。
- Docker Compose 删除旧 `sql/` 初始化目录挂载，并透传 Flyway 开关及迁移账号变量；V1 成为唯一结构迁移入口。
- 新增 `FlywayConfigurationContractTest`，锁定默认关闭、账号隔离和安全迁移参数。

## 2. 启用方式

### 空库或已完成 baseline 的隔离环境

```text
FLYWAY_ENABLED=true
FLYWAY_DB_USERNAME=<受控迁移账号>
FLYWAY_DB_PASSWORD=<迁移账号密码>
```

数据库地址和库名仍由 `DB_HOST`、`DB_PORT`、`DB_NAME` 提供；应用业务账号继续由 `DB_USERNAME` / `DB_PASSWORD` 提供。两类账号不得混用。

### 生产推荐流程

1. 备份并完成变更窗口审批。
2. 使用独立迁移账号执行 `validate` 和 `migrate`；已有结构库先显式 `baseline -baselineVersion=1`。
3. 确认 `flyway_schema_history` 与结构守卫结果后，以 `FLYWAY_ENABLED=false` 启动后端。
4. 只有在受控启动迁移窗口内，才将 `FLYWAY_ENABLED` 临时设为 `true`，并在结束后恢复为 `false`。

## 3. 验证结果

隔离启动环境为本机临时 MySQL 8.0.41（仅监听 3310），使用库
`learning_manage_flyway_app_empty_20260819`；3306 主库未参与验证。

| 检查项 | 结果 |
|---|---|
| `FlywayConfigurationContractTest` | 2 项通过 |
| Maven 全量测试 | 64 项通过，0 失败，0 错误 |
| 应用启动（`FLYWAY_ENABLED=true`） | `/api/health` 返回 HTTP 200；空库创建 20 张业务表和 `flyway_schema_history`，V1 执行 1 条且 `success=1` |
| 应用启动（`FLYWAY_ENABLED=false`） | `/api/health` 返回 HTTP 200；日志无 Flyway 执行记录，历史表仍为 1 条 V1 成功记录 |
| 业务账号权限边界 | `app_runtime` 执行 DDL 探针返回 `ERROR 1142 CREATE command denied` |
| 应用上下文（`test` profile，Flyway 默认关闭） | 通过；`LearningManageApplicationTests` 通过 |
| 主库 3306 | 未连接、未迁移、未创建或修改 `flyway_schema_history` |
| Docker/Testcontainers | Docker Desktop Linux 守护进程不可用，未执行容器化启动 |

## 4. 风险与后续

- PR4 不自动接管主库；生产首次接管仍需按 PR3 的备份、显式 baseline 和结构守卫流程执行。
- 若 `FLYWAY_ENABLED=true` 但未配置独立迁移账号，预期启动失败；该失败是安全保护，不应通过给业务账号追加 DDL 权限解决。
- 后续 CI 应在可用的 MySQL/Testcontainers 环境补充应用进程级 `FLYWAY_ENABLED=true` 启动验收。
