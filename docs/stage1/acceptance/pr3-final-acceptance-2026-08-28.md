# PR3 最终合同验收记录

状态：`PASS`

日期：2026-08-28  
验收分支：`codex/stage1-pr3-final-acceptance`  
验收基线：PR #44 合并后的 `develop`，`3816956a137f3d47720011005abef0433adf223b`

## 1. 验收结论

PR3 的实现、受保护合并、合并后 CI 和合同证据均已闭环。冻结权限矩阵的参数化验收共执行 173 个用例，允许组合 72 个、拒绝组合 101 个；越权访问放行数为 `0`，因此 `S1-A-005=PASS`。

PR3 完成后，阶段 1 主目标切换为 PR4；PR4 负责任务分配与分配历史，不把 PR5/PR6 的未交付内容提前标记为完成。

## 2. S1-A-005 冻结矩阵结果

测试文件：`src/test/java/com/spt/learningmanage/service/impl/PermissionMatrixParameterizedTest.java`

| 矩阵域 | 参数化用例 | 允许组合 | 拒绝组合 | 结果 |
|---|---:|---:|---:|---|
| 项目及任务创建（含 `TASK_CREATE` 兼容别名） | 49 | 22 | 27 | PASS |
| 任务 | 56 | 27 | 29 | PASS |
| 团队及成员管理 | 38 | 15 | 23 | PASS |
| 周复盘 | 30 | 8 | 22 | PASS |
| 合计 | 173 | 72 | 101 | PASS |

矩阵覆盖：

- 个人项目所有者、个人项目外部用户；
- 团队 `OWNER`、`ADMIN`、`MEMBER`、团队外用户；
- 任务受理 MEMBER 与非受理 MEMBER 的内容、状态、重排、分配、删除和历史查看差异；
- 团队成员查看、项目管理、角色修改、成员移除和主动退出的 actor/target 组合；
- 私人复盘作者、指定团队三种角色、其他用户和 `SYSTEM_ADMIN`；
- `SYSTEM_ADMIN` 对项目、任务和复盘没有默认内容访问后门。

测试报告摘要：

```text
PermissionMatrixParameterizedTest
Tests run: 173, Failures: 0, Errors: 0, Skipped: 0
unauthorizedAllowedCount=0
BUILD SUCCESS
```

## 3. PR3 工作包与 Gate

| 项目 | 结果 | 证据 |
|---|---|---|
| WP1 SystemRole | PASS | [WP1 验收记录](pr3-wp1-system-role-acceptance-2026-08-28.md) |
| WP2 权限基础类型 | PASS | [WP2 验收记录](pr3-wp2-permission-foundation-acceptance-2026-08-28.md) |
| WP3 单条权限查询 | PASS | [WP3 验收记录](pr3-wp3-permission-query-single-decision-acceptance-2026-08-28.md) |
| WP4-A 权限内核加固 | PASS | [WP4-A 验收记录](pr3-wp4a-permission-core-hardening-acceptance-2026-08-28.md) |
| WP4-B MySQL Mapper | PASS | [WP4-B 验收记录](pr3-wp4b-permission-mapper-mysql-integration-acceptance-2026-08-28.md) |
| WP4-C 批量权限与 N+1 | PASS | [WP4-C 验收记录](pr3-wp4c-permission-batch-n-plus-one-acceptance-2026-08-28.md) |
| WP4-D 业务 Service、Stats、AI 接入 | PASS | [WP4-D 验收记录](pr3-wp4d-business-service-permission-integration-acceptance-2026-08-28.md) |
| `S1-A-005` 冻结权限矩阵 | PASS | 本记录第 2 节及参数化测试 |
| `S1-A-007` 100 资源 N+1 门禁 | PASS | WP4-C；CI run `33167613189` |
| V1/V2 迁移不可变 | PASS | PR2 记录；本记录第 5 节 |

## 4. 测试与 CI 证据

PR3 合并前后的远端证据：

| 证据 | 结果 |
|---|---|
| PR #42 | 已合并，merge commit `da1f04e1c058daf658e2219520dcee6156c7a095` |
| PR #43 | 已合并，merge commit `fe331f6f776d9c831c2422cc3a96c2dc6a7ba15e` |
| PR #44 | 已合并，merge commit `3816956a137f3d47720011005abef0433adf223b` |
| PR #44 合并后 Backend CI | [Run 33173835276](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33173835276)，5 个 Job 全部成功 |
| PR #44 既有 Maven 门禁 | 193 tests，0 failures/errors/skipped |
| PR #43 N+1 门禁 | [Run 33167613189](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33167613189)，真实 MySQL 查询次数通过 |
| 本次矩阵定向测试 | 173 tests，0 failures/errors/skipped |
| 本次本地全量 Maven | 366 tests；358 passed，8 个 MySQL 用例因本机未配置 `${TEST_DB_USERNAME}` 连接凭据报错 |

本次新增测试后，两份 CI workflow 的 `CI_EXPECTED_TEST_COUNT` 已更新为 `366`。受保护 CI 在具有托管 MySQL 账号的环境中执行完整 366 项；本机不使用共享开发库，也不伪造或记录数据库凭据。

## 5. 数据库不可变性与摘要

PR3 没有修改 Flyway migration。当前文件 SHA-256（大写）为：

```text
E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9  src/main/resources/db/migration/V1__baseline_schema.sql
B40BD46F7CB303F8ED5B79AC86F78AE9078E78F8F3C26C91AAFA89F758683FE1  src/main/resources/db/migration/V2__stage1_business_semantics_and_permissions.sql
```

V1 发布清单中的摘要仍为 `E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9`；V2 继续保持已发布不可变。PR3 的 `git diff --check` 和 migration diff 检查通过。

## 6. 风险状态校准

| 风险 | PR3 收口状态 |
|---|---|
| `S1-R-009` | `CLOSED`：完整矩阵包含 `SYSTEM_ADMIN` 的项目、任务、复盘越权组合，放行数为 0 |
| `S1-R-008` | `OPEN`：PR3 已接入现有 AI ID 批量校验；剩余 AI 授权回归、周复盘润色和草稿确认责任由 PR6 关闭 |
| `S1-R-012` | `OPEN`：Stats 已使用 `assignee_user_id`；WeeklyReview 的完成统计和重点项目查询仍有 `task.user_id` 旧口径，由 PR6 修正并回归 |

以上 OPEN 风险不阻塞 PR3，但已保留明确目标 PR，不能被解释为阶段 1 全部完成。

## 7. 合同更新

机器合同 `docs/stage1/acceptance/stage1-acceptance-contract.json` 已将 `S1-A-005` 从 `PENDING` 更新为 `PASS`，并登记本记录、173 项参数化测试和 366 项 CI 测试门槛作为证据。阶段 1 其他尚未到目标 PR 的 Gate 保持原状态。

