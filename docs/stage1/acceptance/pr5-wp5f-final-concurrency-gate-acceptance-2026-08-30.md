# PR5 / WP5-F 最终并发门禁验收记录

状态：`PASS`

日期：2026-08-30

目标分支：`develop`

## 1. 验收依据

WP5-F 基于 PR #55 合并后的 `develop` 基线执行。PR #55 合并提交为：

```text
c2798c26fcaf093e56419184850fa729c170aa64
```

合并后 Backend CI 使用隔离 MySQL 数据库执行，run `33262101089` 的五项必需 Job 全部成功。

## 2. 竞争场景

真实 MySQL 测试覆盖：

- 指定受理人创建与管理员移除竞争；
- 普通分配与管理员移除竞争；
- `completed → TODO` 重新打开与管理员移除竞争；
- `remove vs remove` 双移除竞争；
- `leave vs remove` 退出/移除竞争。

最终结果满足：

- 任务操作与成员移除竞争后不存在失效成员关联的状态 `0` 任务；
- 双移除只能一个成功，另一个返回 `40300`；
- 退出与移除只能一个成功，另一个返回 `40300`；
- 双终止只产生一组终止日志；
- 竞争失败不产生失败幂等记录；
- 已完成任务保留历史负责人。

## 3. 测试证据

```yaml
wp5f_mysql_concurrency:
  tests: 5
  failures: 0
  errors: 0
full_maven_suite:
  tests: 507
  failures: 0
  errors: 0
  skipped: 0
ci_run_id: 33262101089
ci_result: success
```

Guard、Flyway empty/existing database 和 Docker runtime gates 全部通过。V1/V2 migration 与 Flyway history 未发生变化。

## 4. 结论

WP5-F 的最终并发门禁通过，WP5-F 状态为 `PASS`。本记录与 WP5-E 回滚/对账记录、WP5-D 任务竞争记录共同作为 PR5 最终验收证据。
