# PR2 工作包 2：V2 迁移实跑验收记录

状态：`PASS`

日期：2026-08-27

上游合同：

- [工作包 1 输入与样本合同](../database/pr2-work-package-1-input-and-fixture.md)
- [V2 数据字典与迁移合同](../database/v2-data-dictionary.md)
- [V2 preflight](../../../sql/flyway/stage1/01_preflight_v2.sql)
- [V2 post-verify](../../../sql/flyway/stage1/02_post_verify_v2.sql)

## 1. 验证环境

- 数据库：MySQL `8.0.41`（Docker 镜像 `mysql:8.0.41`）
- 绑定地址：`127.0.0.1:33318`
- 验证数据库：一次性 `learning_manage_ci_empty_s1v2_8041`、`learning_manage_ci_legacy_s1v2_8041`
- fixture：`src/test/resources/db/stage1/v1_to_v2_seed.sql`
- fixture SHA-256：`914E302E9E97FEBBF10885F993A1A3474FCE34F7BF9AAD7C82EA65D70F866733`
- 正式迁移：`src/main/resources/db/migration/V2__stage1_business_semantics_and_permissions.sql`

测试账号和密码仅存在于本地临时容器环境，本记录不保存凭据。

## 2. 验收 Gate

| Gate | 结果 | 证据 |
|---|---|---|
| V2-G-001 | PASS | V1 结构与冻结 fixture 导入 legacy 库成功 |
| V2-G-002 | PASS | 25 项 `V2-P-*` preflight 检查全部 `violation_count=0` |
| V2-G-003 | PASS | Flyway baseline 版本 1 成功，V2 执行 1 条迁移 |
| V2-G-004 | PASS | 12 项 `V2-V-*` post-verify 检查全部 `violation_count=0` |
| V2-G-005 | PASS | 空库执行 V1+V2 共 2 条迁移并到达版本 2 |
| V2-G-006 | PASS | 空库、legacy 库 `validate` 成功，重复 migrate 均执行 0 条 |

## 3. 关键对账结果

- legacy 库记录数：`user=5`、`team=1`、`team_member=3`、`project=2`、`milestone=2`、`task=5`、`weekly_review=2`。
- V2 新增记录：`task_assignment_log=5`、`weekly_review_task=0`。
- 系统角色迁移后为 `USER=3`、`SYSTEM_ADMIN=2`。
- 任务 `6101`～`6105` 的受理人、分配操作人和分配时间均与冻结 JSON 预期一致。
- 日志 `6101`～`6105` 均为确定性的 `INITIAL_ASSIGN`，日志 ID 与任务 ID 一一对应。
- 两条存量周复盘均为 `PRIVATE`，团队、重点项目和共享摘要均为空。
- Flyway history：legacy 库为 baseline `1` 加 SQL `2`，均 `success=1`；空库为 SQL `1`、`2`，均 `success=1`。

## 4. 实跑中发现并修复的问题

首次执行 preflight 时，`V2-P-044` 将三列复合唯一索引按 `information_schema.statistics` 行数统计，产生误报 `violation_count=2`。已将该检查修正为 `COUNT(DISTINCT index_name)`，并补充静态测试断言；修正后 25 项检查全部通过。

Flyway 在 MySQL 8.0.41 输出 `BINARY expr` 弃用警告，但迁移、校验和对账均成功。当前 V2 迁移保持冻结，不在本工作包中改写已验证 SQL。

## 5. 清理与范围

- 两个数据库及迁移账号仅存在于一次性 Docker 容器中，验收结束后清理。
- 未连接或修改仓库外业务数据库。
- 未修改 V1 迁移、Java 业务代码、前端、CI 或部署配置。
