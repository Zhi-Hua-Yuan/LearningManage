# PR3 合并收口记录

状态：`PASS`

日期：2026-08-28  
仓库：`Zhi-Hua-Yuan/LearningManage`  
目标分支：`develop`

## 1. PR3 合并链

PR3 采用连续受保护 PR 交付，均已合并到 `develop`：

| PR | merge commit | 合并后状态 |
|---|---|---|
| [#42](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/42) | `da1f04e1c058daf658e2219520dcee6156c7a095` | 已合并 |
| [#43](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/43) | `fe331f6f776d9c831c2422cc3a96c2dc6a7ba15e` | 已合并 |
| [#44](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/44) | `3816956a137f3d47720011005abef0433adf223b` | 已合并 |

最终 `develop`：

```text
3816956a137f3d47720011005abef0433adf223b
Merge pull request #44 from Zhi-Hua-Yuan/codex/stage1-pr3-permission-service
```

本地收口分支从该合并提交建立：`codex/stage1-pr3-final-acceptance`。

## 2. 合并后远端 CI

[Backend CI run 33173835276](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33173835276) 针对 PR #44 合并后的 `develop` 执行，结论为 `success`，5 个必需 Job 全部通过：

- Guard and migration immutability；
- Maven verification and tested artifact；
- Flyway empty database gate；
- Flyway existing database gate；
- Docker runtime and migration gate。

该次远端 Maven 门禁确认 193 项测试通过，包含 PR3 的真实 MySQL Mapper 与 100 资源 N+1 查询次数门禁。PR3 合并后没有回滚、跳过测试或降低保护规则。

## 3. 合同收口变更

- `S1-A-005`：`PENDING → PASS`；完整冻结权限矩阵新增 173 个参数化用例，允许 72、拒绝 101、`unauthorizedAllowedCount=0`。
- `S1-A-007`：保持 `PASS`；证据沿用 WP4-C 和 CI run `33167613189`。
- `S1-R-009`：`OPEN → CLOSED`；`SYSTEM_ADMIN` 无默认项目、任务、私人复盘内容后门。
- `S1-R-008`：保持 `OPEN`，剩余责任明确转 PR6。
- `S1-R-012`：保持 `OPEN`，WeeklyReview 统计的 `task.user_id` 旧口径明确转 PR6。

本次新增测试后，`.github/workflows/backend-ci.yml` 与 `.github/workflows/release-gate.yml` 的 `CI_EXPECTED_TEST_COUNT` 均为 `366`。这是当前源码的真实 Surefire 总数，不是沿用 PR #44 的旧数字 `193`。

## 4. 迁移与证据摘要

PR3 未修改任何 Flyway migration。V1/V2 当前摘要：

```text
E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9  src/main/resources/db/migration/V1__baseline_schema.sql
B40BD46F7CB303F8ED5B79AC86F78AE9078E78F8F3C26C91AAFA89F758683FE1  src/main/resources/db/migration/V2__stage1_business_semantics_and_permissions.sql
```

V1 发布摘要与既有清单一致；V2 保持 `PUBLISHED / IMMUTABLE`。本次收口没有连接正式数据库、没有写入凭据，也没有改变迁移文件。

## 5. 后续入口

PR3 至此正式收口。README 已更新为：

```text
PR3 SystemRole 与 PermissionService  completed
PR4 任务分配与历史                 next/in_progress
```

PR4 可以从 `develop=3816956a…` 开始；PR5 的成员退出/移除原子性、PR6 的周复盘隐私与剩余 AI/统计风险仍必须按各自合同验收。

