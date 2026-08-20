# PR5-B 主库 V1 基线接管记录

执行日期：2026-08-20（Asia/Shanghai）  
执行状态：通过

## 1. 执行边界

本次在受保护维护窗口内对 3306 `learning_manage` 执行显式 Flyway V1 baseline。除新增 `flyway_schema_history` 及其 baseline 记录外，不允许改写业务表结构和业务数据。

本次未执行：

- `flyway clean`；
- 手工修改或删除 Flyway 历史记录；
- V1 业务表重建；
- 业务数据播种或删除；
- 应用账号 DDL 权限扩展。

## 2. 冻结输入与前置证据

```text
branch=develop
code_commit=a08fba45d487e28cb3a0ae4178694e97f9fe43eb
v1_sha256=E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9
database=learning_manage
mysql_version=8.0.41
baseline_version=1
```

PR5-A 已完成并提供以下证据：

| 证据 | 结果 |
|---|---|
| 主库前置审计 | 所有门禁 PASS，20 张业务表，Flyway 历史表不存在，活跃孤儿为 0 |
| 完整备份 | 133107 bytes，SHA-256 `DDAE77304CF5FECEBB0BC61572C3B14F931019ACE310F3F4E3392DFDB22401A9` |
| 结构备份 | 27857 bytes，SHA-256 `7BC761F10CC60973BCB8A41C93C70E5DE7074293F79CD951540748C5B980EB58` |
| 真实数据恢复库 | `learning_manage_pr5a_restore_20260820` |
| 恢复库 `mysqlcheck` | 20/20 张业务表全部 `OK` |
| 恢复库 baseline 演练 | baseline、validate、info、migrate(0) 全部符合预期 |

## 3. 主库执行结果

使用独立账号 `learning_manage_migrator@localhost`，业务账号 `learning_manage_app` 未获得 DDL 权限。

```text
info（baseline 前）
  info.current=<none>
  info.migration=1|baseline schema|PENDING

baseline
  baseline.baselineVersion=1
  baseline.success=true

validate
  validate.success=true

info（baseline 后）
  info.current=1
  info.migration=1|baseline schema|BASELINE_IGNORED
  info.migration=1|<< Flyway Baseline >>|BASELINE

migrate
  migrate.success=true
  migrate.migrationsExecuted=0
```

结果证明主库已有结构没有重复执行 V1 的建表语句，后续迁移入口可用。

## 4. 主库后置守卫

主库执行 `02_post_baseline_verify_main.sql` 后，结果如下：

| 检查项 | 结果 |
|---|---:|
| Flyway 历史记录行数 | 1 |
| BASELINE 记录 | 1 |
| 业务表数量 | 20 |
| 总表数量（含历史表） | 21 |
| 活跃孤儿数据 | 0 |
| 业务表行数 | 与接管前一致 |

接管后业务表行数保持：

```text
user=28, tenant=1, role=3, permission=15, role_permission=27, user_role=0
team=4, team_member=11, project=35, milestone=37, task=122
weekly_review=3, prompt_template=6, ai_call_log=3, ai_draft=5
ai_draft_confirm_log=2, ai_replan_operation=8, ai_replan_item=73
task_status_idempotency=0, task_title_rename_log=0
```

## 5. 运行状态收尾

- `FLYWAY_ENABLED=false`；
- `FLYWAY_BASELINE_AUTHORIZED=false`；
- `DB_NAME=learning_manage`；
- 隔离库临时授权已撤销；
- 主库迁移账号仅保留 `learning_manage.*` 授权；
- 未执行 `clean` 或 `repair`；
- 未修改业务表和业务数据。

## 6. 结论

PR5-B 已通过。3306 `learning_manage` 已正式接入 Flyway V1，当前基线版本为 1。后续新增数据库结构只能通过追加版本迁移完成，不得修改已发布的 V1 文件。

阶段 0.4 Flyway 基线工作完成；下一阶段为阶段 0 CI/Docker 门禁建设。进入阶段 1/V2 前，仍需完成整体计划要求的 CI 顺序验证。

## 7. 回滚原则

若后续发现业务结构或数据异常，先停止应用发布并保留现场，依据 PR5-A 最新完整备份执行审批后的恢复方案。不得手工删除 `flyway_schema_history`，不得执行 `flyway clean`。
