# PR7 / WP7-B5-2 任务写路径修复验收记录

状态：`IMPLEMENTED / LOCAL_VALIDATION_PASS`

日期：2026-08-31

## 1. 验收结论

WP7-B5-2 已完成本地实现与门禁验证。任务页面的状态变更已切换到独立幂等接口，任务内容/重组更新已切换到显式内容 DTO，旧的带 `status` 更新出口已删除。

本工作包未修改后端接口、数据库迁移、任务 capability、负责人交互或周复盘界面。远端 PR、受保护 CI 和 WP7-B 总体验收继续由后续收口步骤完成。

## 2. 变更范围

前端基线：`e96941d`（WP7-B4）

主要交付物：

- `src/api/task.ts`
- `src/views/task/TaskList.vue`
- `src/utils/taskWrite.ts`
- `src/utils/taskWrite.test.ts`
- `src/api/pr7-api.test.ts`
- `scripts/check-task-cache-consistency.mjs`

## 3. 写路径合同

- 状态变更统一调用 `POST /task/status/change`；请求包含 `taskId`、`targetStatus`、`expectedStatus` 和新的 `clientRequestId`。
- 成功后以 `finalStatus`、`completedAt` 为准，并强制刷新当前任务事实。
- 标题、描述、优先级、截止日期和里程碑统一调用 `updateTaskContentApi`。
- `/task/update` 请求不再携带 `status`，页面不再调用 `updateTaskApi`。
- 内容更新使用显式字段，不展开完整 Task 对象。
- 非法状态响应按合同错误处理并恢复旧状态。

## 4. 本地门禁

| 门禁 | 结果 |
|---|---|
| `npm run contract:test` | `3/3 PASS` |
| `npm run contract:verify` | `44 operations PASS` |
| `npm run test:ci` | `13 files / 87 tests PASS` |
| `npm run test:coverage` | `68.97% statements / 59.11% branches` |
| `npm run type-check` | `PASS` |
| `npm run lint:ci` | `0 warnings / 0 errors` |
| `npm run build` | `PASS` |
| `git diff --check` | `PASS` |

## 5. 后续准入

B5-2 可进入 B5-3 类型合同修正和 WP7-B 最终 PR/CI 收口。WP7-B 在前端受保护合并、远端 CI 和总验收记录完成前仍保持 `执行中`。
