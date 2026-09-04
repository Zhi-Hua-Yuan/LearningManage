# WP1 V3 数据预审、迁移与恢复说明

状态：`FROZEN`
迁移：`V3__stage2_ai_invocation_governance.sql`

## 1. 目标与边界

V3 只为阶段 2 后续工作包准备 AI 调用、草稿和 Trace 数据结构，并把确认日志的数据库幂等边界从 `(user_id,draft_id,operation_id)` 收紧为 `(user_id,draft_id)`。它不改变 Controller、Service、现有 API 或 AI 调用行为，也不实现 WP2～WP6 的运行逻辑。

V1、V2 保持不可变。V3 不删除 `model_name`、`operation_id`、现有正文或业务表；历史未知 Token、成本、Trace 和哈希不伪造为 0。

## 2. 数据预审

只读入口：

```bash
mysql < sql/flyway/stage2/01_preflight_v3.sql
```

13 项检查输出 `check_id`、`finding_count`、`classification` 和 `status`。分类规则如下：

| 分类 | 含义 | 自动处理 |
|---|---|---|
| `CLEAN` | 未发现问题 | 无需处理 |
| `REPAIRABLE_EQUIVALENT_DUPLICATE` | 同一用户/草稿的 scene 与 business_id（NULL-safe）完全相同 | V3 归档较晚记录并保留最早记录 |
| `BLOCKING_CONFLICT` | 同一用户/草稿出现不同 scene 或 business_id | 禁止迁移，人工确认业务事实 |
| `BLOCKING_INTEGRITY_ERROR` | 孤立日志、用户/场景/状态不一致、已确认草稿缺少结果或 Schema 异常 | 禁止迁移，先修复并重新预审 |

`ai_draft.draft_id` 在 V1 已全局唯一；跨用户伪造的确认记录会由“确认用户与草稿用户一致”检查阻断。WP5 仍必须在运行时重新校验权限。

## 3. 迁移顺序与安全性

1. 使用临时 guard/abort 表在任何持久化 DDL 前检查阻断项。
2. 创建 `ai_draft_confirm_log_archive`，复制等价重复的完整旧记录。
3. 对账归档数量与待删除数量；不一致时中止。
4. 删除已归档副本，保留 `create_time` 最早、相同时间下 `id` 最小的记录。
5. 先创建 `(user_id,draft_id)` 唯一索引，再删除旧三列唯一索引。
6. 添加 AI 调用治理、草稿 Schema/Trace、确认 Trace 和重排 Trace 字段。
7. 仅回填 `requested_model=model_name`；其他未知历史元数据保持 `null` 或 `LEGACY_UNKNOWN`。

迁移账号除既有 DML/DDL 权限外，需要数据库级 `CREATE TEMPORARY TABLES`；应用账号仍只有 DML 权限。

## 4. 发布与恢复 Runbook

正式环境执行前：

1. 确认目标为明确命名的业务库，禁止自动 `baselineOnMigrate`。
2. 执行只读预审并保存完整输出；任何 `FAIL` 都停止发布。
3. 生成完整备份和结构备份，记录 SHA-256，并恢复到隔离库验证可用性。
4. 在恢复副本执行 V3、14 项可重复 Schema/数据不变量校验、5 项一次性历史回填校验和应用冒烟。
5. 维护窗口内先迁移、再启动应用。

MySQL DDL 可能隐式提交。V3 失败后不得直接执行未经验证的 `flyway repair`：若持久化 DDL 尚未开始，修复数据后在新副本重演；若已开始，优先从迁移前备份恢复，或以经过评审的新前向迁移修复。向生产执行 V3 必须单独授权。

迁移后只读校验入口：

```bash
mysql < sql/flyway/stage2/02_post_verify_v3.sql
```

`02_post_verify_v3.sql` 只验证可长期成立的 Schema 与数据不变量，因此 WP2～WP6 写入真实 Token、Trace、脱敏状态或新版草稿后仍可重复执行。以下历史回填校验只能在 V3 执行后、V3-aware 应用开始写入前运行：

```bash
mysql < sql/flyway/stage2/03_verify_v3_legacy_backfill.sql
```

V3 本身重复执行与只读预审相同的关键 Schema 和数据阻断，避免运维人员漏跑独立预审后进入非事务 DDL。独立预审仍用于生成可读审计报告。

## 5. CI 入口

```bash
scripts/ci/verify-empty-database.sh
scripts/ci/verify-existing-database.sh
scripts/ci/verify-v3-negative-preflight.sh
scripts/ci/verify-v3-equivalent-duplicates.sh
scripts/ci/verify-v3-recovery.sh
```

CI 使用非 3306 端口、目标库白名单、独立迁移/应用账号。V3 SHA-256 在本轮修复验证完成后重新封存到发布迁移清单；受保护 PR 通过前 `S2-A-005` 保持 `PENDING`。
