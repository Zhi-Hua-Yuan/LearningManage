# PR7 / WP7-E3-3 页面聚焦刷新与迟到响应验收

日期：2026-09-04
状态：PASS / COMPLETED（本地门禁通过，待受保护 PR 合并）

## 1. 范围

- 任务页在窗口重新获得焦点或页面恢复可见时，针对当前打开任务触发去重的强制任务刷新。
- 刷新开始前对当前任务 capability fail-closed，并关闭依赖旧授权的任务级交互；请求失败时不恢复旧 capability。
- 团队项目在任务刷新前强制重新校验团队/项目上下文；确认失权后清空任务上下文并回退个人项目。
- 最新任务响应整体替换旧负责人、分配事实和 capability；任务被删除或不可访问时关闭详情。
- 通过会话、路由上下文、任务加载版本和组件挂载状态阻断迟到响应；脏编辑器或进行中的写操作完成后才排队刷新。

## 2. 实现与证据

前端变更：

- `src/views/task/TaskList.vue`
- `src/views/task/TaskList.assignment-dialog.test.ts`

新增运行时行为：

- `window.focus` 与 `document.visibilitychange` 监听，300ms 防抖和 single-flight 去重；
- 任务刷新前的 capability fail-closed、团队上下文 `force` 重验、删除任务详情收口；
- 脏标题/描述保留，刷新结果仅在当前会话、账号、路由上下文和任务仍然有效时写回；
- 卸载、登出或身份切换时取消待执行刷新。

自动化结果：

| 门禁 | 结果 |
|---|---|
| E3-3 聚焦/分配聚焦测试 | 25 passed |
| 前端全量 `npm run test:ci` | 58 files / 459 tests passed |
| `npm run type-check` | PASS |
| `npm run lint:ci` | PASS |
| `npm run build` | PASS；766 modules transformed |
| `npm run contract:test` / `contract:verify` | 3 passed / 44 operations valid |
| `npm run test:storage-policy` | 14 passed |

覆盖率：statements 84.83%；branches 75.13%；functions 85.71%；lines 88.49%。

关键回归：

- `PR7-T-045`：窗口 focus/visibility 触发一次刷新，刷新期间 capability 只读，最新 assignee/capability 替换旧事实；
- 重复 focus/visibility 事件只产生一次请求；任务从最新列表消失时关闭详情；
- 较新的任务刷新淘汰旧 focus 响应时，旧响应不再降级最新 capability；团队 focus 刷新强制调用 `{ force: true }`；
- 既有 `PR7-T-040～044` 以及 37 operation 回归保持通过。

## 3. 验收结论

```text
WP7-E3-3：PASS / COMPLETED（LOCAL_PASS）
PR7-T-045：PASS
S7-GAP-011：RUNTIME_CLOSED
WP7-E3：LOCAL_PASS（待最终受保护合并收口）
S1-R-013：OPEN（待受保护 PR / post-merge CI 后关闭）
下一主目标：WP7-F / PR7 最终验收
```
