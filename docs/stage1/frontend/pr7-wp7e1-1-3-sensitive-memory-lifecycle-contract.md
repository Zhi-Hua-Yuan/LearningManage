# WP7-E1-1.3 Sensitive Memory Lifecycle Contract

状态：`FROZEN / IMPLEMENTED AS POLICY METADATA`

日期：2026-09-03

前置：

- [WP7-E1-1.1 Storage Asset Inventory](./pr7-wp7e1-1-storage-inventory.md)
- [WP7-E1-1.2 Storage Scope Contract](./pr7-wp7e1-1-2-storage-scope-contract.md)

本文件冻结 `S7-MEM-001`～`S7-MEM-011` 的生命周期 owner、获取点、派生状态、reset 覆盖、会话触发器和 stale response 保护。它只冻结责任边界和可审计不变量，不接入全局登出、401、账号切换或页面 focus 刷新。

## 1. 生命周期模型

敏感内存状态遵循：

```text
EMPTY
  -> acquire/load
LOADING
  -> actor + session + context validation
READY
  -> refresh / mutate
READY
  -> session end / actor change / access loss / context change / unmount
RESET
```

异步响应只有在请求开始时记录的 actor、session、资源上下文和 request revision 仍然有效时，才允许更新页面状态。失效响应不得恢复旧 capability、旧成员选项、旧共享摘要或旧 AI payload。

## 2. 机器策略位置

生命周期元数据与 E1-1.2 共用前端策略清单：

```text
learning-manage-frontend/scripts/storage-asset-policy.mjs
```

不变量测试：

```text
learning-manage-frontend/scripts/storage-asset-policy.test.mjs
```

每个 `S7-MEM-*` 资产现在必须包含：

```text
lifecycleOwner
acquisitionPoints
derivedSurfaces
currentReset.entrypoint
currentReset.coverage
currentReset.status
requiredResetTriggers
staleGuard.strategy
staleGuard.tokens
staleGuard.status
resetIntegrationStatus
```

## 3. 统一触发器

策略使用以下触发器：

```text
SESSION_END
AUTHENTICATION_REQUIRED
ACTOR_CHANGE
TEAM_ACCESS_LOST
PROJECT_CONTEXT_CHANGE
TASK_CONTEXT_CHANGE
REVIEW_CONTEXT_CHANGE
PERMISSION_DENIED
RESOURCE_NOT_FOUND
DIALOG_CLOSE
DRAWER_CLOSE
VIEW_UNMOUNT
```

所有 `MEMORY_ONLY` 资产至少必须声明：

```text
SESSION_END
ACTOR_CHANGE
```

网络异常不自动清除私人表单；会话结束和身份变化无条件清除敏感状态。

## 4. 生命周期责任矩阵

| Asset ID | Lifecycle owner | Current reset | Stale guard | Session integration | Target |
|---|---|---|---|---|---|
| S7-MEM-001 | `collaborationStore.currentUser` | `clearCollaborationContext`，清当前用户、加载状态和 pending promise | `sessionEpoch + startingEpoch` | `MISSING` | E2 |
| S7-MEM-002 | `collaborationStore.teams` | `clearCollaborationContext`，并支持团队级裁剪 | `sessionEpoch + actorId` | `MISSING` | E2 |
| S7-MEM-003 | `collaborationStore.teamProjectsByTeamId` | `invalidateTeamProjectsById`，清 bucket、promise、revision | `sessionEpoch + actorId + teamId + projectRevision` | `MISSING` | E3 |
| S7-MEM-004 | `collaborationStore.teamMembersByTeamId` | `invalidateTeamMembersById`，清 bucket、promise、revision | `sessionEpoch + actorId + teamId + memberRevision` | `MISSING` | E2 |
| S7-MEM-005 | `TaskList taskList/selectedTask` | 任务上下文 reset 与 `failClosedTaskCapabilities` | `taskLoadVersion + project/context`，当前为部分保护 | `MISSING` | E3 |
| S7-MEM-006 | `useTaskAssignmentHistory` | `reset`，清 taskId、records、分页、phase、error | `requestRevision + activeTaskId` | `MISSING` | E2 |
| S7-MEM-007 | `useTeamSharedReviews` | `reset`，清 teamId、records、分页、phase、error | `requestRevision + actorId + sessionEpoch + teamId` | `MISSING` | E2 |
| S7-MEM-008 | `WeeklyReview sensitive form state` | `onBeforeUnmount + resetSummaryDerivedState + resetAssociationContext`，当前为部分覆盖 | view/actor/review epochs，当前为部分保护 | `MISSING` | E2 |
| S7-MEM-009 | `aiPendingRegistry.requestMeta` | `resetAll`，清 requestMeta、status、error、consumedAt | `requestId + pending status` | `MISSING` | E2 |
| S7-MEM-010 | `aiPendingRegistry.responsePayload` | `resetAll`，清 responsePayload、status、error、consumedAt | `requestId + pending status` | `MISSING` | E2 |
| S7-MEM-011 | `useTaskAssignmentDraft` | `close / invalidateUnlessCurrent`，清 task、project、target、reason | `contextKey + taskId` | `MISSING` | E2 |

