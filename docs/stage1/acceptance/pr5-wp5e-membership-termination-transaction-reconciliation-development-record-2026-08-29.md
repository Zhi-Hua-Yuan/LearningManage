# PR5 / WP5-E 成员终止事务回滚与对账开发记录

日期：2026-08-29

## 状态

```yaml
scope: WP5-E
implementation: completed
static_contract: passed
mysql_execution: passed_in_ci
acceptance: pending
baseline: cf084eb7c7b7b663af623bf56c16f629f271407d
expected_full_test_count: 500
ci_run_id: 33260176999
ci_commit: 75fd6b3
```

本记录只证明 WP5-E 的回滚与对账测试开发完成，不代表 WP5-E、WP5-D 或 PR5 已验收。

## 实施范围

- 扩展成员终止真实事务测试，覆盖 `MEMBER_REMOVED` 和 `MEMBER_LEFT` 审计异常回滚；
- 覆盖审计写入数量不一致时，在成员 CAS 前终止并回滚；
- 新增独占的 WP5-E MySQL 夹具，覆盖未完成任务、已完成任务和无未完成任务成员；
- 新增成功移除、主动退出和仅有已完成任务三类对账测试；
- 对账任务负责人、任务日志、成员失效状态、操作人、动作、数量和操作时间；
- 新增静态合同，锁定事务边界、写入顺序、数量检查和夹具范围；
- 未修改 V1/V2 migration、API 路由或生产配置。

## 事务失败不变量

任一审计异常或数量不一致均必须满足：

- 目标成员保持 `is_delete = 0`；
- 未完成任务恢复原受理人、操作者和时间；
- 不产生 `MEMBER_LEFT` 或 `MEMBER_REMOVED` 残留日志；
- 不返回成功终止结果。

## 成功对账不变量

- 未完成团队任务全部解除受理人；
- 已完成任务保留历史受理人且不追加终止日志；
- 每个实际解除任务恰好一条终止日志；
- 返回计数、任务更新数和终止日志数一致；
- 任务 `assigned_at`、日志 `create_time`、成员 `deleted_at` 与返回的 `terminatedAt` 在秒级一致；
- 失效成员不再是状态 `0` 团队任务的当前受理人；
- 不存在孤儿终止日志。

## 变更文件

- `src/test/java/com/spt/learningmanage/service/impl/TeamMembershipTerminationTransactionMySqlTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/TeamMembershipTerminationReconciliationMySqlTest.java`
- `src/test/java/com/spt/learningmanage/mapper/TeamMembershipTerminationConsistencyContractTest.java`
- `src/test/resources/db/stage1/wp5e_membership_transaction_seed.sql`
- `src/test/resources/db/stage1/wp5e_membership_transaction_cleanup.sql`
- `.github/workflows/backend-ci.yml`
- `.github/workflows/release-gate.yml`
- `docs/stage1/README.md`
- 本记录

## 当前验证证据

```yaml
test_compile: BUILD_SUCCESS
static_and_unit:
  tests: 18
  failures: 0
  errors: 0
mysql_transaction_and_reconciliation:
  tests: 6
  failures: 0
  errors: 0
full_maven_suite:
  tests: 500
  failures: 0
  errors: 0
  skipped: 0
ci:
  run_id: 33260176999
  result: success
  checks:
    - Guard and migration immutability
    - Maven verification and tested artifact
    - Flyway empty database gate
    - Flyway existing database gate
    - Docker runtime and migration gate
```

本地已通过静态合同、成员终止 Service/Policy/Controller 单元回归和测试编译；CI 已在隔离 MySQL 数据库执行事务回滚与对账测试，并完成 500 个 Maven 测试及全部工作流门禁。该证据只证明 WP5-E 开发验证完成，不改变正式验收状态。

## 后续门禁

- CI Maven 全量测试已达到实际 Surefire 数量 500，且无失败、无错误、无跳过；
- WP5-E 两个真实 MySQL 测试类共 6 个测试已全部通过；
- Guard、Flyway empty/existing database 和 Docker runtime gates 已全部通过；
- WP5-F 仍负责最终并发门禁、风险关闭和 PR5 最终验收。
