# PR5 / WP5-F 最终并发门禁开发记录

日期：2026-08-29

## 状态

```yaml
scope: WP5-F
implementation: completed
static_contract: passed
mysql_execution: pending_ci
acceptance: pending
baseline: 777f46a92665fbce057d9fbd1a498b6b700be58d
expected_full_test_count: 507
```

本记录只证明 WP5-F 最终并发测试和静态合同的开发完成，不代表 WP5-F 或 PR5 已验收。

## 实施范围

- 新增管理员移除与指定受理人创建竞争测试；
- 新增管理员移除与普通分配竞争测试；
- 新增管理员移除与 `completed → TODO` 重新打开竞争测试；
- 新增 `remove vs remove` 双终止竞争测试；
- 新增 `leave vs remove` 退出/移除竞争测试；
- 验证竞争失败只返回 `40300` 或重新打开资格失败 `50001`，不产生失败幂等记录；
- 验证失效成员不再是状态 `0` 团队任务的当前受理人；
- 验证双终止只产生一组终止日志，且成员关系只失效一次；
- 新增独占的 WP5-F MySQL fixture，未修改任何 V1/V2 migration；
- 新增静态合同，锁定测试覆盖、夹具范围、线程清理和隔离数据库约束；
- 同步 `backend-ci.yml` 与 `release-gate.yml` 的测试计数门禁为 507。

## 竞争不变量

- 任务操作与成员移除竞争后，不存在失效成员关联的状态 `0` 任务；
- 成员移除先提交时，后续任务操作基于锁内事实失败；
- 任务操作先提交时，成员移除随后清理新产生的未完成受理关系；
- `remove vs remove` 和 `leave vs remove` 只能一个请求成功，另一个返回 `40300`；
- 双终止只产生一组终止日志，不产生重复或混合半状态；
- 已完成任务保留历史负责人，重新打开成功后才进入正常的未完成任务清理语义。

## 变更文件

- `src/test/java/com/spt/learningmanage/service/impl/TeamMembershipTerminationConcurrencyMySqlTest.java`
- `src/test/java/com/spt/learningmanage/service/impl/Pr5FinalConcurrencyContractTest.java`
- `src/test/resources/db/stage1/wp5f_membership_termination_concurrency_seed.sql`
- `src/test/resources/db/stage1/wp5f_membership_termination_concurrency_cleanup.sql`
- `.github/workflows/backend-ci.yml`
- `.github/workflows/release-gate.yml`
- `docs/stage1/README.md`
- 本记录

## 当前验证证据

```yaml
test_compile: BUILD_SUCCESS
static_and_unit:
  tests: 22
  failures: 0
  errors: 0
mysql_final_concurrency: pending_ci
full_maven_suite: pending_ci
```

本地已通过测试编译、WP5-F 静态合同、WP5-B/C/D/E 静态合同和成员终止单元回归；真实 MySQL 并发结果必须由隔离 CI 数据库执行后才能记录。

## 后续门禁

- CI Maven 全量测试必须按实际 Surefire 数量达到 507，且无失败、无错误、无跳过；
- WP5-F 五个真实 MySQL 并发测试必须全部通过；
- Guard、Flyway empty/existing database 和 Docker runtime gates 必须全部通过；
- WP5-F 合并后仍需以 `develop` post-merge CI 作为 PR5 最终验收证据；
- 只有完成 post-merge 证据，才能更新 `S1-A-004`、`S1-R-003`、ADR-005 和 PR5 最终收口记录。
