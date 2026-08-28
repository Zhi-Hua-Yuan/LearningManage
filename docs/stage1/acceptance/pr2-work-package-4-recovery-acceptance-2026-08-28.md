# PR2 工作包 4：备份与恢复演练验收记录

状态：`PASS`

日期：2026-08-28

## 1. 验收范围

本工作包验证迁移前备份、V1→V2 迁移和恢复到新 V1 目标库的完整顺序。演练只使用本机一次性 MySQL 8.0.41 实例 `127.0.0.1:33320`，未连接仓库外业务数据库。

执行脚本：[verify-v2-recovery.sh](../../../scripts/ci/verify-v2-recovery.sh)

## 2. Gate 结果

| Gate | 结果 | 证据 |
|---|---|---|
| V2-R-001 | PASS | V1 schema 与阶段 1 fixture 导入隔离源库，Flyway baseline=1 |
| V2-R-002 | PASS | 迁移前 `mysqldump --single-transaction` 成功，SHA-256=`0DB496159589C5FAE2A5381525FEF8CCDE8A9EFAC99DF55D7F2070236EFF9535` |
| V2-R-003 | PASS | 源库 25 项 preflight、V2 迁移 1 条、12 项 post-verify 全部通过 |
| V2-R-004 | PASS | 备份恢复到新目标库，目标库恢复为 V1 baseline |
| V2-R-005 | PASS | 目标库 20 张业务表、21 张总表、20 条业务记录；旧 `assignee_id` 保留，V2 列和关系表不存在 |
| V2-R-006 | PASS | 恢复日志未执行 `DROP DATABASE`，备份文件在脚本退出时删除 |

## 3. 关键输出

```text
recovery.verify.success=true
recovery.backup.sha256=0DB496159589C5FAE2A5381525FEF8CCDE8A9EFAC99DF55D7F2070236EFF9535
recovery.source.version=2
recovery.restored.version=1
recovery.restored.business_tables=20
recovery.restored.business_rows=20
```

## 4. 修正项

首次恢复尝试发现 MySQL migrator 账号没有 `LOCK TABLES` 权限；恢复 Gate 将 dump 参数固定为 `--skip-add-locks`，保持最小权限，不向 migrator 账号扩大表锁权限。Windows MySQL 客户端的 CR 行尾也已在 `ci-common.sh` 汇总入口统一清理，Linux CI 行为不变。

## 5. 清理与限制

- 临时 MySQL 实例、隔离数据库和临时 SQL 文件不纳入仓库；实例关闭后删除临时目录。
- 该演练证明恢复路径可执行，不授权在正式数据库上执行恢复或覆盖原库。
- GitHub Actions `flyway-existing` 仍需在 PR 分支上完成同一脚本的远程实跑，作为合并前保护门禁。
