# PR5 / WP5-D4 任务变更与成员终止并发测试开发记录

日期：2026-08-29

## 状态

```yaml
scope: WP5-D4
implementation: completed
static_contract: passed
mysql_execution: passed_in_ci
acceptance: pending
ci_expected_test_count: 492
ci_run_id: 33257167150
ci_commit: 6d65e4337b4fea3fb942bca67af5e8b3f60a7bc8
```

本记录只证明 D4 测试夹具和静态合同的开发范围，不代表 WP5-D 或 PR5 已验收。

## 开发内容

- 新增隔离的 V2 MySQL 测试夹具，使用项目 `47001`、团队 `27001` 和独占用户/任务 ID 段。
- 覆盖指定受理人创建与成员退出竞争。
- 覆盖普通分配与成员退出竞争。
- 覆盖 `completed → TODO` 重新打开与成员退出竞争。
- 覆盖重新打开与转派竞争，检查状态和负责人 CAS。
- 覆盖 `team_member ... FOR UPDATE` 阻塞创建资格校验。
- 保留空负责人 (`NULL`) 的重新打开 CAS 夹具。
- 所有异步线程显式设置并清理 `UserHolder`，所有 Future 使用有限超时。
- 新增静态合同测试，检查场景覆盖、数据库范围和 Flyway 不变式。
- 同步 `backend-ci.yml` 与 `release-gate.yml` 的测试总数门禁为 492（当前 WP5 分支实际完整 Surefire 总数）。
- CI 测试应用账号补充 `DELETE` 权限，仅用于 `@Sql` 隔离夹具清理；未增加任何 DDL 权限。

## 允许的并发结果

- 任务操作先提交：成员终止随后清理未完成任务负责人并写成员终止日志。
- 成员终止先提交：任务操作基于锁内成员事实失败，不产生半成品或失败幂等记录。
- 重新打开与转派竞争：任一方基于过期状态/负责人快照执行时，CAS 必须失败。
- 已完成任务在成员终止先提交时保留历史负责人；重新打开随后因资格失效返回操作错误。

## 后置项

- 本地测试环境仍使用字面量 `${TEST_DB_USERNAME}` 占位凭据；本地无法替代 CI 证据。
- GitHub Actions 已使用隔离 CI 数据库完成真实 MySQL 测试，但 WP5-B 真实锁验收、WP5-C 真实回滚验收仍需按既定验收流程记录和确认。
- WP5-D 真实并发测试已有 CI 通过证据，正式验收、WP5-E/F 对账及最终 PR5 合并验收仍未完成。

## 验证命令

本地占位凭据不可用时，仅执行测试源码编译和静态合同测试；真实 MySQL 并发结果以 GitHub Actions 隔离数据库门禁为准。

## 本次验证证据

```yaml
test_compile: BUILD_SUCCESS
static_contract:
  tests: 2
  failures: 0
  errors: 0
mysql_concurrency:
  tests: 8
  failures: 0
  errors: 0
full_maven_suite:
  tests: 492
  failures: 0
  errors: 0
  skipped: 0
ci:
  run_id: 33257167150
  result: success
  checks:
    - Guard and migration immutability
    - Maven verification and tested artifact
    - Flyway empty database gate
    - Flyway existing database gate
    - Docker runtime and migration gate
```

本地运行仍会因字面量 `${TEST_DB_USERNAME}` 无法连接数据库而阻塞；CI 已在真实 MySQL 隔离数据库中完成上述 492 项测试并全部通过。该结果作为开发阶段证据保存，不改变 WP5-D 或 PR5 的正式验收状态。
