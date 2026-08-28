# PR3 WP4-C：批量权限与 N+1 查询治理验收记录

状态：`PASS`

日期：2026-08-28

## 1. 验收范围

WP4-C 在 WP4-A 权限内核加固和 WP4-B 真实 MySQL Mapper 集成的基础上，提供批量项目范围、任务可读 ID、严格全量任务可读校验和任务 capabilities 解析，并以 100 个资源查询次数门禁阻止逐资源 N+1。

本工作包不接入 Controller、现有业务 Service、Stats 或 AI，不修改 Flyway migration，不引入权限缓存，也不处理分配/退出并发治理。

输入合同：

- [阶段 1 需求合同](../requirements/stage1-requirements-contract.md)
- [阶段 1 权限矩阵](../authorization/permission-matrix.md)
- [ADR-004：统一 PermissionService](../architecture/ADR-004-permission-service.md)
- [WP4-A：权限内核加固](pr3-wp4a-permission-core-hardening-acceptance-2026-08-28.md)
- [WP4-B：PermissionQueryMapper MySQL 集成](pr3-wp4b-permission-mapper-mysql-integration-acceptance-2026-08-28.md)

## 2. 实现结果

| 项目 | 结果 |
|---|---|
| 批量项目范围 | `resolveProjectScopes(actorId, projectIds)`；按请求顺序去重，未授权/缺失资源省略 |
| 任务可读过滤 | `filterReadableTaskIds(actorId, taskIds)`；适用于服务端候选集 |
| 严格任务校验 | `requireAllTasksReadable(actorId, taskIds)`；任一缺失、删除或越权整体拒绝 |
| 任务 capabilities | `resolveTaskCapabilities(actorId, taskIds)`；复用同一批任务事实，不逐 VO 查询 |
| 输入边界 | actor、ID、空集合、重复 ID、最大 500 项和不可变返回值已固定 |
| 单批判定路径 | 单条项目/任务权限复用批量索引和同一内存判定内核 |
| 数据库迁移 | 无新增或修改 Flyway migration |

## 3. 查询预算合同

非空批量请求固定为一次 actor 查询加一次资源批量查询：

| 方法 | actor 查询 | 资源查询 | 总权限查询 |
|---|---:|---:|---:|
| `resolveProjectScopes` | 1 | 1 | 2 |
| `filterReadableTaskIds` | 1 | 1 | 2 |
| `requireAllTasksReadable` | 1 | 1 | 2 |
| `resolveTaskCapabilities` | 1 | 1 | 2 |

资源数量从 1 增加到 100 时，查询次数不得增加。批量方法不得循环调用单条 `require*` 方法。

## 4. 测试证据

已通过定向单元测试：

```text
.\\mvnw.cmd '-Dtest=PermissionServiceBatchTest,PermissionServiceImplTest' test
```

结果：Tests run 52，Failures 0，Errors 0，Skipped 0，`BUILD SUCCESS`。

覆盖内容：

- 100 个任务只调用一次 actor Mapper 和一次任务批量 Mapper；
- 个人所有者、团队 OWNER/ADMIN、受理 MEMBER、非受理 MEMBER 的 capabilities 矩阵；
- 全有或全无任务 ID 校验；
- 空集合、重复 ID、非法 ID、超过 500 项；
- Mapper 重复事实失败关闭；
- 返回 Map/Set 不可修改；
- 单条权限回归保持通过。

真实 MySQL 门禁测试：

```text
PermissionServiceBatchMySqlTest
```

该测试准备 100 个确定性任务，并使用测试专用 MyBatis 查询计数拦截器断言 actor 查询和任务批量查询各执行一次。PR #43 的隔离 MySQL 8.0.41 CI 已执行并通过，2 个测试全部通过。

## 5. CI 与迁移门禁

backend CI 和 release gate 的 `CI_EXPECTED_TEST_COUNT` 已从 `178` 更新为 `188`，Surefire 实际计数为 `188`；未跳过新增 MySQL 集成测试，未降低门槛。

WP4-C 仍要求：

- 全量 Maven 测试通过；
- 真实 MySQL 查询次数门禁通过；
- `git diff --check` 通过；
- Flyway migration diff 为空；
- Docker runtime 与已有 WP4-B 门禁继续通过。

PR #43 CI 证据：

- [GitHub Actions run 33167613189](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33167613189)
- Maven verification：188 passed，0 failures/errors/skipped；包含 `PermissionServiceBatchMySqlTest` 2 passed 和 `PermissionQueryMapperMySqlTest` 6 passed；
- Guard and migration immutability：`pass`；
- Flyway empty database gate：`pass`；
- Flyway existing database gate：`pass`；
- Docker runtime and migration gate：`pass`。

## 6. 未实现范围

- Controller、业务 Service、Stats、AI 入口接入；
- `TaskVO` 最终组装和跨仓前端变更；
- 周复盘关联资源批量保存；
- 权限缓存、审计事件和并发重新鉴权；
- 完整 PR3 参数化权限矩阵最终验收（S1-A-005）。

## 7. 验收结论

WP4-C 的 Java 批量接口、输入边界、单条/批量共享判定内核、100 资源固定查询次数、真实 MySQL 集成测试、迁移不可变性和 Docker runtime 门禁均已通过。WP4-C 验收结论为最终 `PASS`；S1-A-007 可标记为 `PASS`，S1-R-007 可关闭。
