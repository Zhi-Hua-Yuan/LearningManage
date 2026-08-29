# PR4-D2-E 负责人 CAS、事务与审计对账验收记录

日期：2026-08-29
状态：`COMPLETED / CI_PASS`

## 1. 基线与范围

本工作包基于已合并的 D2-C 基线：

```text
a16181a4cd12c825eb005f56906615d4519e98de
```

分支：`codex/stage1-pr4-d2e`

D2-E 仅补齐负责人变更链路的真实 MySQL 验收，不修改 D1 查询契约、D2-A/B/C 生产代码、
V1/V2 migration 或 PR5/PR6 范围。

## 2. 新增验收内容

- `TaskAssignmentConcurrencyMySqlTest`：两个并发请求使用相同
  `expectedAssigneeUserId`、不同目标负责人，验证恰好一个 CAS 成功，且只写入一条审计日志；
- `TaskAssignmentTransactionMySqlTest`：真实任务更新配合受控日志写入失败，验证事务整体回滚；
- `TaskAssignmentTransactionMySqlTest`：验证 no-op 不改变任务快照、不产生日志；
- `TaskAssignmentAuditReconciliationMySqlTest`：验证当前负责人合法性、历史存在性、孤儿日志、
  动作转换、日志链、最新日志与任务负责人/操作者/时间的一致性；
- 新增 D2-E 专用 fixture；并发测试使用类级一次性提交种子，读对账测试使用测试事务回滚，
  事务测试使用服务自身事务验证回滚，避免测试账号缺少 DELETE 权限造成清理失败。

## 3. 验收断言

| Gate | 预期 |
|---|---|
| 双并发 CAS | 1 个成功、1 个 `50001` 冲突、1 条日志 |
| 并发最终状态 | 任务负责人等于唯一成功日志的 `to_assignee_user_id` |
| 事务回滚 | 任务负责人、操作者、时间和日志计数均恢复 |
| no-op | `changed=false`，任务快照和日志计数不变 |
| 当前负责人合法性 | 异常计数为 0 |
| 历史存在性 | 异常计数为 0 |
| 孤儿日志 | 异常计数为 0 |
| 动作转换 | 异常计数为 0 |
| 日志链连续性 | 异常计数为 0 |
| 最新日志对账 | 负责人、操作者、时间异常计数均为 0 |

## 4. 变更文件

- `src/test/java/com/spt/learningmanage/service/impl/TaskAssignmentConcurrencyMySqlTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskAssignmentTransactionMySqlTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/TaskAssignmentAuditReconciliationMySqlTest.java`
- `src/test/resources/db/stage1/permission_mapper_v2_cleanup.sql`
- `src/test/resources/db/stage1/task_assignment_d2e_audit_seed.sql`
- `src/test/resources/db/stage1/task_assignment_d2e_audit_cleanup.sql`
- `src/test/resources/db/stage1/task_assignment_d2e_concurrency_seed.sql`
- `src/test/resources/db/stage1/task_assignment_d2e_transaction_seed.sql`
- `.github/workflows/backend-ci.yml`
- `.github/workflows/release-gate.yml`
- `docs/stage1/README.md`
- 本验收记录

## 5. 本地验证

测试编译已通过。聚焦执行包含 4 个 D2-E MySQL 测试，但本机未配置
`${TEST_DB_USERNAME}`，四个测试均在 `@Sql` 建立事务阶段被数据库认证阻塞，未进入业务断言：

```text
Tests run: 4, Failures: 0, Errors: 4, Skipped: 0
原因：Access denied for user '${TEST_DB_USERNAME}'@'localhost'
```

因此本地结果不能作为 D2-E PASS 证据。CI 必须在隔离 MySQL 8.0.41 环境实际执行这些测试。

CI 验收已完成：GitHub Actions run `33239260276` 的 Maven verification 在隔离 MySQL 8.0.41
执行 432 个 Surefire 测试，`Failures: 0, Errors: 0`；双并发 CAS、事务回滚、no-op 和审计
对账测试均通过。Guard、Flyway empty/existing database 与 Docker runtime gates 全部通过。

`git diff --check` 应作为提交前 Gate；V1/V2 migration 在本工作包中保持未修改。

## 6. 测试门槛

D2-C 基线为 428 个 Surefire 测试，本工作包新增 4 个测试，
backend CI 和 release gate 的期望值更新为 `432`。最终仍以 CI Surefire 实际计数为准，
不得通过跳过 MySQL 测试或降低门槛收口。

## 7. 合同状态

- `S1-A-003`：`PASS`，CI run `33239260276` 已完成隔离 MySQL 并发、回滚、no-op 和审计对账；
- `S1-R-014`：保持 D2-C 已确认的 `CLOSED`；
- `S1-R-003`：保持 `OPEN`，由 PR5 处理；
- `S1-R-008`、`S1-R-012`：保持 `OPEN`，由 PR6 处理；
- V1/V2 migration：未修改。

## 8. 完成条件

CI MySQL Gate 全部通过、完整 Surefire 计数为 432、所有审计异常计数为 0，完成本工作包。
