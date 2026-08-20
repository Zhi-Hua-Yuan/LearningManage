# PR5-A 主库接管准备记录

执行日期：2026-08-20（Asia/Shanghai）  
状态：PR5-A 完成；3306 `learning_manage` 主库尚未执行 baseline

## 1. 执行边界

本 PR5-A 完成接管前准备和隔离库真实数据演练，但不对 3306 主库执行结构接管：

- 不连接或修改 3306 `learning_manage` 的业务表；
- 不在 3306 主库创建 `flyway_schema_history`；
- 不对 3306 主库执行 `baseline`、`migrate`、`clean` 或 `repair`；
- 迁移账号仅被授予 `learning_manage.*` 权限，隔离库演练授权已撤销；
- 不生成或读取任何密码正文。

数据库动作在用户本机受保护 PowerShell/MySQL 会话中完成，密码正文未进入仓库、记录或聊天。

## 2. 冻结输入

```text
branch=develop
commit=a08fba45d487e28cb3a0ae4178694e97f9fe43eb
v1_sha256=E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9
flyway_version=10.10.0
expected_database=learning_manage
expected_baseline_version=1
```

PR5-A 要求接管窗口内继续保持当前提交中只有 V1 的状态，不得在主库 baseline 前加入 V2。

## 3. 已落地内容

| 内容 | 文件 | 结果 |
|---|---|---|
| 一次性 Flyway 管理入口 | `src/main/java/com/spt/learningmanage/flyway/FlywayAdmin.java` | 仅允许 info/validate/baseline/migrate |
| Windows 调用封装 | `scripts/flyway-admin.ps1` | 凭据只从环境变量读取，不回退业务账号 |
| baseline 授权门槛 | `FlywayAdmin` | 必须显式确认本次 `DB_NAME`、版本1和授权开关 |
| baseline 单元契约 | `FlywayAdminContractTest` | 覆盖授权和禁止 clean/repair |
| 主库接管前置守卫 | `sql/flyway/stage0/main/01_preflight_main.sql` | 只读，检查数据库、表数、历史表、孤儿和结构输出 |
| baseline 后守卫 | `sql/flyway/stage0/main/02_post_baseline_verify_main.sql` | 只读，检查历史记录、表数、行数和孤儿 |
| 环境变量示例 | `.env.example` | 增加 Flyway 开关、迁移账号和 baseline 门槛变量 |

## 4. 本地验证结果

| 检查项 | 结果 |
|---|---|
| `.\mvnw.cmd test` | 67 项通过，0 失败，0 错误 |
| `scripts/flyway-admin.ps1 info`（无凭据） | 在连接前拒绝，未尝试回退业务账号 |
| PR5-A SQL 写操作扫描 | 未发现 `INSERT`、`UPDATE`、`DELETE`、`DROP`、`ALTER`、`CREATE`、`TRUNCATE` |
| `git diff --check` | 通过 |
| 3306 主库 | 未执行 baseline、migrate、clean 或业务表写操作 |

## 5. 数据库侧执行记录

### 5.1 主库前置审计

`01_preflight_main.sql` 在 3306 `learning_manage` 上执行，全部门禁通过：

| 检查项 | 结果 |
|---|---:|
| MySQL 版本 | 8.0.41 |
| 业务表数量 | 20 |
| `flyway_schema_history` | 不存在 |
| 活跃孤儿关联 | 0 |
| `task.assignee_id` / 关键索引和约束 | 已在结构输出中确认 |

接管前业务行数：

```text
user=28, tenant=1, role=3, permission=15, role_permission=27, user_role=0
team=4, team_member=11, project=35, milestone=37, task=122
weekly_review=3, prompt_template=6, ai_call_log=3, ai_draft=5
ai_draft_confirm_log=2, ai_replan_operation=8, ai_replan_item=73
task_status_idempotency=0, task_title_rename_log=0
```

### 5.2 主库备份

备份目录：`.codex-tmp/pr5a-main-20260820/`

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `learning_manage-full.sql` | 133107 bytes | `DDAE77304CF5FECEBB0BC61572C3B14F931019ACE310F3F4E3392DFDB22401A9` |
| `learning_manage-schema.sql` | 27857 bytes | `7BC761F10CC60973BCB8A41C93C70E5DE7074293F79CD951540748C5B980EB58` |

### 5.3 真实数据隔离恢复

| 项目 | 结果 |
|---|---|
| 隔离库 | `learning_manage_pr5a_restore_20260820` |
| 完整备份恢复 | 成功 |
| `mysqlcheck --check` | 20/20 张业务表全部 `OK` |
| 恢复前历史表 | 不存在 |
| 恢复前业务行数 | 与主库备份前一致 |

### 5.4 隔离库 baseline 演练

隔离库临时授予迁移账号同等库级权限，演练结束后已撤销。

```text
info（baseline 前）
  info.current=<none>
  info.migration=1|baseline schema|PENDING

baseline
  baseline.baselineVersion=1
  baseline.success=true

validate
  validate.success=true
  validated 2 migrations（V1 文件 + baseline 记录，符合预期）

info（baseline 后）
  info.current=1
  info.migration=1|baseline schema|BASELINE_IGNORED
  info.migration=1|<< Flyway Baseline >>|BASELINE

migrate
  migrate.success=true
  migrate.migrationsExecuted=0
```

### 5.5 隔离库 baseline 后守卫

`02_post_baseline_verify_main.sql` 全部门禁通过：

| 检查项 | 结果 |
|---|---:|
| 历史记录行数 | 1 |
| BASELINE 记录 | 1 |
| 业务表数量 | 20 |
| 总表数量（含历史表） | 21 |
| 活跃孤儿数据 | 0 |
| 业务行数 | 与接管前一致 |

### 5.6 清理结果

- 已撤销 `learning_manage_migrator` 对隔离库的临时授权；
- 迁移账号仍保留 `learning_manage.*` 授权；
- 3306 主库仍不存在 `flyway_schema_history`；
- 当前 PowerShell 已恢复 `DB_NAME=learning_manage`；
- `FLYWAY_BASELINE_AUTHORIZED=false`；
- `FLYWAY_ENABLED=false`；
- 未执行 `flyway clean`，未手工修改历史表。

业务账号 `learning_manage_app` 不得追加 DDL 权限，也不得被用作 Flyway 账号。

## 6. PR5-B 前置证据

在执行主库 baseline 前，必须补齐以下仓库外证据：

以上 PR5-B 前置证据已完成。进入主库 baseline 前仍需重新确认维护窗口、应用停止状态和最新只读审计结果。

## 7. PR5-B 预期结果

主库正式接管只允许新增：

```text
flyway_schema_history
版本 1 / 类型 BASELINE / 脚本 << Flyway Baseline >>
```

V1 的20张业务表不应被重新创建，业务表行数和结构摘要必须与接管前一致。接管后先以 `FLYWAY_ENABLED=false` 启动应用，再进行健康检查和只读核心接口验证。

## 8. 回滚与停止规则

任何业务表结构、数据行数或质量守卫异常，立即停止发布并保留现场。不得手工删除或修改 Flyway 历史记录，不得执行 `flyway clean`；应依据接管前最新备份和审批后的恢复方案处理。

## 9. 下一步

PR5-A 已完成。下一步进入 PR5-B：在维护窗口内对 3306 `learning_manage` 执行显式版本 1 baseline，随后 validate、info、migrate(0) 和主库后置守卫。应用继续以 `FLYWAY_ENABLED=false` 启动。
