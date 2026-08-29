# PR5 / WP5-D4 任务变更与成员终止并发测试开发记录

日期：2026-08-29

## 状态

```yaml
scope: WP5-D4
implementation: completed
static_contract: passed
mysql_execution: pending
acceptance: pending
ci_expected_test_count: 492
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

- 当前测试环境仍使用字面量 `${TEST_DB_USERNAME}` 占位凭据，真实 MySQL 测试尚未执行。
- WP5-B 真实锁验收、WP5-C 真实回滚验收仍未完成。
- WP5-D 真实并发验收、WP5-E/F 对账及最终 PR5 合并验收仍未完成。

## 验证命令

在真实 MySQL 凭据可用前，只执行测试源码编译和静态合同测试；不得将编译结果记为 MySQL 并发通过。

## 本次验证证据

```yaml
test_compile: BUILD_SUCCESS
static_contract:
  tests: 2
  failures: 0
  errors: 0
mysql_concurrency:
  tests: 6
  failures: 0
  errors: 6
  reason: "Access denied for user '${TEST_DB_USERNAME}'@'localhost'"
```

真实 MySQL 测试已尝试启动，但在 `@Sql` 夹具执行前因占位凭据无法建立连接；因此没有任何并发场景获得通过证据，也没有将该结果记为 D4 验收失败或通过。