`MISSING` 只表示尚未接入全局 session lifecycle，不表示局部 reset 或局部 stale guard 不存在。

## 5. 派生状态归属

为避免页面复制后脱离生命周期管理，派生状态归属如下：

| 派生状态 | 归属资产 |
|---|---|
| 团队项目导航、团队项目路由上下文 | S7-MEM-003 |
| 负责人候选、复盘团队选择 | S7-MEM-004 |
| 任务详情 action controls、selected task capability | S7-MEM-005 |
| 负责人历史 drawer records | S7-MEM-006 |
| 团队共享复盘列表和卡片 | S7-MEM-007 |
| PRIVATE 表单、关联候选、辅助统计和任务快照 | S7-MEM-008 |
| AI pending 状态和 toast 上下文 | S7-MEM-009 |
| AI preview/result payload | S7-MEM-010 |
| 分配对话框成员选择和 reason | S7-MEM-011 |

每个派生状态只能有一个主要 lifecycle owner；跨资产组合状态必须同时满足各自 reset 条件。

## 6. 已验证的不变量

- `S7-MEM-001`～`S7-MEM-011` 全部是 `MEMORY_ONLY`；
- 全部 `persistenceAllowed=false`；
- 全部 `clearOnSessionEnd=true`；
- 全部 `staleGuardRequired=true`；
- 全部声明 `SESSION_END` 和 `ACTOR_CHANGE`；
- 全部具有具体 lifecycle owner、获取点、reset 入口、覆盖字段和 stale guard；
- AI pending reset 会清空业务 metadata 和完整 response payload；
- 负责人历史和共享复盘 reset 会清空 records 与分页状态；
- 任务缓存边界仍由 `normalizeCachedTaskRecords` 将 capability 降级为 deny-all；
- 机器策略资产总数仍为 26，未新增 operation。

## 7. 明确留给后续工作包的缺口

E1-1.3 不关闭以下事项：

- E2 将所有 `SESSION_END`、401、主动登出、token clear 和 actor change 接入统一 reset；
- E3 将 TaskList capability、团队项目和跨页面迟到响应接入全局 actor/session guard；
- E2/E3 提供 `PR7-T-042`～`PR7-T-044` 的运行态清理与多账号证据；
- E3 实现 `PR7-T-045` 的 focus refresh；
- `S1-R-013` 继续保持 `OPEN`。

## 8. E1-1.3 退出条件

满足以下条件后，E1-1.3 可标记完成：

1. 11 项内存资产均完成生命周期字段登记；
2. 所有派生状态均完成 owner 归属；
3. 每项均声明 reset 覆盖范围和统一触发器；
4. 每项均声明具体 stale guard 及当前完整度；
5. `storage-asset-policy.test.mjs` 全部通过；
6. E2/E3 缺口分配明确；
7. 不修改运行时会话清理语义、API operation 或数据库合同。

