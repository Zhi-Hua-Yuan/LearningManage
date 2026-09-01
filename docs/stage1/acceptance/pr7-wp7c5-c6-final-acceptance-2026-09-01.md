# PR7 / WP7-C5-6：聚合验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-09-01

## 1. 验收范围

C5-6 作为 C5 的聚合验收，不增加业务功能。验收对象为负责人历史分页/抽屉、安全降级、分配后历史刷新和任务状态幂等恢复，并汇总 PR7-T-010～T-027 的自动化与角色矩阵证据。

本次未新增后端接口、数据库迁移或前端 operation；`S1-R-013` 继续由 WP7-E 负责全局缓存、会话清理和多账号隔离收口。

## 2. 前端提交与合并证据

| 项目 | 结果 |
|---|---|
| 仓库 | `Zhi-Hua-Yuan/learning-manage-frontend` |
| C5 分支 | `pr7-c5-assignment-history` |
| C5 候选 HEAD | `d276387274ac4ccf325ff9881e82a82371aadde2` |
| PR | [#26](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/26) |
| PR head | `d276387274ac4ccf325ff9881e82a82371aadde2` |
| merge SHA | `57d9ff56bd8ce79b52101c6cfc99fb18ef7addf6` |
| PR required checks | 3/3 PASS |
| post-merge CI | [run 33495271342](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33495271342) |
| post-merge CI 结果 | PASS，3 个 job 全部通过 |

## 3. 本地完整门禁

| 门禁 | 结果 |
|---|---|
| `npm run type-check` | PASS |
| `npm run lint:ci` | PASS；0 warnings / 0 errors |
| `npm run contract:test` | PASS；3/3 |
| `npm run contract:verify` | PASS；44 operations |
| `npm run test:ci` | PASS；36 files / 256 tests |
| `npm run test:coverage` | PASS；81% statements / 71.14% branches / 78.58% functions / 84.36% lines |
| `npm run build` | PASS |
| `git diff --check origin/develop...HEAD` | PASS |

合同 SHA-256 保持：

```text
4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6
```

## 4. PR7-T-010～T-027 证据汇总

| 编号 | 证据 | 结果 |
|---|---|---|
| T-010～T-017 | capability normalizer、精确 action guard、TaskList capability UI、assignment dialog 测试；负责人/非负责人 MEMBER 由冻结 capability 组合和组件集成覆盖 | PASS |
| T-020 | `useTaskAssigneeCandidates`、候选 options、初始负责人创建测试 | PASS |
| T-021 | 分配变更清理项目/聚合任务缓存，替换任务与 capability，并刷新打开历史 | PASS |
| T-022 | `changed=false` 幂等结果不追加本地历史、不重复 changed 副作用 | PASS |
| T-023 | `50001` 回滚/刷新事实/保留目标并要求显式重新确认 | PASS |
| T-024 | `40300` 不登出，刷新任务和 capability | PASS |
| T-025 | username 缺失时按用户 ID/已删除用户安全降级 | PASS |
| T-026 | reason 以纯文本展示，禁止 `v-html` | PASS |
| T-027 | 状态网络重试复用原 `clientRequestId` 和完整 payload | PASS |

详细测试文件：

- `src/utils/taskAssignmentHistory.test.ts`
- `src/composables/useTaskAssignmentHistory.test.ts`
- `src/components/TaskAssignmentHistoryDrawer.test.ts`
- `src/composables/useTaskStatusMutation.test.ts`
- `src/views/task/TaskList.status-mutation.test.ts`
- `src/views/task/TaskList.assignment-dialog.test.ts`

## 5. 角色与异常验收

已通过前端组件/页面集成测试覆盖以下冻结角色矩阵：

- 个人所有者：个人负责人语义、内容/状态/重组/删除能力；
- 团队 OWNER/ADMIN：有效成员、未分配、负责人变更和历史刷新；
- 负责人 MEMBER：只允许内容和状态操作；
- 非负责人 MEMBER：任务只读；
- 状态网络不确定结果、CAS 冲突、`40300`、`40400` 和迟到响应。

同时验证了任务切换、团队/项目上下文切换和历史请求 stale response 不会跨任务写入。历史、成员和 status pending 状态不写入未隔离的持久化存储。

## 6. 关闭判定

C5-6 已满足 C5 聚合验收条件。前端受保护 PR 和合并后 CI 均通过，前端 `develop` 已同步至 `57d9ff5…` 且工作树干净。

因此 C5-6 可标记为 `PASS / COMPLETED / MERGED / CI_PASS`。WP7-C 的 C1～C5 链路已全部具备验收证据，WP7-C 可进入最终收口。

以下事项明确留给 WP7-E/WP7-F：

- `S1-R-013` 全局缓存、登出清理和多账号隔离关闭；
- PR7-A-008 运行时 OpenAPI 与最终 44 operation 的跨仓收口；
- WP7-D 周复盘隐私界面；
- PR7 最终发布与 Tag。
