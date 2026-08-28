# PR3 WP2：权限基础设施验收记录

状态：`PASS`

日期：2026-08-28

## 1. 验收范围

WP2 建立权限统一服务的基础类型与安全边界：稳定的拒绝异常、内部权限动作枚举、权限查询原始事实行模型，以及项目访问范围值对象。本工作包不实现 `PermissionService` 的业务判断、不新增 Mapper SQL、不接入 Controller，也不修改数据库迁移。

上游输入：

- [ADR-004：统一 PermissionService 与查询边界](../architecture/ADR-004-permission-service.md)
- [ADR-003：系统角色、团队角色与租户 RBAC 边界](../architecture/ADR-003-role-boundaries.md)
- [阶段 1 权限矩阵](../authorization/permission-matrix.md)
- [PR3 WP1 验收记录](pr3-wp1-system-role-acceptance-2026-08-28.md)

## 2. 实现结果

| 项目 | 结果 |
|---|---|
| `PermissionDeniedException` | PASS：固定映射 `FORBIDDEN_ERROR`（业务码 `40300`），不暴露内部判断细节 |
| `PermissionActionEnum` | PASS：定义 22 个内部动作，动作名稳定且不接受客户端任意字符串 |
| `TASK_CREATE` 兼容别名 | PASS：通过 `canonical()` 归一到 `PROJECT_CREATE_TASK`，避免重复授权语义 |
| 权限查询行模型 | PASS：分别覆盖项目、任务、周复盘、团队成员的原始 DB 事实；不含密码、Token 或私密复盘正文 |
| `ProjectAccessScope` | PASS：校验个人项目/团队项目不变量，提供 `canManage()` 基础管理判断 |
| API 与数据库 | PASS：无新增或删除 endpoint；无 Flyway migration 变更；无 Mapper SQL 变更 |
| 权限服务业务实现 | 按边界留至后续 WP，未提前引入旁路判断 |

## 3. 自动化测试

定向命令：

```text
.\mvnw.cmd '-Dtest=PermissionDeniedExceptionTest,PermissionActionEnumTest,PermissionModelContractTest' test
```

定向结果：

```text
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

全量命令：

```text
.\mvnw.cmd test
```

Surefire 汇总：

```text
Tests run: 123, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

两份 CI workflow 的 `CI_EXPECTED_TEST_COUNT` 已从 113 同步为 123；`CI_EXPECTED_HISTORY_TOTAL` 保持 2。

## 4. 安全与数据边界

- 权限拒绝统一使用稳定业务码 `40300`，异常消息固定，不回显资源存在性、角色或查询细节；
- 动作枚举为服务内部常量，WP2 不从请求参数动态解析动作；
- 查询行模型只保存后续判定所需的最小原始事实，周复盘模型明确排除 `reflection`、`nextPlan`、`sharedSummary`；
- `ProjectAccessScope` 拒绝缺失 actor、项目或 owner，且拒绝个人项目携带团队角色、团队项目缺失团队角色；
- WP2 未赋予 `SYSTEM_ADMIN` 隐式绕过，也未把 TeamRole 当作 SystemRole 使用。

## 5. 迁移不可变性证据

WP2 未修改或新增 Flyway migration。WP1/阶段 1 全量测试门禁继续覆盖已发布迁移不可变性；本工作包仅新增 Java 类型、测试和验收文档。

## 6. 未实现范围

以下内容继续留在 PR3 后续工作包：

- `PermissionQueryMapper` 及真实 SQL；
- 单条与批量 `PermissionService` 实现；
- Controller/Service 业务路径接入；
- 权限矩阵全覆盖与 100 资源 N+1 Gate；
- 系统管理员治理接口和审计事件。

## 7. 结论

WP2 的权限基础类型、异常语义、最小数据事实边界和契约测试均通过。本地验收为 `PASS`，可以进入 WP3：权限查询 Mapper 与 PermissionService 单条判定骨架。合并前仍需在 PR head 和合并后的 `develop` 上取得远程 CI 证据。
