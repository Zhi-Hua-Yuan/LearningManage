# PR4 最终合同验收记录

状态：`PASS / COMPLETED`

日期：2026-08-29
仓库：`Zhi-Hua-Yuan/LearningManage`
目标分支：`develop`

## 1. 验收结论

PR4 已完成任务身份、初始分配、负责人变更、负责人历史查询以及负责人变更链路的
并发、事务和审计对账验收。

PR #49 已合并，合并提交为：

```text
5d5c4a23dd01b5a272054862f936d32bb9ad5beb
```

合并验收依据为 432 个 Surefire 测试，`0 failures`、`0 errors`；双并发 CAS、事务回滚、
no-op 和负责人审计对账均通过。D2-E 验收记录中保存了 CI run `33239260276` 及其
隔离 MySQL 证据。

因此 PR4 最终合同验收结论为 **PASS**。

## 2. 工作包验收矩阵

| 工作包 | 交付内容 | 状态 | 证据 |
|---|---|---|---|
| WP4-A | 任务身份模型 | PASS | [WP4-A 验收记录](pr4-wp4a-task-identity-acceptance-2026-08-28.md) |
| WP4-B | 创建与初始分配 | PASS | [WP4-B 验收记录](pr4-wp4b-initial-assignment-acceptance-2026-08-28.md) |
| WP4-C | ASSIGN、REASSIGN、UNASSIGN | PASS | [WP4-C 验收记录](pr4-wp4c-task-assignment-acceptance-2026-08-29.md) |
| WP4-D1 | 负责人历史查询契约冻结 | PASS | [D1 验收记录](pr4-wp4d1-assignment-history-contract-acceptance-2026-08-29.md) |
| WP4-D2-A | 负责人历史分页 Mapper | PASS | [D2-A 验收记录](pr4-wp4d2a-assignment-history-mapper-acceptance-2026-08-29.md) |
| WP4-D2-B | 负责人历史查询 Service | PASS | [D2-B 验收记录](pr4-wp4d2b-assignment-history-service-acceptance-2026-08-29.md) |
| WP4-D2-C | 负责人历史查询 Controller/API | PASS | [D2-C 验收记录](pr4-wp4d2c-assignment-history-controller-acceptance-2026-08-29.md) |
| WP4-D2-E | CAS、事务与审计对账 | PASS | [D2-E 验收记录](pr4-wp4d2e-assignment-consistency-acceptance-2026-08-29.md) |

## 3. 已交付能力

- 创建人与负责人语义分离，创建时执行统一初始分配；
- `POST /api/task/assign` 支持 `ASSIGN`、`REASSIGN`、`UNASSIGN`；
- `expectedAssigneeUserId` 显式支持 null-safe CAS；
- 负责人变更与 `task_assignment_log` 同事务写入；
- no-op 不更新任务、不产生审计日志；
- reason 具备 trim、长度和控制字符校验；
- `GET /api/task/{taskId}/assignment-history` 支持固定排序分页；
- 历史查询执行统一权限检查，且不返回隐私字段；
- 未分配用户和已删除用户遵循冻结的 null 语义；
- 历史查询不存在按记录逐条授权或逐条查询用户的 N+1；
- 真实 MySQL 已验证并发 CAS、事务回滚、no-op 和审计账实一致性。

## 4. 合同 Gate

| Gate | 结果 |
|---|---|
| 无效当前负责人 | 0 |
| 每个真实负责人变更均可审计 | PASS |
| 双并发 CAS | 1 个成功、1 个冲突 |
| 竞争失败请求新增日志 | 0 |
| 日志写入失败后的半状态 | 0 |
| no-op 任务更新 | 0 |
| no-op 新增日志 | 0 |
| 孤儿日志 | 0 |
| 非法动作转换 | 0 |
| 日志链断裂 | 0 |
| 最新日志与任务负责人不一致 | 0 |
| 未授权历史访问放行 | 0 |
| 历史隐私字段泄漏 | 0 |

## 5. 测试、CI 与迁移证据

```text
Surefire tests: 432
Failures:       0
Errors:         0
```

D2-E 的隔离 MySQL CI 已执行双并发 CAS、真实事务回滚、no-op 和审计对账。V1/V2
Flyway migration 在 PR4 全程未修改，V2 继续保持 `PUBLISHED / IMMUTABLE`。

PR #50 已将本记录及合并收口记录合并到 `develop`，最终合并提交为
`f6951cc8d986a2bd30fc352f37e1bec7a773af17`。合并后 Backend CI
[run 33240816476](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33240816476)
的 5 个必需 Job 全部成功。

本地缺少 `${TEST_DB_USERNAME}` 时，不能以本地连接失败替代 CI 结果；本记录只采信
受保护 CI 的 432 项完整结果。

## 6. 合同与风险状态

| 项目 | PR4 收口状态 |
|---|---|
| `S1-A-003` | `PASS` |
| `S1-R-014` | `CLOSED` |
| `S1-R-003` | `OPEN`，成员退出/移除竞争交由 PR5 |
| `S1-R-008` | `OPEN`，剩余 AI 授权和复盘责任交由 PR6 |
| `S1-R-012` | `OPEN`，周复盘统计旧口径修正交由 PR6 |

PR4 不提前关闭 PR5、PR6 或 PR8 的合同 Gate。

## 7. 后续入口

PR4 收口后，阶段 1 下一主目标切换为 PR5：成员主动退出、成员移除、未完成任务
原子解除分配，以及该流程与普通负责人变更之间的并发语义。PR5 必须继承 PR4
已冻结的负责人历史查询契约和审计动作定义。
