# PR3 WP3：权限查询 Mapper 与单条判定骨架验收记录

状态：`PASS`

日期：2026-08-28

## 1. 验收范围

WP3 在 WP2 权限基础类型之上落地只读权限事实查询与 `PermissionService` 单条判定骨架，覆盖项目、任务、周复盘和团队成员四类资源。实现遵循 ADR-004 的“查询层只返回最小事实、服务层统一判定、缺失事实默认拒绝”边界。

本工作包不接入 Controller/现有业务 Service，不新增系统管理员旁路，不实现批量权限 API，也不修改数据库迁移。

上游输入：

- [ADR-004：统一 PermissionService 与查询边界](../architecture/ADR-004-permission-service.md)
- [ADR-003：系统角色、团队角色与租户 RBAC 边界](../architecture/ADR-003-role-boundaries.md)
- [阶段 1 权限矩阵](../authorization/permission-matrix.md)
- [PR3 WP2 验收记录](pr3-wp2-permission-foundation-acceptance-2026-08-28.md)

## 2. 实现结果

| 项目 | 结果 |
|---|---|
| `PermissionQueryMapper` | PASS：提供项目、任务、周复盘、团队成员只读事实查询；资源集合采用 `IN + foreach`，空集合安全返回空结果 |
| Mapper SQL 边界 | PASS：只查询项目/任务/团队/成员状态、角色、作者、受理人等判定事实；不读取密码、Token 或周复盘私密正文 |
| `PermissionServiceImpl` | PASS：统一实现项目、任务、周复盘、团队成员的单条 `require*` 判定方法；查询结果必须唯一，否则拒绝 |
| 项目规则 | PASS：个人项目仅 owner 可访问；团队项目要求 active team/member；OWNER/ADMIN 才具备管理动作 |
| 任务规则 | PASS：团队成员可查看；受理人可编辑内容/状态；OWNER/ADMIN 可转派、重排、删除；创建人字段不替代受理人语义 |
| 周复盘规则 | PASS：PRIVATE 仅作者可读写；TEAM 共享读取要求 active team/member；共享读取不返回私人正文字段 |
| 团队成员规则 | PASS：仅 OWNER 可变更角色；OWNER/ADMIN 可移除成员（ADMIN 不能移除 OWNER/ADMIN）；OWNER 不得离开团队 |
| 失败关闭与角色边界 | PASS：未登录、非法 ID、缺失/失效事实、未知角色均拒绝；`SYSTEM_ADMIN` 不绕过私人内容或团队成员边界 |
| API 与数据库 | PASS：本 WP3 未新增 endpoint；无 Flyway migration 变更；未接入现有 Controller/业务 Service |

## 3. 自动化测试

定向命令：

```text
.\mvnw.cmd '-Dtest=PermissionQueryMapperContractTest,PermissionServiceImplTest' test
```

定向结果：

```text
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖内容包括 Mapper namespace/statement/批量参数/敏感字段静态契约，以及 owner、团队成员、管理员、受理人、非受理人、作者、共享复盘、成员治理、非法事实和未登录等允许/拒绝路径。

编译命令：

```text
.\mvnw.cmd -DskipTests compile
```

编译结果：`BUILD SUCCESS`。

全量命令：

```text
.\mvnw.cmd test
```

Surefire 汇总：

```text
Tests run: 140, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

两份 CI workflow 的 `CI_EXPECTED_TEST_COUNT` 已同步为 `140`；`CI_EXPECTED_HISTORY_TOTAL` 保持 `2`。

## 4. 数据与安全边界证据

- Mapper XML 仅包含四条 `SELECT`，无 `INSERT`、`UPDATE`、`DELETE`；查询结果映射到 WP2 行模型，不复用实体或返回私密复盘字段。
- `PermissionServiceImpl` 不读取 `UserHolder`，actor 由调用方显式传入；不把 `SystemRole` 当作团队角色，也不提供 `SYSTEM_ADMIN` 默认放行。
- 单条判定通过批量形态传入单元素 ID，保持后续批量服务可复用的查询边界；零行、多行或失效关系均按拒绝处理。
- 真实数据库执行、Controller 接入、批量查询次数 Gate 和并发治理仍属于后续工作包；本 WP3 的 Mapper 证据为静态 SQL 契约测试，尚未声称已完成线上数据库集成验收。

## 5. 迁移不可变性证据

本 WP3 未修改或新增 Flyway migration。`git diff -- src/main/resources/db/migration` 为空；全量测试中的迁移不可变性检查继续通过。

## 6. 未实现范围

以下内容继续留在 PR3 后续工作包：

- Controller/现有业务 Service 的权限接入与统一异常出口；
- 批量权限 API、100 资源常数级查询次数 Gate；
- 任务转派并发、成员退出联动和审计事件；
- 系统管理员治理接口及真实数据库集成测试。

## 7. 结论

WP3 的权限事实查询、单条判定骨架、拒绝语义和回归测试均通过，本地验收为 `PASS`。当前改动仍保留在 `codex/stage1-pr3-permission-service` 工作区，尚未提交或推送；后续可进入 Controller/业务 Service 接入工作包。

## 8. 后续收口说明

WP3 的 `PASS` 仅表示本工作包的查询与单条判定骨架完成。进入 Controller/业务 Service 接入前，必须先完成 [WP4-A：权限内核加固](pr3-wp4a-permission-core-hardening-acceptance-2026-08-28.md)，以闭合 actor 生命周期、OWNER 一致性和周复盘作者生命周期合同。
