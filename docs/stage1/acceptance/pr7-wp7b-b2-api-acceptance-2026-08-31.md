# PR7 / WP7-B2 API 客户端验收记录

状态：`IMPLEMENTED / LOCAL_VALIDATION_PASS`

日期：2026-08-31

## 1. 验收结论

WP7-B2 已完成。前端 API 层已接入冻结合同中的 7 个新增 operation，并复用 WP7-B1 的 Wire 类型、ID 边界和共享复盘隐私白名单。

B2 未创建 Store、未修改页面和导航运行逻辑。任务状态迁移使用独立的 `/task/status/change` 客户端；现有旧 `updateTaskApi` 作为页面迁移期间的兼容出口保留，并新增不含 `status` 的 `updateTaskContentApi`。

## 2. 基线与变更

| 项目 | 值 |
|---|---|
| 前端仓库 | `Zhi-Hua-Yuan/learning-manage-frontend` |
| B1 提交 | `860b017` |
| B2 提交 | `753dc45` |
| Stage 0 operation | `37` |
| 新增 operation | `7` |
| 当前 operation | `44` |
| 当前合同 SHA-256 | `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |

新增 operation：

- `GET /team/my`
- `GET /team/{teamId}/members`
- `GET /project/team/list`
- `POST /task/assign`
- `GET /task/{taskId}/assignment-history`
- `POST /task/status/change`
- `GET /review/team`

## 3. 主要交付物

- `src/api/team.ts`
- `src/api/guards.ts`
- `src/api/pr7-api.test.ts`
- `src/api/project.ts`
- `src/api/task.ts`
- `src/api/review.ts`
- `src/api/user.ts`
- `src/utils/request.ts`
- `contracts/frontend-api-contract.stage0.json`
- `scripts/ci/api-contract.test.mjs`

## 4. 合同与安全边界

- Stage 0 的 37 个 operation 全部保留，新增集合精确为 7 个。
- 动态 `teamId`、`taskId` 先经过正整数/安全整数 ID 校验，再进行 URL 编码。
- 团队项目查询使用后端实际参数 `pageNum/pageSize`；负责人历史和团队共享复盘使用 `current/size`。
- `expectedAssigneeUserId` 在任务分配请求中为必填属性，显式 `null` 不会被清理。
- 团队成员 Wire 使用后端真实字段 `joinTime`；`teamId` 由读取上下文注入规范化模型。
- 团队 Wire/context 增加团队角色，状态迁移结果对齐后端 `changed` 字段。
- `updateTaskContentApi` 不接受 `status`；状态迁移只能使用 `/task/status/change`。
- `40300` 和 `40101` 被识别为权限拒绝，不会清除登录令牌或跳转登录页。
- 团队共享复盘只使用公开白名单类型，不暴露 `reflection`、`nextPlan`、`taskIds`。
- 未接入 `/team/leave` 或 `/team/member/remove`。

## 5. 门禁证据

| 门禁 | 结果 | 证据 |
|---|---|---|
| TypeScript 类型检查 | `PASS` | `npm run type-check` |
| B2 API 聚焦测试 | `PASS` | 9 tests；含 40300、null 前置条件、动态路径和状态迁移路由 |
| 全量 Vitest | `PASS` | 9 files / 60 tests passed |
| API 合同测试 | `PASS` | 3 subtests passed |
| API 合同校验 | `PASS` | 44 operations；SHA-256 为 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |
| Oxlint / ESLint | `PASS` | 0 warnings / 0 errors |
| 视图与任务缓存回归检查 | `PASS` | 两项脚本通过 |
| 生产构建 | `PASS` | Vite build 完成 |

## 6. 后续准入

WP7-B2 已完成，WP7-B 整体仍保持执行中。下一步进入 B3：用户、团队、项目上下文 Store 与恢复流程；B3 应使用本记录中的 44-operation 合同和 B1/B2 规范化边界。
