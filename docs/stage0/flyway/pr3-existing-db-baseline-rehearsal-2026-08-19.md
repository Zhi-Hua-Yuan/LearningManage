# PR3 已有数据库接管演练记录

执行日期：2026-08-19（Asia/Shanghai）  
状态：通过

## 1. 隔离边界

- 使用同一独立 MySQL 8.0.41 实例（本机 3307），不连接现有 3306 实例。
- 目标库：`learning_manage_flyway_legacy_20260819`。
- 从阶段 0 凭据轮换前后审计得到的**结构-only**转储导入；转储 SHA-256：`F19C30AA58E4E34256352A0327DD448BE7C0E460CE2D8B9A824B06ABC2D800CB`。
- 转储导入前库中有 20 张应用表、无 `flyway_schema_history`，无业务数据。

## 2. 显式 baseline 接管

使用 Flyway 10.10.0 Java API 执行显式 `baseline`，参数为版本 `1`；运行配置保持：

- `baselineOnMigrate=false`
- `validateOnMigrate=true`
- `cleanDisabled=true`
- `outOfOrder=false`

结果：

```text
baseline.baselineVersion=1
baseline.success=true
```

接管后数据库检查：

| 检查项 | 结果 |
|---|---|
| 应用表数量 | `20`，未改变 |
| 总表数量（含历史表） | `21` |
| 历史记录行数 | `1` |
| 历史记录类型 | `BASELINE` |
| 历史记录版本 | `1` |
| 历史记录脚本 | `<< Flyway Baseline >>` |
| 非历史表非空数量 | `0` |
| `task.assignee_id` | 存在 |
| `idx_task_assignee_id` | 存在 |
| `chk_task_status_range` | 存在 |

## 3. 接管后守卫

接管后依次执行 `validate`、`info` 和 `migrate`：

```text
validate.success=true
info.migration=1|baseline schema|BASELINE_IGNORED
info.migration=1|<< Flyway Baseline >>|BASELINE
migrate.success=true
migrate.migrationsExecuted=0
```

这证明 V1 不会在已有结构库上重复执行；显式 baseline 只写入接管标记，后续迁移入口保持可用。

## 4. 结论与限制

- 已有结构库可以通过显式 baseline 版本 1 接管，且不会重建或改写既有 20 张表。
- PR3 未对 `learning_manage` 主库执行 `migrate`、`baseline`、`clean` 或任何 DDL/DML。
- 本次使用结构-only 转储验证结构接管；真实数据量、应用账号权限和生产备份恢复仍需在具备受控凭据的环境执行。
- Docker 不可用的限制已记录；后续应在 CI/Testcontainers 环境补充同等自动化验收。

