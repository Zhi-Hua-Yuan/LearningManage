# PR3 WP4-C：批量权限与 N+1 查询治理验收记录

状态：`IN_PROGRESS`

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

已新增真实 MySQL 门禁测试：

```text
PermissionServiceBatchMySqlTest
```

该测试准备 100 个确定性任务，并使用测试专用 MyBatis 查询计数拦截器断言 actor 查询和任务批量查询各执行一次。本机当前未配置 CI 专用测试数据库凭据，因此该测试等待远程 CI 在隔离 MySQL 8.0.41 环境执行，不能以本地失败结果替代远程证据。

## 5. CI 与迁移门禁

backend CI 和 release gate 的 `CI_EXPECTED_TEST_COUNT` 已从 `178` 更新为预计的 `188`，必须以 Surefire 实际计数为准；不得跳过新增 MySQL 集成测试或降低门槛。

WP4-C 仍要求：

- 全量 Maven 测试通过；
- 真实 MySQL 查询次数门禁通过；
- `git diff --check` 通过；
- Flyway migration diff 为空；
- Docker runtime 与已有 WP4-B 门禁继续通过。

## 6. 未实现范围

- Controller、业务 Service、Stats、AI 入口接入；
- `TaskVO` 最终组装和跨仓前端变更；
- 周复盘关联资源批量保存；
- 权限缓存、审计事件和并发重新鉴权；
- 完整 PR3 参数化权限矩阵最终验收（S1-A-005）。

## 7. 当前结论

WP4-C 的 Java 批量接口、输入边界、单条/批量共享判定内核和单元查询预算证据已完成。真实 MySQL 100 资源查询次数门禁及远程 CI 证据待完成；在该证据通过前，WP4-C 状态保持 `IN_PROGRESS`，S1-A-007 和 S1-R-007 不提前标记为 `PASS/CLOSED`。
