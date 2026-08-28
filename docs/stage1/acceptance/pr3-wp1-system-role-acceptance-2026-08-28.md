# PR3 WP1：SystemRole 验收记录

状态：`PASS`

日期：2026-08-28

## 1. 验收范围

WP1 建立平台级系统角色的唯一 Java 表达，将注册逻辑从字符串字面量迁移到 `SystemRoleEnum`，并验证运行时只接受 V2 规范值。WP1 不实现资源权限判断、`PermissionService`、系统管理员绕过、角色管理接口或数据库迁移。

上游输入：

- [ADR-003：系统角色、团队角色与租户 RBAC 边界](../architecture/ADR-003-role-boundaries.md)
- [阶段 1 权限矩阵](../authorization/permission-matrix.md)
- [PR2 合并收口记录](pr2-merge-closure-2026-08-28.md)

## 2. 实现结果

| 项目 | 结果 |
|---|---|
| 新增 `SystemRoleEnum` | PASS：仅定义 `USER`、`SYSTEM_ADMIN` |
| 规范值解析 | PASS：精确、区分大小写 |
| 旧小写值 | PASS：`user/admin` 在运行时不被接受 |
| 空值与空白值 | PASS：不被接受，不执行 trim 或大小写归一化 |
| 注册默认角色 | PASS：通过 `SystemRoleEnum.USER.getValue()` 写入 |
| 用户资料 DTO | PASS：不暴露 `role/userRole/systemRole` 字段 |
| `User.userRole` 类型 | 保持 `String`，未引入 MyBatis Enum TypeHandler 变化 |
| API | 无新增或删除 endpoint，现有 JSON 角色值保持字符串 |

## 3. 自动化测试

定向命令：

```text
.\mvnw.cmd '-Dtest=SystemRoleEnumTest,UserServiceImplTest' test
```

定向结果：

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

全量命令：

```text
.\mvnw.cmd test
```

Surefire 汇总：

```text
Tests run: 113, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

两份 CI workflow 的 `CI_EXPECTED_TEST_COUNT` 已从 104 同步为 113；`CI_EXPECTED_HISTORY_TOTAL` 保持 2。

## 4. 发布迁移不可变证据

WP1 未修改或新增 Flyway migration。全量测试中的 `FlywayPublishedMigrationImmutabilityTest` 已通过。

```text
V1 SHA-256 E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9
V2 SHA-256 B40BD46F7CB303F8ED5B79AC86F78AE9078E78F8F3C26C91AAFA89F758683FE1
Flyway history expected total 2
```

## 5. 安全边界

- `SystemRoleEnum.fromValue` 不使用 `trim`、`toUpperCase` 或 `equalsIgnoreCase`；
- 未知值不会被静默归类为 `USER`；
- 只有精确的 `SYSTEM_ADMIN` 可被枚举识别为系统管理员；
- WP1 不赋予 `SYSTEM_ADMIN` 任何项目、任务、团队或复盘访问权；
- 普通注册接口不能选择或提交系统角色；
- 尚未启用的租户 RBAC 五表未被接入。

## 6. 未实现范围

以下内容继续留在 PR3 后续工作包：

- `PermissionDeniedException` 与稳定业务码 `40300`；
- 权限动作枚举；
- `ProjectAccessScope` 和权限查询行模型；
- `PermissionQueryMapper`；
- 单条与批量 `PermissionService`；
- 权限矩阵和 100 资源 N+1 Gate。

## 7. 结论

WP1 的系统角色语义、注册兼容性、严格解析和回归测试均通过，可以进入 PR3 下一个权限基础设施工作包。本地 PASS 不替代后续 PR head 和合并后 `develop` 的远程 CI 证据。
