# PR3 WP4-B：PermissionQueryMapper 真实 MySQL 集成测试验收记录

状态：`PASS`

日期：2026-08-28

## 1. 验收范围

WP4-B 在 WP4-A 权限内核加固基础上，使用真实 MySQL 8.0.41、已完成 Flyway V2 的测试数据库和 Spring Boot MyBatis 映射，验证 `PermissionQueryMapper` 的 SQL 可执行性、字段映射和生命周期事实边界。

本工作包不实现批量 `PermissionService` API、Controller/业务 Service 接入、N+1 查询门禁、缓存或新的 Flyway migration。

输入合同：

- [ADR-002：周复盘可见性与团队共享](../architecture/ADR-002-weekly-review-visibility.md)
- [ADR-003：系统角色、团队角色与租户 RBAC 边界](../architecture/ADR-003-role-boundaries.md)
- [ADR-004：统一 PermissionService](../architecture/ADR-004-permission-service.md)
- [阶段 1 权限矩阵](../authorization/permission-matrix.md)
- [PR3 WP4-A 验收记录](pr3-wp4a-permission-core-hardening-acceptance-2026-08-28.md)

## 2. 实现结果

| 项目 | 结果 |
|---|---|
| 真实 MySQL 测试类 | `PermissionQueryMapperMySqlTest`，使用 Spring Boot 测试上下文和真实 DataSource |
| V2 Fixture | `permission_mapper_v2_seed.sql`，固定主键/时间，仅包含 DML |
| 数据库安全护栏 | 测试要求数据库名称匹配 `_test` 或 `_ci_`，Flyway 成功版本必须为 `2` |
| actor 查询 | 覆盖有效、SYSTEM_ADMIN、已删除和不存在用户 |
| 项目查询 | 覆盖个人/团队、删除项目、删除团队、删除成员和多 ID 查询 |
| 任务查询 | 覆盖创建人与受理人区分、删除任务、团队生命周期和成员生命周期 |
| 周复盘查询 | 覆盖 PRIVATE、TEAM、作者退出、团队删除和敏感字段投影边界 |
| 团队成员查询 | 覆盖 actor/target 双侧角色及生命周期字段 |
| 空/缺失/重复 ID | 覆盖空集合、null 集合、缺失资源和重复 ID |
| Flyway migration | 无新增或修改 |

## 3. 测试证据

代码编译已通过：

```text
.\mvnw.cmd -B -ntp -DskipTests test-compile
BUILD SUCCESS
```

真实 MySQL 测试执行命令：

```text
.\mvnw.cmd '-Dtest=PermissionQueryMapperMySqlTest' test
```

全量测试命令：

```text
.\mvnw.cmd test
```

真实数据库测试必须在 MySQL 8.0.41、V2 schema 和 `TEST_DB_*` 环境变量已配置的环境执行。CI 复用 `backend-test` Job 在 Maven 前创建并迁移的 `learning_manage_ci_empty` 数据库；本地不得连接共享开发库或生产库。

CI 已完成真实数据库运行，结果如下：

```yaml
CI run: 33163865826
MySQL: 8.0.41
Database: learning_manage_ci_empty（CI 隔离数据库）
定向 MySQL 集成测试: 6 passed
全量测试: 178 passed
Failures: 0
Errors: 0
Skipped: 0
Maven verification: PASS
Surefire expected test count gate: PASS (178)
```

CI 还通过了 Flyway empty/existing database gate、迁移不可变护栏和 Docker runtime gate。详见 [PR #42 CI run](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33163865826)。

## 4. 查询与隔离边界

- 集成测试使用真实 MySQL，不使用 H2 或内存替代数据库；
- Flyway 在测试上下文中保持关闭，schema 由外部 Flyway 门禁预先迁移到 V2；
- Fixture 在每个测试方法前导入，并由测试事务回滚；
- Fixture 使用固定测试 ID 和固定时间，不依赖自增值或当前时间；
- 权限 Mapper 只返回原始事实，不执行允许/拒绝决策；
- 周复盘查询不读取 `reflection`、`next_plan`、`shared_summary`；
- 测试账号不要求 DELETE 或 DDL 权限；
- WP4-B 不声称已完成批量权限 API 或 100 资源查询次数 Gate。

## 5. CI 门禁

`backend-ci.yml` 和 `release-gate.yml` 的 `CI_EXPECTED_TEST_COUNT` 已更新为 `178`。CI 必须在 V2 数据库准备完成后执行完整 Maven 测试，并继续校验 Surefire 实际测试数等于门槛；不得通过跳过集成类或降低门槛规避测试。

## 6. 验收结论

WP4-B 的测试代码、V2 Fixture、数据库安全护栏和 CI 门槛已在隔离 MySQL 8.0.41 数据库完成验证，178 项测试全部通过，迁移与 Docker runtime 门禁全部通过。WP4-B 验收结论为最终 `PASS`，可以进入 WP4-C 批量权限 API 方案评审；WP4-C 的实现仍需单独遵守其批量查询、N+1 和响应边界合同。
